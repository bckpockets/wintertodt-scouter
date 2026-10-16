import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Scouting } from './scouting.mjs';
import { handleWorlds, routeWorker } from './worker-handler.mjs';

const report = { world: 307, energy: 80, restartSeconds: -1, observedAt: 100 };
const post = (body, headers = {}) => new Request('https://scouter.example/v2/worlds', {
  method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body,
});

test('Worker handler shares the v2 contract and requires independent scouts', async () => {
  const scouting = new Scouting({ clock: () => 100 });
  assert.equal((await handleWorlds(post(JSON.stringify(report)), scouting, '192.0.2.1')).status, 204);
  let response = await handleWorlds(new Request('https://scouter.example/v2/worlds'), scouting, '192.0.2.3');
  assert.deepEqual((await response.json()).observations, []);
  assert.equal((await handleWorlds(post(JSON.stringify(report)), scouting, '192.0.2.2')).status, 204);
  response = await handleWorlds(new Request('https://scouter.example/v2/worlds'), scouting, '192.0.2.3');
  assert.deepEqual((await response.json()).observations, [report]);
});

test('Worker rejects oversized, malformed and unsupported requests', async () => {
  const scouting = new Scouting({ clock: () => 100 });
  assert.equal((await handleWorlds(post('x'.repeat(1025)), scouting, 'test')).status, 413);
  assert.equal((await handleWorlds(post('{'), scouting, 'test')).status, 400);
  assert.equal((await handleWorlds(post('{}', { 'content-type': 'text/plain' }), scouting, 'test')).status, 415);
  assert.equal((await handleWorlds(post('{}'), scouting, null)).status, 400);
});

test('edge overrides spoofed client identity and uses one global object', async () => {
  const scouting = new Scouting({ clock: () => 100 });
  const env = { SCOUTER: { getByName(name) {
    assert.equal(name, 'wintertodt-v2');
    return { fetch(request) {
      assert.equal(request.headers.get('x-real-ip'), null);
      assert.equal(request.headers.get('x-scouter-client-ip'), '192.0.2.1');
      return handleWorlds(request, scouting, request.headers.get('x-scouter-client-ip'));
    } };
  } } };
  const request = post(JSON.stringify(report), { 'CF-Connecting-IP': '192.0.2.1',
    'x-scouter-client-ip': 'spoofed', 'x-real-ip': 'spoofed', 'x-forwarded-for': 'spoofed' });
  assert.equal((await routeWorker(request, env)).status, 204);
});

test('health avoids the object; missing identity fails closed; object outage becomes 503', async () => {
  assert.equal((await routeWorker(new Request('https://scouter.example/healthz'), {})).status, 200);
  assert.equal((await routeWorker(new Request('https://scouter.example/v2/worlds'), {})).status, 400);
  const env = { SCOUTER: { getByName() { return { fetch() { throw Error('unavailable'); } }; } } };
  const response = await routeWorker(new Request('https://scouter.example/v2/worlds', { headers: { 'CF-Connecting-IP': '192.0.2.1' } }), env);
  assert.equal(response.status, 503);
  assert.equal(response.headers.get('Retry-After'), '30');
});
