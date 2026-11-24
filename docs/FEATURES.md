# Meraki Companion feature reference

**Version covered:** Meraki Companion 0.4.60 Recipe Sync, Android Developer Preview. This reference follows the app's screens and tools. [Build instructions and validation results](../android/README.md)

## Contents

- [Getting started and pairing](#getting-started-and-pairing)
- [Live recording and the Record screen](#live-recording-and-the-record-screen)
- [Shot journal](#shot-journal)
- [Shot details and tasting](#shot-details-and-tasting)
- [Replay and comparison](#replay-and-comparison)
- [Recipe library](#recipe-library)
- [Machine settings and Home appearance](#machine-settings-and-home-appearance)
- [Bean bags, roasters, and usage totals](#bean-bags-roasters-and-usage-totals)
- [Files, backups, and diagnostics](#files-backups-and-diagnostics)
- [Private Rotary Pumpers journal sync](#private-rotary-pumpers-journal-sync)
- [MerakiThing shot and recipe sync](#merakithing-shot-and-recipe-sync)
- [Visualizer](#visualizer)
- [Beanconqueror](#beanconqueror)
- [Preview boundaries and verification](#preview-boundaries-and-verification)

## Getting started and pairing

- Three main screens give you direct access to **Record**, **Shots**, and **Recipes**. The three-dot menu opens recording help, quick machine adjustments, machine connection, Visualizer, and journal tools.
- Use Android 8.0 or later. Build and emulator instructions are in the [Android project notes](../android/README.md).
- Browse imported shots, review graphs, replay recordings, write tasting notes, manage recipes and bags, and work with local files without connecting to a machine.
- For live recording, use a compatible machine-side **Secure Link** service on the same local Wi-Fi as the phone. Self Service Installer 0.5.0 is a separate setup baseline.
- Pair by scanning a machine pairing QR code, importing the phone pairing JSON, pasting the full pairing block, or entering the machine address, certificate fingerprint, and pairing key manually. Update the local machine address or forget the pairing and pair again. Your local shots and recipes stay on the phone when you forget a machine pairing.
- Companion checks the machine certificate and recorder identity before accepting live data. Transient connection failures can trigger a limited foreground retry sequence with a visible countdown and a Cancel action. You can also reconnect manually.
- **Getting started**, **Recording details**, and **App notes** explain the workflow, saved settings results, and what the displayed measurements mean.

## Live recording and the Record screen

- Tap **Connect** before grinding and wait for **Ready**. Grind and brew from the espresso machine. Companion observes the machine and saves detected shots automatically. One connection can stay open across multiple shots. Keep the app visible through the final drips.
- The main readout switches between **grinder scale**, **recorded grind dose**, and **brew scale**. It shows the elapsed shot time, selected yield target and progress, and calculated cup flow in g/s when data is available.
- Follow the live weight and cup-flow graphs. The graph can show a recipe yield line, a shaded target time window, and a dashed reference from a matching favourite shot. When brewing stops, the plotted brew curve stops growing while final drips can still affect the settled yield.
- See connection and workflow states such as waiting for data, Ready, Grinding, Waiting for tare, Brewing, and Final drips. Readouts flag delayed data and withhold stale values. The phone screen stays awake while recording.
- Track grinder start and stop, estimated grind duration, and settled grinder dose where the scale data supports it. A recent preceding grind can be attached to a brew. Grinder events can contribute to usage totals even without an attached brew.
- Companion waits briefly for the brew scale's automatic tare and leaves pre-tare weight readings out of the shot curve. It brackets brew start and stop using the observed samples, waits for final weight to settle, and records interruptions and data gaps for review.
- See the selected recipe, its dose, yield, temperature and manual grind setting, its bag age, and whether observed machine targets match it. Machine adjustments are gated while the machine is busy or the data is stale.
- See text warnings for low water and refilling, including last-seen status when the reading is stale. Optional Record animations and reminders cover the dosing cup, brew cup, and steam-wand wipe and purge. These animations can be toggled individually and start off disabled.

## Shot journal

- Browse a local record of saved shots with coffee or recipe name, local date and time, recording quality, yield, brew time, preceding measured dose or target dose, ratio, available bag age, taste, and a miniature graph.
- Search coffee or recipe text, notes, or date. Sort newest or oldest first and load more results as you browse.
- Filter **All shots**, **Favourites**, **Complete**, **Needs review**, or **Short events**. Needs review includes incomplete recordings and data gaps. Events shorter than 15 seconds have their own view so a short flush or other brief event can be inspected separately.
- Mark and unmark favourites. Open shot details or start replay directly from its card.
- Delete an individual local entry with confirmation. Its raw recording logs are retained. A local deletion does not remove the copy in the Rotary Pumpers forum journal.

## Shot details and tasting

- See settled yield, estimated brew time, measured dose or explicitly labelled target dose, brew ratio, and estimated grind time when available.
- Switch between **Weight** and **Cup flow**. Inspect the weight at brew stop, average cup flow, the estimated first detected drip, the pre-infusion setting and interval, gaps in the recording, and the result against the saved recipe targets and time window.
- View the bean bag, snapshot of its age when the shot was brewed, and brand or roaster, including any later brand correction.
- Save a quick taste label such as **Liked it**, **Too sharp**, **Harsh / dry**, **Sour**, **Balanced**, or **Bitter**. Add or edit written tasting notes. Ratings received with compatible records can be displayed.
- **Shot options** can turn the settings of an existing shot into a new recipe, open its Diagnostic graph, display a Brew screen image if the shot already carries one, compare recent shots for the recipe, assign a brand, or delete the entry. Visualizer and Beanconqueror are reachable from the detail screen.

## Replay and comparison

- Replay the saved shot with an animated cursor, cup weight, elapsed time, and phase. Play, pause, restart, drag the timeline, or change speed to **0.5×**, **1×**, or **2×**.
- Replay ends at brew stop and lists settled final yield separately. Missing intervals are marked, with readouts left blank across gaps. Any displayed pressure value is nominal or the configured pre-infusion value.
- In a two-shot comparison, pick a reference from saved shots, with favourites listed first. Both timelines align to brew start and share chart scales. Each shot ends at its own stop time. The comparison shows the difference in brew time and yield when available.
- Open **Compare recent shots for this recipe** from a shot or recipe. Overlay up to ten recent matching-recipe weight curves and tap a shot to isolate it. Its row shows time, measured grind weight when available, date, and any quality or curve-availability note.
- Use complete favourites with matching recorded settings to build a cup-flow reference. See the reference count, average, and the latest matching favourite curve. Four eligible favourites establish the typical range used for the **within**, **slower**, or **faster** comparison.

## Recipe library

- Create, edit, duplicate, search, select, apply, deselect, or delete a recipe. Search matches recipe name and notes. The selected recipe appears first. Deleting or editing a library recipe leaves the copy already attached to a recorded shot.
- Save a recipe from scratch or start with the settings in a recorded shot.
- Store a name, dose, target yield, brew temperature, manual grinder setting, optional shot time window, optional pre-infusion choice, a bean bag, and notes. The editor calculates the dose-to-yield ratio as you type.
- The editor supports **1.0–30.9 g** dose in 0.1 g steps, **10–99 g** yield in whole grams, and **85–96 °C** in whole degrees. The timed target is a post-shot comparison and a chart guide. The grind setting is a reminder for the operator.
- Choose **Keep current**, **On**, or **Off** for pre-infusion in a recipe. Pressure and duration stay at the values set on the machine.
- Give each recipe a machine Home color and name. In the Android Record card, use the standard background, the recipe's Home color, or a product photo picked on the phone. A saved product photo can appear as a thumbnail.
- Import and export recipe backups independently of the shot journal. Recipe backups carry the recipe library, saved bag information, and included product photos. Imports preserve existing recipes with matching IDs.

## Machine settings and Home appearance

- **Quick machine adjustments** shows the current observed targets and lets you change the machine's brew temperature, target brew yield, or target grind dose individually when connected to compatible controls. These changes leave the selected recipe in the phone library as saved.
- Read pre-infusion On/Off, the configured pressure choice, and duration. Toggle On/Off with confirmation when the machine and service are ready.
- Apply a recipe as a sequence of dose, yield, temperature, and optional pre-infusion settings, with progress and verification for each step. Compatible services can verify the return to machine Home and apply the recipe's Home appearance. From Record, the app can pause recording for a confirmed apply and reconnect after verification.
- Choose **Standard Home**, **Steel Blue**, **Grey**, **Forest Green**, or **Dark Purple**, set a custom Home name, check the last appearance result, or restore Standard Home. This is the appearance option provided by the supported machine service.
- Check a previous temperature, dose, yield, pre-infusion, Home or recipe result if its outcome is uncertain. An interrupted recipe reports verified steps and leaves remaining steps for a new user action. Checking a result does not resend the command.
- Companion sets targets and observes the workflow. Start brewing, grinding, steaming, or dispensing water on the machine. Set physical grind size and pre-infusion pressure and duration on the machine.

## Bean bags, roasters, and usage totals

- Create a named bean bag and attach or re-use it across recipes. Track brand or roaster, roast date, and opened date. Create a distinct **New bag today** entry when starting another bag.
- See age since roast, or since opening when the roast date is unavailable. Companion snapshots the bag and its age at brew time so subsequent bag changes leave historical ages intact.
- See lifetime recorded shot count, measured grind count, and the total weight of coffee ground, plus the dates tracking began. The usage totals ledger can retain counted history after a shot is removed from the journal.
- Browse totals by local calendar month and brand or roaster, including unknown brands.
- Assign or correct brands for an individual shot, chosen shots, or a whole month. Whole-month assignment can include counted history no longer visible in the journal. Selected-shot assignment includes linked measured grinds. Keep existing labels by updating only unassigned records, or explicitly replace prior labels. This changes attribution without changing the recorded weights, counts, graphs, or bag dates.

## Files, backups, and diagnostics

| Tool | What it handles |
| --- | --- |
| **Export journal → Last 10 shots** | A JSON array of up to ten newest journal shots. Short events are listed separately in the app and are omitted from this export. |
| **Export journal → Full journal backup** | JSON with journal entries, bag library, and recorded usage totals. Export recipes separately. |
| **Import JSON / backup** | A compatible individual shot, shot array, or Companion journal backup. The app validates records, skips existing shot timestamps, and merges supported bag and usage data from a backup. |
| **Import recipes / Export recipes** | A separate recipe-library JSON backup with bag records and referenced product photos. |
| **Export recording logs** | ZIP of app-owned recording sessions, grinder-event logs, and diagnostics when present. |
| **Shot options → Diagnostic graph** | Full saved recording, including final drips and settling. Electrical readings and available machine diagnostics are shown separately from the normal shot analysis. |
| **Shot options → Brew screen** | View an existing compatible 480 × 480 brew-screen PNG stored in a shot. New screenshots cannot currently be captured over Secure Link. |

Use the Android file picker to open or save files. Journal import accepts up to 64 MB and 1,000 records at a time. The recipe backup format supports up to 200 recipes and 32 MB per backup. The **Load bundled recordings** command can load demo data in a build that includes it. This preview ships an empty example-recordings file.

## Private Rotary Pumpers journal sync

- Open **My Shot Journal** from Companion, create a forum connection key, paste it in the app, verify the account, and confirm whose private journal will receive entries. The app shows a key fingerprint so you can compare it with the forum without displaying the key itself.
- Manually upload saved events, including incomplete events and attached notes or screenshots where available. Existing remote entries can be updated. Entries go to the private forum journal.
- See upload progress and counts for new, updated, and unchanged entries. Stop after the current entry and retry later. Open the web journal or review **Last sync details**.
- Forget the stored phone connection or revoke its key through the forum. The forum service and a valid connection key are required. Phone-side sync is an upload workflow.

## MerakiThing shot and recipe sync

- Use **Journal & app tools → Sync with MerakiThing**. Scan **Pair Companion** on the Thing's Pi menu. Companion saves the pairing, can pair again, and uses the paired local journal service with certificate checking.
- Sync shots and supported shot details in both directions, including recipe association, notes, taste, ratings, weight-curve points, and available grinder summaries. This is a journal exchange; use file export for the full recording archive. Repeat sync merges matching records without creating duplicates in the tested path.
- Exchange recipe library changes with a recipe-capable MerakiThing service. Companion tracks the previous recipe state for merges and retains the selected recipe when it is updated. Use the recipe backup if you need a portable copy of product photos and bag data.
- The app reports that recipe sync requires MerakiThing **0.18.0** or later. The documented exchange was checked with the **0.20.0** journal service.

## Visualizer

- Save a Visualizer-format JSON file for compatible complete recordings. The export carries recorded weight, calculated cup flow, original shot time, available settings and measured grind weight, and captured recipe or bag context when present.
- Set `COMPANION_VISUALIZER_CLIENT_ID` to your registered Visualizer OAuth client ID before starting Gradle to enable sign-in in a developer build. JSON export is available in the default build. With sign-in configured, connect or reconnect in the browser, authorize Companion, and manually confirm each upload. You can choose whether to include the written tasting note. The sign-in is separate from machine pairing.
- View the uploaded shot in Visualizer or save its JSON. Companion records successful upload receipts, flags uncertain results, and lets you check your Visualizer account before enabling another upload. Disconnect the Visualizer account from Companion in its settings.
- Compatible exports require a completed, gap-free extraction of at least 15 seconds with valid timing and weight data. Pressure and temperature sensor curves are omitted.

## Beanconqueror

- Send an eligible complete shot directly to the regular Android Beanconqueror app, version **8.8.2 or later**. Open Beanconqueror once to set it up before the first send.
- Preview the transfer. Include the original date, measured grind dose where available, final yield, brew time, recorded settings, weight and flow curves, and available bag information. Including written tasting notes is optional.
- Enter the exact bean, grinder, and method names already in Beanconqueror. Companion remembers those selections after sending. It warns before sending a shot it has previously handed off because another send may create a duplicate.

## Preview boundaries and verification

- **Measure versus estimate.** Cup flow comes from changes in scale weight with a shared two-second smoothing method. First drip is inferred from the rise in weight. Brew and grind times are estimated from sampled state transitions. Electrical power is a diagnostic reading. The displayed nominal extraction pressure and pre-infusion setting are not measured pressure curves.
- **What can be tried offline.** Use your own compatible JSON to exercise the local journal, detail graphs, replay, and file tools. Live recording needs an installed Secure Link service. Individual settings and appearance features also depend on the machine service supporting those controls.
- **What has been tested.** The test notes record emulator launch, journal import, short-event filtering, playback, and repeated two-way shot and recipe exchange against a local MerakiThing service. On hardware, the owner recorded a live shot in Companion and transferred it with its details to MerakiThing on the Pi. A second sync left one copy. The owner verified a recipe-library change and a shot edit synced from MerakiThing back to Companion. The **Pair again** QR flow also passed on physical devices, followed by a successful recipe edit sync.
- **Project scope.** Meraki Companion is an independent Rotary Pumpers Android app. The active preview source uses [MPL 2.0](../LICENSE). See the [project README](../README.md#source-license) for license and asset notes.
