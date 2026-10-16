# Wintertodt Scouter

A RuneLite plugin for scouting Wintertodt worlds, based on [Nouser/nucleon's Wintertodt Scouter](https://github.com/Nouser/wintertodt-scouter), with updates by **bckpockets**.

**Development preview:** the client and service are implemented, but this is not yet a Plugin Hub release. A public service, live in-game verification, and RuneLite review are release requirements. The default service URL is deliberately empty until hosting is provisioned and verified.

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

## Configuration

Open the Wintertodt Scouter sidebar. With no service configured, only observations of the world you are visiting are available. Other worlds display **Unknown**.

To use a deployed service, set **Scouting service URL** to its HTTPS base URL, e.g. `https://scouter.example.org`. Enable **Share observations** to contribute. Sharing is off by default. The server and its hosting provider necessarily see your IP address. Two distinct network sources must agree before a remote report appears; two clients behind the same public IP count as one.

This version uses the v2 service protocol and a new service URL setting. Legacy `wtUplink` and `wtDownlink` settings are left untouched; configure a compatible service explicitly. The previous hopping preference is retained.

## Maintenance and release

- [Audit and implementation plan](docs/PLAN.md)
- [Hosting and operations](docs/HOSTING.md)
- [Protocol and privacy](docs/PROTOCOL.md)
- [Release and in-game QA checklist](docs/RELEASE.md)

CI builds against the latest RuneLite release on pushes, pull requests, and a weekly schedule. Scheduled CI detects incompatibilities; it does not automatically fix, deploy, or submit them to the Plugin Hub.

## Credits and license

BSD 2-Clause. Original project credits: nucleon, Andrew McAdams, Cyborger1, Psikoi, Nouser. Original copyright notices and license are retained; the toolbar icon is inherited from that project. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
