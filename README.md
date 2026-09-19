# NetShield DNS Resolver

A local DNS resolver, sinkhole (ad/tracker blocker), and traffic dashboard for
Android. Runs either as a device-wide VPN (routing all system DNS queries
through a local sinkhole) or as a local UDP server on `127.0.0.1`, with
DoH/DoT/UDP upstream support, custom blocklists, per-app split tunneling, and
real-time traffic logs.

## Building

Pure Java, no Kotlin, no Compose, no Firebase/Google Play Services - just
AndroidX + Material.

```
./gradlew :app:assembleDebug
```

A release build works without any secrets configured (it just produces an
unsigned APK); locally, set `KEYSTORE_PATH`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEYALIAS`, and `RELEASE_KEY_PASSWORD` environment variables if you
want a signed release build.

In CI, the `release` job in `.github/workflows/build.yml` builds a signed
production APK + AAB on every push to `main`/`master` (and on manual
`workflow_dispatch` runs), using these GitHub Actions secrets:

- `KEYSTORE_BASE64` - your upload keystore, base64-encoded
  (`base64 -w0 your-key.jks > keystore.b64` on Linux/macOS)
- `RELEASE_KEYALIAS` - the key alias inside that keystore
- `RELEASE_KEY_PASSWORD` - the key's password
- `RELEASE_STORE_PASSWORD` - the keystore's password

PR builds from forks can't access these secrets, so they only get the
unsigned debug build - that's expected.

Push a tag like `v1.1.0` (matching `versionName` in `app/build.gradle.kts`)
for your own release records - the in-app update checker no longer reads
GitHub Releases (see below), so this step is now optional/for archival.

## In-app update checker

The app checks `GET https://psbdx.com/wp-json/dns-app/v1/info` on launch,
expecting:

```json
{
  "version": "1.0.0",
  "url": "https://psbdx.com/.../signed-download-link"
}
```

`version` is compared against the installed `versionName`; if newer, a
banner offers to download and install the APK at `url` (via the system
package installer, using a `FileProvider`). `url` is followed as-is,
redirects included, so a freshly generated/expiring signed link works
transparently.

**This is intentionally disabled for F-Droid installs.** F-Droid's
inclusion policy doesn't want apps that self-update, since F-Droid's own
client already verifies and delivers updates from its own reproducible
build. `UpdateChecker.isSelfUpdateAllowed()` checks the installer package
name (`getInstallSourceInfo` / `getInstallerPackageName`) and the whole
feature is a silent no-op whenever that's `org.fdroid.fdroid` - those
users get updates from F-Droid instead. Keep this check in place if you
submit to F-Droid; removing it (or removing the `REQUEST_INSTALL_PACKAGES`
permission usage generally) is likely to get the app rejected or flagged.

## Quick Settings tile & home-screen widget

Beyond the in-app Start/Stop button, the resolver can be toggled with a
single tap from:

- **Quick Settings** ("control panel") - add the "NetShield" tile from the
  panel's edit screen. Reflects Active/Inactive state live.
- **Home-screen widget** - a resizable card showing live status and query
  stats, with a dedicated power button to toggle instantly (tapping the rest
  of the card opens the app).

Both use the same underlying `ServiceManager.ACTION_STOP` self-stop
mechanism as the in-app button, so behavior stays consistent everywhere. If
VPN mode hasn't been granted permission yet, tapping either will briefly
open the app to complete that one-time system consent dialog, then behave
identically afterward.

## License

Licensed under the GNU General Public License v3.0 or later - see
[`LICENSE`](./LICENSE). Free and open source; contributions welcome.

## About / Disclaimer

This app has been built by PSBDx (M. Farhan Hamim). Significant use of
Google AI has been made.
