# Content model for the App Cleaner V1 PRD. build_prd.py renders it to .docx and .pdf.
# Markup inside strings: **bold**, `code`.

APP = "App Cleaner"
TITLE = "App Cleaner: Uninstall Apps"
HEADER = "App Cleaner  |  Product Requirements Document"

INFO = [
    ("App Name", TITLE),
    ("Version", "1.2"),
    ("Date", "September 28, 2026"),
    ("Status", "In development (V1) — describes the current build"),
    ("Author", "Product — Jedy Apps"),
    ("Platform", "Android Native (Kotlin)"),
]

INTRO_NOTE = ("**Scope in one line.** A free batch uninstaller with an honest storage overview — Heavy apps, "
              "Temp files and every app's full size breakdown included — plus a weekly subscription (3-day free "
              "trial) that unlocks only the Unused apps finder, cleanup reminders and no ads. "
              "No connection gate, no account, no backend.")

B = []  # blocks
def h1(t): B.append(("h1", t))
def h2(t): B.append(("h2", t))
def p(t): B.append(("p", t))
def b1(t): B.append(("b1", t))
def b2(t): B.append(("b2", t))
def n1(t): B.append(("n1", t))
def sub(t): B.append(("sub", t))
def table(widths, rows): B.append(("table", widths, rows))
def pagebreak(): B.append(("pagebreak",))

# ---------------------------------------------------------------- 0
h1("0. Objective & Core Logic")
p("Ship the fastest way on Android to go from “my storage is full” to “those apps are gone” — select a "
  "handful of apps once, confirm them in a row, and see exactly how much space came back. Android buries "
  "uninstalling under Settings → Apps → app → Uninstall, one app at a time; on a phone with 120 apps that "
  "is the reason people never clean up. The product target is a median of under 60 seconds from app open to "
  "the first completed batch.")
p("The whole app is three verbs. **Find -> Select -> Remove.** Every screen in this document serves one of "
  "them. Free users get almost all of Find — every app by name, size and date, the Heavy apps list, each "
  "app's temp files and its full size breakdown — and unlimited Remove. The subscription sells one better "
  "Find: which apps you have actually stopped using, because that is the question a plain app list can't "
  "answer.")
p("The five architectural decisions everything else follows from:")
b1("**Android uninstalls, we queue.** The app never removes a package itself. It hands each selected package "
   "to `PackageInstaller.uninstall()` and Android shows its own confirmation dialog, one app after another. "
   "Every call returns a real per-app result (success, cancelled, blocked) through an `IntentSender`, so the "
   "result screen reports what actually happened rather than what we hoped happened. No Accessibility "
   "auto-tapping, no root — nothing that Play policy can pull the listing over.")
b1("**Measure before you delete.** Once a package is uninstalled, its icon, label and size are gone from "
   "`PackageManager` for good. So the app snapshots all three into Room *before* each confirmation dialog "
   "opens. The “Freed 2.4 GB” number and the entire Uninstall History are built from that snapshot — "
   "skipping it is why competitor history screens show blank icons and “0 B”.")
b1("**Show the count before the price.** Usage Access is requested for free, before any paywall. The Unused "
   "apps finder then computes the real answer on-device and shows the count in clear over blurred rows: "
   "“14 apps not opened in 30 days · 3.4 GB”. The number is the pitch. A paywall that says “find unused apps” converts on "
   "curiosity; one that says *you have 14* converts on evidence.")
b1("**The app inventory never leaves the phone.** The list of apps a person has installed is personal and "
   "sensitive data under Google Play's User Data policy. No network call, analytics event or crash log may "
   "carry a package name or app label — events carry counts and byte buckets only (Section 9).")
b1("**An honest cleaner.** “App Cleaner” means cleaning apps off the phone. Since Android 6, a normal app "
   "cannot clear another app's cache or “junk” without root, so V1 never claims to. The app shows each app's "
   "temp files (Android's cache) truthfully and routes clearing them to the system App info page. Fake boost/junk claims are "
   "the #1 reason cleaner apps get suspended under Play's Deceptive Behavior policy — we sidestep the "
   "entire category risk by not making them.")

# ---------------------------------------------------------------- 1
h1("1. Configuration, ASO & Design System")
b1(f"**App Name:** {TITLE} (27 characters). “App Cleaner” is the brand, “Uninstall Apps” is the highest-volume "
   "search phrase in the category and mirrors how the leaders are titled (“Uninstaller”, “Delete apps - Easy "
   "Uninstall”, “Uninstall Apps – App Remover”). The bare name “App Cleaner” is generic and already crowded "
   "(e.g. “Uninstaller - My App Cleaner”, uninstaller.apps.uninstall.free), so the keyword suffix is what "
   "makes the title rank and distinct. Listing copy uses the same plain names as the app — Unused apps, Heavy "
   "apps, Temp files — and never “junk”, “boost” or “cache”.")
b1("**Reference App(s):** Delete apps - Easy Uninstall (com.mobique.deleteapps) — 1M+ installs, 4.4 (33.6K), "
   "the primary reference for its one-screen select-and-remove flow; Uninstaller (com.splendapps.shark) — 10M+, "
   "4.8 (256K), for sort modes and the storage bar; Easy Uninstaller (mobi.infolife.uninstaller) — 10M+, 4.5 "
   "(181K), for uninstall history and usage tracking; AppDrop (com.bulk.uninstall) — 50K+, 4.2, as the "
   "Accessibility auto-uninstall approach we deliberately do not take. Full findings in Appendix A.")
b1("**Design Kit / Figma Reference:** Design system v2 — “Spring green”, light and dark, Plus Jakarta Sans "
   "(details under UI/UX Specifications below; rationale in `docs/design-review.md`).")
b1("**Bundle ID:** com.jedy.appcleaner.uninstaller")
b1("**Platform:** Android Native (Kotlin, Jetpack Compose). Min SDK 26 — `StorageStatsManager` (per-app "
   "sizes) and `StorageManager.getAllocatableBytes` both start at API 26, and Android 7 is under 2% of active "
   "devices.")
b1("**Orientation:** Portrait only, locked in the manifest (`android:screenOrientation` = `portrait`). Android 16 "
   "and later ignore the lock on displays 600dp wide or more (tablets, open foldables), so screens must still "
   "work there in landscape (Section 6).")
b1("**Supported Languages (9):** English, Spanish, French, German, Chinese, Hindi, Arabic, Hebrew, Russian — the "
   "InkSign set plus Russian, reusing its `AppLanguage` / `LocaleController` module. Every language uses the "
   "same plain names (Unused apps, Heavy apps, Temp files).")
b2("Layout Rule: Strict Left-To-Right (LTR) enforced globally, with dynamic switching to Right-To-Left (RTL) "
   "ONLY when Arabic or Hebrew is selected during onboarding or in device settings.")
b1("**Target Audience:** Someone standing in front of the “Storage almost full” notification, trying to take a "
   "video or install an update right now. Also the person handing down an old phone, and the parent clearing "
   "the eleven games a child installed over a weekend. The unifying trait is not a demographic — it is a phone "
   "with far more apps than its owner remembers installing, and a need to get space back in the next two minutes.")
b1("**UI/UX Specifications:**")
b2("Theme & Palette (design system v2): Follows the system theme (light/dark), overridable in Settings. Light: "
   "white background, soft green-grey cards (#EEF2EF) with 1dp borders (#D8E0DB), primary text #0F1714. Dark: "
   "background #0B0F0E, cards #1A2220, borders #2A3531, primary text #F2F5F4. “Spring green” accent #22C55E for "
   "primary buttons, checkboxes, the selection bar, filter chips, the gauge and size bars; green *text* uses "
   "accentText (#15773A light / #4ADE80 dark) so it passes contrast. As an action colour, red (#DC2626 light / "
   "#F87171 dark) is reserved for destructive actions — Remove, Clear history — so a red button always means "
   "“this removes something”. Status colour is a fill, never text: the gauge ring turns amber, then red, as the "
   "phone fills, and a Heavy app's size bar is amber. No orange text anywhere. Premium gold is quiet: a small gold "
   "PRO badge on the Unused apps row and filter, with one strong spot — the Pro card in Settings.")
