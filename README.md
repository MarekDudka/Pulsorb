# Pulsorb

A playful drum machine built with **Kotlin Multiplatform** and **Compose Multiplatform**.
Every glowing circle on the screen is an independent drum. Move a circle to change its tempo
and volume, twist it to change its pitch, and add echo or reverb. Everything is synthesized in
real time, and you can record the result to a WAV file.

Runs on **Android** and **Desktop** (Linux, macOS, Windows).

![Pulsorb – playing](docs/screenshots/playing.png)

## Features

- **Kick, snare, hi-hat, bell and sample circles**: each one has its own beat clock, tempo, volume and pitch
- **A melody ready to play**: press Play and a 120 BPM groove starts with four bells on A-minor pentatonic
  notes. The bells loop at different speeds, so the melody keeps changing and only repeats every 30 beats
- **Microphone samples**: record a short sound and play it as a drum hit
- **Echo** (tempo-synced) and **reverb** for every circle
- **Multi-touch**: drag several circles at once, twist with two fingers to rotate
- **Record the output** to a WAV file
- Add and remove circles while the music is playing (up to 12)
- Animated glowing background and particle bursts on every beat, kept gentle on purpose and
  fully switchable off (**Visual FX: off**) for photosensitive users
- Collapsible circle list

## Controls

The screen is a 2D coordinate plane with its origin **(0, 0) in the bottom-left corner**.

| Gesture | Effect |
|---|---|
| Move a circle **up** | Faster tempo: 40 → 240 BPM |
| Move a circle **right** | Louder: 0 → 100 % |
| **Rotate** a circle (second finger, or mouse wheel on desktop) | Higher pitch, 60 Hz → 18 kHz on a log scale (~2 octaves per turn) |
| **Tap** a circle | Mute / unmute |
| **Echo / Reverb** sliders in the list | Effect amount for that circle |
| **● / ■** in the list (sample circles) | Record / stop the microphone sample |
| **✕** in the list | Remove the circle |
| Tap the **▾ Pulsorb** header | Show / hide the circle list |
| **About** | App version, author, license, privacy and credits |

**Play/Stop** and the **☰** menu button are always visible in the top-right corner. The menu holds
**● Record** (records the mixed output), **Visual FX**, **+ Kick / + Snare / + Hat / + Sample** to
add circles, and **About**. Tap **☰ / ✕** to show or hide it. It scrolls on small screens, so every
button stays reachable. While the menu is hidden, a red recording timer appears next to **Stop**
during recording. The app starts stopped; press **Play**.

For a sample circle, 440 Hz plays the recording at its original speed. Lower values slow it down
and higher values speed it up.

On Android, playback pauses automatically when the app goes to the background (a recording in
progress is saved first); press **Play** to continue. On desktop it keeps playing when minimized.

Both panels start folded: tap **▸ Pulsorb** to show the circle list and **☰** to show the menu.

| | |
|---|---|
| ![Circle list and menu open](docs/screenshots/menus-open.png) | ![Mixing and recording](docs/screenshots/mixing-recording.png) |
| The start-up melody with the circle list and menu open | Six circles with echo and reverb, recording in progress |

## Recordings

Press **● Record** while playing and press it again (or **Stop**) to save. Recordings are
44.1 kHz mono 16-bit WAV files named `Pulsorb-yyyyMMdd-HHmmss.wav`:

- **Desktop:** `~/Music/Pulsorb/`
- **Android 10+:** shared `Music/Pulsorb` folder (no storage permission needed)
- **Android 7–9:** the app's own music folder (the path is shown after saving)

Recording stops automatically after 5 minutes. Recordings made in the same second never overwrite each other.

## Building and running

Requirements: JDK 17 or newer (21 recommended), plus the Android SDK for the Android build.

```bash
# Desktop
./gradlew :composeApp:run

# Android (device or emulator connected)
./gradlew :composeApp:installDebug

# Tests (desktop + Android unit tests)
./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest
```

### Tests

Shared tests in `composeApp/src/commonTest` run on both desktop and Android:

- **VoiceTest**: tempo, mute, volume, pitch, sample speed, echo timing, reverb tail, removal fade-out,
  and stability (no NaN or overflow) at extreme settings for every drum
- **CircleStateTest**: screen position and rotation mapped to tempo, volume and 60 Hz – 18 kHz pitch
- **SamplePrepTest**: microphone sample trimming, normalization, fade-out, silence rejection
- **WavTest**: WAV header and sample encoding
- **SoundEngineTest** and **ParticleSystemTest**: voice management, soft clipping, particle limits
- **DefaultSceneTest**: the start-up melody's tempo, pitch, volume, pentatonic notes and timing

Desktop-only tests in `composeApp/src/desktopTest` play about 2 seconds of sound on the real audio
device, decode a recording with Java's own WAV reader, and check that saved files never overwrite
each other. `SceneRenderTest` renders 30 seconds of the start-up melody offline, checks it isn't
clipped, and leaves it at `composeApp/build/demo/default-scene.wav` so you can listen to it.

You can also open the project in Android Studio or IntelliJ IDEA.

On Android the app asks for microphone permission the first time you record a sample.

## Project structure

```
composeApp/src/
├── commonMain/      shared UI and audio engine
│   ├── App.kt              UI, gestures, circle list, controls
│   ├── SoundEngine.kt      drum synthesis, echo, reverb, mixer, output recording
│   ├── SampleRecorder.kt   microphone sample capture and trimming
│   ├── Particles.kt        beat particle effects
│   ├── Wav.kt              WAV encoding
│   ├── AboutDialog.kt      About dialog
│   ├── AppInfo.kt          app name, version, author, links
│   ├── AudioOutput.kt      expect: speaker output
│   └── AudioInput.kt       expect: microphone input and permission
├── androidMain/     AudioTrack / AudioRecord / MediaStore implementations, MainActivity
├── desktopMain/     javax.sound implementations, main()
├── commonTest/      shared unit tests (desktop + Android)
└── desktopTest/     audio device, WAV decoding and file saving tests
```

Built with Kotlin 2.2.10, Compose Multiplatform 1.7.3, Android Gradle Plugin 9.1.0 and Gradle 9.3.1.

## Privacy

The app works fully offline and collects no data. The microphone is only used to record samples, and
they stay on your device. See the [Privacy Policy](PRIVACY.md).

## Made with AI

This project was developed with the help of **Claude**, an AI assistant by Anthropic. The idea,
features, design decisions and testing are by the author. Most of the source code was written by
the AI following the author's direction.

## License

Copyright (C) 2026 Marek Dudka

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
[LICENSE](LICENSE) file for details.
