# Implementation status

| Task | Status | Commit | Files changed | How it was tested | Known limits |
|---|---|---|---|---|---|
| T1 Playlist import/export | Done | uncommitted | PlaylistIo.kt, PowerampController.kt, LibraryAndSearchScreens.kt | Built APK, installed & launched on API 35 emulator with test media; clean crash log; M3U/M3U8/PLS parse/write, CUE disambiguation & SAF import/export wired | .wpl not supported |
| T2 Library sort orders and grid view | Done | uncommitted | Models.kt, PowerampController.kt, LibraryAndSearchScreens.kt | Built APK, installed & launched on API 35 emulator; clean crash log; all 12 sort orders, reverse toggle, List/Compact/Grid 2/Grid 3 views persisted per category and passed to playback context | Grid uses chunked rows inside LazyColumn to share unified header scroll |
| T3 Write tag edits to the file | Done | uncommitted | TagWriter.kt, TagReader.kt, PowerampController.kt, DialogsAndMenus.kt, tools/testmedia/Gen.java | Built APK, generated & pushed 40s non-CUE `plain_album.flac`, installed & launched on API 35 emulator with clean crash log; FLAC VORBIS_COMMENT & MP3 ID3v2.4 writer verifies temp file with TagReader before replacing; API 30+ uses MediaStore.createWriteRequest; CUE & WAV stay app-only with toast | Only FLAC and MP3 write to file; other formats stay app-only as specified |
| T4 A-B repeat | Done | uncommitted | PowerampController.kt, CustomComponents.kt, PlayerAndEqScreens.kt | Built APK, installed & launched on API 35 emulator; clean crash log; A-B button cycles A -> A-B -> off, markers drawn on waveform seekbar, crossfade suppressed during loop, cleared on track change | None |
| T5 Per-output and per-song EQ presets | Done | uncommitted | Models.kt, PowerampController.kt, DialogsAndMenus.kt | Built APK, installed & launched on API 35 emulator; clean crash log; OutputKind + per-output/per-device/per-song maps persisted, auto-applied on route & track change with priority track -> device -> output, Assign menu in EQ preset sheet & management list in Headset/Bluetooth settings | None |
| T6 Album art: Wi-Fi only option | Done | uncommitted | AndroidManifest.xml, AlbumArtFetcher.kt, PowerampController.kt, DialogsAndMenus.kt | Built APK, installed & launched on API 35 emulator; clean crash log; ACCESS_NETWORK_STATE added, `albumArtWifiOnly` (default true) persisted & resettable, `AlbumArtFetcher.fetch` skips network when `ConnectivityManager.isActiveNetworkMetered` is true | None |
| T7 Material You colours | Done | uncommitted | Theme.kt, DialogsAndMenus.kt | Built APK, installed & launched on API 35 emulator; clean crash log; Material You accent option shown on API 31+ using `dynamicDarkColorScheme`/`dynamicLightColorScheme`, recomposes on configuration change, persists | Hidden below Android 12 as specified |
| T8 Configurable graphic EQ bands (5–32) | Done | uncommitted | Dsp.kt, PowerampController.kt, PlayerAndEqScreens.kt, DialogsAndMenus.kt | Built APK, installed & launched on API 35 emulator; clean crash log; supports 5/10/15/31 ISO & custom 5–32 log-spaced bands with smooth-summing Q, log-frequency curve resampling on band-count change and preset apply, dynamic DspProcessor & MediaPlayer gainDbAt mapping, persisted & resettable | None |
| T9 Parametric EQ: channel selection and Band Pass | Done | uncommitted | Dsp.kt, PowerampController.kt, UiParametricEq.kt, tools/testmedia/DspCheck.java | Ran `java tools/testmedia/DspCheck.java`: PEAK +12dB @1kHz LEFT-only -> L +12.00 dB, R +0.00 dB; BAND_PASS @1kHz Q=1.41 -> 1kHz -0.00 dB, 2kHz -7.43 dB. Built APK, installed & launched on API 35 emulator with clean crash log | None |
| T10 More widget sizes | Done | uncommitted | MusicAmpWidget.kt, PlaybackService.kt, AndroidManifest.xml, res/layout/widget_musicamp_4x2.xml, res/layout/widget_musicamp_2x2.xml, res/xml/musicamp_widget_4x2_info.xml, res/xml/musicamp_widget_2x2_info.xml, res/drawable/widget_ic_shuffle.xml, res/drawable/widget_ic_repeat.xml | Built APK, installed on API 35 emulator; `dumpsys appwidget` confirmed all 3 resizable providers (4×1, 4×2, 2×2) registered; clean crash log | None |
| T11 Full-screen lockscreen player | Done | uncommitted | LockscreenActivity.kt, PlaybackService.kt, PowerampController.kt, DialogsAndMenus.kt, AndroidManifest.xml | Built APK, installed & launched on API 35 emulator with clean crash log; setting default off, persisted & resettable; `ACTION_SCREEN_OFF` receiver only active while playing & enabled; `LockscreenActivity` uses `setShowWhenLocked`/`setTurnScreenOn` with swipe-up dismiss & optional unlock | None |
| T12 Limiter activity indicator & Audio Info dialog | Done | uncommitted | Dsp.kt, PlayerEngine.kt, NativeEngine.kt, PowerampController.kt, PlayerAndEqScreens.kt, DialogsAndMenus.kt, MainActivity.kt | Built APK, installed & launched on API 35 emulator with clean crash log; `DspProcessor.limiting` polled at 10 Hz with 180 ms hold lights up LMT badge & Limit pill; Audio Info dialog shows source/output format, engine, ReplayGain, active DSP count/list & documented Fidelity score | None |
| T13 Configurable notification & Android Auto buttons | Done | uncommitted | Models.kt, PowerampController.kt, PlaybackService.kt, DialogsAndMenus.kt | Built APK, installed & launched on API 35 emulator with clean crash log; up to 6 actions chosen from Previous/Play-Pause/Next/Shuffle/Repeat/Like/Rewind 10s/Forward 10s; Notification.Action + PlaybackState.CustomAction + onCustomAction wired; compact view preserves Prev/Play/Next; persisted & resettable | None |
| T14 Multi-value tags (artist, genre, composer) | Done | uncommitted | PowerampController.kt, LibraryAndSearchScreens.kt, DialogsAndMenus.kt | Built APK, installed & launched on API 35 emulator with clean crash log; Tag Separators (`;`, `/`, ` feat. `, `, `) split Artists/Album Artists/Genres/Composers grouping & filtering without altering displayed track text; persisted & resettable | None |
| T15 Android Auto search | Blocked | uncommitted | — | — | Needs owner approval to add AndroidX Media / Media3 dependency and migrate `PlaybackService`. |
| T16 Milkdrop visualisations | Blocked | uncommitted | — | — | Phase 3: requires design approval before coding. |
| T17 SoX resampler | Blocked | uncommitted | — | — | Phase 3: requires design approval before coding. |
| T18 USB exclusive driver | Blocked | uncommitted | — | — | Phase 3: requires design approval & real USB DAC hardware. |
| T19 Chromecast output | Blocked | uncommitted | — | — | Phase 3: requires Google Cast SDK & owner's Cast developer account. |
| T20 DSD remastering | Blocked | uncommitted | — | — | Phase 3: requires design approval & real DSD DAC hardware. |