b2("Design Integrity: Plus Jakarta Sans (bundled variable font); scripts it lacks — Arabic, Hebrew, Devanagari, "
   "Chinese — fall back to the system font per glyph. Soft cards with 1dp borders instead of shadows, a 20dp "
   "screen gutter, 48dp minimum touch targets, rounded Material Symbols. Text never truncates with “…”: app names "
   "wrap to two lines and labels fit or wrap. List rows are at least 80dp with a 48dp app icon, the name, the "
   "size, a thin size bar and at most one chip. Motion is limited to state changes — the gauge sweep, the "
   "selection bar sliding up, the freed-space count-up and a short confetti burst on the Result screen.")

# ---------------------------------------------------------------- 2
h1("2. Mandatory V1 Infrastructure & Technical Policies")
b1("**Android API Target:** Must target Android 16 (API level 36) or higher.")
b1("**Billing & Purchases:** Google Play Billing Library (MUST be v8.0.0+, v9 recommended) alongside "
   "RevenueCat for purchase state management. V1 sells exactly one product: a **weekly auto-renewing "
   "subscription with a 3-day free trial** (Play base plan + free-trial offer), mapped to a single RevenueCat "
   "entitlement `premium`. No yearly or lifetime plan in V1 (Section 10). "
   "RevenueCat is integrated but its production API key is still to be added; until then release builds show "
   "“Subscriptions aren't available yet” and debug builds can unlock Pro on the device for testing.")
b1("**Analytics & Monitoring:** Full integration of Firebase Analytics and Crashlytics — subject to the "
   "inventory rule below: no package names or app labels in any event parameter, custom key or log line.")
b1("**Smart Notifications System:** Local notifications via WorkManager, tailored for retention. Premium: the "
   "cleanup reminder (Feature 4). Free: at most one generic nudge per 30 days (“Time for a cleanup? You have "
   "126 apps installed”) that needs no usage data.")
b1("**User Privacy & Consent (EU & US):** Google UMP SDK integration for GDPR (EU) and US state privacy "
   "regulations. It ships together with ads (below), so the current build shows no consent dialog.")
b1("**Connectivity:** No connection gate (a deliberate difference from InkSign v1.1). Uninstalling is local "
   "work and the revenue is the subscription, not ad impressions; a wall in front of a two-minute cleanup "
   "task would buy almost no ad revenue and a lot of one-star reviews. Ads, consent and the paywall price load "
   "opportunistically when a connection exists.")
b1("**Ads (planned, not in the current build):** The ad spec in this document — a banner above the Selection "
   "Bar, a native ad on the Result screen, an exit interstitial, all for free users only and never on the "
   "uninstall path (Section 6) — remains the plan. By owner decision the current build ships with no ads and no "
   "ads SDK; Pro's “No ads” benefit takes effect when ads ship.")
b1("**On-Device Inventory Policy:** No account, no login, no server. The installed-app list, usage timestamps "
   "and storage numbers are read, computed and stored on the device only. The Play Data safety form declares "
   "“App activity → Installed apps” and “App activity → App interactions” as **processed on-device, not "
   "collected**. This is a hard architectural rule and the app's primary trust claim.")
b1("**Restricted Permissions & Play Declarations:**")
b2("`QUERY_ALL_PACKAGES` — required to list every installed app. File the Play Console permission declaration "
   "under the core-purpose use case (app management / uninstaller). A launcher-intent `<queries>` fallback is "
   "not acceptable: it silently hides keyboards, widget packs and launcher-less apps, which are exactly the "
   "forgotten ones.")
b2("`REQUEST_DELETE_PACKAGES` — required for `PackageInstaller.uninstall()`. Normal permission, no runtime prompt.")
b2("`PACKAGE_USAGE_STATS` — special app access (Usage Access), granted by the user in Settings. Preceded by "
   "an in-app prominent-disclosure screen (Screen 11) as Play's User Data policy requires.")
b2("`POST_NOTIFICATIONS` — runtime prompt, requested at the end of onboarding.")
b2("No `BIND_ACCESSIBILITY_SERVICE`, no `MANAGE_EXTERNAL_STORAGE`, no root paths.")
b1("**Engines & Libraries:** `PackageManager` + `PackageInstaller` for inventory and removal; "
   "`UsageStatsManager` for last-used data; `StorageStatsManager` + `StorageManager` for sizes; Room for the "
   "cached inventory, size/icon snapshots, the uninstall queue and history; WorkManager for reminders; a small "
   "in-app icon loader (off the main thread, memory cache keyed by package + size) for list icons; Hilt for dependency injection; Jetpack Compose "
   "+ Material 3 for UI.")
b1("**Performance Budget (acceptance criteria):** cold start to a populated list under 1.0s on a mid-range "
   "device (served from the Room inventory cache, refreshed in the background); full inventory refresh of 300 "
   "apps under 2.0s; scrolling at 60fps with icons; next confirmation dialog shown within 300ms of the "
   "previous result.")
b1("**Startup (technical policy):** App startup work (the `AppStartup` hooks — RevenueCat setup, reminder "
   "scheduling, package receivers) runs off the main thread on the application scope, and background rescans "
   "start only after the first frame is drawn. Measured cold start on a real phone: about 0.75 s.")
p("*Ads SDKs and Subscription models are characterized in separate, dedicated PRDs.*")

# ---------------------------------------------------------------- 3
h1("3. Onboarding Experience & Specification")
b1("**Onboarding Type:** Language Selector -> 3-Slide Carousel -> Notification prompt -> Paywall (dismissible).")
b1("**Visual Layout & Elements:**")
b2("Initial Screen: Full-screen language selector, defaulting to device locale. If Arabic or Hebrew is "
   "selected, the UI instantly flips to RTL.")
b2("Hero Illustrations: Animated spring-green scenes on soft layered discs, light and dark. Slide 1: a board of "
   "app tiles pops in and four of them get ticked for removal. Slide 2: the real storage gauge sweeping up to "
   "“80% full”. Slide 3: a month calendar where faded, dusty app icons settle on the days nobody opened them.")
b2("Headline & Subtext: Slide 1 “Remove many apps at once” / “Pick them, confirm, done — no digging through "
   "Settings.” Slide 2 “See what's using up space” / “Every app's size, sorted biggest first.” Slide 3 “Find "
   "your unused apps” / “See the apps you haven't opened in 30 days.”")
b2("Controls & CTAs: “Next” button (bottom end), “Skip” (top end), and a large green “Get started” CTA on the "
   "final slide.")
b1("**Permission Requests & Sequence:**")
b2("Step 1: Push notification permission prompt (Android 13+), triggered on tapping “Get started” on the last "
   "slide.")
b2("Step 2: Onboarding Paywall (Screen 3), dismissible with a visible close (X) from the first frame.")
b2("Step 3: Usage Access, requested contextually only — when the user starts a full check from Home, opens the "
   "Unused apps, Heavy apps or Temp files filter without it, taps the allow prompt on Home's “Where your space "
   "goes” card, or taps Usage access in Settings — always preceded by the disclosure screen (Screen 11). Never "
   "during onboarding: sending a first-time user into a system Settings page before they have seen their own "
   "phone is the single biggest abandonment point in this category.")
b2("With ads (Section 2, not in the current build): the Google UMP GDPR/US consent dialog runs on first launch "
   "right after the language selector, blocking until resolved. With no connection, UMP cannot load: skip it for "
   "this session, treat consent as not granted (non-personalized ads only), and retry on the next cold start.")
b1("**User Logic & Edge Cases:**")
b2("Tapping “Skip” immediately writes `onboarding_complete` and still shows the paywall once; closing it lands on "
   "Home. The paywall is shown at most once in onboarding — never again on cold start.")
