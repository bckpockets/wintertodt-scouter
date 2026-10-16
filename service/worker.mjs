import { DurableObject } from 'cloudflare:workers';
import { Scouting } from './scouting.mjs';
import { handleWorlds, routeWorker } from './worker-handler.mjs';

export class ScouterRoom extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.scouting = new Scouting();
    this.cleanupScheduled = false;
  }

  async fetch(request) {
    // The binding is private to our Worker. All requests use one object so scouts share state.
    if (!this.cleanupScheduled) {
      this.cleanupScheduled = true;
      try { await this.ctx.storage.setAlarm(Date.now() + 10000); }
      catch (error) { this.cleanupScheduled = false; throw error; }
    }
    return handleWorlds(request, this.scouting, request.headers.get('x-scouter-client-ip'));
  }

  async alarm() {
    this.scouting.prune();
    if (this.scouting.clients.size || this.scouting.rates.size) {
      await this.ctx.storage.setAlarm(Date.now() + 10000);
    } else { this.cleanupScheduled = false; }
  }
}

export default { fetch: routeWorker };
