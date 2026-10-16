import http from 'node:http';
import { pathToFileURL } from 'node:url';
import { Scouting } from './scouting.mjs';

export function createServer({ scouting = new Scouting(), trustProxy = false } = {}) {
  const server = http.createServer(async (req, res) => {
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    const reply = (code, body) => { res.writeHead(code); res.end(body === undefined ? undefined : JSON.stringify(body)); };
    if (req.url === '/healthz' && req.method === 'GET') { reply(200, { status: 'ok', version: 2 }); return; }
    if (req.url !== '/v2/worlds') { reply(404, { error: 'Not found' }); return; }
    if (!['GET', 'POST'].includes(req.method)) { res.setHeader('Allow', 'GET, POST'); reply(405); return; }
    // Trust this header ONLY with the bundled reverse proxy and an unexposed app port.
    const address = trustProxy ? req.headers['x-real-ip'] : req.socket.remoteAddress;
    if (typeof address !== 'string' || address.length > 64) { reply(400); return; }
    const key = scouting.clientKey(address);
    if (!scouting.allow(key, req.method)) { res.setHeader('Retry-After', '60'); reply(429); return; }
    if (req.method === 'GET') { reply(200, scouting.snapshot()); return; }
    if (req.headers['content-type']?.split(';')[0].trim() !== 'application/json') { reply(415); return; }
    if (req.headers['content-encoding'] || Number(req.headers['content-length'] || 0) > 1024) { reply(413); return; }
    try {
      const chunks = [];
      let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 1024) { reply(413); return; }
        chunks.push(chunk);
      }
      let payload;
      try { payload = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
      catch { reply(400, { error: 'Invalid JSON' }); return; }
      reply(scouting.report(key, payload));
    } catch {
      if (!res.headersSent) reply(400);
    }
  });
  server.requestTimeout = 5000;
  server.headersTimeout = 5000;
  server.keepAliveTimeout = 5000;
  server.maxHeadersCount = 30;
  server.maxConnections = 1000;
  const sweep = setInterval(() => scouting.prune(), 10000);
  sweep.unref();
  server.on('close', () => clearInterval(sweep));
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = createServer({ trustProxy: process.env.TRUST_PROXY === 'true' });
  server.listen(Number(process.env.PORT || 8080), process.env.HOST || '127.0.0.1', () => {
    console.log('Wintertodt scouting service ready');
  });
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => server.close());
}
