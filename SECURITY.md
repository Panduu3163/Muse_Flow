# Security

Report a suspected vulnerability or exposed credential privately to [the maintainer](mailto:mynulkbr@gmail.com). Include the affected version, a minimal reproduction and impact. Do not put working keys, access tokens, signing keys or private library backups in a public issue.

Never commit `.env`, `local.properties`, `google-services.json`, signing keystores, service-account credentials or device-test exports. A value bundled in an Android APK is available to anyone who extracts the APK; server credentials must stay on a server.

The YouTube/BotGuard integration includes public web-client constants. These are documented in [the credential audit](docs/SECURITY-AUDIT.md) and have exact-value, path-scoped secret-scanner exceptions. They are not a substitute for protecting an application's own credentials. New values require review; do not disable an entire scanner rule to silence a finding.

If a real credential is exposed, revoke or rotate it first. Removing a file or rewriting Git history does not revoke a credential or remove copies held by forks, caches or previous clones.

Only published releases receive release-specific fixes. The current 1.8 work remains a Beta; see [verification status](docs/VERIFICATION.md) for its acceptance limits.