b2("Swipe gestures and button taps must both be supported for carousel navigation.")
b2("The inventory scan starts in the background on the language screen, so Home is populated the moment "
   "onboarding ends — no empty list with a spinner as the first real screen.")
b2("Cold start from the reminder notification (premium) on a device where onboarding somehow never completed "
   "(data cleared): run the language selector only, then go straight to the Apps screen on the Unused apps "
   "filter.")

# ---------------------------------------------------------------- 4
h1("4. Screen Architecture & Screen-by-Screen UI Breakdown")
b1("**Screen 1: Splash Screen** — `SplashScreen` API, green app icon on the theme background, held only until "
   "the Room inventory cache is read (target under 400ms).")
b1("**Screen 2: Onboarding Flow** (detailed in Section 3).")
b1("**Screen 3: Paywall** — Close (X) top-start, always visible. Headline “Find your unused apps” / “See the apps "
   "you don't use anymore, and remove them in a few taps.” Three benefit rows with icons: Find unused apps (“See "
   "the apps you haven't opened in 30 days.”) · Weekly reminders (“A friendly nudge when unused apps pile up.”) "
   "· No ads. When opened from the Unused apps filter, the headline is replaced with the real finding (“14 apps "
   "not opened in 30 days · 3.4 GB”). One plan card: “3 days free, then <price>/week”. Large green CTA “Start "
   "free trial”. Beneath it, the Play-required terms line: “Renews every week after the trial. Cancel anytime in "
   "Google Play.” Footer links: Restore purchases · Terms · Privacy. For users who already used their trial "
   "(RevenueCat reports no eligible offer), the card and CTA switch to “<price>/week” / “Subscribe”. Heavy apps, "
   "Temp files and app sizes are never sold here — they are free.")
b1("**Screen 4: Home** — a dashboard, not a list. Top to bottom:")
b2("Top App Bar: title “App Cleaner”, History icon, Settings gear. Search and sort live on the Apps screen.")
b2("Storage gauge: a ring with the big number “19% full”, “48 GB of 256 GB used”, a plain status — “Lots of free "
   "space”, “Filling up” or “Almost full” — and “208 GB free” (“Only 2 GB left” when almost full).")
b2("Hero card, one card driven by state: before any check, “See what you can remove” with the green CTA “Check my "
   "phone” (“Find unused apps, heavy apps and temp files.”). After a check: “Last check · 2 hours ago”, “You can "
   "free up 3.1 GB”, a “Review” button (opens the Apps screen on the most useful filter) and a quiet “Check "
   "again”. Nothing worth removing: “Not much to clean up”.")
b2("“Where your space goes”: one grouped card with three rows, each a whole category with its count and size — "
   "Unused apps (gold PRO badge for free users; the count is shown, the list is Pro) · Heavy apps · Temp files. A "
   "row with nothing in it reads “None” and has no chevron. Without Usage Access the card carries an allow prompt "
   "(→ Screen 11). When an app is in both Unused apps and Heavy apps, a small line under the rows says “2 apps are "
   "both unused and heavy — counted once.”")
b2("Banners: “Finish removing 3 apps?” after an interrupted batch (Section 6), and “Your Pro plan has ended.” "
   "with “Renew” after a lapse — placed last so it never pushes the page down.")
b1("**Screen 5: Check (Scan)** — opened from “Check my phone” or “Check again”.")
b2("Intro, only without Usage Access: what the check looks for — apps you haven't opened in 30 days · heavy apps "
   "(250 MB or more) · temp files apps keep to open faster — with “Turn on Usage access” (→ Screen 11) or “Quick "
   "check”, which only looks at app sizes.")
b2("Progress: a progress ring with three steps — Looking at your apps · Finding unused apps · Adding up app "
   "sizes. A step that needs Usage Access shows “Needs Usage access”.")
b2("Result: “All done” and the headline “you can free up 3.1 GB”, counting up once; a before/after storage bar "
   "with a legend (“Now: 80% full” / “After cleanup: 72% full”); the same three category rows as Home, with the "
   "overlap line when needed. Primary button for free users: “See 4 heavy apps · 2.2 GB” (Apps screen, Heavy "
   "apps filter), or “See temp files · 640 MB” when that is all there is; for Pro: “Review 11 apps · 3.1 GB”. "
   "Under a free button, a quiet “See your 7 unused apps with Pro” (→ Screen 3) — a count only, never a size. "
   "Secondary: “See all apps”. Nothing found: “Your phone looks tidy”. Failure: “The check couldn't finish · "
   "Nothing on your phone was changed.” with “Try again”. When some sizes are install sizes: “Some sizes are "
   "rough. Turn on Usage access to see exact sizes.”")
b1("**Screen 6: Apps** — the full list, opened from a Home row, the hero card's Review, the Check result or a "
   "reminder.")
b2("Header: a large title “Apps” with “87 apps · 12 GB” that scrolls away with the list (a small title fades into "
   "the top bar). Top bar: back, Search, Sort. Pinned below it: horizontally scrollable filter chips with icons — "
   "All · Unused apps · Heavy apps · Temp files (Unused apps carries the PRO badge for free users).")
b2("Rows: 48dp app icon, name (wraps to two lines, never cut with “…”), the best-known size (“about 248 MB” when "
   "it is only the install size), a thin size bar (amber for a heavy app) and at most one chip, such as “Heavy” "
   "or “Not opened in 4 months”. Tapping the row opens App Details; tapping the checkbox selects. “Select all” "
   "sits in each filter's header.")
b2("Floating Selection Bar, sliding up when anything is selected: “3 selected · 1.2 GB” and a red “Remove” "
   "button. (Planned ad slot for free users above it — Section 2; not in the current build.)")
b2("Sort menu: Size (biggest first — default), Name (A–Z), Date installed (oldest first), Last updated. Pro adds "
   "Last opened (longest ago first); choosing it as a free user opens the paywall. The chosen sort persists per "
   "filter.")
b2("Search: expands in the top bar and filters by name and package name within the current filter; selection is "
   "preserved while filtering (Section 6, item 25).")
b2("Unused apps filter (Pro): rows show “Last opened Mar 2025” or “Not opened lately”. States, in order: (a) no "
   "Usage Access → “See your unused apps” card with “Allow access” → Screen 11; (b) access granted, free user → "
   "the count in clear (“14 apps · 3.4 GB”, “Not opened in the last 30 days”, “They take up 3.4 GB you could get "
   "back.”), the rows blurred, and “See which apps” opens Screen 3; (c) Pro → full list, selectable, with “Select "
   "all 14”. Empty (Pro): “Nice! You've opened every app in the last 30 days.”, or the restored-phone copy on a "
   "recently set-up phone (Section 6).")
b2("Heavy apps filter (free): exactly the apps using 250 MB or more (“Apps that take up 250 MB or more”), each with "
   "a stacked App / Saved files / Temp files bar. Without Usage Access the same rule runs on install sizes, under a "
   "card asking for access to see full sizes — never the paywall. Empty: “No heavy apps”.")
b2("Temp files filter (free): apps by temp-files size, with the whole app as context (“Whole app: 1.2 GB”) and a "
   "short explainer — temp files are files apps keep so they open faster; apps make them again when needed, so "
   "they can be cleared in App info without removing the app. Needs Usage Access; without it, an allow card. "
   "Empty: “No temp files to clear”.")
b2("Empty & Loading States: grey placeholder rows while the first-ever inventory scan runs. Search with no match: "
   "No apps match “<query>”, with “Try a different name.” The All filter is never empty on a real device — if it "
   "is, `QUERY_ALL_PACKAGES` was stripped (Section 6, item 12).")
b1("**Screen 7: App Details Sheet** — modal bottom sheet from any row. Icon, name, version, “Installed from "
   "Google Play” (or “Installed from outside Google Play”), installed and last-updated dates, and last opened "
   "(with Usage Access). “What's using space” shows the total and a free breakdown — App · Saved files · Temp "
   "files; without Usage Access, the install size with “Turn on Usage access to see saved and temp files”. Hint: "
   "“To clear this app's temp files, tap App info, then Storage.” Actions: Open · App info (system settings page) "
   "· Play Store (Play-installed apps only) · red “Remove”.")
