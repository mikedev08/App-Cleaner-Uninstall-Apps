# App Cleaner QA report

**Date:** 28 Sep 2026
**Builds:** debug `92e666b`, then **`ab984ce`**. Everything below was confirmed on `ab984ce` unless it's listed as fixed.
**Unit tests:** 126 / 126 pass on `ab984ce` (15 classes).
**Crashes:** none on any device. There were **ANRs** under the monkey stress test on the tablet (M12).

## Devices

| ID | Device | Android | Screen | Tester |
|---|---|---|---|---|
| **P** | Pixel 9 emulator (InkSign_Pixel_9) | 16 (API 36) | 1080×2424 @420, 411dp | main session |
| **S** | QA_API34_Small (new) | 14 (API 34) | 720×1280 @360, **320dp** | agent |
| **T** | QA_API30_Tablet (new) | 11 (API 30) | 2560×1600 @320, 1280dp landscape | agent |
| **O** | QA_API26 (new) | **8.0 (API 26, minSdk)** | Pixel 2 | agent |

**Test data:**
- 60 generated test apps: long, emoji, Hebrew, Arabic, CJK, mixed-direction, HTML-like, blank and missing labels; no icon; no launcher; two apps both named "Notes"; renamed-on-update; exactly 250.0 MB, 248.5 MB and 500 MB apps; one app with 150 MB data and 100–120 MB cache; 40 fillers.
- Storage filled to 83 → 87 → 97 → 100% (0 bytes free).
- Clock moved +35/+65/+95/+100 days and back.
- Font scale 1.3 and 2.0, largest display size, dark mode, Arabic and Hebrew.
- Split screen, freeform, "Don't keep activities", process kills, and monkey runs (3 seeds × 3000 events).

Screenshots are in the session scratchpad: `…/scratchpad/qa` (P), `qa-agent-api34` (S), `qa-agent-api30` (T) and `qa-agent-api26` (O). Each device's full notes are in `findings.md` in its folder.

---

## Summary

| Severity | Count |
|---|---|
| Critical | 2 |
| High | 6 |
| Medium | 17 |
| Low | 28 |
| Fixed during this QA round | 4 |

---

## Critical

### C1. Android 8.0: after every uninstall, Android's "Uninstalling <app>…" dialog stays stuck on top of the app (O)
- **Steps:** select 1 or more apps → Uninstall → OK on Android's dialog.
- **Actual:**
  - The app is removed and App Cleaner gets `STATUS_SUCCESS` (the row shows "Removed"), but the package installer's `UninstallUninstalling` activity never closes.
  - Each removed app leaves one more stacked in App Cleaner's task.
  - It covers the result screen permanently, and Back does nothing.
  - The only ways out are swiping the app away in Recents or force-stopping the package installer.
- **Expected:** the dialog closes and the result screen is usable.
- **Cause/fix hint:**
  - `ACTION_UNINSTALL_PACKAGE` with `EXTRA_RETURN_RESULT` closes normally on the same emulator. The broken path is `PackageInstaller.uninstall()`, which launches the `STATUS_PENDING_USER_ACTION` intent (`PackageInstallerRemover.kt`).
  - Use the intent path on API 26 (check 27/28 too).
- **Screenshots:** O/`47_single_stuck.png`, `45_b2_stuck.png`, `36_after_back.png`

### C2. On a 320dp phone, the uninstall confirm sheet loses its Cancel and Uninstall buttons (S)
- **Steps:** select **3 or more** apps → Uninstall.
- **Actual:**
  - With 3 apps, both buttons are squashed into blank pills with no label.
  - With 23 apps, the buttons aren't there at all (not even in the accessibility tree). Back is the only way out, so the batch can't be started.
  - The same happens with **1 app at font scale 2.0** and **2 apps at the largest display size**. At font 1.3 the buttons read "Canc" and "Uninstall …".
- **Expected:** Cancel and Uninstall are always visible.
- **Cause/fix hint:** `UninstallConfirmSheet` puts the header, the list (`heightIn(max = 360.dp)`), the info text and the buttons in one Column that doesn't scroll and is taller than the ~520dp available. Pin the buttons to the bottom, and let the list take the remaining height and scroll.
- **Screenshots:** S/`confirm3apps.png`, `many_confirm3.png`, `fs20_confirm1.png`, `d440_confirm2.png`, `fs13_confirm.png`

---

## High

