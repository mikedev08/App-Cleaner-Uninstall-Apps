# App Cleaner design review

**Build reviewed:** `main` @ `ed65447` ("Redesign Home, Scan and Apps; real CleanupScan"), debug build.
**Devices:**
- Pixel emulator (1080×2424, 420 dpi, ~411dp wide), sample data, Pro expired.
- Real phone, CPH2645 (1080×2376, 480 dpi, **360dp wide**), 87 real apps, Pro forced on by the debug switch, 3-button navigation.

Both devices were checked in light and dark theme.
**Date:** 28 Sep 2026
**Scope:** visual design only: layout, spacing, colour, hierarchy and how clear the screens are. Mechanics aren't reviewed, except for the visible bugs in §9.

> **About "the latest version":** the APK on the phone (installed 09:56) has the same SHA-1 as the emulator build (`c52d779…`), and `main` has no newer commits. It's the same code. The phone pass still found a lot of new problems, because real data and a narrower screen expose things that sample data hides. Those findings are in **§2A**.

---

## 1. Summary

The visual language is good. The type is clean, the cards are soft and rounded, the green brand fits a "cleaner" app, and dark mode was done properly rather than just inverted.

The problems are not about style. They're about **structure and consistency**:

1. **Home tries to be every screen at once.** It shows storage status, a scan button, a dashboard, an app list, lifetime stats and an upsell. The same numbers show up in three or four places, and the same idea goes by four different names. That's the "everything in one place, a bit confusing" feeling.
2. **Colour has no hierarchy.** Green, red, orange, gold and pale yellow all compete. Red is used for *good news*, and several text colours are too faint to read comfortably.
3. **The spacing system isn't followed consistently.** Side margins, top bars, trailing columns and row heights all vary slightly from screen to screen.

Fix section 2 first, because it changes what's on the screen. Sections 3 and 4 are then mostly changes to the colour tokens and shared components.

---

## 2. Information architecture: why it feels like "everything in one place"

### 2.1 What Home currently holds (top to bottom)

| # | Block | What it's for |
|---|---|---|
| 1 | Premium banner ("Your premium ended — Renew") | Upsell |
| 2 | Storage gauge, "5.3 GB of 8.0 GB", "You have room to spare", "2.7 GB free" | Status |
| 3 | "Scan my phone" + caption, which becomes the "Last scan · You can free up 1.1 GB · Scan again" card after a scan | Main action and its result |
| 4 | "Where your space goes": 4 stat cards (Unused apps, Space hogs, App data & cache, Freed so far) | Dashboard |
| 5 | "Biggest apps · What's taking your space" + 5 rows + "See all" + "See all apps" | App list preview |

That's five jobs on one scrolling screen, with an upsell on top.

### 2.2 The same information in several places

| Information | Where it appears |
|---|---|
| Unused apps: 7 · 1.1 GB | Home stat card · Home "Last scan" card · Scan result headline · Scan result sentence · Scan result row · "Free up 1.1 GB" button |
| Space hogs: 3 · 1.0 GB | Home stat card · Home "Biggest apps" chips · Scan result row · Apps "Taking the most space" section |
| Freed so far: 470 MB · 4 apps | Home stat card · History hero card |
| Biggest apps list | Home "Biggest apps" · Apps "Taking the most space" (same apps, same order) |
| Way into the Apps list | Home "See all" · Home "See all apps" · Scan "Review all apps" · Scan "Space hogs ›" |

### 2.3 One idea, four names

Large apps are called **"Space hogs"** (Home cards, chips, Scan), **"Large"** (Apps filter), **"Biggest apps"** (Home section) and **"Taking the most space"** (Apps section heading). Someone reading these will reasonably assume they're four different lists.

Unused apps are close to consistent ("Unused apps" / "Unused"). "App data & cache" appears on Home and Scan, but there's no screen it leads to.

### 2.4 What the scan is for isn't clear

