# Wintertodt Scouter test client

This is a development RuneLite client containing the Wintertodt Scouter plugin. It is for coordinated testing before Plugin Hub review.

## Start

1. Install normal RuneLite from https://runelite.net if you do not have it already.
2. Extract every file from this ZIP into one folder. On Windows, run `START.cmd`. It uses RuneLite's bundled Java when available, then JAVA_HOME or Java on PATH. Java 11 or newer is required.
3. On other platforms, run `java -ea -jar wintertodt-scouter-test-client.jar --developer-mode` from the extracted folder.
4. In the new RuneLite window, search plugin settings for **Wintertodt Scouter** and enable it. Open its sidebar icon.

## Jagex account login

If the development client cannot sign in, use RuneLite's official guide:
https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

On Windows, open **RuneLite (configure)** from Start, add `--insecure-write-credentials` to **Client arguments**, save, then launch normal RuneLite through Jagex Launcher with your chosen character. Close that client and reopen `START.cmd` so the test client reads the refreshed local session.

This option saves a reusable login session on your own computer. Never send anyone `credentials.properties`, launcher tokens, your `.runelite` folder, or account details. Remove the temporary flag after testing and delete your own `.runelite/credentials.properties` when you are finished with development login. It is not included in this ZIP.

## Two-scout test

1. Use two different RuneScape accounts and two different public IPs (for example, one tester on home broadband and one on another connection). Two clients behind the same router count as one source.
2. In both clients, use the default service URL: `https://wintertodt-scouter.wintertodt-scouter-service.workers.dev`.
3. Each tester enables **Share observations** explicitly. Reports contain the world, energy/restart time and observation timestamp. The service also receives your public IP; no character name is sent.
4. Enter the same regular Wintertodt-themed world and arena. Keep the sidebar open. Compare local energy and the restart countdown with the game's interface.
5. While both scouts remain in the arena, a separate reader can inspect https://wintertodt-scouter.wintertodt-scouter-service.workers.dev/v2/worlds. An agreeing shared report should appear within about 10 seconds. The coordinator can do this without needing a third game account.
6. Stop reporting from one scout. Shared reports should disappear once the second fresh source expires from the aggregation window; local readings should keep working. Never expect remote data to remain current without scouts.

Report the world number, approximate time, expected versus shown reading, and any error text. Screenshots are useful if account information is hidden.

## Build details

Built from bckpockets/wintertodt-scouter. This ZIP contains the test-client JAR, launcher, instructions and project license/attribution only. It contains no user configuration or credentials. A bundled RuneLite dependency version is specific to this build; rebuild the test client after RuneLite/game compatibility updates.