### H1. Dismissing Android's uninstall dialog with Back stalls the batch, and Stop doesn't end it (P, S, O)
- **Steps:** start a batch → press Back on (or tap outside) Android's dialog → Stop → Stop.
- **Actual:**
  - The row stays "Waiting for your confirmation" (Android 8 sends no status for Back).
  - After Stop, the screen shows "Stopping after this app…" indefinitely. On P, three Stop taps over 30 s didn't end it.
  - Only "Show it again" ends it: the dialog reappears and the batch finishes as stopped.
- **Expected:** Stop ends the batch straight away and marks the pending app "Not attempted".

### H2. Several Cancels in a row: no "stop the batch?" prompt (P, S, T, O)
- **Steps:** a batch of 6 or more apps → Cancel three dialogs in a row.
- **Actual:** the next dialog opens straight away every time. Cancelling all 5 dialogs of a 5-app batch gave no prompt either.
- **Expected** (your PRD note): after 3 consecutive cancels, ask "Stop removing the rest?".
- There's also no warning before a big batch that Android will ask once per app (for example, 63 dialogs).

### H3. Scan doesn't re-measure app sizes cached within the last 24 hours (P, T, O)
- **Steps:** give an app 150 MB of data and 100 MB of cache → Scan.
- **Actual:**
  - The scan, the Cache tab and Home still use the old size (33 kB).
  - Opening the app's details sheet re-measures it (262 MB total, 105 MB cache), and only then do the lists update.
  - Missed as a result: one Large app and about 105 MB of cache.
- **Cause:** `AppSizeMath.isStale` uses a 24 h window, and "Scan my phone" doesn't bypass it.
- **Fix hint:** a manual scan should re-measure every app (or at least any app whose data or cache changed).

### H4. The uninstall progress screen's Stop button is clipped once the "Don't see Android's dialog?" banner shows (S)
- Only a sliver of Stop is visible, although a blind tap still hits it. Seen with 1- and 2-app batches.
- **Screenshots:** S/`uninst_11_banner.png`, `uninst_10b.png`

### H5. At font scale 2.0, the paywall is only its pinned footer (S)
- The Unlock button, the disclaimer and the links fill the screen, so the title, benefits and plans are never visible. With real offerings, users couldn't see the price they're paying.
- The Close X overlaps Unlock, "Terms" renders one letter per line, and "Privacy" is off-screen.
- **Screenshots:** S/`fs20_pay1.png`, `fs20_pay_strip.png`

### H6. At font scale 2.0 and the largest display size, most text is unreadable (S)
- **Font 2.0:**
  - App names "We…" / "Wol…".
  - Tabs "Un…" / "La…" / "C…".
  - Title "App Cle…".
  - History rows show no app name at all.
  - Settings "Lan/gua/ge", "Priva/cy".
  - Details sheet "Last opened" renders one letter per line.
  - The gauge caption collides with the arc.
- **Largest display size (~262dp wide):**
  - The selection bar shows "7…".
  - "Weekly remind/e/rs" breaks mid-word.
  - "Syste…", "U…".
- **Screenshots:** S/`fs20_home_strip.png`, `fs20_apps_strip.png`, `fs20_set_strip.png`, `fs20_details5.png`, `d440_home_strip.png`, `d440_set_strip.png`

---

## Medium

### Uninstall flow
- **M1. "Try again" on one app makes the other failed apps disappear (T).**
  - After a 24-app batch with 7 cancels, Try again on 1 app runs as a new 1-app batch. Its result replaces the old one, and the other 6 are gone.
  - `engine.retry` creates a new batch and the navigation pops the old result.
  - Screenshots: T/`batch_after.png`, `result_after_try1.png`
- **M2. The selection bar drops the unit.** "891" instead of "891 MB" with 21 apps selected at 320dp (S). Screenshot: S/`notif_03_zoom.png`

### Data correctness
- **M3. Android 8: the Cache tab and Home count every app as holding cache (O).**
  - On API 26, empty `cache` and `code_cache` folders report 16 kB, so Home says "Cache · 62 apps" and the tab offers "Select all 62".
  - Filter with a small floor (e.g. > 64 KiB), not `> 0` (`InsightRows.kt:91`).
  - Screenshots: O/`22_cache_tab.png`, `18_settings_storage.png`
- **M4. Unused says "not opened in 60 days" when Android only has minutes or days of usage history (O, T).**
  - After a clock correction (which also happens on a real phone that boots with a wrong clock), `UnusedRule` treats "no record in the window" as unused.
  - This happens even when the window is shorter than the threshold.
  - Result: 52 apps flagged, a reminder saying "52 apps unused for 60+ days", and rows saying "Not opened since at least <this month>".