b1("**Screen 8: Uninstall Confirm Sheet** — “Remove 5 apps?”, the selected apps (icon + name + best-known size), "
   "“You'll get back 2.4 GB” (“about” when estimated), “Android will ask you to confirm each one. You can get them "
   "back from History.” and “Anything saved in these apps is deleted too.” Any flagged app carries a warning chip "
   "(e.g. “Your keyboard”, “Special access”; Section 6 item 10). Buttons: “Cancel” and red “Remove 5 apps”.")
b1("**Screen 9: Uninstall Progress** — full-screen, sits underneath the system dialogs. Determinate progress "
   "“Removing 2 of 5”, and the queue as a list with per-app states: Waiting, Waiting for you to confirm, Removed "
   "(green check), Skipped, Not removed, Already removed. A “Stop” text button ends the queue after the current "
   "dialog (“Stopping after this app…”). If Android's dialog doesn't appear: “Don't see Android's pop-up?” with "
   "“Show it again”.")
b1("**Screen 10: Result Screen** — the storage gauge drops to the new fill (“Your phone is now 72% full”), a large "
   "count-up “2.4 GB freed up”, “5 apps removed”, and a short confetti burst. If any were skipped or failed, a "
   "collapsible “2 not removed” section lists them with the reason and “Try again” (or “Try all again”). Only when "
   "Usage Access is granted and unused apps remain: a teaser card “9 more unused apps still take up 1.8 GB · Not "
   "opened in 30 days” with “See unused apps”. Primary CTA “Done”. (Planned: a native ad below the summary for "
   "free users — Section 2; not in the current build.)")
b1("**Screen 11: Usage Access Disclosure** — prominent disclosure required by Play. Illustration, headline “Turn "
   "on Usage access”, subtitle “This lets App Cleaner see which apps you haven't opened, and how much space they "
   "take.”, and three plain lines: What we see (“When you last opened each app, and how much space it takes.”), "
   "Why (“To find unused apps and heavy apps.”), Where it goes (“It stays on your phone. Nothing is uploaded.”). A "
   "small animated hint showing the switch to turn on. CTA “Go to Settings”, secondary “Not now”.")
b1("**Screen 12: Uninstall History** — from the top bar History icon. A hero total — “9.8 GB freed” (“· rough "
   "total” when some sizes were estimated) and “31 apps removed” — then removed apps grouped by day (Today, "
   "Yesterday, dates), each with its snapshotted icon, name, size and time. Trailing pill: “Reinstall” (opens the "
   "Play listing) for Play-installed apps; “Not from Play” (disabled) otherwise; “Reinstalled” once it is back. "
   "Overflow: “Clear history” (“This only clears the list. Apps you removed stay removed.”). Empty: “Apps you "
   "remove will show up here, so you can always get them back.”")
b1("**Screen 13: Settings** — a large title, then three groups. **Preferences:** Language · Theme (System / "
   "Light / Dark) · Usage access (Allowed / Not allowed, with Allow) · Rate App Cleaner. **Cleanup reminders:** "
   "the Weekly reminders toggle (Pro; for free users it opens the paywall), “Only when unused apps pile up.”, "
   "with “Allow notifications” or a Usage access prompt when needed. **Subscription:** the Pro card lower down "
   "(free: “Get App Cleaner Pro” — Find unused apps · Get weekly cleanup reminders · No ads, with “Try it free”; "
   "Pro: “Pro is active”), Manage subscription (deep-link to Play subscriptions), Restore purchases. Footer: “App "
   "Cleaner 1.0 · Privacy · Terms”. A Debug section (force premium, RevenueCat status) appears only in debug "
   "builds. There is no About section and no size-display setting.")

# ---------------------------------------------------------------- 5
h1("5. Detailed Feature Specifications (Core V1)")
h2("Main Feature 1: App Inventory, Search & Sort")
b1("**Core Mechanics & Logic:**")
b2("Source: `PackageManager.getInstalledPackages(PackageInfoFlags.of(0))` under `QUERY_ALL_PACKAGES`. Keep a "
   "package when it is user-installed: `(flags and FLAG_SYSTEM) == 0`. System apps — including updated system "
   "apps — are hidden in V1. The app's own package is always excluded.")
b2("Per-app record: package_name, label, version_name, first_install_time, last_update_time, installer "
   "(`getInstallSourceInfo().installingPackageName`), apk_bytes and storage_uuid. apk_bytes is the sum of "
   "`File(sourceDir).length()` plus every entry of `splitSourceDirs` — readable without any permission, and it is "
   "the fallback size (shown as “about”) whenever an app has not been measured.")
b2("Cache-first: the whole inventory is persisted in Room. Cold start renders from Room instantly, then a "
   "background refresh diffs the live package list against it (added / removed / version changed). Labels are "
   "resolved with `loadLabel()` off the main thread; icons load lazily off the main thread from "
   "`loadIcon()` into a memory cache keyed by package + size.")
b2("Live updates: a context-registered receiver for `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED` and "
   "`ACTION_PACKAGE_REPLACED` (data scheme `package`) patches the list while the app is open, so an app "
   "removed from the launcher meanwhile disappears without a manual refresh.")
b2("Storage gauge: `StorageStatsManager.getTotalBytes(UUID_DEFAULT)` and `getFreeBytes(UUID_DEFAULT)` — no "
   "permission required — drive the Home gauge (“19% full”, “48 GB of 256 GB used”, “Lots of free space / Filling "
   "up / Almost full”). Sizes are formatted with "
   "`Formatter.formatShortFileSize()`, which uses the same SI units as system Settings, so our numbers match "
   "what the user sees there.")
b2("Best-known size: every row, total, selection, Confirm Sheet and Result uses one number per app — the measured "
   "app + data + cache total (`StorageStats`, Feature 3) when Usage Access is granted, else apk_bytes labelled "
   "“about”. There is no setting to switch between install size and total; the old “Show app size as” option is "
   "gone.")
b1("**Micro-interactions & Edge Cases:** Sort and search are pure in-memory operations over the Room snapshot "
   "(300 apps sort in well under 16ms), so they never show a spinner. Selection is a single set of package names "
   "shared across filters, search and sort — it is never silently cleared by switching views. A pull-to-refresh on "
   "the list forces a full rescan.")

h2("Main Feature 2: Batch Uninstall Engine")
b1("**Core Mechanics & Logic:**")
b2("Queue: tapping “Remove N apps” on the Confirm Sheet writes an `uninstall_queue` batch to Room (batch_id, "
   "package, order, state, snapshot_bytes). Before each item runs, its icon (PNG, 96px), label, installer and "
   "size are written to the history snapshot table — text-only when the phone is critically out of space "
   "(Section 6, “Storage almost full”). Sizes are the best-known size (Feature 1): the "
   "full `StorageStats` total when Usage Access is granted, apk_bytes otherwise — the Result screen says "
   "“about” when any size was estimated.")
b2("Removal: for each item, call `PackageInstaller.uninstall(packageName, statusReceiver.intentSender)`. The "
   "receiver gets `STATUS_PENDING_USER_ACTION` first; the engine launches the carried `Intent.EXTRA_INTENT`, "
   "which is Android's own confirmation dialog. The final status then arrives on the same receiver: "
   "`STATUS_SUCCESS` → removed; `STATUS_FAILURE_ABORTED` → skipped (user tapped Cancel); `STATUS_FAILURE_BLOCKED` "
   "→ failed, blocked by device policy; anything else → failed with the status code logged (never the package).")
b2("Verify: on `STATUS_SUCCESS`, confirm `getPackageInfo()` now throws `NameNotFoundException` before marking the "
   "item removed, moving its snapshot into History and adding its bytes to the freed total. Only then does the "
   "engine start the next item, within 300ms.")
