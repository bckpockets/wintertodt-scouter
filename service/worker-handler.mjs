export function jsonReply(status, body, extraHeaders = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', 'cache-control': 'no-store',
      'x-content-type-options': 'nosniff', ...extraHeaders },
  });
}

export async function handleWorlds(request, scouting, address) {
  const url = new URL(request.url);
  if (url.pathname !== '/v2/worlds' || url.search) return jsonReply(404);
  if (!['GET', 'POST'].includes(request.method)) return jsonReply(405, undefined, { Allow: 'GET, POST' });
  if (!address || address.length > 64) return jsonReply(400);
  const key = scouting.clientKey(address);
  if (!scouting.allow(key, request.method)) return jsonReply(429, undefined, { 'Retry-After': '60' });
  if (request.method === 'GET') return jsonReply(200, scouting.snapshot());
  if (request.headers.get('content-type')?.split(';')[0].trim() !== 'application/json') return jsonReply(415);
  if (request.headers.has('content-encoding') || Number(request.headers.get('content-length') || 0) > 1024) return jsonReply(413);
  const reader = request.body?.getReader();
  if (!reader) return jsonReply(400);
  let size = 0;
  const chunks = [];
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > 1024) { await reader.cancel(); return jsonReply(413); }
      chunks.push(value);
    }
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    let report;
    try { report = JSON.parse(new TextDecoder().decode(bytes)); }
    catch { return jsonReply(400, { error: 'Invalid JSON' }); }
    return jsonReply(scouting.report(key, report));
  } catch { return jsonReply(400); }
  finally { reader.releaseLock(); }
}

export async function routeWorker(request, env) {
  const url = new URL(request.url);
  if (url.pathname === '/healthz' && !url.search && request.method === 'GET') {
    return jsonReply(200, { status: 'ok', version: 2 });
  }
  if (url.pathname !== '/v2/worlds' || url.search) return jsonReply(404);
  // Only Cloudflare's connection header establishes identity. User-supplied proxy headers are ignored.
  const address = request.headers.get('CF-Connecting-IP');
  if (!address || address.length > 64) return jsonReply(400);
  const headers = new Headers(request.headers);
  headers.delete('x-real-ip');
  headers.delete('x-forwarded-for');
  headers.set('x-scouter-client-ip', address);
  const forwarded = new Request(request, { headers });
  try { return await env.SCOUTER.getByName('wintertodt-v2').fetch(forwarded); }
  catch { return jsonReply(503, { error: 'Scouting temporarily unavailable' }, { 'Retry-After': '30' }); }
}
