<!--
TITLE: say what changes for the user, in plain words. No "feat:" prefixes.
  Good: "Onboarding-first with optional language picker + native ad slots"
  Bad:  "Update OnboardingScreen.kt" / "fixes"

Keep each section short. Delete a section only if it truly doesn't apply.
-->

## Summary

<!-- One or two sentences: what this PR does and why. If it has more than one
commit with a distinct purpose, give each its own ### subsection below. -->

### 1. <What changed, in plain words>

<!-- What the user or developer will notice. Then the decisions a reviewer
should know about, one bullet each, leading with the point in bold. -->

- **<The point>.** <Why it's done this way, and what it avoids.>

## Net change

<!-- Paste from: git diff --shortstat main...HEAD
Say which part adds or removes the bulk, if it isn't obvious. -->

## Testing

<!-- What was actually run, and what it showed. CI covers build, unit tests
and lint on every push; list anything beyond that. -->

- [ ] CI green (build, unit tests, lint)
- [ ] Installed the debug APK from the CI run and tried the change on a device
- [ ] Checked in an RTL language (Arabic or Hebrew) if the UI changed

**Not yet checked:** <!-- Be explicit. "Nothing" is a valid answer. -->

## Screenshots

<!-- For UI changes: before / after. Delete for non-UI PRs. -->

## Follow-ups

<!-- Known gaps, loose ends, or things this PR deliberately leaves alone. -->
