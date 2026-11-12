# 1.8 Beta verification status

Development version: **1.8.0-beta (62)**. Verification completed on 12–13 November 2026; repository publication checks are recorded separately. This source update is not a declaration of a fully accepted stable release.

## Completed

- 475 app tests and 7 InnerTube tests: **482 total**, zero failures, errors or skips.
- Lint: zero errors and seven warnings at the device-tested checkpoint.
- Optimized Beta and Verification APKs built and verified against the existing signing certificate.
- In-place Beta upgrades preserved the existing library. Destructive import, restore and storage tests used the separately packaged Verification app.
- Backup preview/cancel, malformed and unsupported files, duplicate-safe restore, history/settings restoration, CSV/M3U results and import cancellation.
- Multiple unique external backups with verified writes; destination permission survived restart/update. Automatic backup scheduling enabled and disabled successfully.
- Download completion/removal, explicit retry after a durable offline failure, and cold offline playback after clearing cache with no active network.
- Paused queue restoration, explicit resumption, speed/reset, EQ preset/reset, timer expiry and audio-focus transfer between apps.
- Square widget at 3×3, 4×4 and wider allocations; cold Play, Pause and Next; large-text geometry. Quick Settings pause/open behavior.
- Phone landscape and 200% text checks for reachable player controls, plus three dedicated native player layout checks.
- More than five minutes of sampled screen-off playback through repeat-one transitions. This was USB-powered stability testing, not an acoustic or battery-drain measurement.

The physical device was a Xiaomi 14 Ultra on Android 16 / API 36 with the Xiaomi launcher. Earlier manual checks span builds 59–61; discovered defects were fixed and affected flows retested, with final upgrade/layout checks on build 62. Every manual case was not repeated from scratch on the final build.

## Corrected during device testing

- Unknown metadata no longer claims a downloaded song lasts less than one minute; partial totals are explicitly marked.
- An online recording remembered with a removed download's file URI can download the exact recording again.
- Short windows and large text use bounded artwork and scrolling so player controls remain reachable.

## Remaining acceptance limits

- Headphone/Bluetooth disconnects, arranged incoming calls, another launcher, and other physical Android versions including API 37.
- Full TalkBack/Switch Access, RTL/split-window and reboot/long-idle journeys.
- Physical storage exhaustion, process death exactly at a save/restore commit, cloud-document-provider failures and forced grant revocation. Automated fault tests provide separate coverage.
- Full active-transfer interruption/cancellation matrix and unplugged battery measurements.
- A live newer-update download and installer completion. The public GitHub endpoint hit an unauthenticated request limit during testing; recoverable error feedback and the bundled changelog worked.
- Lyrics succeeded for a tested recording; another provider result failed gracefully. Synchronized highlighting was not established by that successful plain-lyrics result.

The proposed draggable Glass navigation styles are research only and are **not implemented** in this update. Measured loudness, guaranteed gapless output and true overlapping crossfade are not validated features.
