# Wintertodt Scouter

A RuneLite plugin for scouting Wintertodt worlds, based on [Nouser/nucleon's Wintertodt Scouter](https://github.com/Nouser/wintertodt-scouter), with updates by **bckpockets**.

**Development preview:** this is not yet a Plugin Hub release. The Cloudflare service is deployed and its public HTTPS endpoints are verified. Local energy, restart timing and manual hopping have been tested in-game; cross-world reporting and the remaining release checks still need verification before RuneLite review.

## Features

- Discover regular Wintertodt worlds from RuneLite's live world directory, including newly added themed worlds.
- Read energy from the named game interface and restart time from the game varbit.
- Show direct local observations even when the service is unavailable.
- Label unknown and stale data; expire observations after 30 seconds. Countdown reaching zero means **Awaiting start**, never an invented new round.
- Preserve row selection as data updates; use a **Hop to selected world** button or double click.
- Poll only while the sidebar is visible. Bound requests, timeouts and exponential backoff prevent an outage from creating a request storm.
- Optional sharing: only world, energy/restart seconds, and observation time are transmitted. No character name, account ID, location coordinates, or inventory.
- Included service requires two agreeing network sources, rejects old reports, bounds memory/request sizes, and rate limits requests.

Seasonal/Leagues, PvP, high-risk, beta, and other special world types are intentionally excluded pending explicit account-mode handling. This rebuild does not automatically play or hop worlds.

## Development

Use JDK 11 or newer. The plugin targets Java 11 and resolves the current RuneLite release.

```powershell
.\gradlew.bat test jar
.\gradlew.bat run
node --test service/scouting.test.mjs
```

On Linux/macOS, use `./gradlew`. The development launcher requires the usual RuneLite/Jagex account setup. Never put account credentials into this repository.

For the Cloudflare adapter, run `npm ci`, `npm test`, and `npm run check:worker` in `service/`. `npm run dev:worker` starts the local Workers runtime; `npm run deploy:worker` publishes to your signed-in Cloudflare account. See the hosting guide before deployment.

## Configuration

Open the Wintertodt Scouter sidebar. The default service is `https://wintertodt-scouter.wintertodt-scouter-service.workers.dev`. An unset, empty or whitespace-only service URL uses this default automatically. Other worlds display **Unknown** until independent scouts contribute agreeing fresh observations.

Set **Scouting service URL** to an HTTPS base URL to override the default service. Clearing the setting restores the default, including on existing development installations. Enable **Share observations** to contribute. Sharing is off by default. The server and its hosting provider necessarily see your IP address, including when retrieving reports. Two distinct network sources must agree before a remote report appears; two clients behind the same public IP count as one.

This version uses the v2 service protocol and a new service URL setting. Legacy `wtUplink` and `wtDownlink` settings are left untouched; configure a compatible service explicitly. The previous hopping preference is retained.

## Maintenance and release

- [Audit and implementation plan](docs/PLAN.md)
- [Hosting and operations](docs/HOSTING.md)
- [Protocol and privacy](docs/PROTOCOL.md)
- [Release and in-game QA checklist](docs/RELEASE.md)

CI builds against the latest RuneLite release on pushes, pull requests, and a weekly schedule. Scheduled CI detects incompatibilities; it does not automatically fix, deploy, or submit them to the Plugin Hub.

## Credits and license

BSD 2-Clause. Original project credits: nucleon, Andrew McAdams, Cyborger1, Psikoi, Nouser. Original copyright notices and license are retained; the toolbar icon is inherited from that project. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
