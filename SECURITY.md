# Privacy & Security Policy

**NetShield DNS Resolver** ("the app") is developed and maintained by PSBDx (M. Farhan Hamim). This document covers both what data the app handles and how to report security issues.

_Last updated: 2026-09-13_

---

## Part 1: Privacy Policy

### Summary

- The app does **not** collect, store, or transmit personal data to the developer or to any third party controlled by the developer.
- The app contains **no analytics SDKs, ads SDKs, or trackers**.
- The app does **not** use Firebase or Google Play Services.
- All DNS resolution, filtering, and logging happens **locally on your device**.

### What the app does with your data

#### DNS queries
When active (either in VPN mode or local UDP server mode), the app intercepts DNS queries from your device or from apps you've chosen to route through it, checks them against your configured blocklists, and forwards non-blocked queries to the upstream DNS resolver **you selected** (via plain UDP, DNS-over-HTTPS, or DNS-over-TLS).

- Which provider receives your DNS queries is entirely under your control, since you choose the upstream resolver.
- The developer has no access to this traffic — it never passes through any PSBDx server.
- Your choice of upstream provider is subject to that provider's own privacy policy, not this one.

#### Traffic logs
Real-time query logs (domains queried, blocked/allowed status, timestamps) are generated for the in-app dashboard so you can see what your device is doing. These logs:

- Are stored **only on your device**.
- Are **not** uploaded anywhere.
- Can be cleared at any time from within the app.

#### Blocklists
Custom blocklists you add are fetched directly by your device from the source URL you provide. The developer does not see which blocklists you use.

#### Update checks
On launch, the app checks `GET /repos/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/releases/latest` on GitHub's public API to see if a newer release is available. This request is sent directly to GitHub, not to PSBDx, and contains no personal information beyond what any standard HTTP request includes (e.g. IP address, as seen by GitHub).

- This check is automatically and permanently disabled when the app is installed via **F-Droid**, since F-Droid handles updates itself.

#### Permissions
The app may request:

- **VPN service** — required to route system-wide DNS traffic through the local sinkhole. No traffic is sent to any external VPN server; the "VPN" is local-only, terminating on the device itself.
- **Install packages** — used solely by the optional in-app updater to install a new version you've chosen to download (not applicable on F-Droid builds).
- **Network access / network state** — required to perform DNS resolution and check connectivity.

No permission is used to collect or transmit personal data off the device.

### Data sharing

The app shares no data with the developer, advertisers, or analytics providers, because it contains no mechanism to do so. The only network destinations the app itself talks to are:

1. Your chosen upstream DNS resolver(s)
2. Sources you configure for custom blocklists
3. GitHub's public release API (update check only, skipped on F-Droid)

### Children's privacy

The app does not knowingly collect data from anyone, including children, since it does not collect personal data at all.

### Changes to this policy

Updates to this policy will be posted in this file in the project repository. Continued use of the app after a change constitutes acceptance of the revised policy.

---

## Part 2: Security Policy

### Supported Versions

Only the **latest release** of NetShield DNS Resolver is actively supported with security fixes. Users are encouraged to keep the app updated via the in-app updater (non-F-Droid builds) or their F-Droid client.

| Version          | Supported          |
| ---------------- | ------------------- |
| Latest release   | :white_check_mark: |
| Older releases   | :x:                 |

### Reporting a Vulnerability

If you discover a security vulnerability in NetShield DNS Resolver, please report it responsibly:

1. **Do not open a public GitHub issue** for security vulnerabilities.
2. Instead, report it privately via GitHub's [private vulnerability reporting](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/security/advisories/new) feature on this repository, if enabled, or contact the maintainer directly.
3. Please include:
   - A description of the vulnerability and its potential impact
   - Steps to reproduce it
   - The app version and Android version affected
   - Any relevant logs or proof-of-concept code

You should expect an initial response acknowledging the report. Confirmed vulnerabilities will be addressed in a subsequent release, with credit given to the reporter unless anonymity is requested.

### Scope

This policy covers the NetShield DNS Resolver Android application source code in this repository, including:

- The local DNS sinkhole and resolver logic
- VPN mode traffic routing
- DoH/DoT/UDP upstream handling
- Blocklist parsing and per-app split tunneling
- The in-app update checker

It does **not** cover:

- Third-party upstream DNS providers you choose to configure
- Third-party blocklist sources you choose to add
- GitHub's infrastructure (report GitHub-specific issues to GitHub directly)

### Security Design Notes

- The app is built in pure Java with **no Kotlin, no Jetpack Compose, and no Firebase/Google Play Services**, minimizing third-party attack surface.
- VPN mode is local-only: traffic is routed to a sinkhole running on the device itself, not to a remote VPN server.
- The in-app update checker only installs release APKs published to this repository's official [Releases](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/releases) page, and is disabled entirely on F-Droid installs (`UpdateChecker.isSelfUpdateAllowed()`), since F-Droid independently verifies and delivers its own reproducible builds.
- Signed release builds require keystore secrets (`KEYSTORE_BASE64`, `RELEASE_KEYALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_STORE_PASSWORD`) that are only available to the `release` CI job on pushes to `main`/`master`; pull requests from forks only ever produce unsigned debug builds and cannot access these secrets.

### Disclosure Policy

Once a reported vulnerability is confirmed and fixed, a security advisory and/or release note will be published describing the issue at a level of detail that helps users understand the risk, without providing an exploit roadmap for unpatched versions still in use.

---

## License

This project is licensed under **GPL-3.0 or later**. See [`LICENSE`](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/blob/main/LICENSE) for details.

## Contact

Questions can be raised via [GitHub Issues](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/issues) on this repository.
