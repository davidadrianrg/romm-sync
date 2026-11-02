# RomM Sync — pre-1.0 plan (working document)

> Version numbering stays in the 0.8.x series until David explicitly approves
> calling a release 1.0. This document describes the work, not a release.

## Goals

1. **Complete visual reskin** — from the current functional Material look to a
   deliberate, premium interface built around physical media.
2. **Replace the Shelf tab with a game launcher** inspired by `slot.` on the
   KONKR Pocket Advance: cartridge/disc carousel, platform shelves, insert
   interaction, minimal chrome.
3. **Fix high-impact bugs**, starting with large game uploads from the app.
4. **Iterate against a real Android emulator**, not only code review: install
   the APK, navigate, screenshot, compare.

## Design direction

- **Physical media first.** Each game is an object: a cartridge for cartridge
  platforms, a jewel case/disc for CD/DVD platforms. Cover art lives on the
  label/case, not in a rectangular card.
- **One hero game at a time.** Centered object, neighbours peeking at the
  edges, platform shelf selector at the bottom. Less grid, more ritual.
- **Warm dark palette.** Near-black warm background, cream type, amber accent;
  avoid pure black/white and saturated Material greens.
- **Quiet chrome.** Small status line, one platform chip, one search affordance.
  Details/actions live in a bottom sheet, not on the shelf.
- **Motion with weight.** Snap between objects, slight tilt/perspective on
  neighbours, insertion/ejection feedback. No generic fades.
- **State on the object.** Downloaded, missing, syncing, multi-disc and manual
  download are badges on the cartridge/case, not separate rows.

## Workstreams

### A. Design system

- Color tokens (warm dark surfaces, cream text, amber accent, semantic
  success/warning/error), typography scale, spacing, corner radii, elevation.
- Shared components: media object (cart/case), platform chip, status badge,
  bottom sheet, empty state, list row, progress.
- Motion tokens and haptics where available.
- Light theme is optional; default dark.

### B. Launcher (replaces Shelf)

- Horizontal carousel: previous/current/next, platform shelves.
- Cartridge renderer for cartridge platforms; jewel case (with disc) for
  optical platforms. Data-driven: platform → media type.
- Platform selector with counts, remembered selection, "All" mode.
- Game object states: downloaded, not downloaded, downloading, sync status,
  manual mark, multi-disc.
- Tap opens the existing game detail (reuse current actions: download, delete,
  save path, sync toggle, manual save download/upload, match metadata).
- Long-press: quick actions (favorite, download, details).
- Search and filter without leaving the launcher.
- Empty/first-run states.

### C. Bugs

**Large game uploads (priority)**
Root causes found in `RomUploader` / `NetworkModule`:
- `writeTimeout` is 30 s for every 8 MB chunk: a sustained upload below
  ~2.1 Mbps fails a chunk and aborts the whole upload.
- No per-chunk retry: one transient failure discards a multi-GB upload.
- No resume: the `upload_id` is lost on failure; retry starts from zero.
- No foreground service: leaving the app can kill the coroutine mid-upload.
- `complete` can exceed the 10 min read timeout while the server assembles a
  huge file; the current 5 min verification window may be too short.

Fix plan: upload manager with foreground service + persistent queue, adaptive
chunk size (8–16 MB based on measured throughput), per-chunk retry with
backoff, resumable sessions (persist `upload_id`/offset), longer write/read
timeouts, explicit phase UI, and verification by ROM id after an uncertain
`complete`.

**Other bugs to triage**
- Save sync edge cases after the slot migration.
- Multi-disc grouping in launcher and detail.
- File pickers and Android/data access UX.
- Error messages that surface raw exceptions.

### D. QA with emulator

- AVD `romm_test`: Pixel 6, API 34, x86_64, 3 GB RAM.
- Host note: this Fedora machine crashes the emulator with `-no-window`
  (headless GPU) even with KVM; **windowed + `-gpu host -accel on` is stable**.
- Script: boot, install APK, seed settings, navigate, screenshot each screen.
- Screenshot review per change; no visual change is "done" without a capture.

## Suggested order

1. Design tokens + launcher prototype (static) → approve direction.
2. Launcher implementation + platform shelves, reusing the detail sheet.
3. Reskin remaining screens (Library, Platforms, Downloads, Sync, Settings).
4. Upload manager rewrite and other high-impact bug fixes.
5. Full pass on emulator, polish motion, states and empty cases.
6. Only then discuss version numbering.

## Open questions

- Launcher default: one hero object centered (slot-style) vs. a visible shelf
  with several objects? Current concept is hero-centered.
- Optical discs: jewel case front (current concept) or exposed disc?
- Should the launcher be the app's home tab (replacing Library) or remain a
  separate tab while Library keeps the grid?