Before any scan, Home already shows "7 unused apps · 1.1 GB" and "3 space hogs · 1.0 GB". So what does "Scan my phone" add? After the scan, the result screen shows the same numbers again. The scan feels like a detour, not like the thing that produces the answer.

### 2.5 Pro gating is spread everywhere

PRO pills appear on two of the four Home cards, two of the three Apps filters, two of the three Scan rows, one Settings row and one Settings segmented control. There's also the full-width "premium ended" banner on Home *and* Apps, plus the upsell card at the top of Settings. It's hard to tell what the free app actually does. And "Free up 1.1 GB" is a free-looking button whose 1.1 GB comes from the *Pro-only* Unused category.

### 2.6 Proposed structure

**Rule: each screen has one job, each number lives in one place, and each idea has one name.**

**Vocabulary** (use it everywhere: cards, filters, chips, headings):
- **Unused**: not opened in N days
- **Large**: above the size threshold (drop "Space hog", "Biggest apps" and "Taking the most space")
- **Cache**: app data and cache

**Home** (status + one action):
1. A smaller storage gauge with "2.7 GB free". Put "5.3 of 8.0 GB" inside the gauge's open bottom.
2. **One hero card whose content depends on state:**
   - *Never scanned:* "See what you can remove" → **Scan my phone**
   - *Scanned:* "You can free up 1.1 GB" (in green or dark text, not red) → **Review** (opens the scan result or the Apps screen) · small "Scan again" link
3. **Categories**: a single grouped card with three rows (Unused · Large · Cache), each showing "count · size" and a chevron, each opening the Apps screen with that filter applied. Put the lock/PRO on the row once instead of stacking pills.
4. Remove "Biggest apps" from Home, since it's the same as the Large category. If you want a preview, show at most three rows under Large.
5. Move "Freed so far" into History. The history icon in the top bar already leads there.
6. Premium: one compact line or row (not a large yellow banner), and never on more than one screen at a time.

**Scan result**: the one place with the full breakdown. Show the headline number *once*, the before/after storage bar (with a legend), and the three category rows. The main button's label should match what it actually frees ("Remove 3 large apps · 1.0 GB" for free users).

**Apps**: the full list. Filters are *All · Unused · Large · Cache*, with the same names as Home. The header scrolls away; only the filter bar stays pinned.

**History**: the lifetime total ("470 MB freed · 4 apps") plus the timeline.

**Settings**: settings only. Put the Pro card lower, or in a "Subscription" section, rather than making it the first thing on the screen.

---

## 2A. What the real phone shows

Real data on a 360dp-wide phone makes the confusion from §2 much more concrete. These are the issues the emulator didn't show.

### The numbers contradict each other
- **Home says "0 Space hogs · 0 B"**, but directly below it "Biggest apps" lists five apps of 335–473 MB, and the Apps screen's "Taking the most space" section says **"9 apps · 3.1 GB"**. Someone reading this sees three answers to the same question. That's the four-names problem from §2.3 turned into an actual contradiction.
- **The scan result headline says "3.7 GB could be freed"**, but the sentence right under it says the unused apps use **"3.2 GB"**. The missing 0.5 GB is presumably app data & cache, which has **no row** on the result screen. On the same screen, "Space hogs · 0 apps · 0 B" does get a row with a chevron, leading to an empty list.
- **"Free up 3.7 GB"** is one tap on a button that, as labelled, removes 34 apps. The scale of that action isn't visible anywhere near the button.

### The percentages don't mean anything to people
- Every row in "Biggest apps" says **"0.2% of your storage"** or **"0.1% of your storage"**, next to a bar that's **nearly full**. The bar is scaled to the largest app, while the percentage is of the whole 256 GB disk, so a full bar sits next to "0.2%". Show just the size, or make the bar match the percentage.

