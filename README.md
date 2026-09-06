# Fake Off

Fake Off is a small Android app that presents a black, minimum-brightness,
immersive screen and consumes touches and key events delivered to its activity.
It can optionally enable Android's **Do Not Disturb** mode while active. To exit,
tap the black screen three times and then press **Volume Down** three times, all
within ten seconds. Input is evaluated in a moving ten-second window: unrelated
inputs are ignored, and any valid tap-tap-tap-volume-volume-volume subsequence
unlocks the app.

## Build

```bash
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The **Build APK** GitHub Actions workflow also builds and tests every pull
request. Its `FakeOff-debug-apk` artifact can be downloaded from the workflow
run without installing the Android toolchain locally.

## Android safety limitations

This is a visual simulation, not a real powered-off state:

- Android does not allow an ordinary app to intercept or disable the power
  button. Long-press power and emergency controls remain available.
- System gestures, notification shade access, OEM overlays, and incoming system
  UI may remain available. Immersive mode only hides system bars temporarily.
- The display remains on at minimum brightness so the app can detect the unlock
  taps. Turning the physical display off would make those taps unavailable.
- Do Not Disturb requires the user to explicitly grant Notification Policy
  access. Without it, the app cannot silence notifications from other apps.
- The app restores the previous interruption filter when fake-off mode ends or
  the activity is destroyed.

These restrictions are intentional Android platform safeguards; bypassing them
would require device-owner/kiosk privileges or a modified operating system.
