# Payment service

Creates hosted payment links and publishes provider notifications to the existing
Redis Stream (`youtrade-payment`, field `payload`). The consumer applies balance
updates and must handle redelivery idempotently by order `id`, as with Heleket.

## TryBit API V2

Copy `.env.example` to `.env` and fill in `TRYBIT_API_KEY`, `TRYBIT_SHOP_ID` and
`TRYBIT_SECRET_KEY` from the project's **Integration & API** settings. `.env` and
`.env.*` are ignored by Git; `.env.example` contains no credentials. Spring loads
`.env` from the working directory as a properties file: use unquoted `KEY=value`
lines, without `export`. Environment variables take precedence over the file.
Existing Heleket and Redis settings are still required to run the whole service.
Docker Compose currently loads `default.env`; put these variables there when
using Compose, or supply `.env` through the container's environment.

Configure the project's notification URL in the TryBit dashboard:

`https://youtradecs.xyz/webhook/trybit/notification`

The notification URL is a **project setting**, not an invoice-creation parameter.
Both JSON (recommended) and `application/x-www-form-urlencoded` POSTBACKs are
supported, including a trailing slash on the callback URL.

Create a payment with:

```http
POST /payment/create/trybit?id=YOUR_ORDER_ID&amount=100.25
```

Response (same shape as Heleket):

```json
{"id":"YOUR_ORDER_ID","amount":100.25,"type":"TRYBIT","url":"https://pay.trybit.com/INVOICE_ID"}
```

Invoices are created in USD with a one-hour payment lifetime, explicitly sent as
`add_fields.time_to_pay: {"hours": 1, "minutes": 0}`.
The callback requires an unexpired HS256 JWT signed
with `TRYBIT_SECRET_KEY`, then retrieves the invoice through the authenticated
TryBit API. The event's order, status and `amount_usd` come from that API response,
not from the untrusted notification. Cryptocurrency amounts are never used as USD.

| TryBit invoice status | Redis event status |
| --- | --- |
| `paid`, `overpaid` | `COMPLETED` |
| `created`, `partial` | `PENDING` |
| `canceled`, `failed` | `CANCELLED` |

Event example:

```json
{"id":"YOUR_ORDER_ID","amount":100.25,"type":"TRYBIT","status":"COMPLETED"}
```

As with Heleket, the event amount is the original invoice amount (USD), including
for partial or excess payments. `partial` does not complete the payment. TryBit
documents POSTBACK delivery after invoice confirmation; these mappings do not
imply that it sends a webhook for every intermediate or canceled state.

A fallback poll checks the provider's invoice list every 60 seconds (fixed delay,
first run after 10 seconds). It scans invoices created today and yesterday in UTC,
including invoices created before this service restart, and publishes `CANCELLED`
for `canceled` or `failed` invoices with a real order ID. It does not replay paid
or pending invoices from the history. Pagination uses batches of 100, with a safety
limit of 100 pages per pass; reaching that limit produces a warning.

Cancellation events from polling and webhooks share a concurrent in-memory cache
through the common `TrybitPaymentService.processInvoice(...)` entry point. Invoice
validation, status mapping, cancellation deduplication and event publication are
separate steps; polling selects the `CANCELLATIONS_ONLY` processing mode.
The cache retains entries for three days. Redis failures do not populate it, so the next
poll retries publication. There is no new database. After a restart or across
multiple instances, a cancellation can be delivered again; the consumer must
deduplicate by order ID. Other repeated valid webhooks may also repeat events.

Set `TRYBIT_POLL_ENABLED=false` to disable the fallback, or adjust
`TRYBIT_POLL_INTERVAL_MS` (default `60000`). `TRYBIT_POLL_LOOKBACK_DAYS` accepts 1
or 2 (default 2). A polling failure is logged and retried on the next pass. Old
invoices outside this creation-date window are not checked by the fallback.
The consumer must recognize the new `TRYBIT` top-up type before deployment.

Authenticated notifications with a missing, blank, null or `None` order ID are
logged and acknowledged with HTTP 200 and an empty body. They do not trigger an
API lookup or a Redis event (and therefore do not trigger downstream WebSocket
notifications). Signature verification still applies to these notifications.

The callback returns HTTP 200 with `true` after Redis accepts a regular order event. Invalid
signatures return 401, malformed or mismatched orders 400, upstream failures or
unknown statuses 502. Missing TryBit configuration returns 503, while Heleket
remains available. API/Redis failures are not acknowledged as successful payments.

Official contract:
[authorization](https://docs.trybit.com/api-reference-v2/authorization),
[create invoice](https://docs.trybit.com/api-reference-v2/create-invoice),
[invoice information](https://docs.trybit.com/api-reference-v2/invoice-information),
[POSTBACK](https://docs.trybit.com/api-reference-v2/postback).

## Verification

Use JDK 25 and run `mvn test` for the MVC and signature checks. To additionally
exercise the real HTTP client against the local provider fixture, start:

```sh
postman simulation run postman/simulations/trybit.sim.yaml --port 18450 --no-history --output ndjson
```

Then, in another terminal:

```sh
mvn -Dtrybit.test.base-url=http://localhost:18450/trybit/v2 package
```

The fixture tests creation, confirmation, USD mapping and upstream errors without
calling the real provider. Redis writes are mocked and their payload is asserted.
The separate `mvn -Dtrybit.live-check=true -Dtest=TrybitLiveApiTest test` check reads
credentials from `.env` and creates **one new unpaid USD 1.00 invoice per run** at
the real provider, then reads it back. It never pays an invoice or writes to Redis.
It is disabled by default.

Verified locally: 39 automated tests passed against local mocks/fixtures; the
separate live check created and retrieved invoice `INV-SRDS4ITE`. Actual provider
POSTBACK delivery and the external Redis consumer were not exercised.