b2("One dialog at a time, always in the foreground: the engine runs only while Screen 9 is resumed. If the "
   "user leaves the app, the queue pauses after the current dialog and resumes when they come back — Android "
   "blocks background activity starts, and a dialog appearing over another app would be alarming anyway.")
b1("**Micro-interactions & Edge Cases:** Skipping one app never stops the batch — a Cancel almost always means "
   "“not that one”, so the queue moves on and the app lands in “not removed” with a “Try again” action. Three "
   "Cancels in a row are different: the engine pauses before the next dialog and asks “Stop removing the rest?” "
   "(Section 6, “Several dialogs cancelled in a row”). “Stop” "
   "on Screen 9 ends the batch after the current dialog and goes straight to the Result screen with what was "
   "done so far. A single-app uninstall (from App Details) uses the same engine with a queue of one, so History "
   "and the freed-space count behave identically. Interruptions, device-admin apps and default-app warnings are "
   "detailed in Section 6.")

h2("Main Feature 3: Finders — Unused Apps (Pro), Heavy Apps & Temp Files (Free), and the Check")
b1("**Core Mechanics & Logic:**")
b2("Access check: `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, packageName) == MODE_ALLOWED`, "
   "re-checked in every `onResume`. The grant flow opens `Settings.ACTION_USAGE_ACCESS_SETTINGS` with "
   "`Uri.parse(\"package:\" + packageName)` so the user lands on our toggle directly where the OEM supports it. "
   "Returning without granting (`onResume`, access still off) puts the user back on the exact filter and scroll "
   "position they left, with a gentle inline explanation — never the paywall (Section 6).")
b2("Last used: `UsageStatsManager.queryUsageStats(INTERVAL_YEARLY, now − 2 years, now)`, reduced per "
   "package to the latest of `lastTimeUsed` and `lastTimeVisible` (API 29+). Android keeps daily buckets for "
   "about a week but yearly buckets for about two years, so the yearly interval is what lets the finder see "
   "usage older than a week at all.")
b2("Unused rule (fixed 30 days): an app is unused when `now − lastTimeUsed ≥ 30 days` AND `now − first_install_time "
   "≥ 30 days` (an app installed last week is not “unused for 30 days”). The 30 days is one constant "
   "(`UNUSED_THRESHOLD_DAYS`) used by the Check, Home, the Unused apps filter, reminders and the paywall; there is "
   "no 30 / 60 / 90 choice anywhere, and a value stored by an older build is ignored. The second clause also covers "
   "phones restored from a backup, where `first_install_time` is the restore date (Section 6, “Apps restored to a "
   "new phone”). An app with no usage record inside the retention window is shown as “Not opened lately” — never "
   "“Never opened”, which we can't know.")
b2("Exclusions: never list as unused the current default launcher, keyboard (`Settings.Secure.DEFAULT_INPUT_METHOD`), "
   "SMS app and dialer (`Telephony.Sms.getDefaultSmsPackage()`, `TelecomManager.getDefaultDialerPackage()`), any "
   "enabled accessibility service or notification listener, or an active device admin. These run constantly "
   "without being “opened”, and suggesting their removal would be a bad recommendation dressed up as a smart one.")
b2("Heavy apps rule (free): an app is heavy when its best-known size (Feature 1) is 250 MB or more, in the decimal "
   "units Android's formatter shows. One rule, one function — `LargeApps.isLarge` — used by Home, the Check "
   "result and the Heavy apps filter, so the count is the same on every screen. No share-of-disk rule and no "
   "second threshold anywhere.")
b2("Storage breakdown (free): `StorageStatsManager.queryStatsForPackage(appInfo.storageUuid, packageName, "
   "Process.myUserHandle())` → appBytes, dataBytes, cacheBytes (plus `externalCacheBytes` on API 31+), shown to "
   "users as App / Saved files / Temp files. Queried on a background dispatcher in batches of 20 and cached in "
   "Room with a timestamp; stale after 24h or on `ACTION_PACKAGE_REPLACED`.")
b2("Temp files (free): the cacheBytes of the same query, listed biggest first on the Temp files filter. Clearing "
   "is an honest deep-link to App info (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) with “To clear this app's "
   "temp files, tap App info, then Storage.” — never a fake in-app button.")
b2("The Check and the free-up headline: the Check (Screen 5) runs inventory → usage → sizes and stores one result "
   "that Home's hero card and category rows also read. “You can free up X” counts each app once: every app in "
   "Unused apps or Heavy apps, plus the temp files of apps in neither. When an app is in both lists, a small line "
   "notes the overlap (“counted once”).")
b2("Gating: only the Unused apps list is Pro. The unused rule runs for any user who granted access — that is what "
   "makes the blurred teaser's count and the Check's Pro hint real; the entitlement only controls whether unused "
   "rows render in clear and are selectable. Heavy apps, Temp files, the size breakdown, the Check, batch "
   "uninstall and History are free for everyone.")
b1("**Micro-interactions & Edge Cases:** Filters recompute instantly from cached timestamps and sizes. Measured "
   "sizes replace install sizes as they arrive, and an app can move into Heavy apps when it does — every screen "
   "updates together (Section 6). Revoked access, OEMs without the package-specific Settings page, and clock "
   "changes are covered in Section 6.")

h2("Main Feature 4: Uninstall History & Cleanup Reminders")
b1("**Core Mechanics & Logic:**")
b2("History (free): Room table `uninstall_history` (package, label, icon_png, bytes, bytes_is_estimate, "
   "installer, version_name, removed_at). Written only from the verified-success path of Feature 2, so it "
   "never contains an app that is still installed. “Reinstall” opens "
   "`market://details?id=<package>` with a `https://play.google.com/store/apps/details?id=` fallback when no "
   "Play Store is present. If the package reappears on the device (reinstalled), its history row gets a "
   "“Reinstalled” tag instead of the button.")
b2("Reminders (premium): a `PeriodicWorkRequest` every 7 days (flex 1 day, requires battery not low). The "
   "worker checks entitlement and Usage Access, runs the Feature 3 unused rule (30 days), and posts "
   "one notification only if the count grew since the last reminder or 30 days have passed: “9 apps not opened "
   "in 30 days · 2.1 GB” / “Take a look?” The tap deep-links to the Apps screen on the Unused apps filter with "
   "those apps pre-selected.")
b1("**Micro-interactions & Edge Cases:** Reminders are off by default until the first successful premium "
   "unlock, then on, and toggleable in Settings. If notifications are denied, the "
   "Settings toggle shows “Allow notifications” linking to `Settings.ACTION_APP_NOTIFICATION_SETTINGS`. History is "
   "capped at 1,000 rows (oldest pruned) and excluded from auto-backup, because icons of removed apps have no "
   "value on a new device.")

# ---------------------------------------------------------------- 6
h1("6. Error States & Edge Cases")
p("Every failure below has a defined recovery. Uninstalling is irreversible, so the bar here is: the user is "
  "never surprised by what was removed, and never told something was removed when it wasn't.")
sub("A. Package & Uninstall Integrity")
n1("**Device-admin apps.** An active device administrator (`DevicePolicyManager.getActiveAdmins()`) cannot be "
   "uninstalled until deactivated. Flag it on the Confirm Sheet with a “Special access” chip (“Has special access to your phone. Turn it off "
   "first.”), and if it fails in the queue show “Turn off its special access first” with “Open settings” → `Settings.ACTION_SECURITY_SETTINGS`.")
n1("**Blocked by policy.** `STATUS_FAILURE_BLOCKED` (work-managed devices, carrier locks, parental controls): "
   "mark failed with “Your phone doesn't let you remove this app”, no retry button — retrying cannot succeed.")
n1("**User cancels a dialog.** Skip and continue (Feature 2). The Result screen lists it under “not removed · "
   "you cancelled” with “Try again”.")
n1("**App killed mid-queue.** The queue lives in Room, not memory. On next launch, Home shows a banner “Finish "
   "removing 3 apps?” with “Continue” (re-verifies each package still exists first) and “No thanks”. Items "
   "that were removed before the kill are already in History.")
