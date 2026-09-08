# Turn Off

Turn Off is a small Android app that presents a pure-black, zero-window-brightness,
game-style immersive screen and consumes touches and key events delivered to its
activity. The immersive window hides the status bar, navigation controls, and
large-screen taskbar while turn-off mode is active, and disables Android's
system-bar contrast scrims so no lighter bar background remains.
It locks the activity to its current orientation so Android does not offer a
rotation-suggestion button and always requests Android lock task mode when
activated. Ordinary installations use Android's user-confirmed screen pinning;
a managed device-owner installation enters kiosk mode without that prompt when
the package has been allowlisted.
It can optionally enable Android's **Do Not Disturb** mode while active. To exit,
tap the black screen three times and then press **Volume Down** three times, all
within ten seconds. Input is evaluated in a moving ten-second window. The six
operations must be continuous and in that exact order; an extra tap or another
key event between them prevents that candidate sequence from unlocking. Older
unrelated input can slide out as a new continuous sequence is entered.

## Build

```bash
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The **Build APK** GitHub Actions workflow also builds and tests every pull
request. Its `TurnOff-debug-apk` artifact can be downloaded from the workflow
run without installing the Android toolchain locally.

## Android safety limitations

This is a visual simulation, not a real powered-off state:

- Android does not allow an ordinary app to intercept or disable the power
  button. A short press can lock or wake the physical display, and a long press
  can open the system power or emergency UI. The app receives neither press as
  a normal `KeyEvent`, so `dispatchKeyEvent` cannot consume it. Turn Off keeps
  this system safety control available rather than implying that every hardware
  button can be blocked.
- If the user does not accept Android's screen-pinning prompt, system gestures,
  notification shade access, OEM overlays, and incoming system UI may remain
  available. Immersive mode hides system bars but cannot apply kiosk restrictions.
- Turn Off starts managed lock task mode when a device-owner administrator has
  already allowlisted the package. This kiosk mode can restrict Home,
  Recents, and the notification shade without a pinning prompt. A normal app
  cannot silently grant itself that role.
- On a personal device, every activation requests Android's user-confirmed
  screen-pinning mode. It keeps Turn Off in front more strongly than immersive
  mode, but Android shows its confirmation UI and deliberately retains the
  system unpin action.
- The panel remains logically on at a zero per-window backlight override so the
  app can detect the unlock taps. On OLED screens the pure-black content turns
  the pixels off; LCD hardware may retain a faint backlight because Android does
  not let an ordinary foreground app physically power off the display while it
  continues receiving touches. Turning the physical display off would make
  those taps unavailable.
- Do Not Disturb requires the user to explicitly grant Notification Policy
  access. While turn-off mode is active, the app uses that access to silence
  notifications from other apps and suppress their visual effects, including
  heads-up banners, status-bar icons, badges, ambient notifications, and the
  notification list. Without access, it cannot hide or silence notifications.
- The app restores the previous interruption filter when turn-off mode ends or
  the activity is destroyed. It also restores the previous notification visual
  effects policy.
- Activation starts immediately. The home screen reports optional access that
  is missing and provides shortcuts for reviewing notification, display,
  gesture, and wireless emergency-alert settings before activation.

These restrictions are intentional Android platform safeguards. A managed,
device-owner kiosk can restrict some global actions, but provisioning that mode
factory-resets or administratively enrolls the device and still is not a
general-purpose way for an app to intercept power. Fully changing power-key
behavior requires a customized operating system.

### Pixel double-press Power gesture

Turn Off can temporarily set Pixel's double-press Power camera gesture to
**None** while turn-off mode is active and restore its prior value on exit. The
setting is protected by Android, so this works only when secure-settings access
has been granted from a computer for this installation:

```bash
adb shell pm grant com.example.fakeoff android.permission.WRITE_SECURE_SETTINGS
```

Without that optional development permission, use **Open system gesture
settings** and select **None** manually. This affects only the double-press
shortcut; it does not intercept a single press, long press, emergency gesture,
or another system-owned Power-key action. Reinstalling the app revokes the adb
grant.

### Why an AccessibilityService is not used on Pixel

An accessibility service can request key-event filtering, but that does not
make it a system input-policy component. Android's system policy handles the
Power key and does not deliver it through the accessibility key-filter callback.
Returning `true` from either `AccessibilityService.onKeyEvent` or the activity's
`dispatchKeyEvent` therefore cannot consume Power on a Pixel 10. Adding an
accessibility service would ask the user for a powerful, privacy-sensitive
permission without providing the requested behavior, so Turn Off deliberately
does not request that permission.

The supported behavior is to keep Turn Off active across ordinary activity
resumes and consume keys Android actually delivers to it, including the Volume
Down presses used by the exit sequence. Blocking the physical Power key is not
a supported behavior for a normal Play-installable application on Pixel.

### Pixel Always-on display

While Turn Off is in the foreground, it keeps the display awake and draws a
minimum-brightness black window. Because the device has not entered its ambient
sleep state, Pixel's Always-on display should not be shown during normal
turn-off use.

If Power is pressed, Android can still put the display to sleep and then show
the system-owned Always-on display. Its setting is protected; an ordinary app
cannot silently change it. Turn Off therefore provides an **Open display
settings for Always-on display** button so the user can disable the feature in
Pixel Settings before activation. The exact setting name and location can vary
with the installed Pixel/Android release.

### Black lock screen

While turn-off mode is active, its black activity is allowed to appear above
the keyguard. After the user wakes the screen, Android can therefore show the
black turn-off window instead of exposing the lock-screen wallpaper and
notifications. The app does not request dismissal of the keyguard, does not
turn the screen on by itself, and does not weaken the device PIN, pattern,
password, or biometric security. Leaving turn-off mode removes this permission
before returning to the app's controls.

Turn Off also moves its existing task back to the foreground if another
ordinary app takes focus while turn-off mode is active. This keeps the black
screen above normal application windows without creating a draw-over-other-apps
overlay or requesting broad overlay access.

This behavior is best-effort. Android may still show trusted system surfaces,
including Always-on display, emergency UI, the power menu, permission dialogs,
and low-level boot screens. If Android kills the app process, its activity can
no longer cover the keyguard.