- **M5. Home shows a stale or contradictory scan (P, S, T).**
  - "Last scan · Just now" never changes after a quick scan followed by granting access (S), or after the clock moves back (P).
  - After +95 days, Home says Unused "None" while the reminder and the Unused tab say 21 (S).
  - After revoking Usage Access, Home keeps showing "Unused 30 apps 1.2 GB" (T).
  - After the clock moves back, "You can free up 891 MB · Review" opens an empty tab (S).
  - Only Scan again fixes these.
- **M6. Free tier: the result screen and History show the APK size, not the size the list and confirm sheet showed (O).**
  - Example: the confirm sheet said "You'll free 2.2 MB", and the result said "about 2.0 MB freed".
  - Cause: `UninstallEngine.measure()` uses StorageStats only for Pro.

### Navigation and notifications
- **M7. Search results open at the old scroll position (O).** After scrolling down, typing a query hides the first matches and the "N apps" count. Screenshot: O/`30_search_scrollpos.png`
- **M8. Tapping the cleanup reminder while the app is on another screen (e.g. Settings) does nothing until you press Back (O).** `SelectionStore.requestTab` is only read by Home and Apps.

### Layout (small screens, tablets and windows)
- **M9. Text truncation and wrapping at 320dp and default font (S):**
  - "Allow access for a full sc…" (scan result button)
  - "Unu…" next to the lock (Apps filter)
  - "Cac…" at font 1.3
  - The Settings "Notifications are off" banner is squeezed into a ~40dp column: "Notific/ations/…"
  - Hebrew: a size headline splits across lines ("…750" / "MB"), and the Unused tab label and the confirm button are truncated.
  - Screenshots: S/`scan_04_result.png`, `apps_01.png`, `settings_s3.png`, `he_apps_strip.png`
- **M10. Tablet landscape: every full screen is one phone-width column stretched across 1280dp (T).**
  - App names and their sizes end up about 2000px apart.
  - Home's second section falls below the fold, with empty margins beside the gauge.
  - Bottom sheets are width-capped, but full screens aren't. Cap the content width at around 640–840dp, or use two panes.
  - Screenshots: T/`apps_all_land.png`, `result_land.png`, `history_land.png`
- **M11. Freeform window: the caption buttons sit on top of the History and Settings icons (T).** Tapping the gear closes the window. The caption-bar insets aren't respected. Screenshots: T/`freeform2.png`
- **M12. Monkey stress: ANRs, then the app can stay blank white (T).**
  - Seed 202 caused an ANR in 2 of 4 runs.
  - Traces: dismissing a `ModalBottomSheet` while waiting on the renderer, and 12.6 s of main-thread CPU in overscroll touch handling.
  - Afterwards the window stayed white (ComposeView 0×0) even after relaunching. Only rotation or killing the app recovered it.
  - The emulator was under heavy load, so the ANR itself may be environmental. The blank window that survives relaunch still needs a look.
  - Traces: T/`anr_202.txt`, `anr_202b.txt`, `sigquit_blank.txt`

### Languages and accessibility
- **M13. Hebrew: some sizes read reversed** ("kB 29", "יתפנו kB 82") in the details sheet and the confirm sheet. Those strings lack the bidi isolates used elsewhere (S). Screenshots: S/`he_det_zoom.png`, `he_confirm.png`
- **M14. TalkBack: the select circle on each app row has no label or state (P).** It's a clickable View flagged "Not Accessibility Friendly", so a screen-reader user can't tell which app it belongs to or whether it's selected. Use `Modifier.toggleable` / `selectable` with the app name, `Role.Checkbox`, and the state.

### Reminders and permissions
- **M15. Reminders stop for about 3.5 months after the clock is corrected (S).**
  - The job's earliest run is +102 days, and forced runs log "before schedule".
  - Separately, the first reminder comes about 7 days after enabling, not the 1 day the KDoc says.
- **M16. Turning reminders on with notifications denied gives no runtime prompt (S).**
  - The switch just turns on.
  - "Allow notifications" opens system settings instead of the permission dialog.
  - The onboarding Skip path never asks for notifications.
- **M17. The free "Time for a cleanup?" nudge has no tap destination (S).** It just resumes the last screen.

---

## Low

