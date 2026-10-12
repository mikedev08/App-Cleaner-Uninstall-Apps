# Content model for the App Cleaner AdMob PRD. build_admob_prd.py renders it to PDF.
# Markup inside strings: **bold**, `code`, *italic*. Table cells may hold "\n" line breaks.

APP = "App Cleaner"
VERSION = "1.0"
HEADER = "App Cleaner AdMob PRD  |  Android (Kotlin / Compose)  |  v1.0"

TITLE = "App Cleaner — AdMob Integration"
SUBTITLE = ["Interstitial & Native ads, UMP consent, ad event logging",
            "Android (Kotlin / Jetpack Compose) — for AI developer agents and reviewers"]

INFO = [
    ("Document Version", "1.0 — 2026-09-28"),
    ("App", "App Cleaner: Uninstall Apps — com.jedy.appcleaner.uninstaller"),
    ("Target Platform", "Android, minSdk 26, targetSdk / compileSdk 37, portrait only"),
    ("Languages", "Kotlin, Jetpack Compose, Hilt"),
    ("Ad SDK", "Google Mobile Ads (AdMob) 25.5.0 + User Messaging Platform 4.0.0"),
    ("Ad Formats", "Interstitial, Native"),
    ("Mediation", "AdMob only for now — mediation adapters and groups come with production IDs"),
    ("Analytics", "Firebase Analytics (BoM 34.19.0, project app-cleaner-jedy) through the app's Analytics interface"),
    ("Monetisation", "Weekly Pro subscription through RevenueCat; Pro removes every ad"),
    ("Status", "IMPLEMENTED WITH GOOGLE TEST IDs — production IDs, mediation adapters and on-device QA pending"),
    ("Based on", "Jedy AdMob Mediation PRD v1.0 Template and InkSign AdMob PRD v1.0, adapted to App Cleaner; "
                 "supersedes the ad notes in App Cleaner PRD v1.2 (section 1.3)"),
]

B = []  # blocks
def h1(t): B.append(("h1", t))
def h2(t): B.append(("h2", t))
def h3(t): B.append(("h3", t))
def p(t): B.append(("p", t))
def b1(t): B.append(("b1", t))
def code(t): B.append(("code", t))
def note(t): B.append(("note", t))      # amber callout
def table(widths, rows): B.append(("table", widths, rows))

# ------------------------------------------------------------------ 1
h1("1. Purpose & Scope")
p("This PRD specifies how ads work in App Cleaner: what is shown, where, when, to whom, and how it is "
  "measured. It follows the Jedy AdMob Mediation PRD template and records where App Cleaner deliberately "
  "differs from it. App Cleaner removes apps through Android's own confirmation dialogs, one after another; an "
  "ad in the middle of that queue would break the batch and read as a trick, so the uninstall path is kept "
  "free of ads completely.")
h2("1.1 Goals")
b1("Show interstitial and native ads through AdMob to free users, with mediation added before launch.")
b1("Interstitial show rate of at least 60% and native show rate of at least 70%.")
b1("Pre-load every ad before its show point; never load-and-wait on the user's time, except the capped "
   "5-second wait for the first ad after the launch paywall (section 5.4).")
b1("Pro users see no ads at all: no consent form, no SDK start, no load, no show. Ads return when Pro lapses.")
b1("Never touch the uninstall path: no ad on the Review sheet, the Progress screen, between Android's "
   "confirmation dialogs, or before the Result screen.")
b1("One native ad per screen, pinned to the bottom so it is on screen the whole visit and can't be scrolled "
   "past: Home, Apps, History, Scan, Result and Settings. Never two ads on one screen.")
b1("Track every ad lifecycle event, including show-rate KPIs, through the app's Analytics interface.")
b1("Comply with GDPR / US state privacy laws through UMP; the app is not child-directed.")
h2("1.2 Out of Scope (this version)")
b1("Rewarded, banner and app-open ad formats (the v1.2 banner above the Selection Bar becomes the pinned "
   "native ad, section 1.3).")
b1("Mediation adapters and mediation groups (listed as pending in section 2.2).")
b1("AdMob dashboard configuration and store submission.")
note("**SHOW RATE IS THE #1 KPI — but never at the cost of a removal.** Every decision optimises show rate "
     "inside the surfaces where ads are allowed. Pre-loading, readiness checks and fresh native ads per screen "
     "are non-negotiable; ads on the uninstall path are forbidden.")