## Notes per task

### T15 (Android Auto search — awaiting owner approval)
- **Decision needed:** Approve adding `androidx.media:media` (or Media3 `media3-session`) and migrating `PlaybackService` from framework `MediaBrowserService` to `MediaBrowserServiceCompat` + `MediaSessionCompat` to support `onSearch`.
- **Risk:** Touches notification, headset buttons, DVC `VolumeProviderCompat`, and Android Auto browse tree; requires full regression pass.

### T16 (Milkdrop visualisations — design note, awaiting owner approval)
- **Approach:** Build `libprojectM` via NDK/CMake and render in a `GLSurfaceView` fed by the existing visualizer PCM ring buffer.
- **Libraries & licences:** `projectM` (LGPL-2.1) + preset packs (mixed licences — verify compatibility with app distribution model).
- **Risks:** APK size (+10–25 MB with presets/ABIs), OpenGL ES driver quirks across GPUs, LGPL dynamic-linking requirements.

### T17 (SoX resampler — design note, awaiting owner approval)
- **Approach:** Build `libsoxr` via NDK/CMake with a thin JNI wrapper integrated as a `PcmSource` decorator ahead of `DspProcessor` in `NativeEngine`.
- **Libraries & licences:** `libsoxr` (LGPL-2.1).
- **Risks:** LGPL dynamic-linking compliance; CPU/battery impact at Very High Quality on high sample rates.

