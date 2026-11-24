# Meraki Companion changelog

This history covers the Android app and the separate Mac setup package. Earlier entries are reconstructed milestones from archived release notes, test records, and source snapshots. Versions described as beta, owner test, or developer preview retain those statuses.

## Android app

### 0.4.60 — Recipe Sync Developer Preview

- Extended MerakiThing journal sync to exchange recipe library changes. Companion accepts the local address from the MerakiThing pairing QR and preserves the selected recipe when it is updated by sync.
- Prepared a Secure Link-only source snapshot. Removed bundled owner addresses, recordings, legacy recorder material, and the bundled Visualizer OAuth registration. Visualizer JSON export works with the default configuration. Direct sign-in and upload require a developer's registered client ID.
- Added the Companion cup logo, the Android launcher and startup artwork, the feature reference, and the MPL 2.0 source license. The reviewed dose, brew, and steam animations and their companion HTML are included. The low-water warning is shown as text.
- **Verification.** The earlier preview passed 61 host checks. On the Android emulator, journal import, short-event filtering, and playback passed. A local MerakiThing 0.20.0 service exchanged shots and recipes in both directions and a repeated sync produced no duplicate records. The emulator transport check used a synthetic pairing record.
- **RC1 on Pixel.** A privately signed test build installed in place and opened successfully. The existing journal, recipe library, bean bags, and measured grind totals were preserved. On first launch, the usage ledger counted one existing MerakiThing shot without adding a duplicate journal entry.
- **Live recording and repeat sync.** The owner recorded a new shot in Companion and synced it to MerakiThing on the Pi. The shot arrived with its details. A second sync left one copy of the shot.
- **Recipe and return sync.** The owner confirmed a recipe-library change reached MerakiThing and a shot edit made on MerakiThing synced back to Companion.
- **QR re-pairing.** The owner confirmed the **Pair again** QR flow on the Pixel and MerakiThing. A recipe edited in Companion synced successfully afterward.

### 0.4.59 — MerakiThing journal sync

- Added Companion-initiated shot journal sync with MerakiThing over a paired local connection. The journal exchange includes shot data and supported tasting metadata.
- **Verification.** The owner confirmed a sync between a Pixel and MerakiThing 0.16.0 on the Pi. Repeat sync and a return edit on those devices remained separate checks.

### 0.4.58 — Brand corrections

- Added brand assignment for a single shot, selected shots, or an entire month. Whole-month assignment defaults to unassigned entries, with confirmation required to replace an existing label.
- Corrections can include linked and standalone measured grinds. The recorded dates and weights, bag observations, and lifetime counts are preserved.
- Brand corrections survive restart, backfill, and backup restoration and can be included with new shot exports.
- **Verification.** Host checks covered assignment, usage, and export. Emulator checks covered assigning a whole month, selected shots, and one shot while preserving measured totals.

### 0.4.57 — Graphite

- Updated the app palette to graphite, charcoal, soft white, sage, and peach. The recipe's product photo and machine Home appearance remain user-selectable.

### 0.4.56 — Beanconqueror name matching

- Added editable bean, grinder, and preparation-method names to the transfer review. Companion remembers the exact names for later transfers so Beanconqueror can match existing entries.
- **Verification.** The owner confirmed a complete-shot import reused an existing bean and grinder. Host checks and a decoder/importer simulation also passed. Beanconqueror's custom graph scaling and native flow summary remained limitations.

### 0.4.55 — Direct Beanconqueror transfer

- Added a dedicated shot-detail action for sending a complete shot to Beanconqueror 8.8.2. The transfer includes the sampled weight curve, calculated cup flow, brew settings, and optional tasting notes.
- Added a transfer review and a warning before sending the same shot again.

### 0.4.54 — Bag details and monthly totals

- Added separate brand, roast date, and opening date fields to bag editing.
- Added month-by-month usage totals in the phone's time zone. The monthly view includes measured standalone grinds and groups entries without an assigned brand as **Unknown brand**. Lifetime totals remain available.

### 0.4.53 — Recent-shot comparison

- Added a recipe comparison that overlays weight curves from up to ten matching shots. Tap a shot to isolate its curve. The list shows saved brew time and measured grind weight where available.
- Reduced repeated Record-screen refreshes. Evaluation of the separate recorder transport trial was still pending on hardware.

### 0.4.52 — Recent-shot export

- Added a **Last 10 shots** JSON export alongside the full journal backup.

### 0.4.50–0.4.51 — Record animations and layout

- Added optional dosing cup, brew cup, and steam animations under Record settings. They start disabled and follow the activity shown on the Record screen.
- Refined the Record layout around the live scale, timer, progress, and weight and cup-flow graphs.
- **Verification.** For 0.4.50, the APK built and the animations and Record states passed portrait and landscape emulator checks.

### 0.4.47–0.4.49 — Live Record screen

- Introduced a compact live view with scale weight, brew timer, progress, calculated cup flow, recipe targets, and clearly marked offline and stale-data states. Compatible favourite shots and a recipe's time window can appear on the chart.
- Put recipe application and one-shot adjustments within reach of Record. The app checks for fresh machine data and idle state before applying settings.
- **Verification.** The 0.4.47 build passed presentation, recording, and export regressions. Both orientations were exercised with synthetic UI data.

### 0.4.46 — Recipe and bag tracking

- Added shared bag tracking to recipes and attached bag context to saved shots. Recipe backups include bag information and referenced product photos.

### 0.4.45 — Automatic tare handling

- Improved brew-start handling around automatic scale tare. The recorder waits for the scale to settle before using readings for the shot curve.
- **Verification.** Regression cases covered tare starts, connecting mid-brew, recovery for subsequent shots, and timing in graphs and exports.

### 0.4.43–0.4.44 — Recipe backgrounds

- Added recipe-specific Record card backgrounds that can use the recipe's Home color or a chosen product photo. Added background controls to the recipe editor.

### 0.4.42 — Cup flow and first detected drip

- Expanded shot analysis with scale-derived cup flow and an estimate of the first detected drip. Chart and export validation checked that both use the same calculated flow.

### 0.4.40 — QR pairing

- Added in-app QR scanning for Secure Link pairing and a confirmation step before replacing the existing connection.

### 0.4.38 — Visualizer sharing

- Focused the sharing screen on Visualizer, with browser sign-in, manual confirmation, optional tasting notes, receipt reuse, and Visualizer JSON export.
- **Verification.** Build, emulator, export, sign-in, receipt, and uncertain-upload checks passed.

### 0.4.18 — Usage coverage

- Split shot and measured-grind coverage so each count has its own start date. Coffee-ground weight uses measured grinder records.

### 0.4.5–0.4.9 — Early Android milestones

- Improved Secure Link pairing and recovery. Added journal search, filters, sort order, and recipe search and duplication.
- Introduced a dedicated shot-details view with cup-flow analysis, favourite comparisons, tasting notes, and clearer replay controls.
- Added a durable usage ledger and kept short events available in their own journal filter.

## Mac setup package

### 0.5.0 — Owner Test

- Added a single Mac setup window for staged machine-side installation, private backup verification, activation confirmation, pairing, and post-restart checks.
- Bundled Android 0.4.41 in this historical owner-test package. The setup and Android version numbers track separate products.
- **Verification.** Installer, removal, backup, and recovery paths were exercised in simulation. Live acceptance of the new 0.5.0 automation remained pending. Earlier 0.4.0 live pairing was confirmed.
