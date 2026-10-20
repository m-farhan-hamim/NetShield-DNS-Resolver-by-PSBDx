# Accessibility

NetShield DNS Resolver should be usable by as many people as possible, including people who use a
screen reader (TalkBack), large text or display sizes, switch access, or who have low vision or limited
dexterity.

## Where we stand today

**NetShield has not been audited by an accessibility specialist and has not been formally tested with
assistive technology.** The notes below come from checking the app's layouts and colors in the source
code (last reviewed 2026-10-17). They show what we found, not a certified result.

### What works

- **Icon-only images have labels.** All 22 images and icon buttons in the layouts have a
  `contentDescription`, so a screen reader can announce them.
- **Text follows the system font size.** All text sizes use `sp`, so large-text settings apply.
- **Light and dark themes.** NetShield follows your phone's light/dark setting by default, and you can choose Light or Dark yourself under Settings, Appearance.
- **Strong contrast in both themes.** Measured against every background the app uses, text stays above the common guideline (WCAG 2.2 AA asks for 4.5:1 for normal text). Light theme: primary text 15.0:1 to 17.9:1, secondary text 6.4:1 to 7.6:1, muted text 5.0:1 to 5.9:1, accent color 5.0:1 to 5.9:1. Dark theme: primary text 14.0:1 to 18.3:1, secondary text 5.7:1 to 7.5:1, muted text 5.0:1 to 6.5:1, accent color 8.1:1 to 10.6:1.
- **Settings are grouped into labelled tiles.** Each tile is at least 124dp tall, reads as one item with a title and a short description, and the back button at the top of a section is a full 48dp target.
- **Standard Android controls.** Screens are built from standard AndroidX and Material widgets, which
  bring TalkBack, keyboard and switch-access support by default.

### Known gaps

We would rather tell you than hide them:

- **Small touch targets.** 16 icon-style controls are drawn smaller than the recommended 48dp x 48dp
  (for example the back arrow at 32dp and some list icons at 36dp to 40dp). They can be hard to hit
  for people with limited dexterity.
- **Labels cannot be translated.** 16 content descriptions are written directly in the layouts instead
  of in `strings.xml`, so they are English only.
- **Not yet checked:** focus order and keyboard navigation, the traffic charts and dashboard for a
  text alternative, home-screen widgets, the quick-settings tile, notification wording, landscape and
  tablet layouts, right-to-left languages, and behavior at the largest font and display sizes.

## Guidelines for contributors

Any user-interface change must follow these. Reviewers will check them.

1. **Label every control.** An icon-only button needs `android:contentDescription="@string/..."`. A
   purely decorative image uses `android:importantForAccessibility="no"`. Put the text in
   `strings.xml`, not in the layout.
2. **Make touch targets at least 48dp x 48dp.** If the icon is smaller, add padding or set
   `android:minWidth` and `android:minHeight` rather than shrinking the tap area.
3. **Use `sp` for text** and let it wrap or scroll. Do not set fixed heights that cut text off at large
   font sizes.
4. **Use color tokens, never hex codes.** Colors come from `res/values/colors.xml` (light) and
   `res/values-night/colors.xml` (dark), so both themes work. Check any new color pair is at least 4.5:1 for
   normal text and 3:1 for large text and icons in **both** themes, and test your screen in both.
5. **Never rely on color alone.** Show state with text or an icon as well (for example "On", "Error"),
   not only a red or green color.
6. **Give inputs a label or hint,** and show errors as text near the field.
7. **Announce changes.** When something changes without the user touching it (a test result, a status),
   use `accessibilityLiveRegion` or `announceForAccessibility`.
8. **Do not hide important information in short toasts.** A screen reader user may miss it. Use text on
   the screen for anything the user must act on.
9. **Keep a sensible focus order** and make sure every action works without touching the screen.
10. **Respect motion settings.** Do not add animation that cannot be turned off.

## How to test

Please try your change with at least one of these before you open a pull request:

- **TalkBack:** turn it on in Settings, then Accessibility. Move through the screen with swipes and
  check that every control is announced in a way that makes sense.
- **Large text:** set Font size and Display size to their largest in Settings and check nothing is cut
  off or overlapping.
- **Accessibility Scanner:** Google's free Accessibility Scanner app lists missing labels, small
  targets and low contrast.
- **Keyboard or switch access:** make sure every action can be reached without touch.

## Report a problem

Accessibility problems are bugs, and we take them seriously. Please
[open an issue](https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx/issues/new/choose)
with the bug report form and start the title with `[Accessibility]`. Tell us:

- what you were trying to do,
- which assistive technology or setting you use (for example TalkBack, font size 200%),
- your device and Android version.

You can also write to **support@psbdx.com**. NetShield is maintained by volunteers, so we cannot promise
a fix date, but accessibility reports are prioritised.

## Standards we aim for

- [Android accessibility guidelines](https://developer.android.com/guide/topics/ui/accessibility)
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/), level AA, where it applies to a mobile app
