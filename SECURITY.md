# <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f6e1_fe0f/512.gif" alt="🛡️" width="32" height="32"> Security Policy for NetShield DNS Resolver by PSBDx

We take the security and privacy of **NetShield DNS Resolver** very seriously. As a privacy-focused, local DNS resolution tool for Android, keeping your network traffic secure, leak-free, and resistant to exploits is our highest priority.

This document outlines our current release status, offline security architecture, vulnerability reporting process, and disclosure guidelines.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f4e6/512.gif" alt="📦" width="28" height="28"> Supported Versions & Release Status

> **Note:** NetShield DNS Resolver is currently in active **Beta / Pre-release development**. There are **no official stable releases** yet.

| Version / Branch | Supported | Status / Description |
| ---------------- | --------- | -------------------- |
| `main` / `master`| 🟢 Yes    | Active development branch (Latest Beta builds) |
| Pre-release / Beta tags | 🟢 Yes | Actively receiving bug & security patches |
| Deprecated builds | 🔴 No     | Outdated pre-release binaries |

We strongly encourage all beta testers to always update to the latest pre-release APK build published on our [GitHub Releases](../../releases) page.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f4f1/512.gif" alt="📱" width="28" height="28"> Privacy & Security Architecture Overview

NetShield DNS Resolver is designed with a **privacy-first, local-first** architecture:

* **100% Offline Operation:** NetShield does **not** rely on any proprietary central servers, user tracking telemetry, or remote logging backends.
* **Open-Source Blocklists:** Blocklist sync is performed directly between the client device and user-configured open-source filter repositories.
* **Upstream DNS Propagation:** Users can select trusted upstream resolvers (such as **Google, Quad9, Cloudflare, AdGuard**, etc.) or configure custom upstream endpoints (UDP/TCP/DoH/DoT).
* **Local DNS Overrides:** Users have full control to add custom local DNS rules and block specific domains on demand.
* **Operating Modes:**
  1. **Local VPN Mode:** Uses Android's local `VpnService` to loopback and inspect DNS queries locally on-device without routing actual internet traffic through a remote server.
  2. **DNS Server Mode:** Turns the Android device into a local network DNS server, allowing other LAN devices to resolve DNS through NetShield.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f6a8/512.gif" alt="🚨" width="28" height="28"> Reporting a Vulnerability

If you discover a security vulnerability, DNS leak, or unexpected exploit in NetShield DNS Resolver, **please do not open a public GitHub issue.** Publicly disclosing vulnerabilities exposes active beta installations to potential risk before a patch is ready.

### <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f465/512.gif" alt="👥" width="24" height="24"> How to Report Privately

1. **Email Contact (Direct):**
   - Email the maintainer directly at: **`mfhamim95@gmail.com`**
   - Email Subject Line: `[SECURITY] Vulnerability Report: NetShield DNS Resolver`

2. **GitHub Private Vulnerability Reporting:**
   - Go to the **Security** tab of this repository.
   - Click **Report a vulnerability** to open a confidential security advisory directly with the maintainers.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f4cb/512.gif" alt="📋" width="28" height="28"> Information to Include in Your Report

To help us assess and resolve the security issue quickly, please provide:

- **Description:** Clear summary of the flaw or security leak vector.
- **Affected Mode:** State whether the issue affects **Local VPN Mode**, **DNS Server Mode**, or both.
- **Component Involved:** e.g., DNS parser, blocklist downloader/sync engine, custom record rule override, or upstream forwarder.
- **Proof of Concept (PoC):** Reproduction steps, logcat outputs, or packet captures showing the vulnerability or leak.
- **Environment Details:** Android version, device model, and configured upstream DNS provider (e.g., Cloudflare, Quad9, custom DoH/DoT).

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/23f1_fe0f/512.gif" alt="⏱️" width="28" height="28"> Response & Disclosure Timeline

We follow responsible vulnerability disclosure practices:

- **Initial Response:** Within **48 hours** of receiving your email or advisory.
- **Assessment & Triage:** Within **3 to 5 business days**, detailing initial verification and proposed fix timeline.
- **Beta Patch Release:** A fixed beta build tag will be released on GitHub alongside a security advisory disclosure once resolved.

---

## <img src="https://fonts.gstatic.com/s/e/notoemoji/latest/1f3af/512.gif" alt="🎯" width="28" height="28"> Scope

### In-Scope
- DNS query/response memory buffer safety, unhandled crashes, or infinite loops caused by malformed packets.
- Unintended DNS leaks bypassing local VPN or local server boundaries.
- Flaws in parsing custom blocklists or malicious DNS rule overrides.
- Security vulnerabilities when running in local LAN **DNS Server Mode**.

### Out-of-Scope
- Vulnerabilities present in third-party upstream DNS providers (Google, Quad9, Cloudflare, AdGuard, etc.).
- Attacks requiring root-level or physical access to the host device.
- Issues related to third-party open-source blocklist source contents.

---

*Thank you for helping keep NetShield DNS Resolver and our open-source community safe!*