h2("1.3 Changes to the App Cleaner PRD v1.2 ad plan")
table([26, 37, 37], [
    ["Topic", "PRD v1.2 (planned)", "This PRD"],
    ["Selection Bar slot", "Banner above the floating Selection Bar",
     "No banner (out of scope). The Apps screen's pinned native ad instead, which rises above the Selection "
     "Bar while it is up, a clear gap away from Remove (section 6.2)."],
    ["Exit interstitial", "Leaving Result via Done, skipped if not loaded within 2 s",
     "Kept as Trigger C on arriving at Home from Result. No wait: the ad has had the whole batch to load."],
    ["Other interstitials", "None", "Trigger A after the paywall, Trigger B on resume, Trigger C on returning "
     "Home from Apps, History or Settings, all per the template."],
    ["Consent timing", "UMP right after the language selector, blocking",
     "Same, not blocking: the carousel goes on while UMP works; returning users refresh at launch."],
    ["Native on Result", "Below the summary", "Pinned just above Done (section 6.2)."],
])

# ------------------------------------------------------------------ 2
h1("2. Placeholder Reference")
h2("2.1 Resolved values")
table([30, 42, 28], [
    ["Placeholder", "Value in App Cleaner", "Where"],
    ["[APP_ID]", "ca-app-pub-3940256099942544~3347511713\n(Google sample app ID)",
     "manifestPlaceholders[\"admobAppId\"] in app/build.gradle.kts"],
    ["[INTERSTITIAL_AD_UNIT_ID]", "ca-app-pub-3940256099942544/1033173712 (test)", "ads/AdConfig.kt"],
    ["[NATIVE_AD_UNIT_ID]", "ca-app-pub-3940256099942544/2247696110 (test)", "ads/AdConfig.kt"],
    ["[MIN/TARGET/COMPILE_SDK]", "26 / 37 / 37", "app/build.gradle.kts"],
    ["[ADMOB_SDK_VERSION]", "25.5.0 (same as InkSign)", "gradle/libs.versions.toml"],
    ["UMP version", "4.0.0", "gradle/libs.versions.toml"],
    ["[ANALYTICS_CLASS]", "Analytics interface -> FirebaseAnalyticsSink (debug also logs tag AppCleanerFunnel)",
     "ads/AdEventLogger.kt, core/analytics/"],
    ["[NATIVE_LAYOUT_ID]", "R.layout.ad_native (standard, 84dp) — the pinned ad on every screen", "res/layout/"],
    ["[NATIVE_LARGE_LAYOUT_ID]", "Not used: a pinned ad stays compact so the screen above it keeps its room",
     "—"],
    ["[MAX_CONSECUTIVE_FAILS]", "3", "ads/AdConfig.kt"],
    ["[HAS_TAB_VIEW]", "false — no bottom bar; the Apps filter chips are not screens", "—"],
    ["[DEBUG_TEST_DEVICE_ID]", "Not needed while test unit IDs are used", "—"],
    ["Pro check", "Premium.isPremium (RevenueCat entitlement \"premium\")", "data/billing/Premium.kt"],
])
h2("2.2 Pending before production")
table([30, 22, 48], [
    ["Placeholder", "Needed from", "Notes"],
    ["Production [APP_ID]", "AdMob console", "Replace the sample ID in manifestPlaceholders"],
    ["Production interstitial and native unit IDs", "AdMob console",
     "Replace in AdConfig; keep test IDs for debug builds"],
    ["[MEDIATION_ADAPTERS] + versions", "Business decision",
     "Other Jedy apps: Meta, Pangle, Liftoff, AppLovin, Mintegral"],
    ["Mediation groups", "AdMob console", "Per format, per the chosen networks"],
    ["[DEBUG_TEST_DEVICE_ID]", "logcat on the test phone", "Required once real unit IDs are used on test devices"],
    ["Privacy policy URL", "AdMob app settings", "Required for US state privacy (section 11)"],
])
note("Google's test unit IDs are used in **both** debug and release builds today. Replace them in AdConfig and "
     "the manifest placeholder before any public release; never ship the sample App ID.")