**Copy and wording**
- **L1.** The confirm sheet promises "You can reinstall from History" even for sideloaded apps; History marks those "Not from Play" and tapping the row does nothing (P, S).
- **L2.** The empty Unused state says "Nice — every app has been used in the last 60 days" when apps were never opened; they're only excluded because they were installed recently. This matches the restored-phone case in your PRD notes (P, S).
- **L3.** Three phrases for the same state: "No use on record", "No recent use", "No use recorded" (S).
- **L4.** "Freed so far" / History totals still count apps that were later reinstalled (P).

**Dates and time**
- **L5.** After the clock moves back, History shows a future-dated section ("Jan 6, 2027") above "Today", and apps show "Installed Jan 2027" (P).
- **L6.** The details sheet can show "Last opened Sep 1" before "Installed Sep 28" after clock changes (S).

**Layout**
- **L7.**
  - App names cut to about 11 characters at 320dp ("Huge Gam…").
  - History rows cut by the "Not from Play" chip.
  - Locked threshold chips "30 da…" (S).
- **L8.** The Scan pre-screen and the Usage Access explainer cut the text mid-line above the buttons, with no scroll hint. The area does scroll (S).
- **L9.** Paywall: "Privac/y" wraps mid-word, and the Close X floats over scrolled content (S).
- **L10.** Onboarding in landscape: the illustration disappears and the subtitle is cut (S).
- **L11.** The language picker shows about 1.5 languages at a time on 320dp (S).
- **L12.** Large tab without Usage Access: the access card pushes the free list below the fold (S).
- **L13.** Landscape details sheet: the Uninstall button starts below the fold (T).
- **L14.** Landscape search with the keyboard up: the summary card and the selection bar fill the space, so no results are visible (T).
- **L15.** Cold start briefly shows "Scan my phone" before the Last scan card, and the card jumps in height (T).
- **L16.** Dark theme: bottom sheets barely stand out from the page, and the scrim is faint (T).

**RTL and localisation**
- **L17.** In RTL, app labels take the UI's direction instead of their own: "b>Tag</b>…>", "✨💬 Fire Chat 🔥". Use `TextDirection.Content` or BidiFormatter for labels (S, O).
- **L18.** Arabic mixes Eastern and Western digits on the same line (O).
- **L19.** Material accessibility labels ("Close sheet", "Drag handle") stay in English after an in-app language switch (S, O).

**Other**
- **L20.** Process death loses the selection, the open confirm sheet and the search query (`SelectionStore` is in memory only). "Don't keep activities" alone keeps them (T).
- **L21.** If the app dies while the post-onboarding notification prompt is open, onboarding restarts from the language step (S).
- **L22.** The two "Notes" apps are indistinguishable in lists; show the package name as a tiebreaker (S).
- **L23.** Disabled apps look like any other app, with no "Disabled" tag (P).
- **L24.** Home's "Unused · None" row can't be tapped (P, S).
- **L25.** The Cache tab offers "Select all 62", which would uninstall apps just to clear cache (P, O).
- **L26.** Android 11 split screen: cold start took 20.8 s, and 9.2 s in freeform (debug build, busy emulator). Re-check on a release build (T).
- **L27.** A debug-only string ("Forced on by the debug switch below.") isn't translated (P).
- **L28.** Cold start is 3.6–3.8 s on the debug build at 80% storage, and 4.3 s at 97% (P). Measure a release build with baseline profiles before judging.

---

## Fixed during this round (by the design-review commits up to `ab984ce`)
- **Resume banner hidden:** after a crash, "Finish removing N apps?" loaded above the visible area. It now shows at the top, and Discard works.
- **"Large" defined three ways:** the scan and Apps used "≥2% of the phone" while bar colours used 250 MB. It's now 250 MB everywhere (Home, Scan and Apps all show 4 apps · 1.6 GB, with the 249 MB app correctly not Large).
- **Size mismatch:** the selection bar and the confirm sheet showed different sizes (APK vs total). They now match.
- **Dead-end button:** "Free up 129 MB" (cache only) led to an empty Unused tab. The scan CTA is now "Review N big apps · X".

## Checked, not a bug
- **Terms and Privacy links** redirect to the jedyapps.com homepage. You said these are handled through RevenueCat for subscriptions.
- **The paywall lists "No ads"** while the app has no ads yet. You said this is intended.
- **"Wolt וולט 2" displays as "Wolt 2 וולט" in English.** That's standard bidi behaviour; the RTL-UI case is L17.
- **Free space didn't return after removing the 500 MB app on Android 8.0.** The Pixel Launcher still held the deleted APK open; that's the launcher, not App Cleaner.
- **On API 34, a clock change shifts Android's stored usage timestamps.** This is an emulator/testing limitation: "Not opened in N days" chips can't be produced by moving the clock.

