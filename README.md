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

Invoices are created in USD. The callback requires an unexpired HS256 JWT signed
with `TRYBIT_SECRET_KEY`, then retrieves the invoice through the authenticated
TryBit API. The event's order, status and `amount_usd` come from that API response,
not from the untrusted notification. Cryptocurrency amounts are never used as USD.

| TryBit invoice status | Redis event status |
| --- | --- |
| `paid`, `overpaid` | `COMPLETED` |
| `created`, `partial` | `PENDING` |
| `canceled` | `CANCELLED` |

Event example:

```json
{"id":"YOUR_ORDER_ID","amount":100.25,"type":"TRYBIT","status":"COMPLETED"}
```

As with Heleket, the event amount is the original invoice amount (USD), including
for partial or excess payments. `partial` does not complete the payment. TryBit
documents POSTBACK delivery after invoice confirmation; these mappings do not
imply that it sends a webhook for every intermediate or canceled state. There is
no scheduled status polling. Repeated valid notifications may repeat Redis events.
The consumer must recognize the new `TRYBIT` top-up type before deployment.

The callback returns HTTP 200 with `true` after Redis accepts the event. Invalid
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

Verified locally: 15 automated tests passed against local mocks/fixtures; the
separate live check created and retrieved invoice `INV-SRDS4ITE`. Actual provider
POSTBACK delivery and the external Redis consumer were not exercised.