# ------------------------------------------------------------------ 3
h1("3. Dependencies & Project Setup")
h2("3.1 Gradle dependencies (version catalog)")
code("""# gradle/libs.versions.toml
playServicesAds = "25.5.0"
userMessagingPlatform = "4.0.0"
play-services-ads = { group = "com.google.android.gms",
    name = "play-services-ads", version.ref = "playServicesAds" }
user-messaging-platform = { group = "com.google.android.ump",
    name = "user-messaging-platform", version.ref = "userMessagingPlatform" }

// app/build.gradle.kts
implementation(libs.play.services.ads)
implementation(libs.user.messaging.platform)
// Mediation adapters: added with production IDs (section 2.2),
// pinned to versions certified for play-services-ads 25.5.0.""")
h2("3.2 AndroidManifest.xml")
code("""<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${admobAppId}" />
<!-- Measurement waits until consent is resolved -->
<meta-data android:name="com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT"
    android:value="true" />
<!-- Already declared: INTERNET, ACCESS_NETWORK_STATE -->""")
h2("3.3 Test ad unit IDs (development)")
table([35, 65], [
    ["Ad Format", "Test Unit ID"],
    ["App ID (sample)", "ca-app-pub-3940256099942544~3347511713"],
    ["Interstitial", "ca-app-pub-3940256099942544/1033173712"],
    ["Native Advanced", "ca-app-pub-3940256099942544/2247696110"],
])

# ------------------------------------------------------------------ 4
h1("4. Architecture")
h2("4.1 Package structure")
code("""com.jedy.appcleaner.uninstaller/
|-- ads/
|   |-- AdConfig.kt              // Unit IDs, timings, retry constants
|   |-- AdEventLogger.kt         // Hilt singleton -> Analytics
|   |-- AdGate.kt                // Pro + onboarding gate (tested)
|   |-- ConsentManager.kt        // UMP: consent info, form, privacy options
|   |-- AdManager.kt             // @Singleton orchestrator, the only entry point
|   |-- InterstitialAdManager.kt // Pre-load, show, retry, expiry
|   |-- InterstitialRules.kt     // AdSurface, policy, ResumeGate, backoff (tested)
|   |-- NativeAdManager.kt       // Loads native ads through NativeAdQueue
|   |-- NativeAdQueue.kt         // Fresh ad per request (tested)
|   |-- NativeAdContent.kt       // rememberNativeAd, NativeAdCard, LocalAdManager
|   `-- PinnedNativeAd.kt        // PinnedAdLayout: the ad pinned to a screen's bottom
|-- core/analytics/              // Ad events (section 9)
|-- navigation/AppCleanerNavHost.kt // Route -> AdSurface, Triggers A, C
`-- MainActivity.kt              // Gate, lifecycle -> ResumeGate (Trigger B)""")
h2("4.2 Initialization flow (every cold start)")
table([6, 94], [
    ["#", "Step"],
    ["1", "AppCleanerApplication.onCreate(): no ad work."],
    ["2", "MainActivity collects the gate (Pro, onboarding language step) and calls AdManager.onGateChanged(activity)."],
    ["3", "Gate ALLOW, once per process: ConsentManager.requestAndShow() (UMP). New users reach ALLOW when they "
          "leave the language step; everyone else at launch."],
    ["4", "After consent resolves (or fails — errors never block): MobileAds.initialize() on a background thread."],
    ["5", "Init callback on the main thread -> AdManager.preloadAll(): interstitial load + native preload."],
    ["6", "Native requests made before init wait for it; nothing loads before MobileAds.initialize() completes."],
    ["7", "UI is interactive from the start; onboarding goes on while consent and loading run behind it."],
])
note("**Critical:** consent before initialize, initialize before any load. Never change this order. Pro "
     "users never reach step 3.")
h2("4.3 Pro and the ad gate")
table([28, 72], [
    ["Gate", "Meaning"],
    ["SUPPRESSED_PRO", "Premium.isPremium is true. Nothing runs; anything loaded is destroyed at once, including "
                       "an ad on screen (the slot leaves composition). Logged once per placement per visit as "
                       "ad_suppressed."],
    ["WAIT_FOR_LANGUAGE", "A first-run user still on the language step. Nothing starts yet, so the UMP form is "
                          "never the first thing a new user sees."],
    ["ALLOW", "Free user past the language step. Consent, init and preload run once per process."],
])
b1("Every load and every show re-checks Pro at the moment it happens: a purchase mid-load drops the ad on arrival.")
b1("Pro ending (lapse, refund, debug switch off) re-opens the gate and ads start again without a restart.")
h2("4.4 AdManager and Compose")
b1("AdManager is a Hilt **@Singleton** and the only API the UI uses for ads.")
b1("MainActivity provides it to Compose through **LocalAdManager** (null in previews and tests, so no slot "
   "ever renders there).")
