# MusicAmp - Poweramp Clone

An audiophile Android music player and dynamic audio DSP application inspired by and replicating **Poweramp v3**, built with **Kotlin** and **Jetpack Compose**.

## Features

- 🎛️ **10-Band Graphic & Parametric Equalizer**: Real-time frequency response curve scroller, Preamp gain, and built-in preset manager with smooth glide transitions.
- 🔄 **Authentic Rotary Knobs**:
  - High-precision dual-mode gesture controls (vertical linear drag + circular tangential rotation).
  - Silky-smooth easing curve transitions (`FastOutSlowInEasing`) on preset changes, double-tap reset, and step adjustments.
  - Glowing neon green progress arc and bipolar center-split arcs for Balance.
  - Magnetic center detent indicators.
- 🔊 **DSP Audio Engine & Effects**:
  - Real-time Bass & Treble tone controls.
  - Stereo Expand, Balance, Mono DSP, and Tempo control (0.50x - 2.00x).
  - Multi-parameter Reverb engine (Damp, Filter, Fade, Pre-delay, Pre-delay mix, Size, Mix).
- 🌊 **Waveseek & Real-time Visualization**:
  - Dynamic scrolling waveform scrub bar.
  - Audio visualization presets (Centered Spectrum, Milkdrop Neon Horizon, Symmetrical HD Bars, Radial Reactor).
- 📂 **Full Library & Tag Management**:
  - 19 library categories, playlist management, queue, search, and Sleep Timer.
  - Unified bottom dock with mini-player and bottom navigation.

## Tech Stack

- **Kotlin** & **Jetpack Compose**
- **Material 3**
- **AndroidX AudioFX (Equalizer, BassBoost, Virtualizer, PresetReverb)**
- **Coroutines & Flow**

## Building

```bash
./gradlew assembleDebug
```