n1("**Package changed mid-queue.** Before each item, re-read the package. Already gone (removed elsewhere) → "
   "“Already removed”, excluded from the freed total. Updated since selection → re-measure size before the "
   "dialog so the snapshot is honest.")
n1("**Dialog reported success but the package remains.** If `getPackageInfo()` still succeeds after "
   "`STATUS_SUCCESS` (seen on some OEM skins that intercept uninstall), mark failed “Android didn't remove "
   "this app” with “Try again” — never write it to History or count its bytes.")
n1("**OEM interception.** Some skins (MIUI/HyperOS, older EMUI) show their own security dialog before the "
   "system one. The engine is status-driven, not UI-driven, so extra dialogs only delay the result; no special "
   "handling beyond not timing out the queue.")
n1("**Storage almost full (the usual reason people open the app).** Users typically arrive at 95–99% full, and "
   "the app's own writes — the Room snapshot, the history icon PNG, the icon image cache — can fail when internal "
   "storage is close to 0 bytes. Graceful fallback, in order: (1) before writing a history icon, check "
   "`StorageManager.getAllocatableBytes(UUID_DEFAULT)`; under 5 MB, or on any `IOException`/`ENOSPC`, skip the PNG "
   "and save a text-only snapshot (label, size, installer, removed_at; `icon_png = null`) — History then draws a "
   "neutral placeholder icon; (2) the list's icon cache stays in memory, so drawing the "
   "list never writes to disk; (3) if even the small Room insert throws `SQLiteFullException`, the uninstall still proceeds — the "
   "snapshot is held in memory and written right after the first successful removal, which is exactly when space "
   "comes back. A failed snapshot write never blocks, delays or cancels a removal. Logged as "
   "`history_snapshot_degraded` (reason: low_space, io_error, db_full).")
n1("**Very large inventories.** 500+ apps must stay smooth: the list is a `LazyColumn` with stable keys, sizes "
   "stream in as they resolve (shimmer per row, not a blocking spinner), and the queue has no batch-size limit.")
n1("**Default and always-running apps.** Selecting the current keyboard, launcher, SMS app, an enabled "
   "accessibility service or an active notification listener adds a warning chip on the Confirm Sheet "
   "(“Your keyboard — you'll need to pick another”). Warn, don't block: it's the user's phone.")
n1("**Adopted storage / SD card apps.** Always query sizes with the app's own `storageUuid`, never "
   "`UUID_DEFAULT`; if the volume is unmounted, show “Size unavailable” for that app rather than 0 B.")
n1("**Inventory missing apps.** If the scan returns fewer than 5 user apps on a device reporting many more "
   "packages, `QUERY_ALL_PACKAGES` was stripped (sideloaded/modified build). Show an inline card “Some apps may "
   "be hidden from App Cleaner” and log `inventory_incomplete`.")
sub("B. Permissions, Privacy & Data Accuracy")
n1("**Usage Access revoked.** Checked on every resume. Unused apps and Temp files fall back to the “Allow "
   "access” state, and Heavy apps and every size fall back to install sizes (“about”); cached measurements are "
   "cleared so stale numbers are never presented as current; the reminder worker exits silently.")
n1("**OEM without the package-specific Usage Access page.** If starting the intent with the `package:` URI throws "
   "`ActivityNotFoundException` or lands on a list, fall back to the plain `ACTION_USAGE_ACCESS_SETTINGS` and "
   "show a 2-second overlay hint: “Find App Cleaner in the list and turn it on.” On recent Android versions "
   "(14, 15, 16) several OEMs — Samsung, Xiaomi and Pixel included — accept the `package:` URI but ignore it and "
   "open the general list, so the hint must show whenever the list opens, not only on an exception. When the "
   "user comes back (`onResume`) without granting, they return to the exact filter and scroll position they left, "
   "with a gentle inline card (“Usage access is still off — here's how to turn it on”) and a “Try again” button. "
   "Never an aggressive paywall, never a modal, and no repeat of the full disclosure screen.")
n1("**Apps restored to a new phone.** After Smart Switch or Google Restore, every restored app gets a fresh "
   "`first_install_time` (the restore date), and usage history does not transfer. An app the user hasn't opened "
   "for 80 days on the old phone therefore can't be called unused until 30 days have passed on the new one. "
   "This is deliberate: the rule under-reports right after a restore but never mislabels an app the user may still "
   "need. Help copy for an empty Unused apps list on a recently set-up phone: “Just set up this phone? Unused apps show "
   "up after 30 days.”")
n1("**Clock changed.** Usage timestamps and `System.currentTimeMillis()` can disagree if the user moves the clock. "
   "Any `lastTimeUsed` in the future is treated as “used today” — the finder may under-report after a clock "
   "change, but never labels an app unused that was just opened. There is no usage counter or free-tier "
   "limit anywhere in V1, so there is nothing a clock rewind can unlock.")
n1("**Sizes before and after Usage Access.** Until an app is measured, its size is its install size, shown as "
   "“about”. An app under 250 MB by install size can cross into Heavy apps once its data is measured, and the "
   "free-up headline grows with it. Home, the Check result and the Heavy apps filter all read the same best-known "
   "sizes through `LargeApps.isLarge`, so they change together — the counts never disagree between screens, even "
   "while measurement is still running.")
n1("**Inventory in telemetry.** Analytics parameters are validated by a single `AnalyticsEvent` sealed class whose "
   "fields are counts, enums and byte buckets only — there is no String field that could carry a package name. "
   "Crashlytics custom keys follow the same rule.")
sub("C. Monetization, Ads & Offline")
n1("**Paywall with no connection.** RevenueCat can't fetch offerings: show the benefits, replace the plan card with "
   "“Connect to the internet to start your free trial” and a “Try again” button. Never a blank card or an "
   "infinite spinner; the close (X) always works.")
n1("**Premium offline.** RevenueCat's cached `CustomerInfo` keeps `premium` active offline; entitlement is "
   "refreshed on the next connected foreground.")
n1("**Subscription lapses, grace period, account hold.** During Play grace period the entitlement stays active. "
   "When it ends, the Unused apps filter returns to the blurred count, reminders stop, and ads return once ads "
   "ship; Heavy apps, Temp files, sizes, History, settings and selections are untouched. A one-time Home banner "
   "explains: “Your Pro plan has ended.” with “Renew”.")
n1("**Trial already used.** Play grants one trial per account; if RevenueCat reports no eligible trial, the "
   "paywall copy switches to the plain weekly price (Screen 3). Never show “3 days free” to someone who "
   "will be charged today.")
n1("**Ads never touch the uninstall path** (applies once ads ship — Section 2). No interstitial is ever shown between confirmation dialogs or before "
   "the Result screen. The only interstitial slot is on leaving the Result screen via “Done”, skipped silently "
   "if not loaded within 2 seconds. A banner that fails to fill collapses to zero height.")
sub("D. Interaction & Multi-Step Logic")
n1("**Several dialogs cancelled in a row.** In a batch of 10, 10 system dialogs appear one after another. If the "
   "user taps Cancel on 3 consecutive dialogs, the engine does not launch the next one; Screen 9 shows an in-app "
   "prompt “Stop removing the rest?” with “Stop” (ends the batch, goes to the Result screen with what was done) "
   "and “Keep going” (resets the streak and continues). A single Cancel, or Cancels separated by a removal, "
   "never triggers it. Logged as `uninstall_cancel_streak_prompt` (action: stop, keep_going).")
n1("**Selection across filters and search.** Selection is one global set. If the search filter hides selected apps, "
   "the Selection Bar says “5 selected (2 hidden by search)” and the Confirm Sheet always shows the full list, so "
   "nothing invisible is ever uninstalled.")
n1("**No undo — History is the undo.** Uninstalling can't be reversed in-app. The Confirm Sheet is the last "
   "checkpoint, and History with “Reinstall” is the recovery path; app data is not recoverable and the Confirm "
   "Sheet does not pretend otherwise.")
