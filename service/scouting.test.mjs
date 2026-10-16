import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Scouting, validate } from './scouting.mjs';
import { createServer } from './server.mjs';

const sample = (overrides = {}) => ({ world: 307, energy: 80, restartSeconds: -1, observedAt: 100, ...overrides });

test('strict payload bounds, freshness, no extra identifying fields', () => {
  assert.ok(validate(sample(), 100));
  for (const bad of [{ world: 1 }, { energy: 101 }, { energy: 1.2 }, { observedAt: 70 },
    { observedAt: 106 }, { restartSeconds: 10 }, { player: 'name' }, { world: '307' }]) {
    assert.equal(validate(sample(bad), 100), false);
  }
  assert.ok(validate(sample({ energy: -1, restartSeconds: 90 }), 100));
});

test('one source per address, needs independent agreement, drops outliers', () => {
  const service = new Scouting({ clock: () => 100 });
  assert.equal(service.report('one', sample()), 204);
  assert.equal(service.report('one', sample()), 409);
  assert.deepEqual(service.snapshot().observations, []);
  service.report('two', sample({ energy: 79 }));
  service.report('three', sample({ energy: 1 }));
  assert.equal(service.snapshot().observations[0].energy, 79);
});

test('reports expire and replay never renews their timestamp', () => {
  let now = 100;
  const service = new Scouting({ clock: () => now });
  service.report('one', sample()); service.report('two', sample());
  now = 111;
  assert.deepEqual(service.snapshot().observations, []);
  now = 130;
  assert.equal(service.report('one', sample()), 400);
  service.prune();
  assert.equal(service.clients.size, 0);
});

test('a reporter hopping worlds cannot populate two worlds', () => {
  let now = 100;
  const service = new Scouting({ clock: () => now });
  service.report('one', sample()); service.report('two', sample());
  now = 101;
  service.report('two', sample({ world: 309, observedAt: 101 }));
  assert.deepEqual(service.snapshot().observations, []);
});

test('restart deadline agreement preserves observation age', () => {
  const service = new Scouting({ clock: () => 105 });
  service.report('one', sample({ energy: -1, restartSeconds: 90 }));
  service.report('two', sample({ energy: -1, restartSeconds: 85, observedAt: 105 }));
  assert.deepEqual(service.snapshot().observations, [sample({ energy: -1, restartSeconds: 90 })]);
});

test('conflicting round phases are withheld', () => {
  const service = new Scouting({ clock: () => 100 });
  service.report('one', sample()); service.report('two', sample());
  service.report('three', sample({ energy: -1, restartSeconds: 90 }));
  service.report('four', sample({ energy: -1, restartSeconds: 90 }));
  assert.deepEqual(service.snapshot().observations, []);
});

test('rate limits reset and do not store raw addresses', () => {
  let now = 100;
  const service = new Scouting({ clock: () => now });
  const key = service.clientKey('192.0.2.1');
  assert.equal(key.includes('192.0.2.1'), false);
  for (let i = 0; i < 30; i++) assert.equal(service.allow(key, 'POST'), true);
  assert.equal(service.allow(key, 'POST'), false);
  now = 160;
  assert.equal(service.allow(key, 'POST'), true);
});

test('HTTP contract, malformed requests, limits, health, read-only GET', async () => {
  const server = createServer({ scouting: new Scouting({ clock: () => 100 }) });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(`${base}/healthz`)).status, 200);
    assert.equal((await fetch(`${base}/unknown`)).status, 404);
    assert.equal((await fetch(`${base}/v2/worlds`, { method: 'DELETE' })).status, 405);
    assert.equal((await fetch(`${base}/v2/worlds`, { method: 'POST', body: '{}' })).status, 415);
    const post = body => fetch(`${base}/v2/worlds`, { method: 'POST', headers: { 'content-type': 'application/json' }, body });
    assert.equal((await post('{')).status, 400);
    assert.equal((await post('x'.repeat(1025))).status, 413);
    assert.equal((await post(JSON.stringify(sample()))).status, 204);
    const response = await fetch(`${base}/v2/worlds`);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    assert.deepEqual(await response.json(), { version: 2, observations: [] });
  } finally { await new Promise(resolve => server.close(resolve)); }
});