### Text breaks at 360dp
- **"You have room to spare"** wraps to two lines, with "spare" on its own, and becomes a ~200dp block of headline. Size it for 360dp, or shorten it ("Plenty of room").
- **"Taking the most space"** (Apps) wraps to two lines because "9 apps · 3.1 GB" takes the right side.
- **"App data & cache"** wraps in its stat card, so row 2 of the grid is ~50dp taller than row 1.
- In Settings, the "Usage Access" description runs to **four lines** and the reminder row to **five**, because the trailing "Allowed" label and toggle take so much width.
- **The unused chip is cut off:** "🕒 Not opened in 2" / "Not opened in 1" (the unit is missing). The chip is too narrow for its text.

### Data that looks wrong
- **"Installed Jan 1970"** on preinstalled/system apps (הערות, Amazon Shopping, YouTube Music). That's the Unix epoch shown as a date. Hide the date when it's unknown.

### Colour on real data
- **Every size on Home is orange** (473 → 335 MB), and on the Apps list the first ~10 rows are orange and the rest suddenly turn black with green bars. Nothing on screen explains what changed. In dark mode the orange becomes bright yellow, and the whole list glows.
- The "Not opened" chip is orange text on pale yellow, low contrast like the rest of the orange text (§3).
- The stat card colours now depend on state: Unused = red tile + red text, Space hogs (0) = green tile + green "0 B", Cache = yellow tile + orange text, Freed (0) = green "Nothing removed". Four cards, three colour meanings, and green is used for "zero".
- The good news: with **real app icons**, the "confetti" problem from §3.2.7 mostly disappears. It came from the sample data's letter tiles.

### Zero states and empty states
- The zero-value cards ("0 Space hogs · 0 B", "0 B Freed so far · Nothing removed") each take a full card. Hide them or collapse them into a single line when they're zero.
- **The History empty state** ("No removed apps yet") is nice and calm. Minor points: the floating green and yellow squares feel random (and the yellow adds another accent), and the second small history icon duplicates the main one.
- **The "Pro is active" card** in Settings (mint surface, dark text) is much calmer than the dark green upsell. That's the tone the rest of the premium UI should aim for.

