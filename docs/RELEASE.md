# Release gates

## Automated checks

- `./gradlew test jar` against current RuneLite.
- `node --test service/scouting.test.mjs` on Node 24.
- Check GitHub Actions push/PR runs and weekly compatibility runs.
- Inspect the packaged JAR for the plugin class, icon and license resources.

## Manual game QA

User-confirmed on 2026-10-16 in the development client: manual world hopping, local boss energy, and the restart countdown work in the arena. The energy parser was corrected to accept the game's possessive label, with a regression test. This confirms those local flows; the broader checks below and deployed cross-world reporting still need verification.

- Enable/disable/re-enable plugin; check navigation removal, no duplicate listeners or stale callbacks.
- In Wintertodt, compare energy to the game UI across multiple ticks and health changes.
- Watch round end, restart timer, countdown expiry and the next round. Confirm no guessed 100% state.
- Hop rapidly between worlds: verify old-world samples are never uploaded under the new ID and the two-tick settle period is sufficient. If not, add a verified game-interface update signal before release.
- Test lobby/outside-region, logout, disconnect, reconnect and loading screens; no outside-region uploads.
- Verify world 402 and every current regular Wintertodt-themed world are listed from the live directory.
- Select a row while updates occur; test button and double click, busy/combat prompt, full world and disabled hopping.
- Confirm special world types are excluded and cannot be entered through this panel.
- Configure the production HTTPS service; test two independently connected reporters, outage, recovery, 429 and malformed response.
- Turn sharing off and close the sidebar; verify no new POSTs/GETs respectively. Existing calls may finish unless configuration changes cancel them.
- Test an existing installation with custom legacy endpoint settings; it must not silently contact those URLs.

## Plugin Hub submission

Submission status, 2026-10-16: [PR #18702](https://github.com/runelite/plugin-hub/pull/18702) was submitted and closed by a reviewer pending the [Plugin takeover policy](https://github.com/runelite/runelite/wiki/Plugin-takeover-policy). The reviewer requires a collaborator-access request to have been open for two weeks before reopening. No collaborator request has been posted yet. A proposed request is in [COLLABORATOR_REQUEST.md](COLLABORATOR_REQUEST.md). Do not reopen or create a replacement PR to bypass that condition. If the author grants access, coordinate changes in the existing repository and obtain the requested author acknowledgement.

The initial Hub build also rejected construction of fresh Gson instances. Commit `9ad5a87f0f136765fd80b79bb6759815be624bbb` switches networking to RuneLite's injected Gson and passes all 19 Java tests. The prepared submission branch points to that commit; Hub validation will need to rerun when review resumes.

The [current manifest](https://github.com/runelite/plugin-hub/blob/master/plugins/wintertodt-scouter) points to Nouser. This is an ownership/repository change requiring RuneLite maintainer review, not an automatic replacement.

A draft PR may prepare the ownership transition, provided it explicitly lists hosting and live QA as incomplete and is not presented as release-ready. Once those gates pass, update the pinned commit and request review. Change only the existing manifest in a fork of `runelite/plugin-hub`:

```properties
repository=https://github.com/bckpockets/wintertodt-scouter.git
commit=<verified full 40-character commit>
warning=This plugin shares your IP with a 3rd party server not controlled or verified by the RuneLite Developers.
```

Do not submit a placeholder hash or claim deployment/gameplay verification that did not happen. Explain the maintenance transition, protocol change, opt-in reporting, server operator, issue fixes, and deferred seasonal support. Keep the existing warning. Run Hub checks and address review in the same PR. Only RuneLite maintainers can accept/merge it.

## Rollback

Do not roll back to an unverified legacy endpoint. Revert the plugin manifest to the last verified maintained commit, or ask maintainers to disable the listing if no working service is available. Service and plugin protocol versions must match.
