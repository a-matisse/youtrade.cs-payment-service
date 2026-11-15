// Local fixtures derived from docs.trybit.com/api-reference-v2/{create-invoice,invoice-information}.
// These control routes are test-only; no provider credentials or real transfers are used.
const http = require('http');
const json = { 'Content-Type': 'application/json' };

http.createServer(async (req, res) => {
  const reply = (code, body) => { res.writeHead(code, json); res.end(JSON.stringify(body)); };
  try {
    const body = req.body || {};
    if (req.method === 'POST' && req.url === '/v2/invoice/create') {
      if (req.headers.authorization !== 'Token test-api-key') return reply(401, { status: 'error' });
      if (body.shop_id !== 'test-shop' || body.currency !== 'USD' || typeof body.amount !== 'number'
          || body.amount <= 0 || !body.order_id) return reply(400, { status: 'error' });
      const lifetime = body.add_fields && body.add_fields.time_to_pay;
      if (!lifetime || lifetime.hours !== 1 || lifetime.minutes !== 0) {
        return reply(400, { status: 'error', message: 'Expected a one-hour invoice lifetime' });
      }
      const invoice = {
        uuid: 'INV-89UX09KA', status: 'created', order_id: body.order_id,
        amount: 0.034187, amount_usd: body.amount, amount_in_fiat: body.amount,
        link: 'https://pay.trybit.com/89UX09KA', fiat_currency: 'USD'
      };
      await pm.state.set('trybit.invoice', invoice);
      return reply(200, { status: 'success', result: invoice });
    }
    if (req.method === 'POST' && req.url === '/v2/invoice/merchant/list') {
      if (req.headers.authorization !== 'Token test-api-key') return reply(401, { status: 'error' });
      if (!/^\d{2}\.\d{2}\.\d{4}$/.test(body.start) || !/^\d{2}\.\d{2}\.\d{4}$/.test(body.end)
          || !Number.isInteger(body.offset) || body.offset < 0 || !Number.isInteger(body.limit)
          || body.limit < 1 || body.limit > 100) return reply(400, { status: 'error' });
      const invoice = await pm.state.get('trybit.invoice');
      const items = invoice ? [invoice] : [];
      return reply(200, { status: 'success', result: items.slice(body.offset, body.offset + body.limit), all_count: items.length });
    }
    if (req.method === 'POST' && req.url === '/v2/invoice/merchant/info') {
      if (req.headers.authorization !== 'Token test-api-key') return reply(401, { status: 'error' });
      const failure = await pm.state.get('trybit.failure');
      if (failure === 'upstream') return reply(503, { status: 'error' });
      if (failure === 'invalid-json') { res.writeHead(200, json); return res.end('not-json'); }
      if (failure === 'api-error') return reply(200, { status: 'error', result: {} });
      if (!Array.isArray(body.uuids)) return reply(400, { status: 'error' });
      const invoice = await pm.state.get('trybit.invoice');
      return reply(200, { status: 'success', result: invoice && body.uuids.includes(invoice.uuid) ? [invoice] : [] });
    }
    // Test control route: emulate confirmation without sending cryptocurrency.
    if (req.method === 'POST' && req.url === '/__control') {
      const invoice = await pm.state.get('trybit.invoice');
      if (invoice && body.status) {
        invoice.status = body.status;
        await pm.state.set('trybit.invoice', invoice);
      }
      await pm.state.set('trybit.failure', body.failure || null);
      return reply(200, { ok: true });
    }
    return reply(501, { error: 'Not modeled by this contract fixture' });
  } catch (_) {
    return reply(500, { error: 'Fixture failed' });
  }
}).listen(process.env.PORT || 4500);
