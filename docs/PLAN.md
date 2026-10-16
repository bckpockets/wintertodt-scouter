# Rebuild plan and source audit

Reviewed 2026-10-15. Baseline: Nouser/wintertodt-scouter commit
`39ad95a7c17e6fb84c95831ee89ea08ed023b2da`, also the Plugin Hub's pinned commit at inspection.

## Findings and implementation

| Finding | Evidence | Rebuild approach |
| --- | --- | --- |
| Client API incompatibility | Original uses `World.getLocation()`; current 1.13.2 exposes `getRegion()` | Current region API and generated `gameval` interface identifiers; latest-release CI |
| Wrong restart parsing | Energy and timer both read packed widget `25952282`; restart is reached only after reading energy zero | Separate energy-title parser and `WINT_TRANSMIT_RESPAWNDELAY`, 0.6 seconds per tick |
| Old observations made to look new | `submitToAPI()` rewrites `last.time` without a new sample | Immutable observations, no timestamp mutation, expiry and out-of-order rejection |
| Lost uploads | `processLocalData()` marks data uploaded before HTTP completes | Bounded independent requests; failure backoff; subsequent observations can be sent |
| Stale world after hop | Only checks `!= HOPPING`; upload uses `LOGGED_IN || != HOPPING` | Require logged-in state, reset on transitions, region/visible-interface checks, two settling ticks |
| Broken custom endpoint settings | Uplink and downlink methods use reversed config keys | One new, HTTPS-only v2 base URL; old endpoints deliberately not reused |
| UI hopping unreliability | Rows replaced during refresh; nested labels receive different mouse events | Stable JTable, selected world preserved, explicit button, client-thread hop validation |
| Unbounded/lifecycle-unsafe callbacks | No per-direction in-flight limits or shutdown cancellation | Single request per direction, timeout, cancel, session generation guards |
| Response leaks and malformed data | POST error responses not closed; incomplete parsing validation | try-with-resources, size cap, strict numeric fields, invalid-row isolation |
| Server outage | Read-only request returned a Cloudflare block in this environment | New service included; do not claim the old server is universally down |
| Hard-to-maintain backend dependency | Repository contains client only | Small Node service with no npm dependencies, Docker/Caddy deployment and operations notes |

Relevant original issues: [#13](https://github.com/Nouser/wintertodt-scouter/issues/13),
[#12](https://github.com/Nouser/wintertodt-scouter/issues/12),
[#11](https://github.com/Nouser/wintertodt-scouter/issues/11),
[#9](https://github.com/Nouser/wintertodt-scouter/issues/9),
[#7](https://github.com/Nouser/wintertodt-scouter/issues/7),
[#4](https://github.com/Nouser/wintertodt-scouter/issues/4),
[#3](https://github.com/Nouser/wintertodt-scouter/issues/3),
[#1](https://github.com/Nouser/wintertodt-scouter/issues/1).

## Delivery sequence

1. Create bckpockets repository, preserve attribution, inspect original code and current RuneLite source.
2. Implement isolated observation, network, display and manual-hopping components.
3. Implement a replacement service with strict protocol validation, expiry and independent-source agreement.
4. Run Java and service regression tests; inspect panel rendering; build distributable plugin JAR.
5. Publish source, CI, audit, privacy and deployment instructions.
6. Provision approved hosting, configure DNS/HTTPS, verify the deployed protocol and health endpoint, then set the production default URL.
7. Complete live-game QA with the user; prepare the Plugin Hub ownership/replacement PR for maintainer review.

Steps 6–7 are release gates. Unit tests cannot establish live game-widget correctness, hosting uptime, or Plugin Hub acceptance.

## Scope decisions

Retain the existing plugin name and package to make a reviewed ownership transition possible rather than create a competing listing. Maintainers may require contact with the previous owner; no ownership agreement is implied.

Do not silently migrate a legacy endpoint to a service operated by someone else. Sharing is opt-in. Do not embed an authentication secret in a distributable plugin: it would be public and could not authenticate players. Consensus/rate limits reduce accidental or simple malicious bad reports, but distributed fake clients remain a limitation.

Leagues support is deferred until a live seasonal world directory and account compatibility can be tested. No guessed seasonal IDs are included. World 402 and future regular themed worlds are discovered by activity rather than a fixed list.
