# Hosting proposal and runbook

## Recommendation

Start with one small fixed-price Linux VPS, the included Node 24 service and Caddy automatic HTTPS. The service uses bounded ephemeral memory and has no database or npm runtime dependencies. A restart loses only short-lived scouting reports. This is operationally simple, but a single VPS is not high availability; routine OS/container updates and monitoring are still required.

Budget target: approximately US$5–10/month plus an optional domain. Confirm the provider's live regional price, taxes, IPv4 charge and availability before purchase. Nothing has been purchased or deployed by this repository.

The [Hetzner June 2026 price table](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/) lists EU CX23 at €3.99/month excluding VAT and separately bills IPv4; availability varies. Prefer a region near the initial scouting population. An existing server with adequate isolation is also suitable.

[Cloudflare Workers](https://developers.cloudflare.com/workers/platform/pricing/) is another option, but frequent polling needs a budget calculation and a stateful service adaptation. At a five-second interval, a single continuously active reader makes 17,280 GETs/day; an active sharing reader can double that. The 100,000 requests/day free allowance is therefore not a reliable public-plugin operating budget. Workers Paid starts at $5/month and is usage-based; storage/stateful compute may add charges. This implementation targets a VPS, not Workers.

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