n1("**Rotation and process death on Screen 9.** Queue state is in Room and the engine resumes from the first "
   "item not yet in a final state; the pending system dialog is re-requested only if no result came back.")
n1("**RTL locales.** In Arabic or Hebrew all chrome mirrors — the filter-chip order and scroll direction, the checkbox side, the Selection Bar, "
   "the progress bar fill direction, back arrows. App labels and icons are user content and render exactly as "
   "the app provides them; storage numbers use the locale's digits via `Formatter`.")
n1("**Large screens ignore the portrait lock.** On Android 16+ the manifest's portrait lock is ignored on displays "
   "600dp wide or more (tablets, unfolded foldables). Every screen must still work in landscape and at wide "
   "widths there: content keeps a readable width, sheets stay usable, and nothing assumes a portrait height.")

# ---------------------------------------------------------------- 7
h1("7. Add to Board (Development Tickets)")
b1("**Ticket 1: [Develop Core] — Inventory, Design System, Home, Check, Apps & Onboarding.** Target API 36, min "
   "26, portrait only. Implement the `QUERY_ALL_PACKAGES` inventory with user-app filtering, apk_bytes measurement, "
   "the Room inventory cache with background diffing and package broadcast receiver, the icon loader, and startup "
   "work off the main thread. Build design system v2 (Spring green tokens, light/dark, Plus Jakarta Sans, 20dp "
   "gutter, 48dp touch targets, no truncated text). Build Home (gauge, hero card, “Where your space goes”), the "
   "Check screen, and the Apps screen with pinned filter chips, sort, search, the global selection set and the "
   "floating Selection Bar. Build splash, the 9-language selector (InkSign's locale module plus Russian) with "
   "dynamic LTR/RTL, the 3-slide carousel and the notification prompt. Covers Section 6 items 28–29.")
b1("**Ticket 2: [Develop Core] — Batch Uninstall Engine, Result & History.** Build the Room-backed queue, "
   "pre-dialog snapshots, `PackageInstaller.uninstall()` with the status receiver, post-success verification, "
   "Confirm Sheet (with device-admin and default-app warnings), Progress and Result screens, the resume-after-kill "
   "banner, App Details sheet, and Uninstall History with Reinstall. Covers Section 6 items 1–12 and 24–27.")
b1("**Ticket 3: [Develop Core + Premium] — Usage Access, Finders & Reminders.** Build the disclosure screen and "
   "grant flow with OEM fallback, the yearly-interval last-used query, the fixed 30-day unused rule with "
   "exclusions, `StorageStatsManager` batching and caching, best-known sizes and the shared `LargeApps.isLarge` "
   "rule, the free Heavy apps and Temp files filters, the three-state (no access / blurred / Pro) Unused apps "
   "filter, the Check's count-once headline, and the weekly reminder worker with pre-selected deep link. Covers "
   "Section 6 items 13–17.")
b1("**Ticket 4: [Develop Premium] — Subscription, Paywall & Ads.** Integrate Play Billing v8+ and RevenueCat "
   "with the weekly base plan and 3-day trial offer (add the production API key), trial-eligibility copy "
   "switching, the `premium` entitlement gate (Unused apps, reminders, no ads — nothing else), onboarding and "
   "contextual paywall triggers, offline and lapse states, and Restore. Implement the `AnalyticsEvent` sealed "
   "class so no event can carry a package name. Ads — the banner, Result-screen native ad and exit interstitial "
   "with non-blocking no-fill fallback, removed for subscribers — and UMP consent are specified but held back by "
   "owner decision; not in the current build (Section 2). Covers Section 6 items 18–23.")

# ---------------------------------------------------------------- 8
h1("8. Basic User Flow")
b1("**Step 1 (Splash & Onboarding):** User opens the app, selects language (UI adapts RTL/LTR), views the 3-slide "
   "carousel, allows notifications, and closes or accepts the onboarding paywall -> fires `onboarding_start`, "
   "`onboarding_step_viewed`, `onboarding_complete`, `paywall_viewed`. (UMP consent joins this step when ads "
   "ship.)")
b1("**Step 2 (Find):** User lands on Home and sees the gauge at “92% full · Almost full”. They tap “Check my "
   "phone”, turn on Usage Access via the disclosure screen, and the check reports “you can free up 6.4 GB”. They "
   "tap “See 4 heavy apps · 3.9 GB” and tick the ones they recognise as junk on the Apps screen -> fires "
   "`home_viewed`, `usage_access_prompt_shown`, `usage_access_granted`, `app_selected`.")
b1("**Step 3 (Core Experience — Remove):** User taps “Remove”, reviews “Remove 4 apps? · You'll get back 3.9 GB”, "
   "confirms four system dialogs in a row and lands on “3.9 GB freed up” -> fires `uninstall_batch_started`, "
   "`uninstall_batch_completed` (North Star), and `uninstall_failed` for any item that failed.")
b1("**Step 4 (Monetisation):** On the Check result (“See your 7 unused apps with Pro”) or the Result screen's "
   "teaser, the free user opens the Unused apps filter, sees “14 apps · 3.4 GB” over blurred rows, taps “See which "
   "apps” and starts the 3-day trial -> fires `unused_apps_found`, `premium_teaser_viewed`, `paywall_viewed`, "
   "`trial_started`.")

# ---------------------------------------------------------------- 9
h1("9. Funnel Analytics Events")
p("No event parameter may contain a package name, app label or free text (Section 2, Section 6 item 18). "
  "Byte values are bucketed: <100MB, 100MB–500MB, 500MB–1GB, 1–5GB, >5GB. "
  "Filter names in params use the plain names: all, unused, heavy, temp_files. `threshold_days` is kept as a "
  "param but is always 30 in V1, so a future change would show up in the data.")
b1("`onboarding_start` — as soon as the language selector appears.")
b1("`onboarding_step_viewed` — params: step_number (1, 2, 3), language_selected.")
b1("`onboarding_complete` — params: skipped (boolean). When the user reaches Home.")
b1("`home_viewed` — params: app_count, storage_used_pct, has_usage_access (boolean), is_premium (boolean). Once per session.")
b1("`app_selected` — params: tab (all, unused, heavy, temp_files), via (checkbox, select_all, reminder_preselect). Sampled: first "
   "selection per session only.")
b1("`uninstall_batch_started` — params: app_count, bytes_bucket, source_tab (all, unused, heavy, temp_files), "
   "has_warnings (boolean).")
b1("`uninstall_batch_completed` — **North Star event.** Params: removed_count, skipped_count, failed_count, "
   "bytes_freed_bucket, bytes_is_estimate (boolean), seconds_to_complete, stopped_early (boolean), is_premium. Fires "
   "when the Result screen appears with removed_count ≥ 1. We optimise the product against weekly users who "
   "fire this, and against median seconds from app open to the first one (target under 60).")
b1("`uninstall_failed` — params: reason (blocked, device_admin, not_removed_after_success, status_code_other), "
   "status_code. One per failed item.")
b1("`uninstall_queue_resumed` — params: remaining_count, action (continue, discard).")
b1("`uninstall_cancel_streak_prompt` — params: action (stop, keep_going), remaining_count. When three Cancels in a "
   "row pause the batch (Section 6).")
b1("`history_snapshot_degraded` — params: reason (low_space, io_error, db_full). A history entry was saved without "
   "its icon, or late, because the phone was out of space (Section 6).")
b1("`usage_access_prompt_shown` — params: trigger (scan, unused_tab, heavy_tab, temp_files_tab, details_sheet, "
   "home_card, settings).")
b1("`usage_access_granted` — params: seconds_in_settings, used_fallback_page (boolean).")
b1("`unused_apps_found` — params: count, threshold_days (always 30). When the unused rule finishes with Usage "
   "Access granted, free or Pro.")