b1("The NavHost maps the current back-stack route to an **AdSurface** and reports it to AdManager, which "
   "decides whether an interstitial may show. The Review sheet reports its own surface while it is open.")

# ------------------------------------------------------------------ 5
h1("5. Interstitial Ad Requirements")
h2("5.1 Show rate target: >= 60%")
b1("load() runs right after SDK init, and again after every dismiss or failed show.")
b1("show() never waits (except section 5.4): if the ad isn't ready it logs interstitial_missed_show, reloads "
   "and lets the user carry on immediately.")
b1("An ad older than 55 minutes is discarded and reloaded (AdMob ads expire after an hour).")
b1("No show while another interstitial is on screen.")
h2("5.2 Triggers")
table([17, 47, 19, 17], [
    ["Trigger", "When", "Cooldown", "Status"],
    ["A — After offer", "The paywall is closed without buying, from any source (onboarding, Home, Scan result, "
     "Unused filter, reminders, sort menu, Settings). Shows over the screen the paywall returns to.",
     "None", "Planned"],
    ["B — Resume", "App returns to the foreground after a real trip to the background (onStart after onStop). "
     "Not on cold start, not after a configuration change, not after the app's own external screens (5.3).",
     "None", "Planned"],
    ["C — In-session", "Arriving at Home from Result (Done or Back), Apps, History or Settings.",
     "45 s since the last impression of ANY trigger", "Planned"],
])
note("Triggers A and B bypass the 45-second cooldown; C respects it. Every impression, from any trigger, resets "
     "the clock. A C show point inside the cooldown is skipped silently and is not a missed show.")
h2("5.3 Where interstitials are allowed (AdSurface)")
table([30, 13, 15, 42], [
    ["Surface", "Resume (B)", "In-session (C)", "Reason"],
    ["ONBOARDING, PAYWALL, USAGE_ACCESS", "No", "No", "First impression / purchase decision / permission "
     "disclosure"],
    ["REVIEW (UninstallConfirmSheet open)", "No", "No", "Start of the uninstall path"],
    ["UNINSTALL (Progress)", "No", "No", "Android's dialogs are queued over it; an ad would break the batch"],
    ["RESULT", "Yes", "No", "Celebration first; the exit is Trigger C on Home"],
    ["SCAN, APPS, HISTORY, SETTINGS", "Yes", "No", "Resume only; in-session ads wait for Home"],
    ["HOME", "Yes", "Yes", "Natural break between cleanups"],
])
h3("Resume suppression (ResumeGate)")
p("Returning from the app's **own** external screens is not a resume: Usage Access settings, App info, "
  "notification and security settings, the Play Store (details, Reinstall, Rate, Manage subscription), the "
  "Play purchase sheet, the notification permission dialog and the ad's own activity. MainActivity overrides "
  "startActivity, startActivityForResult and startIntentSenderForResult and sets the flag before calling "
  "super; launches that go through another context call AdManager.suppressNextResume() themselves. The flag "
  "clears on the next onResume, so a translucent dialog (Android's uninstall confirmation, a permission "
  "prompt) that never stops the activity cannot leave it set. A new activity instance (Back from Home, then a "
  "relaunch in the same process) is a launch, never a resume: the gate resets in onCreate.")
h2("5.4 First ad after the launch paywall")
table([6, 94], [
    ["#", "Step"],
    ["1", "A first-run user closes the onboarding paywall without buying and lands on Home."],
    ["2", "If the SDK is not initialised yet, AdManager waits for it, then up to 5 s for an interstitial to load."],
    ["3", "If the user is still on Home by then, the ad shows (Trigger A); if they have moved on, nothing shows. "
          "No overlay: Home is usable the whole time."],
    ["4", "Logged as interstitial_showed or interstitial_missed_show with trigger=after_offer."],
])
h2("5.5 Result -> Home")
b1("Done (or Back) on the Result screen navigates to Home first; Trigger C then runs on Home.")
b1("The Progress screen never shows an interstitial, and Try again on Result goes straight back to Progress "
   "with no ad.")
h2("5.6 Back button")
p("While an interstitial is on screen, Back is owned by the SDK's full-screen activity. The app adds no Back "
  "handler that could close the ad; it closes only through its own controls.")

