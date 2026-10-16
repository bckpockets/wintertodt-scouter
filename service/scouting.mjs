import { createHmac, randomBytes } from 'node:crypto';

export const TTL = 30;
const MAX_CLIENTS = 10000;
const MAX_RATE_KEYS = 20000;

export function validate(input, now) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return false;
  const { world, energy, restartSeconds, observedAt } = input;
  if (Object.keys(input).sort().join(',') !== 'energy,observedAt,restartSeconds,world') return false;
  return [world, energy, restartSeconds, observedAt].every(Number.isSafeInteger)
    && world >= 301 && world <= 2000 && observedAt > now - TTL && observedAt <= now + 5
    && ((energy >= 0 && energy <= 100 && restartSeconds === -1)
      || (energy === -1 && restartSeconds >= 0 && restartSeconds <= 300));
}

/** Ephemeral reports: no database, no account names, no raw IPs retained. */
export class Scouting {
  constructor({ clock = () => Math.floor(Date.now() / 1000), minimumReporters = 2 } = {}) {
    this.clock = clock;
    this.minimumReporters = minimumReporters;
    this.salt = randomBytes(32);
    this.clients = new Map();
    this.rates = new Map();
  }

  clientKey(address) { return createHmac('sha256', this.salt).update(address).digest('hex'); }

  allow(key, method) {
    const now = this.clock();
    const id = `${key}:${method}`;
    const existing = this.rates.get(id);
    if (existing && now < existing.until) {
      // Allow a small NAT-shared household; larger shared networks may be throttled.
      if (existing.count >= (method === 'POST' ? 30 : 60)) return false;
      existing.count++;
      return true;
    }
    this.prune();
    if (this.rates.size >= MAX_RATE_KEYS) return false;
    this.rates.set(id, { until: now + 60, count: 1 });
    return true;
  }

  prune() {
    const now = this.clock();
    for (const [key, value] of this.clients) {
      if (now - value.observedAt >= TTL) this.clients.delete(key);
    }
    for (const [key, value] of this.rates) {
      if (now >= value.until) this.rates.delete(key);
    }
  }

  report(key, input) {
    const now = this.clock();
    if (!validate(input, now)) return 400;
    this.prune();
    const previous = this.clients.get(key);
    if (previous && input.observedAt <= previous.sourceObservedAt) return 409;
    if (!previous && this.clients.size >= MAX_CLIENTS) return 503;
    // A client has at most one current world. Never refresh an observation on replay.
    this.clients.set(key, { ...input, sourceObservedAt: input.observedAt, observedAt: Math.min(input.observedAt, now) });
    return 204;
  }

  snapshot() {
    this.prune();
    const now = this.clock();
    const groups = new Map();
    for (const report of this.clients.values()) {
      // Only aggregate recent observations, rather than mixing different round stages.
      if (now - report.observedAt > 10) continue;
      const key = `${report.world}:${report.energy < 0 ? 'restart' : 'energy'}`;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(report);
    }
    const byWorld = new Map();
    for (const reports of groups.values()) {
      if (reports.length < this.minimumReporters) continue;
      const timer = reports[0].energy < 0;
      const values = reports.map(r => timer ? r.observedAt + r.restartSeconds : r.energy).sort((a,b) => a-b);
      const median = values[Math.floor(values.length / 2)];
      const agreeing = reports.filter(r => Math.abs((timer ? r.observedAt + r.restartSeconds : r.energy) - median) <= (timer ? 5 : 5));
      if (agreeing.length < this.minimumReporters || agreeing.length * 2 <= reports.length) continue;
      const observedAt = Math.min(...agreeing.map(r => r.observedAt));
      const world = reports[0].world;
      const candidate = { world, energy: timer ? -1 : median,
        restartSeconds: timer ? Math.max(0, Math.min(300, median - observedAt)) : -1, observedAt };
      const previous = byWorld.get(world);
      // Conflicting phases are withheld until clients agree on the new round state.
      if (previous) byWorld.set(world, { conflict: true });
      else byWorld.set(world, candidate);
    }
    return { version: 2, observations: [...byWorld.values()].filter(r => !r.conflict).sort((a,b) => a.world-b.world) };
  }
}