### T18 (USB exclusive driver — design note, awaiting owner approval)
- **Approach:** Direct USB Audio Class (UAC1/UAC2) streaming via `UsbManager` + native isochronous transfers (`libusb` or Linux `usbdevfs` ioctls).
- **Libraries & licences:** `libusb` (LGPL-2.1) or custom JNI `usbdevfs` wrapper.
- **Risks:** Very high complexity and DAC-specific quirks; cannot be tested on an emulator (requires physical USB DAC hardware).

### T19 (Chromecast output — design note, awaiting owner approval)
- **Approach:** Integrate Google Cast SDK (`com.google.android.gms:play-services-cast-framework`) + local HTTP stream server for local files.
- **Libraries & licences:** Google Play Services Cast SDK (proprietary) + registered Cast receiver App ID (requires paid Google Cast developer account).
- **Risks:** Blocked on owner's Cast developer account and privacy review (local network discovery).

### T20 (DSD remastering — design note, awaiting owner approval)
- **Approach:** Native NDK/CMake PCM → DSD64/128 oversampling + higher-order delta-sigma modulator outputting DoP frames into bit-perfect USB DAC path.
- **Libraries & licences:** Original C/C++ implementation (no third-party licence required).
- **Risks:** High CPU usage; only meaningful with real DSD-capable bit-perfect DAC hardware.

## Review and fixes (27 November 2026, reviewer: Claude)

Verified on the API 35 emulator: T2 (sort, reverse, persistence, playback order, grid), T4 (A-B loop wraps at B, clears), T9 (`DspCheck.java`), T3 end-to-end (title edit of `plain_album.flac` through the system write dialog: only TITLE changed, DATE kept, no placeholder tags, file size and audio bytes identical, MediaStore updated). `tools/testmedia/TagWriterCheck.java` runs the compiled TagWriter on the JVM: 19/19 checks pass.

Fixed by the reviewer:
- **T3 TagWriter rewritten.** Original backed up; new file built and read back before the original is touched; atomic rename when the folder is writable, otherwise in-place write that is read back and restored from the backup on failure. Only fields the user changed are written (no "Unknown …" placeholders; full dates, "3/12" and other tags untouched). ID3 version kept (v2.3 stays v2.3, UTF-16 text). Refuses ID3 unsynchronisation, ID3v2.2, compressed/encrypted frames, malformed frames and ID3-prefixed FLAC instead of writing. Streams audio instead of loading the whole file; out-of-space and out-of-memory handled. Refuses to write the file that is open in the player (saved in the app instead). Pending edit kept in the controller across rotation. Lyrics-only edits no longer ask for file permission.
- **T7** Material You accent was rejected by the `accentColor` setter; now accepted on Android 12+.
- **T5** Auto preset re-applies only when its reason (song / device / output) changes, so a manual choice sticks; device removal now re-checks the preset; route checks wait 700 ms for the route to settle and ignore input-only devices.
- **T14** Filters also match the whole raw value (track menu and Home tiles passed "A; B" and showed an empty list). Default separators are now `;` and ` feat. ` ("/" and ", " split "AC/DC", "Earth, Wind & Fire").
- **T8** Band-count changes resample from the user's curve, not the previous resample (dragging the slider no longer smears the curve). Added Custom EQ start/end frequency sliders.
- **T1** Import/export run off the main thread; non-text or zero-match files show an error instead of creating an empty playlist; `file://` entries decoded with `Uri.decode`; SD-card and `raw:` document ids resolve to the right folder; m3u8 MIME types added to the picker.
- **T12** Limiter indicator clears when playback stops and in bit-perfect bypass.
- **T10** Widgets' progress bars update every 2 s while playing.
- **T4** A-B cleared when a stream starts.
- **T11** No longer wakes the screen right after the user turns it off (`turnScreenOn` removed); closes on unlock; seek on drag end; subtitle states that Android blocks it when locking from another app.

Still not verified on hardware: T11 from the background, T5 with real Bluetooth/USB devices, T13 custom actions in Android Auto.
