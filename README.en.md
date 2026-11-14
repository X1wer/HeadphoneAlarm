# FülesÉbresztő – headphone-only alarm clock

[Magyar leírás / Hungarian version](README.md)

A native Android alarm clock whose sound plays **only on connected, allowed headphones**. If no suitable headphones are connected, or the audio route cannot be verified, the phone **only vibrates** – it never plays the alarm on the built-in speaker.

> **Download:** the signed release APK is on the [Releases page](https://github.com/X1wer/HeadphoneAlarm/releases/latest) (`FulesEbreszto-1.0.apk`). It is also available in the [RepoShelf](https://github.com/X1wer/reposhelf) app catalog, which can install and update it. GitHub Actions still builds a **debug APK** for testing; release signing happens only outside the repository (the key is never stored here).

---

## 1. Purpose and main features

- Any number of alarms: name, hour/minute, repeat days or a one-time date, on/off switch, sound, volume (0–100 % in 5 % steps), vibration with headphones, snooze length, maximum ringing time.
- On first start a **switched-off** sample alarm: 06:30, Monday–Friday, 60 %, 10 min snooze, 10 min maximum, no vibration with headphones.
- **Headphone output:** wired (jack), wired headset and USB-C headphones automatically; Bluetooth A2DP, Bluetooth LE Audio, hearing aids and other USB audio devices only after you explicitly allow them.
- **Fail-closed audio:** the player starts muted and only becomes audible when the actual output is proven to be allowed headphones. Any doubt means vibration.
- **Disconnect handling:** instant mute, switch to vibration, shown on screen, re-checked on reconnection.
- **Full-screen alarm** also on the lock screen; swipe up = stop, swipe down = snooze; separate buttons, screen-reader actions and notification actions.
- **Built-in sounds** (4, self-synthesised, rights-free) and **your own sound files** (MP3, WAV, OGG, M4A … via the system file picker, with a persistable read grant).
- Exact scheduling with `AlarmManager.setAlarmClock()`; restored after reboot (even before unlocking), app update, manual time change and time-zone change.
- **Languages: Hungarian and English.** The app follows the phone language (Hungarian phones → Hungarian, every other language → English) and can be switched in *Settings → Language*.
- Dark (default), light and system theme; 24-hour format switch; layout adapts to large font sizes.
- Fully offline: no `INTERNET` permission, ads, analytics or account.

## 2. Supported Android versions

| | |
|---|---|
| Minimum | Android 10 (API 29) |
| Target (targetSdk) | Android 16 (API 36) – meets Google Play's 2026 target requirement |
| Compile (compileSdk) | Android 17 (API 37) |
| Tested | Android 17 (API 37) emulator – see *Testing* |

targetSdk is deliberately 36: the new background-audio restrictions for apps targeting API 37 could not yet be verified on a real device.

## 3. Installation

1. Download `FulesEbreszto-1.0.apk` from the [latest release](https://github.com/X1wer/HeadphoneAlarm/releases/latest) – or install it with [RepoShelf](https://github.com/X1wer/reposhelf), which also keeps it up to date.
2. Open the downloaded file on the phone. Android asks you to allow installing unknown apps for the app you open it with (e.g. your browser or Files).
3. Start **FülesÉbresztő**.

The release APK is signed with the certificate whose SHA-256 is `7aa8f53bc27116f891e8539e8f0914918356bbb918e6a0261360552e0d67b3d7`. A debug build installed earlier (from GitHub Actions or Android Studio) has a different signature: uninstall it first (its alarms are lost).

From a computer: `adb install -r FulesEbreszto-1.0.apk`. Debug builds for testing: GitHub → *Actions* → latest successful “Android – tesztek és debug APK” run → *Artifacts*.

## 4. First start and permissions

On first start the app asks for **notifications** (Android 13+). *Settings → System permissions* shows the status and opens the right Android setting when something is missing:

| Permission | Why | Note |
|---|---|---|
| Exact alarms | ring on time | Android 13+: `USE_EXACT_ALARM` (granted at install); Android 12: user-grantable |
| Notifications | alarm notification, Stop/Snooze buttons | runtime permission on Android 13+ |
| Full-screen alarm | alarm screen over the lock screen | Android 14+: grant manually if the system did not |
| Vibration | fallback signal | granted at install |
| Battery optimisation | information only | `setAlarmClock()` bypasses Doze; aggressive vendor power saving may still interfere |

A yellow warning on the main screen shows missing critical permissions. The app does **not** request Bluetooth, microphone or internet permissions.

## 5. Creating an alarm

1. Main screen → **+ New alarm**.
2. **Time:** scroll the hour and minute wheels (24-hour), or use “Type with keyboard”.
3. **Alarm name** (default: “Alarm” / „Ébresztés”).
4. **Repeat:** Mon … Sun chips or presets (Weekdays / Weekend / Every day / Once). With no day selected the alarm is one-time and a date can be picked.
5. **Alarm sound**, **Volume**, **Vibrate with headphones too**, **Snooze** (5/10/15 min or custom), **maximum duration**.
6. **Save alarm** – saving switches the alarm on and schedules it; a short message tells you when it will ring.

Tap a card to edit it; long-press for **Copy** (switched-off copy) or **Delete** (with confirmation). The top of the main screen shows e.g. “Next alarm: Monday 06:30”.

## 6. Choosing your own alarm sound

Editor → **Choose alarm sound** → **Add sound file**. The system file picker opens; the app takes a **persistable read permission** (`takePersistableUriPermission`) so the file stays available after a restart. The file never leaves the phone.

- Every sound has a **test playback** button – it also only plays on verified headphones.
- When you remove a sound file, alarms using it switch to “Classic alarm”.
- If the file later becomes unavailable (deleted, permission revoked), the alarm plays the **built-in fallback tone** – again headphones only.

## 7. Managing headphones

- **Wired and USB-C headphones/headsets:** allowed automatically.
- **Bluetooth (A2DP, LE Audio), hearing aids, other USB audio devices:** after connecting they appear under **Settings → Allowed Bluetooth and USB devices**; alarms only play on devices switched on there. Do **not** allow Bluetooth speakers or car kits.
- **Bluetooth LE speakers** can never be allowed.
- **Settings → Audio output** shows the current media output, connected outputs, media volume and the state of output-change monitoring. **Test headphone sound** plays an 8-second test tone – only on verified headphones.
- If the headphones' **media volume is 0**, the main screen warns you and the alarm vibrates as well.

## 8. Snooze and stop gestures

The gesture can start **anywhere** on the alarm screen:

- **Swipe up (≥ 110 dp, mostly vertical) = stop.** Sound and vibration stop; the next repetition stays scheduled.
- **Swipe down = snooze** (“Snooze – 10 min” feedback). The alarm rings again when the snooze ends.
- Short or diagonal moves do nothing; one gesture triggers at most one action; repeated events have no effect.
- Accessibility: **Stop** and **Snooze** buttons, TalkBack custom actions and the notification's Stop/Snooze buttons.
- The Back button does not silence the alarm.

## 9. How the alarm system works

```
AlarmManager.setAlarmClock ──▶ AlarmReceiver ──▶ AlarmRingService (foreground, systemExempted)
                                                     │
                                       AlarmStateMachine (single decision maker)
                                         │ effects
             ┌───────────────────────────┼──────────────────────────────┐
     SafeHeadphonePlayer           VibrationController           AlarmEngine (Room)
   (muted start, route             (USAGE_ALARM vibration)      (snooze / dismiss / schedule
    verification, instant mute)                                   the next repetition)
                                         │
                         AlarmActivity (full screen, gestures)
```

- **State machine:** `IDLE → SCHEDULED → TRIGGERED → CHECKING_AUDIO → PLAYING_HEADPHONES / VIBRATING_ONLY → SNOOZED / DISMISSED / TIMED_OUT`, `ERROR` on failures. Sound and vibration are switched only by the effects the state machine returns; repeated or invalid events do nothing (idempotent stop, no double snooze, no vibration after stop).
- **Scheduling:** at most one main and one snooze trigger per alarm (unique PendingIntent identity). `NextTriggerCalculator` computes the next wall-clock occurrence (DST: a non-existent 02:30 rings at 03:30, a repeated 02:30 rings only once).
- **Stale triggers:** on ringing the app checks that the alarm still exists, is enabled and is scheduled for this very occurrence – deleted, edited or already rung alarms never ring.
- **Several alarms at once:** one ringing session; stop/snooze apply to all of them. Another alarm due during a snooze rings on its own.
- **Reboot / time changes:** after `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` every alarm is rescheduled. The database lives in **device-protected storage**, so the phone can ring before it is unlocked. An alarm missed while the phone was off rings right away if still within its maximum ringing time; older ones produce a notification. No foreground service is started at boot (Android 15+ rule).
- **Maximum ringing time** (default 10 min): the alarm stops and a notification says so.

## 10. Known limitations

- **Not yet tested on a real device – in particular not with Bluetooth or wired headphones.** Device-level tests ran on an Android 17 emulator without headphones, so speaker-free behaviour could not be physically measured.
- Audio-output safety limits: see the next section.
- On Android 14+ the full-screen alarm permission is not granted automatically for every install source; without it only a heads-up notification appears (sound/vibration still work).
- “Do Not Disturb – total silence” may mute media sound and vibration.
- During a phone call the alarm only vibrates.
- Before the first unlock after a reboot (direct boot) your own sound files cannot be read; the built-in fallback tone plays.
- The in-app language switch was verified on Android 17 (native per-app languages). The Android 10–12 path (context wrapping) is covered by the build but was not run on those versions.

## Audio-output safety in detail

Android offers no API that lets an app **guarantee** that not a single sample reaches the speaker. FülesÉbresztő therefore uses a layered “silent when in doubt” strategy:

1. **Not `USAGE_ALARM`:** Android duplicates the alarm stream to the speaker even with headphones connected. Playback uses `USAGE_MEDIA`, which normally goes to the headphones only.
2. **Muted start + `setPreferredDevice()`:** the player starts at volume 0, bound to the chosen headphones. `setPreferredDevice()` is only a request, not a guarantee – therefore:
3. **Verification of the actual route:** volume only fades in (over 5 s) when the output reported by the player (`getRoutedDevice`, on Android 16+ `getRoutedDevices` – multiple outputs) consists only of allowed headphones **and**, on Android 13+, the system's media route (`getAudioDevicesForAttributes`) contains neither the built-in speaker nor the earpiece. If this cannot be shown within 1.5 s, the alarm switches to vibration.
4. **Event-based monitoring:** `AudioDeviceCallback` (removed/added), `ACTION_AUDIO_BECOMING_NOISY` (sent before the system reroutes), routing-change listener and audio-focus loss. These **mute synchronously first** and only then re-evaluate. As a safety net the route is also re-checked every 250 ms while playing.
5. **Calls or other audio modes** and loss of audio focus keep it silent.

**Remaining risks (cannot be excluded on every device):**

- On a sudden disconnect, a few tens of milliseconds already in the audio hardware buffer may reach the speaker before the mute takes effect, depending on the vendor implementation.
- Below Android 16 the player reports only one output; if a vendor “dual audio” feature also duplicates to the speaker, only the system media-route check reveals it on Android 13–15, and nothing does on Android 10–12.
- Bluetooth devices are identified without the `BLUETOOTH_CONNECT` permission, by name and (partially anonymised) address; a device with the same name and address suffix as your headphones cannot be told apart. Only allow devices that really are headphones.
- If the system output switcher routes media to the phone speaker while Bluetooth is connected, the app treats the situation as uncertain and only vibrates.

**Tested devices:** only an Android 17 (API 37) x86_64 emulator without headphones. No physical Bluetooth/wired/USB-C test was done – for important alarms keep using a second alarm clock until you have tried it on your phone.

## 11. Troubleshooting

| Symptom | What to do |
|---|---|
| The full-screen alarm does not appear | Settings → System permissions → Full-screen alarm → Open, allow it |
| Only vibrates although headphones are connected | For Bluetooth, allow the device in the list; check “Audio output” (current media output, media volume); try “Test headphone sound” |
| Headphones are quiet or silent | Raise the phone's media volume (with headphones connected) and the alarm volume |
| Rings late | Check the “Exact alarms” permission; exclude the app from vendor battery saving |
| Built-in sound plays instead of your file | The file is unavailable: add it again with “Add sound file” |
| “Missed alarm” notification | The phone was off or the clock was moved forward at the alarm time |
| Details | Settings → Testing → Diagnostic log (stored locally only, no personal data) |

## 12. Building the APK

**Android Studio:** *File → Open* → project root → Gradle sync → *Build → Build App Bundle(s)/APK(s) → Build APK(s)*. Output: `app/build/outputs/apk/debug/app-debug.apk`.

**Command line** (JDK 17+; the Gradle Wrapper downloads Gradle 9.6.1, the Android Gradle Plugin downloads missing SDK packages):

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Emulator/device tests: `./gradlew connectedDebugAndroidTest`.

**GitHub Actions:** `.github/workflows/android.yml` runs on every push to `main`, on pull requests and manually (*Actions → Android – tesztek és debug APK → Run workflow*). It runs the unit tests and lint, builds the debug APK and uploads it as the `FulesEbreszto-debug-apk` artifact. Any failure fails the run.

---

## Technical summary

- Kotlin 2.4, Jetpack Compose + Material 3, MVVM, Room 2.8, DataStore, Navigation Compose, Android Gradle Plugin 9.4.1, Gradle 9.6.1.
- Packages: `domain` (pure Kotlin: time calculation, state machine, gestures, scheduling plan, formatting in both languages), `audio` (route rules, safe player, vibration), `alarm` (engine, AlarmManager, service, notifications, receivers), `data` (Room, DataStore, log), `i18n` (app language), `ui` (screens). UI texts: `res/values/strings.xml` (English, default) and `res/values-hu/strings.xml` (Hungarian).
- Tests: 95 JVM unit tests (`app/src/test`) and 13 instrumented tests (`app/src/androidTest`: Room, real AlarmManager, Compose gestures).
- Built-in sounds are generated by `tools/generate_sounds.py` (synthesised waveforms, free of third-party rights).

## Privacy

No internet, ads, analytics, account or cloud backup (`allowBackup=false`). All data (alarms, settings, allowed devices, diagnostic log) stays on the phone.

## Licence

The source code is MIT licensed (see `LICENSE`). Libraries: AndroidX/Jetpack and Kotlin – Apache License 2.0.