# ------------------------------------------------------------------ 6
h1("6. Native Ad Requirements")
h2("6.1 Show rate target: >= 70%")
b1("One native ad is preloaded at init; each request takes it and replenishes the queue (NativeAdQueue).")
b1("Each screen appearance requests a fresh ad; ads are never reused across screens.")
b1("A slot takes **no space** until an ad has loaded — never an empty grey box.")
b1("Ads are destroyed when their screen leaves composition.")
h2("6.2 Placements")
p("One native ad per screen, pinned to the bottom above the navigation bar. It never scrolls away, and the "
  "screen's content ends above it instead of running under it. Until an ad has loaded (and for Pro) the "
  "screen has its full height. Where a screen has its own pinned buttons, the ad sits just above them.")
table([20, 62, 18], [
    ["Screen", "Placement", "Style"],
    ["Home", "Pinned at the bottom, under the scrolling Home content.", "STANDARD"],
    ["Apps (every filter)", "Pinned at the bottom. While the Selection Bar is up, the ad rises above it, so "
     "Remove is never pressed against the ad; the list reserves both heights under its last row.", "STANDARD"],
    ["History", "Pinned at the bottom.", "STANDARD"],
    ["Scan", "While scanning and on the result, just above the result's buttons (at the bottom while "
     "scanning). Never on the Usage access explanation, which is a permission disclosure.", "STANDARD"],
    ["Result", "Just above Done. The freed-space gauge shrinks (220dp down to 140dp) so a result that nearly "
     "fits is one page instead of scrolling a few dp.", "STANDARD"],
    ["Settings", "Pinned at the bottom.", "STANDARD"],
    ["Onboarding, Paywall, Usage Access, Review sheet, App details sheet, Progress", "Never", "—"],
])
note("**One ad per screen.** An in-list ad on top of the pinned one (tried in the Apps list) put two ads on "
     "screen once the list scrolled; it was removed.")
h2("6.3 Visual distinction & policy")
b1("Every native ad carries a visible \"Ad\" badge, localized in all nine app languages.")
b1("Background clearly distinct from content: #E3E8F0 in light theme, #232A36 in dark (a cool grey the app "
   "uses nowhere else), with the theme's 1dp border; colours follow theme changes at runtime.")
b1("The call to action uses the app's green; the ad has no checkbox and no size bar, so it can't be mistaken "
   "for a selectable app.")
b1("STANDARD has no MediaView by design (it stays compact at the bottom). Google's native ad validator "
   "reported \"No implementation issues found\" on the test ads.")
b1("The validator's own popup is turned off in the manifest (NATIVE_AD_DEBUGGER_ENABLED = false): it "
   "outlived the screen its ad was on. It only ever appears on test ads.")

# ------------------------------------------------------------------ 7
h1("7. AdConfig Constants")
table([40, 32, 28], [
    ["Constant", "Value", "Purpose"],
    ["INTERSTITIAL_UNIT_ID", "ca-app-pub-3940256099942544/1033173712", "Test unit (replace for release)"],
    ["NATIVE_UNIT_ID", "ca-app-pub-3940256099942544/2247696110", "Test unit (replace for release)"],
    ["INTERSTITIAL_MIN_INTERVAL_MS", "45 000", "Trigger C cooldown"],
    ["AFTER_OFFER_MAX_WAIT_MS", "5 000", "Wait for the first ad after the launch paywall"],
    ["RETRY_STEP_MS", "30 000", "Backoff step for failed interstitial loads"],
    ["RETRY_MAX_DELAY_MS", "300 000", "Backoff cap"],
    ["MAX_CONSECUTIVE_FAILS", "3", "Stop retrying until the next show point"],
    ["AD_MAX_AGE_MS", "55 min", "Discard loaded ads before AdMob's 1 h expiry"],
])

# ------------------------------------------------------------------ 8
h1("8. Consent Manager & SDK Initialization")
b1("ConsentManager wraps UMP: requestConsentInfoUpdate -> loadAndShowConsentFormIfRequired. Errors are logged "
   "as consent_error and never block the app or the ads flow.")
b1("Consent must be resolved before MobileAds.initialize() (section 4.2).")
b1("Offline, UMP cannot load: the error path initialises the SDK anyway, and consent is refreshed on the next "
   "cold start. No connection gate — ads load opportunistically.")
