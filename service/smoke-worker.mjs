import assert from 'node:assert/strict';

// Only local emulation: never inject synthetic observations into a deployed service.
const base = 'http://127.0.0.1:8787';
const health = await fetch(`${base}/healthz`);
assert.equal(health.status, 200);
const report = { world: 307, energy: 82, restartSeconds: -1, observedAt: Math.floor(Date.now() / 1000) };
const post = () => fetch(`${base}/v2/worlds`, { method: 'POST',
  headers: { 'content-type': 'application/json' }, body: JSON.stringify(report) });
assert.equal((await post()).status, 204);
assert.equal((await post()).status, 409);
const response = await fetch(`${base}/v2/worlds`);
assert.equal(response.status, 200);
assert.deepEqual(await response.json(), { version: 2, observations: [] });
console.log('Local Worker + Durable Object: health, upload, replay rejection, and quorum checks passed.');
