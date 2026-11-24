# Meraki Companion

<p align="center">
  <img src="branding/Meraki-Companion-Logo.png" width="124" alt="Meraki Companion cup and flow logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Companion-0.4.60-7d987d" alt="Companion version 0.4.60">
  <img src="https://img.shields.io/badge/Android-8.0%2B-454a4d" alt="Android 8.0 or later">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MPL--2.0-454a4d" alt="Mozilla Public License 2.0"></a>
  <img src="https://img.shields.io/badge/status-Developer%20Preview-7d987d" alt="Developer Preview">
</p>

**Meraki Companion** is an independent Android app for Meraki espresso owners. Follow an extraction as it happens, replay and compare your shots, and keep the recipe and tasting notes together so you can dial in your next cup.

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Using Companion](#using-companion)
- [Machine setup and recovery](#machine-setup-and-recovery)
- [Troubleshooting](#troubleshooting)
- [Development and feedback](#development-and-feedback)
- [License](#license)

## Features

| Area | What you can do |
| --- | --- |
| **Live recording** | Follow grind and brew weight, elapsed time, and calculated cup flow. Save the shot and final yield. |
| **Shot journal** | Review graphs and tasting notes, mark favourites, replay an extraction, and compare recent shots. |
| **Recipes** | Save dose, yield, brew temperature, grind setting, and a target time window. Apply supported settings with an existing compatible machine setup. |
| **Beans and usage** | Track bean bags, roast and opening dates, brand assignments, and measured grind totals. |
| **Backups and sharing** | Import and export journal data, save backups, and send compatible shots to other tools. |

See the [full feature reference](docs/FEATURES.md) for details, optional integrations, and limits.

## Requirements

| To do this | You need |
| --- | --- |
| **Build the preview** | Android Studio, Android SDK Platform 35, and a JDK 25 Gradle runtime. See the [build instructions](android/README.md#build). |
| **Run the app** | Android 8.0 or newer. An emulator works for offline features. |
| **Record live** | An existing compatible Secure Link service on the espresso machine, a pairing code, and the phone and machine on the same local Wi-Fi. |
| **Apply recipe settings** | A paired machine with the corresponding settings service enabled. |

## Quick start

1. Open [`android/`](android/) in Android Studio, sync Gradle, and build the debug variant. The [Android notes](android/README.md#build) include the terminal command.
2. Open **Recipes** to create your first recipe. If you already have compatible shot or journal JSON, open **Journal & app tools** to import it and try the graphs and replay.
3. If your machine already has a compatible Secure Link setup, open **Machine connection** and scan its pairing QR code. Then open **Record** and pull a shot.

The preview is distributed as Android source. Back up your journal before replacing an existing installation with a developer build.

## Using Companion

| I want to… | Where to go |
| --- | --- |
| Follow a live shot | **Record** |
| Replay a pull or write tasting notes | **Shots** → open the shot |
| Compare recent pulls | **Shots** → open the recipe comparison |
| Save a recipe for tomorrow | **Recipes** → create or edit a recipe |
| Export or import a journal | **⋯** → **Journal & app tools** |
| Pair or update the machine connection | **⋯** → **Machine connection** |

## Machine setup and recovery

Companion’s optional machine integration adds a background service and makes a reversible startup change. It preserves the original Meraki display executable and MCU firmware.

The Mac Self Service Installer 0.5.0 is a separate owner-test package. Fresh machine setup, the updated repair-removal preview, and cleanup for older test installations remain **TBD** for public distribution. This preview’s live recording was tested using an existing compatible installation.

## Troubleshooting

**The machine won’t connect.** Check that the phone and espresso machine are on the same local Wi-Fi. Open **Machine connection** to check the saved address or to pair again using your machine’s current code.

**I want to try the app without pairing.** The recipe library and local journal work offline. Import a compatible shot file to try the detail graphs and replay.

**I can export for Visualizer, but direct sign-in is unavailable.** Export is available by default. Direct sign-in uses a developer’s own registered client ID. See the [Android build notes](android/README.md#build).

## Development and feedback

Start with the [Android build and test notes](android/README.md) and the [changelog](CHANGELOG.md). The owner-tested Pixel build preserved the existing journal, recipes, bags, and measured grind totals during an update, and recorded a new espresso shot. The test notes include emulator checks and optional integration results.

For a GitHub issue, include the Companion version, Android version, and what you expected to happen. For a live-connection issue, include the machine model and firmware versions if known. Keep pairing credentials and private journal data out of reports.

## License

Companion’s original source is licensed under the [Mozilla Public License 2.0](LICENSE). Third-party dependencies and fonts retain their own terms.
