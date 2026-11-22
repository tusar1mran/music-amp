# MusicAmp - Poweramp Clone

An Android music player with a 10-band equalizer and DSP controls, inspired by Poweramp. Built with Kotlin and Jetpack Compose.

## Features

**Playback**
- Plays your on-device library (MediaStore), with real tags: title, artist, album, genre, year, track number, bitrate and sample rate.
- Background playback through a foreground service, with a media notification, lock-screen controls, and headset / Bluetooth media buttons.
- Audio focus handling (pauses for calls, ducks for notifications) and pause on headset unplug.
- Gapless transitions and crossfade (configurable length), next / previous with real history, shuffle, repeat modes, play queue.
- Playback follows the list you started from (album, artist, folder, playlist, search results).
- Sleep timer, bookmarks, playlists, ratings, play counts.
- State is saved between runs: last track and position, EQ, DSP, reverb, settings, playlists, bookmarks, queue.

**Rotary knobs and sliders**
- One gesture mode is chosen per touch: touches on the outer ring turn like a dial, touches in the middle drag vertically.
- The knob follows the finger with no lag and does not spring back on release; presets, reset and tap glide smoothly.
- Magnetic centre detent with haptic tick on Balance and the 0 dB point of EQ bands.
- Effect updates are coalesced, so dragging never floods the audio framework.

**Audio effects** (Android AudioFX, on a single audio session shared by every track)
- 10-band equalizer mapped onto the device equalizer, bass / treble shelves, preamp (boost via LoudnessEnhancer, cut via player volume).
- Limiter option reduces gain automatically when boosted bands would clip.
- Stereo expand (Virtualizer), balance, tempo (0.5x - 3x, pitch kept), reverb (room model chosen from Size, gated by Mix).

**Themes** (Settings > Look and Feel)
- Poweramp (default: black, charcoal surfaces, neon green accent), Light, Dark, Black (OLED), Follow system.
- Accent colours: Poweramp Green, Berry, Blue, Green, Orange, Teal, Purple, Red.

**Display**
- Real cover art (embedded tag or folder art) on the player, mini-player and lists.
- Waveform seek bar built from the decoded audio (cached on disk).
- Live FFT spectrum over the cover: long-press the cover to switch visualization on. Needs the microphone permission, which is used only to read this app's own output. Without it the app shows a synthetic animation.

## Known limitations

- Mono downmix, ReplayGain, DVC, hi-res output, buffer size and Android Auto toggles are present in Settings but are not implemented.
- Streams cannot be played yet.
- Parametric EQ is not implemented; the Parametric preset behaves like Graphic.
- "Remove from library" hides a track in this app; it does not delete the file.
- Tag edits are stored in the app and do not change the audio file.
- The visualization presets from the original design are not wired to the current player screen; there is one spectrum style.
- Layouts are tuned for phones taller than about 700 dp; the Volume and Reverb tabs are cramped on very short screens.

## Tech stack

Kotlin, Jetpack Compose, Material 3, Android AudioFX, MediaSession, coroutines.

## Building

The project has no Gradle wrapper checked in. Open it in Android Studio, or run a local Gradle (8.x):

```bash
gradle assembleDebug
```
