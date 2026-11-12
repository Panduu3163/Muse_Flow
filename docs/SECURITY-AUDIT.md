# Credential audit

13 November 2026. Review covered tracked source, reachable Git history, the publication snapshot and relevant local configuration. Raw scanner reports and private development data are kept outside the repository.

## Findings

The initial Gitleaks 8.30.1 history scan reviewed 80 locally reachable commits and reported five matches:

| Location | Classification | Handling |
| --- | --- | --- |
| InnerTube transcript request | Legacy public YouTube web-client constant, also present in historical upstream yt-dlp source | Retained for compatibility; exact-value/path exception |
| BotGuard WebView | Public Google web-client constant documented by BgUtils | Retained; exact-value/path exception |
| BotGuard WebView | Public request identifier in BgUtils's example | Retained; exact-value/path exception |
| Two historical README entries | Scanner interpreted the prose phrase describing signature/cipher handling as a key | Exact phrase/path exception |

No developer-owned private API key was identified in those findings. This classification is based on source review and comparison with public upstream references, not on attempting to authenticate with a credential. A clean scanner result is useful evidence, not a guarantee that no possible secret exists.

References: [yt-dlp's historical YouTube client](https://github.com/yt-dlp/yt-dlp/blob/2023.11.16/yt_dlp/extractor/youtube.py), [BgUtils research](https://github.com/LuanRT/BgUtils), [BgUtils example](https://github.com/LuanRT/BgUtils/blob/main/examples/index.ts).

The signing keystore and machine configuration are local and ignored. No keystore, private environment file, service-account credential or `google-services.json` was found among the audited reachable Git object paths. Android's conventional local signing passwords in the Gradle configuration are not the private key itself; the keystore must remain private.

## Continuing checks

The secret-scan workflow uses the pinned Gitleaks 8.30.1 release with a verified archive checksum. The configuration extends the default rules and adds only reviewed, exact-value/path exceptions. It does not turn off Google-key or generic-key detection globally.

The exceptions are rule-scoped and require both the reviewed path and exact value. A local positive-control check placed three different synthetic credentials in the same BotGuard source path; Google-key, GitHub-token and generic-key rules still rejected them. This guards against accidentally excluding the entire file.

With Gitleaks installed locally:

```sh
gitleaks git . --log-opts="--all" --redact --no-banner
gitleaks dir . --redact --no-banner
```

Scan a clean checkout to avoid reading large private build/test folders. Never copy unredacted findings into an issue or build log. See [the security policy](../SECURITY.md) for reporting and rotation guidance.