b1("privacyOptionsRequired (StateFlow) drives a **Privacy choices** row in Settings, shown only to free users "
   "when UMP reports privacy options as REQUIRED; it reopens the UMP privacy form.")

# ------------------------------------------------------------------ 9
h1("9. Ad Event Logging")
p("AdEventLogger forwards to the app's Analytics interface; every ad event is a member of the AnalyticsEvent "
  "sealed class, so there is one logging path and no free-form event names. The existing rule holds: no "
  "package names, labels or free text in params (codes, enums and counts only; consent_error sends the UMP "
  "error code, not its message). FirebaseAnalyticsSink sends them to Firebase; debug builds also echo "
  "them to logcat (tag AppCleanerFunnel).")
table([30, 50, 20], [
    ["Event", "Fired when", "Params"],
    ["interstitial_loaded", "Interstitial loaded", "—"],
    ["interstitial_load_failed", "Load failed", "code"],
    ["interstitial_showed", "onAdShowedFullScreenContent()", "trigger"],
    ["interstitial_impression", "onAdImpression()", "—"],
    ["interstitial_clicked", "onAdClicked()", "—"],
    ["interstitial_missed_show", "Show point reached but no ad ready (or the 5 s after-offer wait ran out)",
     "reason, trigger"],
    ["interstitial_show_failed", "onAdFailedToShowFullScreenContent()", "code"],
    ["native_loaded", "Native ad loaded", "—"],
    ["native_load_failed", "Native load failed", "code"],
    ["native_showed", "Native ad first bound into a view", "placement"],
    ["native_impression / native_clicked", "SDK callbacks", "—"],
    ["native_preload_miss", "A screen asked before the preload was ready", "—"],
    ["ad_suppressed", "A placement skipped because the user is Pro (once per placement per visit)",
     "format, placement"],
    ["consent_error", "UMP callback error", "code"],
])
p("trigger in after_offer, resume, in_session. placement in home, apps, history, scan, result, settings.")
note("**Show-rate monitoring:** interstitial = interstitial_showed / (interstitial_showed + "
     "interstitial_missed_show), alert below 60%, split by trigger. Native = native_showed / (native_showed + "
     "native_load_failed), alert below 70%.")

# ------------------------------------------------------------------ 10
h1("10. Retry Logic & Error Handling")
table([34, 22, 44], [
    ["Attempt", "Delay before retry", "Notes"],
    ["1st failure", "30 s", "failCount = 1"],
    ["2nd failure", "60 s", "failCount = 2"],
    ["3rd failure", "90 s", "failCount = 3"],
    ["MAX_CONSECUTIVE_FAILS reached", "Stop", "Resumes at the next show point's load()"],
])
b1("Native: no retry loop. Each request is one load; a failure leaves the slot hidden and the next screen tries "
   "again.")
b1("Error code 3 (NO_FILL) means no demand — don't hammer; codes -1 / 2 are transient and covered by the backoff.")

# ------------------------------------------------------------------ 11
h1("11. Consent & Privacy Compliance")
b1("**GDPR (EEA):** UMP; MobileAds.initialize() only after consent; DELAY_APP_MEASUREMENT_INIT in the manifest.")
b1("**US state privacy:** handled by UMP; the privacy policy URL must be set in AdMob app settings.")
b1("**COPPA:** App Cleaner is not child-directed; no tagForChildDirectedTreatment.")
b1("**Firebase Analytics consent mode:** not yet tied to the UMP result — wire before EEA release.")
b1("**Data minimisation:** ad events carry no package names or app labels; the installed-app list never leaves "
   "the device and is never passed to ad requests.")
b1("**Play Data Safety form:** declare ads (advertising ID) and app interactions for ad measurement.")

