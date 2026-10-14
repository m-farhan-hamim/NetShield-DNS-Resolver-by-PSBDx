# Contributing to NetShield DNS Resolver

Thank you for wanting to help. NetShield is a small, privacy-focused, free and open source app, and
good contributions of every size are welcome: bug reports, testing on your device, accessibility
fixes, documentation, and code.

By taking part you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Quick start

1. **Found a bug or have an idea?** [Open an issue](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/issues/new/choose)
   and use the form. Search existing issues first.
2. **Found a security problem?** Do **not** open a public issue. Follow [SECURITY.md](SECURITY.md).
3. **Want to change code?** For anything bigger than a small fix, open an issue first so we can agree
   on the approach before you spend time on it (VPN, DNS and proxy changes especially).
4. **Ready?** Fork, create a branch from `main`, make a focused change, and open a pull request.
   Fill in the pull request form completely.

## What every change must respect

These are the project's principles. A pull request that breaks one of them will not be merged.

- **Pure Java, minimal dependencies.** The app uses Java with AndroidX and Material only. No Kotlin,
  Jetpack Compose, Firebase or Google Play Services, and no new libraries without a very good reason
  (explain it in the pull request).
- **Free software only, and F-Droid friendly.** No proprietary SDKs, no ads, no analytics, no tracking,
  and no prebuilt binaries (`.so`, `.jar`, `.aar`, `.dex`, ...). Everything must build from source.
- **Privacy first.** Query data never leaves the device. No telemetry. New network connections need a
  clear reason and must be to something the user chose (an upstream resolver, a blocklist source).
- **Reproducible builds.** Do not add anything that makes the APK differ from build to build, such as
  timestamps, random values or build-machine paths.
- **Least privilege.** Do not add Android permissions or exported components unless the feature cannot
  work without them, and say so in the pull request.
- **Accessible.** User-interface changes must follow [ACCESSIBILITY.md](ACCESSIBILITY.md).
- **Small and focused.** One topic per pull request. Do not mix a bug fix with a refactor.

## Files only maintainers change

To keep the project safe, these files can only be edited by the maintainers. A pull request from anyone
else that changes them is stopped automatically:

- everything under `.github/` (workflows, issue and pull request templates, bot scripts)
- `README.md`
- `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CONTRIBUTING.md`, `ACCESSIBILITY.md`

Found a typo or a mistake in one of them? Please [open an issue](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/issues/new/choose)
and we will fix it.

Please also leave these to the maintainers: the app version (`versionName` and `versionCode` in
`app/build.gradle.kts`) and the release changelogs in `fastlane/`. Versions are bumped when a release is
prepared.

## Setting up

You need a JDK (17 or newer) and Android SDK 36. CI builds with JDK 21 (Temurin) and Gradle 9.3.1.

The repository does **not** contain the Gradle wrapper (CI generates it), so use an installed Gradle:

```bash
gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. If you cannot build locally, open the pull
request anyway: CI builds the debug APK for every pull request. Please do not commit `gradlew` or
`gradle-wrapper.jar`.

### Tests

- **Proxy engine** (`app/src/main/java/com/example/proxy/core`): it is pure Java and is tested against a
  real Linux TCP stack through a TUN device. You need Linux, JDK 17+, Python 3, `/dev/net/tun` and
  `sudo`:

  ```bash
  bash tools/proxy-interop/run-interop.sh
  ```

  CI runs this automatically for any change to the proxy core.
- **Pull request bot** (maintainers): `node --test .github/scripts/pr-guard.test.js`

There is no unit-test setup for the Android-specific code yet. Test that on a device or emulator and
describe what you did in the pull request.

## Code style

- Match the style of the code around your change (4-space indentation, braces on the same line).
- Comments explain *why*, not *what*.
- User-visible text goes in `res/values/strings.xml`, never hard-coded in Java or layouts.
- Sizes for text are in `sp`, for everything else in `dp`.
- Target: `minSdk 24`, `targetSdk 36`, Java 11 language level.
- Keep Android-independent logic free of Android classes so it can be tested on a normal JVM.

## Branches and commits

- Branch from the latest `main`. Suggested names: `feat/...`, `fix/...`, `docs/...`.
- Commit messages: a short summary line, prefixed with the kind of change, for example
  `fix: stop crash when the blocklist is empty` or `docs: explain proxy setup`
  (`feat`, `fix`, `docs`, `build`, `ci`, `test`, `refactor`).
- Do not rewrite history on a branch after someone has reviewed it.

## Pull requests

1. Fill in every part of the [pull request form](.github/pull_request_template.md) and tick the
   checklist. Keep its headings as they are.
2. Make sure CI is green: the build, CodeQL code scanning, and the pull request bot.
3. Be ready to answer questions. Reviews are done by volunteers, so please be patient.

### What the pull request bot checks

Every pull request is checked automatically. This is meant to protect users, and it is not personal.

| Check | What happens |
|---|---|
| **Pull request form** is complete | If not, the check fails and says what is missing. |
| **Protected files** are untouched | If you edited one, the check fails and lists the files. Maintainers are exempt. |
| **Risky code patterns** (binaries, loading code at runtime, running shell commands, hidden unicode characters, sending data to chat or webhook endpoints, sensitive permissions, ...) | High-risk patterns fail the check until a maintainer has reviewed them. Lower-risk things (build files, new URLs, new permissions, reflection) only add a note and a label for the reviewer. |

The bot only reads the diff. It never runs your code. It is a pattern check and **not a malware scanner**,
and it does not replace a human review. If it flags something that is fine, explain it in the pull
request and a maintainer will look. Maintainers' own pull requests are still scanned, but findings are
then only a heads-up.

## AI-assisted contributions

You may use AI tools. You are responsible for the result:

- Read, understand and test every line before you submit it, and be able to explain it in review.
- Do not submit large generated changes you have not run.
- Mention it in the pull request if a tool wrote a significant part.

(This project itself was built with the help of AI tools, see the About screen.)

## Licensing

NetShield is licensed under the **GNU General Public License v3.0 or later**. By submitting a
contribution you agree to license it under the same terms. Only submit work you wrote or have the right
to share under that license.

## Getting help

- Questions and ideas: [open an issue](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/issues/new/choose).
- Security reports: see [SECURITY.md](SECURITY.md).
- Conduct concerns: see [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
