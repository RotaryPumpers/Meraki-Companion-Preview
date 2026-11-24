# Meraki Companion 0.4.60

Developer preview source under review. Meraki Companion is an Android app for recording espresso shots and reviewing them in a journal. It also includes recipes, bag and brand tracking, data export, and integrations with Visualizer and Beanconqueror.

This tree comes from Companion 0.4.60 Recipe Sync. The Journal & app tools menu includes **Sync with MerakiThing** for exchanging shots and recipes with the MerakiThing 0.20.0 journal service. Machine recording uses the paired Secure Link service. The Android project builds independently of the service. Live recording and machine settings require a compatible service that is already set up. Previously saved shots, including their images, can be imported without a machine connection.

## Build

1. Open this folder in Android Studio.
2. Install Android SDK Platform 35. Use a JDK 25 Gradle runtime, as specified in `gradle/gradle-daemon-jvm.properties`.
3. Sync the project and build the debug variant. From a terminal in this folder, the equivalent command is `./gradlew :app:assembleDebug` on Mac/Linux or `gradlew.bat :app:assembleDebug` on Windows.

The debug build is for development and emulator testing. Keep any existing journal backed up before replacing an installed Companion build.

Visualizer sign-in is optional. To enable it in a developer build, set the `COMPANION_VISUALIZER_CLIENT_ID` environment variable to your own registered Visualizer OAuth client ID before starting Gradle. Visualizer JSON export remains available with the default build.

## Host checks

Set `JSON_JAR` to the absolute path of an org.json JSON-java 20240303 JAR. Run from this folder with `java` and `javac` on your path.

```sh
classes=$(mktemp -d)
javac --release 17 -cp "$JSON_JAR" -sourcepath app/src/main/java -d "$classes" tests/*.java
java -cp "$classes:$JSON_JAR" com.rotarypumpers.meraki.BrandAssignmentChecks
java -cp "$classes:$JSON_JAR" com.rotarypumpers.meraki.ReplayTest
java -cp "$classes:$JSON_JAR" com.rotarypumpers.meraki.core.SecureRecorderChecks
java -cp "$classes:$JSON_JAR" com.rotarypumpers.meraki.ThingJournalChecks
```

## Review status

The original 0.4.60 archive supplied the sync changes. This review carries forward the Secure Link-only cleanup and accepts the private IPv4 address supplied by the MerakiThing pairing QR. The earlier 0.4.60 review assembled the debug APK, verified its signature, and passed 61 Companion host checks. The MerakiThing 0.20.0 recipe merge passed offline checks for both edit directions and repeat sync.

The standard 0.4.60 debug app (version code 86) installed and launched on the API 37 emulator. Journal import through the Android file picker, the Short events filter, and shot playback passed with synthetic data. The Secure Link pairing form and MerakiThing scanner opened. The virtual camera did not decode the test QR, so the emulator used a synthetic pairing record for the transport test. Companion and the local MerakiThing 0.20.0 journal service exchanged shots and recipes in both directions over pinned TLS. A second sync returned the same records without duplicates.

The owner recorded a new shot on the espresso machine with 0.4.60 RC1 on the Pixel. The shot transferred with its details to MerakiThing on the Raspberry Pi. A second sync left one copy of the shot.

The owner also confirmed that a recipe-library change reached MerakiThing and an edit made to the shot on MerakiThing synced back to Companion.

The owner confirmed the **Pair again** QR flow on the Pixel and MerakiThing, then edited the test recipe in Companion and confirmed the change synced to MerakiThing.

The legacy shell recorder, its machine-side executable, and private test captures are excluded. This preview uses Secure Link for machine connections. See `Third-Party-Notices/` and the font notices in `app/src/main/assets/`.

See the [repository license notice](../README.md#source-license) for source coverage and the reviewed animation set.