b1("`premium_teaser_viewed` — params: tab (unused), found_count, bytes_bucket. Only the Unused apps filter has a "
   "teaser — Heavy apps and Temp files are free. The blurred-count state; this is the step that validates "
   "decision 3 in Section 0.")
b1("`paywall_viewed` — params: trigger_source (onboarding, scan_result, unused_tab, sort_menu, reminder_toggle, "
   "settings), trial_eligible (boolean), offering_loaded (boolean).")
b1("`trial_started` / `subscription_started` — params: trigger_source. From the RevenueCat purchase callback.")
b1("`paywall_dismissed` — params: trigger_source, seconds_visible.")
b1("`purchase_restored` — params: restored_premium (boolean). From Restore purchases on the paywall or in Settings.")
b1("`history_reinstall_tapped` — params: days_since_removed_bucket (0, 1–7, 8–30, >30).")
b1("`reminder_notification_opened` — params: unused_count, threshold_days (always 30).")
b1("`inventory_incomplete` — params: visible_count. Detects a stripped `QUERY_ALL_PACKAGES` (Section 6 item 12).")

# ---------------------------------------------------------------- 10
h1("10. Out of Scope for V1")
p("Each of these is a credible V2 and should get its own PRD. None may be retrofitted as a gate on the free "
  "select-and-remove flow — that flow staying open and unlimited is what keeps the rating in the 4.5+ band "
  "where the category leaders sit.")
b1("**App archiving (Android 15+).** `PackageInstaller.requestArchive()` removes the APK but keeps data and the "
   "icon, so the app restores in one tap. The strongest V2 candidate: it is “free up space” with no regret, and "
   "no competitor in Appendix A offers it yet. Deferred only because it covers under half the install base today.")
b1("**Accessibility auto-uninstall** (AppDrop's approach). Play restricts `AccessibilityService` to disability "
   "use cases; using it to tap “OK” is a removal risk for the whole listing. Not planned.")
b1("**APK backup / extract before uninstall.** Split APKs mean a backup is a folder of files and restoring needs "
   "an installer flow — a medium lift for a rare need.")
b1("**System apps and bloatware** — listing them, “Uninstall updates”, and deep-linking to Disable. Most can't be "
   "removed without root; showing them in V1 adds clutter and “why won't it uninstall” reviews.")
b1("**Junk cleaning and in-app temp-file (cache) clearing.** Not possible for a normal app since Android 6 without "
   "root or Accessibility abuse. Not planned — promising it is what gets cleaner apps suspended (Section 0). The "
   "Temp files filter shows the real numbers and links to App info instead (Feature 3).")
b1("**A choice of unused threshold (30 / 60 / 90 days).** Removed by owner decision in v1.2: one fixed 30-day rule "
   "is easier to explain and keeps every count identical. Revisit only with `unused_apps_found` data.")
b1("**Landscape and tablet layouts.** The app is portrait-only. Beyond keeping every screen usable where Android "
   "16+ ignores the lock (Section 6), there is no dedicated landscape or two-pane design in V1.")
b1("**Yearly and lifetime plans.** V1 validates the weekly price first; RevenueCat is already in place, so adding "
   "base plans later is a configuration change plus a paywall layout, not a re-integration.")
b1("**Work-profile and secondary-user apps.** Visible only to their own profile; would need a device-owner or "
   "cross-profile setup.")
b1("**Permission audit / privacy score** (as in Batch Uninstaller & Cleaner). A different product promise "
   "(security) with its own review risk; better as its own app.")
b1("**Home screen widget and quick-settings tile** for storage. Nice retention hook, not needed to prove the "
   "core loop.")

# ---------------------------------------------------------------- Appendix
pagebreak()
h1("Appendix A: Reference App Findings")
p("Gathered from the live Play Store listings on 28 September 2026.")
table([2200, 1400, 2900, 2860], [
    ["Reference app", "Scale", "What to take", "What to avoid"],
    ["Delete apps - Easy Uninstall (Mobique)", "1M+ installs\n4.4 (33.6K)",
     "The primary reference: one clean screen, multi-select, search and sort, remove in a few taps. Proof the "
     "simple select-and-remove loop alone earns a 4.4.",
     "Keyword-stuffed listing (seven feature names that are all the same feature). No sense of what is unused "
     "or large, so the user still has to guess."],
    ["Uninstaller (SplendApps)", "10M+ installs\n4.8 (256K)",
     "Highest rating in the category. Sort by name, size and install date in both directions, a storage "
     "indicator, and a note up front that system apps can't be removed.",
     "A persistent status-bar notification for quick access — clutter most users switch off."],
    ["Easy Uninstaller (Infolife)", "10M+ installs\n4.5 (181K)",
     "Uninstall history (“recycle bin”), usage tracking, uninstall reminders and a cached app list for instant "
     "start — our Features 3 and 4 validated at scale.",
     "An aged feature list (“Support Android 1.6-4.x”) and battery tracking that no longer works on modern "
     "Android."],
    ["AppDrop: Bulk Uninstaller", "50K+ installs\n4.2 (591)",
     "Grid view, dark mode, and an honest “no root, no permissions” message.",
     "Accessibility-service auto-uninstall — a Play policy liability — and an APK-extraction premium tier "
     "that sells a rare need."],
    ["Uninstall Apps – Uninstaller Plus (Sleeptech)", "5K+ installs\n4.1 (145)",
     "Usage-based “recently used / less used” views and a large-app finder — we keep the unused view Pro and make the heavy-app finder free.",
     "Buries the usage views in a long listing; no evidence shown before asking the user to act."],
])
p("**The gap all of them leave open: none shows the user their own evidence — how many apps they really "
  "stopped using and how much space those apps hold — before asking them to do anything. That real count, "
  "free to see and premium to act on, is the V1 thesis.**")
sub("Decision log")
b1("**Name.** “App Cleaner” kept as the brand; the Play title adds “Uninstall Apps” for ranking and to tell "
   "it apart from the many near-identical titles. The listing must never claim junk cleaning or speed boosting.")
b1("**Weekly subscription only, with a 3-day trial** (RevenueCat; production API key still to be added). Since "
   "v1.2, Pro is only the Unused apps finder, cleanup reminders and no ads; Heavy apps, Temp files, every app's "
   "size breakdown, the Check, unlimited batch uninstall and History are free.")
b1("**No connection gate.** Unlike InkSign v1.1, revenue is the subscription, so blocking offline sessions "
   "would cost reviews for almost no ad revenue.")
b1("**Usage Access asked for free, before the paywall,** so the paywall can show the user's real numbers.")
b1("**Sequential system dialogs, not Accessibility.** Slower by one tap per app; the listing is safe.")
b1("**v1.1 edge cases.** Added after review: a text-only History fallback when the phone is almost out of space "
   "(the removal must never be blocked by our own writes), a “Stop removing the rest?” prompt after three Cancels "
   "in a row, a gentle return path when OEMs ignore the Usage Access `package:` URI, and the restored-phone case "
   "for the unused rule. All four are kept in v1.2 and are being implemented.")
b1("**v1.2 — the PRD now describes the current build.** Owner decisions: (1) Pro narrowed to Unused apps, "
   "cleanup reminders and no ads — Heavy apps, Temp files and the per-app size breakdown moved to free; (2) one "
   "fixed 30-day unused rule — the 30 / 60 / 90 choice is gone from Settings, the filter and the paywall; (3) plain "
   "names in every language — Unused apps, Heavy apps (250 MB or more, one shared `LargeApps.isLarge` rule), Temp "
   "files — and no “Large”, “Space hog”, “Cache” or “APK” in the UI; (4) one best-known size everywhere, the "
   "APK/Total setting removed, and a free-up headline that counts each app once; (5) a new structure — Home "
   "dashboard, Check screen, Apps screen with filter chips — replacing the tabbed Home list; (6) design system v2 "
   "(Spring green, Plus Jakarta Sans, text never truncated); (7) Russian added, 9 languages; (8) portrait only; "
   "(9) no ads in the current build, with the ad spec kept as the plan; (10) startup work off the main thread.")