### Edge-to-edge and the navigation bar
- With 3-button navigation, the bar area is a **translucent scrim over content**. In light mode it's a white wash, which is fine. In **dark mode it's a noticeably bluish-navy band** (clearly different from the app's `#0B0F0E` background) across the bottom of every screen. Match the scrim to the background, or make it fully transparent and pad the last item.
- Content near the bottom (a heading, the next card) sits half-hidden behind the buttons while you scroll. Give the scrolling content bottom padding equal to the navigation bar's height plus 16dp.

### Unchanged on the phone
Everything in §4 reproduced on the phone as well: the list gutter vs page gutter, the Settings heading indent, the four top-bar styles, the misaligned Language chevron, the hard cut under the top bar, row heights changing with chips, and ragged bar ends.

---

## 3. Colour scheme

Tokens: `core/ui/theme/Color.kt`.

### 3.1 What works
- **The green accent** `#22C55E` with `onAccent #052E16` is fresh and on brand, and very readable on buttons (6.5:1).
- **The neutrals have a slight green tint** (`#F4F7F5` surface, `#0F1714` text), which ties the palette together.
- **Dark mode tokens are adjusted per colour**: `accentText #4ADE80`, softer `danger #F87171`, softer gold.
- **The Settings hero gradient** (`#0B3B20` → `#166534`) is the most premium-looking thing in the app.

### 3.2 Problems

1. **No colour leads.** On Home, green (gauge, button), red (stat sub-lines, chips, sizes), orange (sizes), gold (PRO pills) and pale yellow (banner) are all fully saturated at once. Only green should stand out.
2. **Red is used for good news.** "1.1 GB could be freed" / "You can free up 1.1 GB" is the best news the app can give, and it's shown in the error colour, the same red as the destructive Uninstall button and the "Space hog" chip. The scan result reads like a warning.
3. **Gold is louder than the main button.** `premiumGold #F5B301` is the most saturated colour on screen and appears 8–9 times. The upsell badge pulls attention away from the primary action.
4. **Sizes are shown in four colours**: red (hog), orange (medium), green bar with black text (small), plus orange again for InkSign. Colour-coding every number is noise, and the bar length already shows relative size.
5. **Text contrast** (on the `#F4F7F5` card unless noted; 4.5:1 is the usual minimum for small text):

   | Colour | Used for | Contrast |
   |---|---|---|
   | `accent #22C55E` as text | "4 apps removed", "in 10 apps" | **2.1:1** ❌ |
   | `warning #F59E0B` as text | "101 MB", "78 MB", "44 MB" | **2.0:1** ❌ |
   | `textMuted #94A09C` on white | secondary labels, footer | **2.7:1** ❌ |
   | `danger #EF4444` | stat sub-lines, sizes | 3.5:1 ⚠️ |
   | white on `removeRed #EF4444` | Uninstall button | 3.8:1 ⚠️ |
   | `accentText #15803D` | some green text | 4.6:1 ✅ |
   | `onPremiumGold` on gold | PRO pill | 7.5:1 ✅ |

   The readable green (`accentText`) already exists but isn't used everywhere green text appears.
6. **Cards barely stand out from the background.** Surface vs background is **1.08:1** in light mode and **1.10:1** in dark mode. In light mode the cards read as faint smudges, and in dark mode they nearly vanish. The `border` token exists but cards don't use it.
7. **The placeholder app icons** (orange, pink, red, purple, teal, yellow, blue letter tiles) make every list look like confetti. If the app draws its own placeholders, use one muted tint.

### 3.3 Proposed colour rules
- **Green** = brand, primary actions, and anything positive, *including* "you can free X".
- **Red** = destructive actions only (Uninstall, Remove). Not for sizes, stats or categories.
- **Gold** = Premium, in **one** strong spot (the upsell card). Everywhere else use a quiet variant: gold text on `premiumGoldSurface`, or an outlined pill.
- **Orange** = remove it as a text colour. If you need a warning, use it on a surface with dark text.
- **Sizes** in `textPrimary`; the bar carries the meaning, so only the bar (or only the "Large" chip) gets colour.
- **Green text** always uses `accentText`, never `accent`.
- Darken `textMuted` to at least about `#6B7773` (≈4.5:1 on white).
- Give cards a 1dp `border`, or darken `surface` a step (e.g. `#EEF2EF` light, `#1A2220` dark).

---

## 4. Spacing and layout system

Measurements are from screenshots (px ÷ 2.625 = dp).

| Issue | Where | Measured |
|---|---|---|
| **Side margins differ.** App rows are wider than everything else | Home "Biggest apps", Apps list, History list | Rows at **16dp**, all other content at **20dp** |
| **Settings headings are indented** relative to their cards | Settings title and section headings | Headings at **~25dp**, cards at 20dp |
| **The top bar changes per screen** | Home / Apps / History / Settings | Four treatments (logo + title; plain arrow + big title; same without collapse; circular filled back button + collapsing title). The Apps bar sits **~4dp higher** than History/Home, and the Settings back arrow is **~5dp** further right |
| **Top-bar tap targets are too small** | Back, history, settings, search, sort, overflow | **~24dp** (63px). The usual minimum is 48dp |
| **The Apps header stays pinned and is huge** | Apps | Title + banner + filters take **~43%** of the screen |
| **Content cuts off hard under the top bar** | All scrolling screens | No divider or tonal change; text is cut mid-line (easiest to see in dark mode) |
| **Dead space under the gauge** | Home | **~48dp** between the gauge arc and "5.3 GB of 8.0 GB" |
| **The layout jumps while loading** | Home | The placeholder ("—", no headline) is ~75dp shorter than the loaded state. Then the Premium banner arrives and pushes everything down another ~120dp |
| **Progress tracks end at different points** | App rows | Each track stretches to fit its size label, so the right ends are ragged |
| **Row heights vary** | App rows | Rows with a chip are **~30% taller** than rows without, which breaks the rhythm |
| **Trailing column misaligned** | Settings: Language row | The "English ›" chevron is **~40dp** left of every other chevron |
| **Floating selection bar** | Apps (multi-select) | Its own ~10dp side margin (not 20); the Uninstall button is ~10dp from the right while content on the left starts ~23dp in |
| **Heading alignment** | Apps: "Taking the most space" | The red dot pushes the text ~16dp right; "Everything else" sits on the margin |
| **Stacked buttons too close together** | Scan result | ~8dp between two 60dp full-width buttons |

**Proposed rules**
- One side margin (**20dp**) for *everything*: headings, cards, rows and bottom bars.
- A spacing scale of 4 / 8 / 12 / 16 / 24 / 32. Section heading → content: 12dp. Between sections: 32dp. Between cards: 12dp. Between stacked buttons: 12dp.
- One `AppTopBar` component: 48dp icon buttons, the same height on every screen, a large title that collapses on scroll everywhere (or nowhere), and a tonal fill or hairline once content scrolls under it.
- App rows: a fixed-height layout (put the chip on the same line as the subtitle), a fixed-width bar track, and a fixed-width trailing column for size + checkbox.
- Loading placeholders at the same height as loaded content. Reserve space for the banner, or show it in a place that doesn't push the layout.

---

## 5. Component consistency

- **Header icons**: History is outlined, Settings (gear) is filled. Pick one style (outlined is a good fit for this visual language).
- **Locked states come in three forms**: "🔒 PRO" pill, bare "🔒" ("Total"), and *no indicator at all* (the 30/60/90-day control, which also has nothing selected, so it looks broken). Use one lock treatment everywhere.
- **The PRO pill on the Pro upsell card** ("🔒 PRO" above "Unlock App Cleaner Pro") is odd: a lock on the thing that unlocks. Use a crown/sparkle label or just "PRO".
- **The PRO pill squeezes titles**: in Settings, "Remind me about unused apps" wraps to two lines because of the pill. Put the pill under the title or at the trailing edge.
- **Trailing items aren't consistent**: Scan result rows mix PRO pills and chevrons; History mixes a "Reinstalled" chip and plain "Not from Play" text. Pick one pattern for status (for example, all chips).
- **Two grouping patterns**: Scan uses separate cards per row, Settings uses grouped cards. Pick one for lists of options (grouped is calmer).
- **Segmented controls**: in the Apps filter, the selected "All" is much wider than the rest, and the PRO pills are squeezed inside. In Settings the segments are even. Use equal-width segments, and move the lock out of the segment label (or show a lock icon only).
- **Checkboxes**: the unchecked state is a white circle on a light grey card and barely shows. Use a 2dp `textMuted` outline.
- **Duplicate links**: "See all" and "See all apps" on Home go to the same place.
- **Redundant labels**: "10 apps · 1.3 GB" under the Apps title and then "10 apps" again above the list.

---

## 6. Screen-by-screen notes

### Home
- "You have room to spare" is as heavy as "67%", so the two headlines compete. Make the status line a size or two smaller.
- The stat cards are tall for what they show: the icon tile and PRO pill take a whole row, and the number fills only about a third of the card. If you keep them, make them shorter (icon beside the number) or turn them into rows (see 2.6).
- The sub-line colours vary card to card (red, red, green, green) with no clear meaning.
- The Premium banner: "Renew" is indented under the text rather than aligned to the card's padding; the ✕ is centred vertically instead of at the top; the banner is ~120dp tall for a single message.

### Apps
- The pinned header is too tall (see §4); the banner should scroll away.
- The filter bar is uneven and crowded (see §5).
- It's the same list as Home "Biggest apps", with a different name.

### Scan (running)
- The two waiting steps look different: "Finding apps you don't use" is black, "Measuring storage" is grey.
- The progress ring has a faint dashed line at about 9 o'clock, where the gradient tail ends.
- The bottom ~30% of the screen is empty. Centre the content vertically, or make the ring bigger.

### Scan (done)
- "1.1 GB" appears four times: headline, sentence, Unused row, button. Keep the headline and the button.
- "1.1 GB could be freed" is red (see §3).
- The two-tone "67% full → 53% after cleanup" bar has no legend for grey vs green.
- The secondary "Review all apps" button is nearly as heavy as the primary. Use a text button or an outlined button.

### History
- "about 470 MB freed": the hedge word "about" sits in the largest headline on the screen. Put the approximation in the sub-line ("≈ estimate") or drop it.
- The hero card has a 20dp margin, the rows 16dp.
- A "Reinstalled" chip and plain "Not from Play" text sit in the same position.

### Settings
- The Pro card is the first thing on the screen and takes about half of it.
- Two feature lines end with a single orphaned word ("months", "up"); rewrite them shorter. The check icons are centred on two-line text instead of lining up with the first line.
- The "English ›" chevron is misaligned (see §4).
- The 30/60/90 control shows no selection and no lock.
- The footer's dots are spaced unevenly ("1.0 ·" is tight, " · Privacy · " is wide).
- The Debug section with its truncated "Not configure…" should be hidden in release builds (it probably already is; just check).

---

## 7. Dark mode

Generally good. Notes:
- Cards are barely distinguishable from the background (1.10:1). Add the border or raise the surface a step.
- The dark maroon (`dangerSurface #3B1618`) icon tiles and "Space hog" chips look muddy next to the clean green tiles. If red is reserved for destructive actions (§3.3), this goes away.
- The hard cut under the top bar is most visible here.

---

## 8. Priority fix list

| # | Change | Effort | Impact |
|---|---|---|---|
| 0 | Make the numbers agree: one definition of "Large" behind Home, Scan and Apps; headline = sum of the rows shown; add a Cache row to the scan result | S–M | **Critical** |
| 0b | Fix 360dp breakage: headline size, truncated "Not opened in …" chip, wrapped section/row titles | S | **Critical** |
| 1 | Settle the vocabulary (Unused · Large · Cache) and rename everywhere | S | High |
| 2 | Restructure Home: gauge → one state-driven hero card → a grouped category list; drop "Biggest apps" and "Freed so far" from Home | M | High |
| 3 | Colour rules: green for "can free", red only for destructive actions, a quiet gold except in one spot, sizes in neutral text | S–M | High |
| 4 | Contrast: `accentText` for green text, drop orange text, darken `textMuted` | S | High |
| 5 | One 20dp margin everywhere (app rows, Settings headings, selection bar) | S | Medium |
| 6 | One shared top bar: 48dp targets, same height, same collapse behaviour, scrolled state | M | Medium |
| 7 | Apps: header scrolls away, only filters pinned; equal-width filter segments | S | Medium |
| 8 | One lock/PRO treatment; fix the 30/60/90 empty state | S | Medium |
| 9 | App row: fixed height, fixed track width, fixed trailing column | S | Medium |
| 10 | Remove duplicates: "See all" vs "See all apps", "10 apps" twice, "1.1 GB" ×4 | S | Medium |
| 11 | Loading states at the final height; the banner shouldn't push content down | S | Low–Med |
| 12 | Small polish: gauge dead space, scan ring seam, pending-step colours, footer dots, orphaned words, icon style | S | Low |

---

## 9. Outside scope, but visible

- **History date headings are wrong.** Entries from the same day got split into a "Today" section and a dated section, and the labels swapped places between two visits a few minutes apart ("Today" + "Sep 28, 2026", then "Jan 6, 2027" + "Today"). It looks like a timezone or day-boundary bug in the grouping.
- **The scan copy says "haven't opened in 60 days"** while Settings shows no threshold selected (emulator, Pro expired). On the phone (Pro on) 30 days is selected and the copy matches.
- **"Installed Jan 1970"** for preinstalled apps (phone), which is the epoch timestamp shown as a date.
- **"Not opened in 2" / "Not opened in 1"**: the chip text is cut off (phone), so the unit is missing.
