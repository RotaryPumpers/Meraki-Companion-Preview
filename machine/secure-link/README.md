# Secure Link recording service

This developer candidate contains the foreground TLS listener from Secure Link Lab 0.1.2 and the source of its ARMv7 telemetry recorder. Companion uses the listener to receive live readings and acknowledge each sample after saving it. The recorder observes the supported display process. The listener does not expose machine controls.

The three historical lab packages and owner setup 0.3.0 contained identical copies of the recorder shipped with owner setup 0.5.0. The recovered lab listener is a different build from the installed owner-test listener. It needs its own hardware qualification.

## Test from a checkout

Use Go 1.26 or newer and a JDK 17 or newer. Start in this directory.

```sh
classes=$(mktemp -d)
android_src=../../android/app/src/main/java
javac --release 17 -sourcepath "$android_src" -d "$classes" \
  "$android_src/com/rotarypumpers/meraki/core/SecureRecorderTransport.java" \
  android/TransportCheck.java
RP_JAVA_CLASSES="$classes" go test -race ./...
go run . --self-test
```

The loopback tests use the Android transport from the same checkout. They exercise pairing, certificate pinning, sample acknowledgement, disconnect handling, and rejection of invalid credentials. They need no espresso machine or pairing data.

## Build the Linux ARMv7 pair

Build the recorder from source with Zig and the listener with Go. The build embeds the digest of the recorder so a different executable cannot be substituted at runtime.

```sh
mkdir -p build
zig cc -target arm-linux-musleabihf -mcpu=cortex_a7 -Os -static -s \
  recorder/rp_capture.c -o build/rp-capture-armv7
recorder_sha=$(shasum -a 256 build/rp-capture-armv7 | cut -d ' ' -f 1)
CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 \
  go build -buildvcs=false -trimpath -ldflags="-s -w -X main.recorderSHA=$recorder_sha" \
  -o build/rp-secure-link-armv7 .
```

Keep these binaries together. The recorder checks the display executable against the supported build before following its telemetry. Installing the service on a stock machine requires a separate, qualified setup path. The Mac installer, machine settings service, and recovery tooling are outside this candidate.

## Hardware qualification

A first owner test needs an idle supported Meraki, a spare test journal, and an independently verified way to return to the owner’s working setup. Record one shot using the candidate, confirm that the saved shot has its weight, timing, and trace, and confirm that recording reconnects. A subsequent test needs a clean supported machine and a documented setup and rollback path that a second developer can follow.