---

## Passed

**Build and stability**
- 126/126 unit tests pass.
- No crashes on any device, including at **0 bytes free**.
- Clean monkey runs: seeds 101 and 303 (3000 events each), and seed 202 in 1 of 4 runs.

**Install and onboarding**
- Installs and cold-starts on Android 8.0, 11, 14 and 16.
- Onboarding: language → 3 pages → paywall. Back, Skip, Close, rotation and force-stop mid-flow all work, and the language persists.
- The notification prompt appears only where it should (API 33+).
- At font 2.0 all onboarding pages fit.

**Usage Access**
- On Android 11/14/16 the deep link opens App Cleaner's own page.
- On Android 8 it correctly falls back to the list.
- Returning without granting shows the same gentle explainer (not a paywall), and "Not now" works.
- Granting is picked up on return; revoking while open switches the screens to access cards; re-granting recovers.

**App list**
- All user apps are listed: 59/59 on Android 11, including no-launcher, no-label, blank, symbol, RTL, CJK and emoji labels.
- App Cleaner never lists itself.
- Long names get "…", HTML shows literally, and no-label and no-icon apps fall back correctly.
- The list updates live on install, rename (updated label and version) and external uninstall, and the selection follows.
- Search: case-insensitive, matches package names, handles no results, and shows "2 selected (2 hidden by search)".
- All sort options work, and Last used puts no-record apps first.
- The details sheet: sizes match Settings > Storage exactly (O), "Open" is hidden for launcher-less apps, and App info works.

**Sizes and the scan**
- Device totals match `dumpsys diskstats`.
- The 250 MB boundary is exact.
- "Both unused and big, counted once" has no double counting.
- The scan survives rotation.
- The gauge warns at 75% ("Getting full") and at 90%+ ("almost full").

**Uninstall flow**
- OK, Cancel and Back give the right statuses (Removed / Skipped / waiting).
- "Show it again" works.
- The Stop confirmation and "Keep going" work.
- Batches of 3, 7, 24 and 63 apps (the 63-app one to the confirm sheet only).
- Rotation, split-screen resizing and focus changes mid-dialog all work.
- A process kill mid-batch restores the progress screen, and the relaunch banner offers Continue / Discard; Continue skips apps already removed.
- The result screen, Done, per-app Try again and Try all again work.
- History: groups, totals, icons, "Not from Play", and Clear history with its confirmation and empty state.
- **At 0 bytes free:** batch creation, History rows and icon PNGs are all written, with no crash. The icon save already falls back quietly if it fails, and Android frees app caches first.

**Pro and paywall**
- Free vs Pro gating works in both directions.
- Force premium and "Unlock (debug)" work.
- The paywall shows "Subscriptions aren't available yet" when billing isn't configured.

**Reminders**
- WorkManager jobs are scheduled on API 26 and 34.
- The reminder posts the correct count and size, and tapping it opens Unused with those apps preselected.
- Nothing is posted with 0 unused apps or with notifications denied.
- The free nudge posts only for free users.
- Turning Pro off cancels the job.

**Unused detection**
- 30-day threshold at +35 days: correct.
- Apps installed within the threshold are excluded.
- Moving the clock back before the install date: no crash, and timestamps are clamped.

**Themes, languages, rotation and windows**
- Dark mode on all screens, and the app's Theme setting overrides the system in both directions and persists.
- Arabic and Hebrew flip to RTL straight away, including the full uninstall flow in Hebrew; plurals and dates are correct.
- Hindi and Chinese render.
- Selection and search survive four rotations.
- Split screen adapts to half width, and freeform starts and resizes without a crash.
- "Don't keep activities": search, tab, selection, the confirm sheet and a batch mid-dialog all survive.

---

## Not tested
- **Real purchases and restores:** RevenueCat isn't configured in debug, and purchases are off-limits for testing.
- **Other phone makers:** Samsung, Xiaomi and other OEM builds, including their Usage Access screens.
- **Storage and profiles:** SD card and adoptable storage, work profiles and multiple users.
- **Reinstalling from Play in History:** needs Play-installed apps. No test app came from Play.
- **Real long-term usage history:** apps actually unused for 60–90 days on a real phone.
- **Release build:** performance with R8 and baseline profiles.
