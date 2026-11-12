# Working on MuseFlow

MuseFlow is maintained by [@Panduu3163](https://github.com/Panduu3163). Discuss substantial behavior or dependency changes in an issue before preparing a patch.

1. Follow [the build instructions](docs/BUILDING.md).
2. Keep changes focused and preserve existing library data, recording identity and signing compatibility.
3. Run `./gradlew testDebugUnitTest lintDebug assembleDebug --max-workers=1`. Add a regression test when fixing a behavior that can meaningfully be reproduced.
4. Run the secret scanner described in [the audit](docs/SECURITY-AUDIT.md). Inspect the staged diff for local paths, keys, generated files and private test data.
5. Describe what changed, how it was verified, and what remains untested. Keep third-party license notices with adapted code and assets.

Device checks belong to the specific Android version, launcher and hardware tested. Do not report an untested configuration as passed. Use a separate app variant or an exported backup for destructive recovery tests.

Commits should carry accurate author information. The repository's update commits are credited to the maintainer; automated tooling must not append attribution trailers.