# ------------------------------------------------------------------ 12
h1("12. Testing & QA Checklist")
h2("12.1 Pre-release checklist")
table([5, 23, 46, 26], [
    ["#", "Check", "Pass criteria", "Status"],
    ["1", "SDK + consent order", "Consent -> initialize -> preload in logcat", "Verified on device"],
    ["2", "Native and interstitial load", "native_loaded, interstitial_loaded after launch", "Verified on device"],
    ["3", "Production IDs in release", "Release build uses real App ID and units", "Pending (test IDs)"],
    ["4", "EEA consent form", "UMP form on an EEA test device / debug geography", "Pending"],
    ["5", "Outside EEA", "No form; ads load", "Verified (Israel, gdprApplies = 0)"],
    ["6", "Pro = no ads", "Debug Pro switch: no consent, no loads, slots gone; off again: ads return", "Pro verified (ad_suppressed); return pending"],
    ["7", "Trigger A", "Ad after closing the paywall without buying; first-run wait capped at 5 s", "Pending"],
    ["8", "Trigger B on resume", "Ad after Home -> reopen; none after Settings, Play Store, App info", "Resume verified; suppression unit-tested"],
    ["9", "Trigger C cooldown", "Ad on returning Home; none within 45 s of the last impression", "Verified on device"],
    ["10", "Uninstall path clean", "No interstitial on Review, Progress or between dialogs; none before Result",
     "Pending"],
    ["11", "Apps pinned ad", "Bottom; above the Selection Bar while selecting; last row clears both", "Pending"],
    ["12", "One ad per screen", "Section 6.2 positions; never two ads on a screen; Result fits one page", "Pending"],
    ["13", "No empty ad boxes", "Slots take no space until loaded", "Pending"],
    ["14", "Fresh native per screen", "New ad on each screen appearance", "Pending"],
    ["15", "native_preload_miss < 20%", "From logcat", "Pending"],
    ["16", "All section 9 events", "In logcat (tag AppCleanerFunnel) and Firebase DebugView", "Logcat verified; DebugView pending"],
    ["17", "Show rates", "Interstitial >= 60%, native >= 70% per section 9 formula", "Pending"],
    ["18", "Minified release works", "No ad crashes with R8 (release has R8 off today; rules are in place)",
     "Pending"],
])
h2("12.2 Keep rules (app/src/main/keepRules/rules.keep)")
code("""-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.ump.** { *; }
# Add adapter-specific rules for each mediation network when adapters are added.""")
h2("12.3 Unit tests")
p("AdGateTest, InterstitialPolicyTest, ResumeGateTest, RetryBackoffTest, NativeAdQueueTest — the Pro and "
  "language gate, cooldown and bypass rules per surface, resume suppression, backoff and the native hand-out "
  "queue. JUnit 4 with hand-written fakes, like the rest of the suite.")

# ------------------------------------------------------------------ 13
h1("13. Agent Implementation Instructions")
table([8, 92], [
    ["Step", "Action"],
    ["1", "Add the dependencies, manifest meta-data and App ID placeholder (section 3) with the test IDs."],
    ["2", "Create the ads/ package (section 4.1), reusing InkSign's tested classes where the rules match."],
    ["3", "Add the ad events to AnalyticsEvent (section 9)."],
    ["4", "MainActivity: gate -> AdManager, lifecycle -> ResumeGate, external-launch suppression (5.3). "
          "Onboarding's language step opens the gate."],
    ["5", "NavHost: route -> AdSurface; Trigger A on paywall close, Trigger C on arriving at Home (5.2)."],
    ["6", "Pinned native ads on Home, Apps, History, Scan, Result and Settings (6.2); the Privacy choices row in Settings."],
    ["7", "Ad badge and Privacy choices strings in all nine languages."],
    ["8", "Build: assembleDebug, testDebugUnitTest, lintDebug — all green. Run the 12.1 checklist on the test "
          "phone and report show rates by trigger."],
    ["9", "Screenshots for the PR (light and dark): Home, Apps (with and without a selection), History, Scan, Result, "
          "an interstitial, Settings with Privacy choices."],
    ["Later", "Resolve section 2.2: production IDs, adapters and their keep rules, test device ID, Firebase consent mode."],
])
note("**Rules that don't change:** no ads on the uninstall path; Pro sees nothing; consent before initialize; "
     "never an empty ad box; no ad next to the Remove button.")

# ------------------------------------------------------------------ 14
h1("14. Appendix — Useful Links")
table([35, 65], [
    ["Resource", "URL"],
    ["AdMob Android get started", "https://developers.google.com/admob/android/quick-start"],
    ["Interstitial ads", "https://developers.google.com/admob/android/interstitial"],
    ["Native ads", "https://developers.google.com/admob/android/native/start"],
    ["Mediation adapter compatibility", "https://developers.google.com/admob/android/mediation"],
    ["UMP / consent", "https://developers.google.com/admob/android/privacy/gdpr"],
    ["Test ad units", "https://developers.google.com/admob/android/test-ads"],
    ["AdMob policies", "https://support.google.com/admob/answer/6128543"],
])
p("End of Document — App Cleaner AdMob PRD v1.0")
