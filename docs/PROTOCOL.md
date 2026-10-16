# Scouting API v2 and privacy

Base URL uses HTTPS. No redirects, embedded credentials, query parameters or fragments. The client appends `/v2/worlds`. The old a/b/c/d API is not compatible.

GET `/v2/worlds`:

```json
{"version":2,"observations":[{"world":307,"energy":82,"restartSeconds":-1,"observedAt":1792116000}]}
```

POST `/v2/worlds`, `Content-Type: application/json`:

```json
{"world":307,"energy":82,"restartSeconds":-1,"observedAt":1792116000}
```

Timestamps are UTC Unix seconds. Exactly one phase is valid: energy 0–100 with restartSeconds -1; or energy -1 with restartSeconds 0–300. World IDs are integers 301–2000. The client additionally limits sharing/hopping/display to regular themed worlds from RuneLite's directory.

POST accepts only the four specified integer fields. HTTP 204 accepts a report; 400 rejects malformed/stale/future data; 409 rejects an older or duplicate report from the same source; 413 rejects >1 KiB bodies; 415 rejects unsupported media; 429 rate limits; 503 rejects new sources at capacity. `/healthz` is a process-health endpoint, not evidence that scouts are online.

The service expires observations at 30 seconds, aggregates only samples at most 10 seconds old, and publishes only when at least two sources agree within five energy points or five seconds of a restart deadline. A strict majority within each phase is required. If both phases independently qualify, the world is withheld. Report timestamp is the oldest agreeing observation, never the GET time. The client shows a countdown locally but does not infer energy or the next round after it expires.

There is one current report per network address. A salted HMAC of the address is retained in memory; the random key changes at process restart. Reports expire after 30 seconds and rate-limit buckets after 60 seconds, with cleanup every 10 seconds. No raw IP addresses, character names or account identifiers are logged or stored by application code. Hosting providers and reverse proxies still process IP addresses; infrastructure logs require a separate retention review. The supplied Caddy configuration does not enable access logging.

Limits: 10,000 concurrent reporting sources, 20,000 rate buckets, 30 POSTs/minute/address, 60 GETs/minute/address, 1,000 open Node connections, five-second request/header timeout. The Java client makes at most one GET and one POST at a time, no faster than once per five seconds each; GETs run only while its sidebar is open. Backoff grows to 160 seconds on failures. GET responses over 128 KiB or 500 rows are rejected.

One public IP counts as one source (including NAT/VPN). IPv6 address rotation and distributed attackers can bypass this heuristic. There is no cryptographic proof of in-game observation. Treat data as advisory; direct local observations take precedence. Service restart clears all reports, and worlds remain Unknown until new sources agree.

**Proxy trust:** `TRUST_PROXY=true` is only safe with the supplied private Docker network and no published application port. Caddy overwrites X-Real-IP using the actual connection peer. Never expose port 8080 publicly or put an additional proxy in front without reworking the trusted-client-address policy.
