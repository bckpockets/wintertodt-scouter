# Hosting proposal and runbook

## Free Cloudflare deployment

Production deployment verified on 2026-10-16:

- Base URL: `https://wintertodt-scouter.wintertodt-scouter-service.workers.dev`
- Worker: `wintertodt-scouter`; private binding: `SCOUTER` → `ScouterRoom`.
- Version: `bc3f4e6f-fb3e-471e-b1a4-fdd5b9edb023`.
- Dashboard plan: Free (confirmed); no upgrade selected.
- Public `/healthz`: HTTP 200, `{"status":"ok","version":2}`.
- Public `/v2/worlds`: HTTP 200 with an empty v2 observation list before scouts contribute.

The new workers.dev hostname briefly failed TLS handshakes during initial provisioning, then passed certificate-verified HTTPS checks without disabling verification. These checks establish deployment and API reachability; independent in-game reporting remains a separate release check.

The initial deployment targets Cloudflare Workers Free with one SQLite-class Durable Object and a free `workers.dev` HTTPS address. The adapter is `service/worker.mjs`; configuration is `service/wrangler.jsonc`. All edge locations route to one object. Reports remain in memory; only cleanup alarms use object storage. An eviction or deployment clears reports until scouts report again.

In `service/`, run:

```sh
npm ci
npm test
npm run check:worker
npx wrangler login
npm run deploy:worker
```

Use the Free plan and verify the account remains on it before deploying. No domain purchase is needed. Test the printed HTTPS address at `/healthz` and `/v2/worlds`, then configure its base URL in the plugin. Sharing stays opt-in. Login pages and browser challenges must not intercept the API routes.

The public Worker uses Cloudflare's `CF-Connecting-IP`, overwrites the internal source header, and ignores user-supplied proxy headers. A private Durable Object binding carries the request. Application logging is disabled in the supplied configuration; Cloudflare still processes connection metadata. Alarms prune expired reports/rate buckets roughly every ten seconds while state exists, subject to platform scheduling delays.

Free quotas apply to Worker and Durable Object requests, duration and alarms. At five-second intervals, one continuously active reader makes 17,280 GETs/day, plus uploads when sharing. This fits an initial trial rather than unlimited public traffic. Quota exhaustion activates the client's remote-data fallback and backoff. This configuration does not select a paid plan.

`npm run dev:worker` starts the local Workers/Durable Objects emulator. Dry-run bundling and handler tests do not establish public deployment or uptime. Verify the actual binding locally and the public endpoint before release.

## Optional VPS deployment

Operators who later prefer a VPS can use the included Node 24 service and Caddy automatic HTTPS. The service uses bounded ephemeral memory and has no database or npm runtime dependencies. A restart loses only short-lived scouting reports. A single VPS is not high availability; routine OS/container updates and monitoring are still required.

Budget target: approximately US$5–10/month plus an optional domain. Confirm the provider's live regional price, taxes, IPv4 charge and availability before purchase. Nothing has been purchased or deployed by this repository.

The [Hetzner June 2026 price table](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/) lists EU CX23 at €3.99/month excluding VAT and separately bills IPv4; availability varies. Prefer a region near the initial scouting population. An existing server with adequate isolation is also suitable.

See [Cloudflare Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/) and [Durable Objects pricing](https://developers.cloudflare.com/durable-objects/platform/pricing/) for free-plan allowances. The repository supports both adapters; no paid hosting is required for the initial Cloudflare trial.

## Provisioning (requires an approved account and budget)

1. Provision a current supported Linux server (at least 1 GB RAM) with SSH keys. Apply OS security updates and configure automatic security patching.
2. At the provider firewall, permit inbound TCP 80/443 and restrict SSH to the operator's address. Keep port 8080 closed. Set a spend alert where the provider supports it.
3. Install Docker Engine and Compose from their official repositories. Clone the reviewed release commit to the server.
4. Point a DNS hostname at the server. Use direct DNS initially; do not enable browser challenges for the API. A suitable existing subdomain avoids buying a new domain.
5. In `service/`, set `SCOUTER_DOMAIN` and run:

```sh
SCOUTER_DOMAIN=scouter.example.org docker compose up -d --build
```

6. Check Caddy obtained a trusted certificate, verify HTTP redirects to HTTPS, and check:

```sh
curl --fail https://scouter.example.org/healthz
curl --fail https://scouter.example.org/v2/worlds
```

7. Test the actual configured client against the deployed service using two independent network addresses. Verify absent scouts produce Unknown, not fabricated health. Confirm HTTP 429, invalid input handling, and source-IP isolation through Caddy.
8. Only after these checks, set the plugin's production default URL and run release QA. Sharing remains opt-in.

The Docker application runs as non-root with a read-only filesystem, dropped capabilities, memory/CPU/PID limits, and an internal-only port. Caddy stores its certificate state in named volumes. Container tags track supported major releases; record image digests for each deployment and review updates before rolling out. Docker/Caddy deployment still needs validation on the actual host.

## Ongoing operation

- Monitor `/healthz` externally and independently check `/v2/worlds` parses. An empty observation list is valid during low population.
- Review uptime, memory, CPU, rate-limit traffic, and TLS renewal. Do not collect character data or enable request-body logging.
- Install OS and container security updates; rebuild from a tested commit. A weekly CI build checks current RuneLite compilation, not server health.
- Deploy a new container, verify health, and keep the previous commit/image available for rollback. Preserve Caddy volumes.
- If the service fails, the plugin backs off, expires remote observations, and continues local readings. Fix the service without requiring players to reset configuration.
- Scale only after measuring actual traffic. There is no load-test-backed capacity promise yet. Multiple service instances require shared aggregation; do not load-balance independent in-memory stores.
