# <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f6e1/512.gif" alt="🛡️" width="32" height="32"> Security Policy for NetShield DNS Resolver by PSBDx

We take the security and privacy of **NetShield DNS Resolver** very seriously. As a privacy-focused, local DNS resolution tool for Android, keeping your network traffic secure, leak-free, and resistant to exploits is our highest priority.

This document explains which versions we support, how to verify your download, what the app does on the network, how to report a vulnerability privately, and how we handle disclosure.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f4e6/512.gif" alt="📦" width="28" height="28"> Supported Versions & Release Status

NetShield DNS Resolver ships **stable releases** as well as **pre-release builds** (Alpha, Beta and Nightly). Security fixes go into the newest build of each channel, so please keep the app up to date.

| Version / Channel | Supported | Status / Description |
| ----------------- | --------- | -------------------- |
| Latest stable release | 🟢 Yes | Receives security fixes |
| Latest Alpha / Beta / Nightly build | 🟢 Yes | Receives fixes, may contain unfinished features |
| `main` branch | 🟢 Yes | Active development |
| Older `3.x` releases | 🟡 Update first | Fixes ship in the newest release. Please update and re-test before reporting |
| Below `3.0.0` | 🔴 No | End of support (the same cutoff our issue bot uses for bug reports) |

Get the latest build from our [GitHub Releases](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/releases) page, F-Droid, or APKPure.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f512/512.gif" alt="🔒" width="28" height="28"> Official Sources & Verifying Your Download

NetShield is distributed **only** through these channels:

* **GitHub Releases:** [Latest signed APK](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/releases/latest)
* **F-Droid:** [com.netshield.dns.psbdx](https://f-droid.org/packages/com.netshield.dns.psbdx/)
* **APKPure:** [com.netshield.dns.psbdx](https://apkpure.com/p/com.netshield.dns.psbdx)

NetShield is **not** published on the Google Play Store. An app with a similar name from the Play Store or any other third-party site is an unofficial or modified build that this project does not maintain, so we cannot vouch for it. Uninstall it and install an official build instead.

Official releases are signed with one key. You can check an APK's signing certificate fingerprint with the steps in the README: [Verify your download](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/blob/main/README.md#verify-your-download). **If the fingerprint does not match, do not install the file.**

Found a fake or modified copy of NetShield? Please tell us at **`support@psbdx.com`**.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f4f1/512.gif" alt="📱" width="28" height="28"> Privacy & Security Architecture Overview

NetShield DNS Resolver is designed with a **privacy-first, local-first** architecture:

* **No Telemetry:** NetShield does **not** rely on any proprietary central servers, user tracking telemetry, or remote logging backends. It does not use Firebase or Google Play Services.
* **What the App Connects To:**
  * Your chosen **upstream DNS resolvers** (such as **Google, Quad9, Cloudflare, AdGuard**, or a custom UDP/TCP/DoH/DoT endpoint).
  * **Open-source filter repositories** you configure, for blocklist sync.
  * **GitHub's public releases API**, to check whether a newer version exists. This update check is **disabled for F-Droid installs**.
* **Local DNS Overrides:** Users have full control to add custom local DNS rules and block specific domains on demand.
* **Operating Modes:**
  1. **Local VPN Mode:** Uses Android's local `VpnService` to loopback and inspect DNS queries locally on-device. No traffic is tunneled to a remote server.
  2. **DNS Server Mode:** Turns the Android device into a local network DNS server, allowing other LAN devices to resolve DNS through NetShield.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f6a8/512.gif" alt="🚨" width="28" height="28"> Reporting a Vulnerability

If you discover a security vulnerability, DNS leak, or unexpected exploit in NetShield DNS Resolver, **please do not open a public GitHub issue, discussion, or pull request.** Public disclosure exposes active installations to risk before a patch is ready.

### <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f465/512.gif" alt="👥" width="24" height="24"> How to Report Privately

1. **GitHub Private Vulnerability Reporting (preferred):**
   - [Report a vulnerability](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/security/advisories/new) to open a confidential security advisory directly with the maintainers.
   - You can also go to the **Security** tab of this repository and click **Report a vulnerability**.

2. **Email Contact (Direct):**
   - Email the maintainer at: **`support@psbdx.com`**
   - Email Subject Line: `[SECURITY] Vulnerability Report: NetShield DNS Resolver`

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f4cb/512.gif" alt="📋" width="28" height="28"> Information to Include in Your Report

To help us assess and resolve the security issue quickly, please provide:

- **Description:** Clear summary of the flaw or security leak vector.
- **Affected Mode:** State whether the issue affects **Local VPN Mode**, **DNS Server Mode**, or both.
- **Component Involved:** e.g., DNS parser, blocklist downloader/sync engine, custom record rule override, upstream forwarder, or update checker.
- **Proof of Concept (PoC):** Reproduction steps, logcat outputs, or packet captures showing the vulnerability or leak.
- **Environment Details:** NetShield version, where you installed it from (GitHub, F-Droid, APKPure), Android version, device model, and configured upstream DNS provider (e.g., Cloudflare, Quad9, custom DoH/DoT).

> **Protect your own privacy:** logs and packet captures can contain domain names, IP addresses and other personal data. Please remove anything private before sending.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/23f1/512.gif" alt="⏱️" width="28" height="28"> Response & Disclosure Timeline

We follow responsible vulnerability disclosure practices:

- **Initial Response:** Within **48 hours** of receiving your email or advisory.
- **Assessment & Triage:** Within **3 to 5 business days**, detailing initial verification and proposed fix timeline.
- **Fix & Advisory:** A fixed build is released on GitHub (stable and/or pre-release, as applicable) together with a published security advisory once the issue is resolved.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f91d/512.gif" alt="🤝" width="28" height="28"> Coordinated Disclosure

- Please keep the details private until a fix has been released and the advisory is public.
- We will keep you updated on our progress while we work on the fix.
- With your permission, we will credit you in the advisory. If you prefer to stay anonymous, just tell us.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f3af/512.gif" alt="🎯" width="28" height="28"> Scope

### In-Scope
- DNS query/response memory buffer safety, unhandled crashes, or infinite loops caused by malformed packets.
- Unintended DNS leaks bypassing local VPN or local server boundaries.
- Flaws in parsing custom blocklists or malicious DNS rule overrides.
- Security vulnerabilities when running in local LAN **DNS Server Mode** (for example, exposure beyond the intended network or abuse as an open resolver).
- Weak or missing certificate validation for encrypted upstreams (DoT / DoH) that allows interception or downgrade.
- Exported Android components (activities, services, receivers, the Quick Settings tile, the widget) that other apps could abuse.
- The in-app update check and APK download/install flow, such as tampering or forced downgrades.
- Weaknesses in our release pipeline or GitHub Actions workflows, such as secret exposure or script injection.

### Out-of-Scope
- Vulnerabilities present in third-party upstream DNS providers (Google, Quad9, Cloudflare, AdGuard, etc.).
- Attacks requiring root-level or physical access to the host device.
- Issues related to third-party open-source blocklist source contents.
- Unofficial, repackaged or modified builds (please report those to `support@psbdx.com` instead).
- Automated scanner output without a working proof of concept, and social engineering.

---

*Thank you for helping keep NetShield DNS Resolver and our open-source community safe!*
