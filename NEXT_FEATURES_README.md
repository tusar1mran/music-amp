# MusicAmp: next features (implementation guide for an AI coding agent)

Written 27 November 2026. This file tells you, the implementing agent, exactly what to build, where, in which order, and how your work will be checked. Read it fully before you change any code.

## 0. Assumptions

- You are working in this repository (`C:\Projects\music-amp`) on Windows, with the Android SDK at `%LOCALAPPDATA%\Android\Sdk` (platforms 34/35, NDK and CMake installed, emulator AVDs `p2ptest` and `ScannerPixel`, both Android 15).
- The repository has **no Gradle wrapper**. A Gradle 8.10.2 install and JDK 17 are present (see section 3).
- The previous round of work (native engine, DSP, DSD, CUE, bit-perfect, DVC, Android Auto, widget, etc.) may still be uncommitted. **Do not revert or reformat it.**
- Feature references come from Poweramp's public feature page (https://powerampapp.com/). Build original implementations; do not copy Poweramp code, assets, names or skins.

---

## 1. Ground rules (must follow)

1. **One task at a time, in the order of section 6.** Finish a task (build passes, acceptance checks pass) before starting the next.
2. **After every task, update `IMPLEMENTATION_STATUS.md`** (template in section 8). A reviewer will verify each task against its acceptance checks using that file. A task not listed there counts as not done.
3. **Never break existing features.** Section 4 lists behaviour that must keep working. Re-test the ones your task touches.
4. **No secrets.** Never hardcode API keys, tokens or passwords. If a feature needs a key (for example a cloud service), stop and record it as "Blocked: needs a decision from the owner" in the status file.
5. **Privacy.** Any feature that sends data off the device must be **off by default**, say in its settings subtitle exactly what is sent and to whom, and work offline without errors.
6. **No new third-party dependencies** unless the task explicitly allows one. If you add one, pin an exact version that is at least two weeks old and note it in the status file.
7. **Do not invent APIs.** If you are unsure an Android API or method exists at the project's `compileSdk` (35), check the SDK (`%LOCALAPPDATA%\Android\Sdk\platforms\android-35\android.jar`, for example with `javap`) before using it.
8. **Keep the style of the surrounding code**: Kotlin, Jetpack Compose, state in `PowerampController` as `mutableStateOf`, comments in plain English, Australian spelling in user-facing text (colour, organise, licence).
9. **Do not commit or push unless the repository owner tells you to.** If they allow commits, make **one commit per task** with the message `feat(T<id>): <title>`, so each task can be reviewed on its own.
10. If a task turns out to be impossible or much larger than described, stop that task, write down why in the status file, and move on to the next one.

---

## 2. Codebase map (read these before coding)

All Kotlin sources are in `app/src/main/java/com/musicamp/poweramp/`.

| File | What it does |
|---|---|
| `PowerampController.kt` | The single app controller (≈3,000 lines). All UI state (`mutableStateOf`), library scan, playback, effects, settings, persistence. Created once in `PowerampApp`. |
| `PlayerEngine.kt` | `PlayerEngine` abstract player + `MediaPlayerEngine` (Android MediaPlayer, used for streams and as fallback). Supports CUE segments (`startMs`/`endMs`). |
| `NativeEngine.kt` | `NativeEngine` (MediaCodec decode → `DspProcessor` → `AudioTrack`, own audio thread), `MediaCodecSource`, `PcmSource` interface, `OutputPlan`, `DspHolder` (shared DSP config). |
| `Dsp.kt` | `DspConfig` (immutable snapshot of all DSP settings), `Biquad`, `Reverb`, `DspProcessor` (chain order: preamp/ReplayGain → graphic EQ → parametric EQ → tone → width/mono → reverb → volume/balance → limiter), `ParametricBand`, `FilterType`, `AutoEqParser`. |
| `DsdSource.kt` | DSF/DFF reader, DSD→PCM decimation and DoP packing. |
| `TagReader.kt` | Reads ReplayGain, lyrics, cue sheets and basic text tags from FLAC, Ogg/Opus, MP3 (ID3v2), MP4 and DSF. `SeekableSource` = random-access file reader from a `Uri` or path. **Read-only today.** |
| `CueSheet.kt` | Cue sheet parser. |
| `BitPerfectOutput.kt` | Android 14+ bit-perfect USB DAC output via `AudioMixerAttributes`. |
| `AlbumArtFetcher.kt` | Optional cover download (MusicBrainz + Cover Art Archive), disk cache, 1 request/second limit. |
| `PlaybackService.kt` | `MediaBrowserService`: foreground notification, `MediaSession`, Android Auto browse tree, headset buttons, DVC volume provider, widget updates. |
| `MusicAmpWidget.kt` | 4x1 home-screen widget (`AppWidgetProvider`, RemoteViews). |
| `MainActivity.kt` | Activity, permissions, DVC volume keys, root Compose content. |
| `Models.kt` | `AudioTrack`, `PlaylistItem`, `EqPreset`, enums (`LibraryCategory`, `RepeatMode`, …). |
| `Theme.kt` | `AppTheme` (mode, accent, font), colour tokens, `LookAndFeelScope`, `AppTextStyleScope`, `FontWeight.appWeight`. |
| `DialogsAndMenus.kt` | Settings screen (categories: "Look and Feel", "Audio", "Visualization", "Background", "Album Art", "Library", "Headset/Bluetooth", "Misc"), dialogs, sheets. Reusable rows: `SettingToggleRow`, `SettingSliderRow`, `SettingChipSelectorRow`. |
| `PlayerAndEqScreens.kt` | Player screen and the EQ / Volume / Reverb tabs. |
| `LibraryAndSearchScreens.kt` | Library categories, track lists, search, lyrics screen. |
| `CustomComponents.kt` | Knobs, sliders, waveform seekbar, visualizer canvas, misc components. |
| `UiParametricEq.kt`, `UiLyrics.kt`, `UiPlayerBackground.kt` | Parametric EQ editor + AutoEQ import, LRC parser/synced lyrics view, player background. |

### Patterns you must reuse

- **New setting** = a `var x by mutableStateOf(default)` in the settings section of `PowerampController`, **plus** `o.put("x", x)` in `buildStateJson()` **plus** `x = o.optXxx("x", x)` in `restoreState()`, **plus** a reset in `resetSettingsToDefault()`, **plus** a row in the right settings category in `DialogsAndMenus.kt`. A setting that only exists in the UI is a bug; that is exactly what the previous round fixed.
- **React to a setting change** from the controller: add a `snapshotFlow { … }.drop(1)` collector in `startSettingWatchers()`.
- **DSP changes**: add fields to `DspConfig`, fill them in `buildDspConfig()` (controller), implement them in `DspProcessor`. The engine picks up a new `DspHolder.config` on its next buffer; never block the audio thread.
- **Effects refresh**: call `applyEqualizerBands()` / `applyToneAndEffects()` (they coalesce into `applyAllEffectsNow()`).
- **Toasts**: `controller.showToast(title, subtitle)`.
- **Fonts in Compose**: write `FontWeight.Bold.appWeight` (not plain `FontWeight.Bold`) so the font setting applies.
- **New public screen composable**: wrap content in `LookAndFeelScope(controller) { … }` like the existing screens.

---

## 3. Build, install and test

Build (Git Bash):

```bash
cd /c/Projects/music-amp
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot"
~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle --offline -q \
  "-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot" :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. `--offline` means new dependencies cannot be downloaded; drop it only for a task that is allowed to add one.

Emulator smoke test (Git Bash; `MSYS_NO_PATHCONV=1` stops Git Bash rewriting device paths):

```bash
export MSYS_NO_PATHCONV=1
S="$LOCALAPPDATA/Android/Sdk"; A="$S/platform-tools/adb.exe"
"$S/emulator/emulator.exe" -avd p2ptest -no-window -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
"$A" wait-for-device
"$A" install -r app/build/outputs/apk/debug/app-debug.apk
"$A" shell pm grant com.musicamp.poweramp android.permission.READ_MEDIA_AUDIO
"$A" shell am start -n com.musicamp.poweramp/.MainActivity
"$A" logcat -d -b crash          # must contain nothing from com.musicamp.poweramp
```

Test media: `tools/testmedia/Gen.java` writes a 10-minute FLAC with ReplayGain, an embedded cue sheet (3 tracks) and LRC lyrics, a DSD64 `.dsf`, and a WAV:

```bash
mkdir -p /tmp/tm && "/c/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot/bin/java" tools/testmedia/Gen.java /tmp/tm
"$A" push /tmp/tm/cue_album.flac /sdcard/Music/cue_album.flac   # same for the other files
```

The WAV (20 s) and DSF (10 s) are hidden while **Settings › Library › Ignore Short Tracks** is on. DSF files only appear after granting the folder in **Settings › Library › Extra folders**. `tools/testmedia/tap.sh "<visible text>"` taps a UI element by its text (uses `uiautomator dump`).

---

## 4. Must keep working (regression list)

Re-check whichever of these your task could affect:

1. Library loads; the FLAC test file appears as three CUE tracks "Opening", "Middle", "Ending".
2. Playback through the native engine (Settings › Audio › AUDIO ENGINE STATUS shows "MusicAmp engine …"), seek, pause/resume, next/previous, crossfade, gapless.
3. CUE track end moves to the next track at the right position.
4. DSD file plays (PCM 88.2 kHz) after the folder grant.
5. DVC: with DVC on, `dumpsys audio` shows the speaker media volume at max and volume keys change the app's volume instead.
6. Graphic EQ, parametric EQ + AutoEQ import, tone, limiter, reverb, mono, balance, tempo.
7. ReplayGain, bit-perfect toggle (state text), hi-res toggle, buffer size.
8. Notification controls, widget buttons, Android Auto browse tree, headset buttons setting.
9. Settings persist across an app restart.
10. No crash in `logcat -b crash`.

---

## 5. Out of scope

Do not do these in this round: Poweramp-style 3rd-party skin APKs, the 20,500-preset AutoEq database bundle (licence and size questions), artist-image download (needs a third-party image service and owner approval), anything that needs a paid developer account.

---

## 6. Tasks

Each task has: **Goal**, **Steps**, **Acceptance checks** (what the reviewer will test). Do the phases in order. Phase 3 tasks are large; only start them after phases 1 and 2 are complete and recorded.

### Phase 1: quick wins

#### T1. Playlist import and export (m3u, m3u8, pls)
**Goal:** Users can export any playlist to a file and import playlists from files.

**Steps**
1. Add `PlaylistIo.kt` with pure functions: `writeM3u(tracks, extended = true): String` (`#EXTM3U`, `#EXTINF:<seconds>,<artist> - <title>`, then the file path), `parseM3u(text): List<String>`, `parsePls(text): List<String>` (`FileN=` entries). Handle UTF-8 (m3u8) and Windows-1252 fallback for `.m3u`, blank lines, comments, relative paths and `file://` URLs.
2. In the controller add `fun exportPlaylist(playlistId: Long, target: Uri)` (writes through `contentResolver.openOutputStream`) and `fun importPlaylist(source: Uri, displayName: String?)`.
3. Matching on import, in this order: exact file path → path relative to the playlist's folder → file name (case-insensitive) → "artist - title" from `#EXTINF`. Unmatched entries are counted and reported in the toast ("12 of 14 tracks matched").
4. UI: in the Playlists category add "Import playlist" (SAF `OpenDocument`, mime `audio/x-mpegurl`, `audio/mpegurl`, `audio/x-scpls`, `text/*`) and on each playlist's menu "Export" (SAF `CreateDocument("audio/x-mpegurl")`, default name `<playlist>.m3u8`).
5. CUE virtual tracks export as the image file path once per cue track with an `#EXTINF` title; on import they are matched by file + title.

**Acceptance checks**
- Export a playlist of 3 tracks → file contains `#EXTM3U`, 3 `#EXTINF` lines and 3 paths.
- Delete the playlist, import the file → same 3 tracks in the same order; toast shows "3 of 3".
- Importing a `.pls` with `File1=`…`File3=` works.
- A malformed file shows an error toast, no crash.

#### T2. Library sort orders and grid view
**Goal:** Every track list and category list can be sorted and shown as a list or grid.

**Steps**
1. Add `enum class SortOrder` in `Models.kt`: TITLE, ARTIST, ALBUM, ALBUM_ARTIST, YEAR, DURATION, DATE_ADDED, PLAY_COUNT, RATING, FILE_NAME, TRACK_NUMBER, LAST_PLAYED.
2. Controller: `sortOrderByCategory: SnapshotStateMap<LibraryCategory, SortOrder>`, `sortReversed` per category, `viewStyleByCategory` (LIST, LIST_COMPACT, GRID_2, GRID_3). Persist all three.
3. Apply sorting where the track list for a category is built (find where `LibraryAndSearchScreens.kt` builds its lists). Sorting must be stable and case-insensitive; Albums sorted by TRACK_NUMBER sort by disc, then track.
4. UI: a sort button in the library list header opening a sheet with the orders, a "Reverse" toggle and the view style choice. Grid uses `LazyVerticalGrid` with album art (`controller.loadArt`), title and subtitle.
5. Playback context: the list order shown must be the order used by next/previous (the controller already plays "the list you started from"; pass the sorted list).

**Acceptance checks**
- All Songs sorted by Duration shows the shortest first; Reverse flips it; setting survives restart.
- Albums in Grid 2 shows 2 columns with covers.
- Starting playback from a sorted list and pressing Next follows the sorted order.

#### T3. Write tag edits to the file
**Goal:** The Edit Tags dialog writes to the audio file, not only to app memory (today `updateTrack()` only stores edits in preferences).

**Steps**
1. Add `TagWriter.kt` with no new dependency. Required formats:
   - **FLAC**: rewrite the VORBIS_COMMENT block (replace or add TITLE, ARTIST, ALBUM, ALBUMARTIST, GENRE, DATE, COMPOSER, TRACKNUMBER). If the new block fits in the existing VORBIS_COMMENT + PADDING space, rewrite in place; otherwise rewrite the whole file to a temp file and replace.
   - **MP3**: write an ID3v2.4 tag (TIT2, TPE1, TALB, TPE2, TCON, TDRC, TCOM, TRCK) preserving other frames you can parse (keep unknown frames byte-for-byte); rewrite the file if the tag grows beyond its padding.
   - Other formats: keep the current app-only behaviour and tell the user "Saved in MusicAmp only (this format can't be written yet)".
2. Permissions: on Android 11+ (API 30+) request write access with `MediaStore.createWriteRequest(contentResolver, listOf(uri))` and launch the returned `PendingIntent` from the UI (`ActivityResultContracts.StartIntentSenderForResult`); write only after RESULT_OK. Below API 30 write through the content URI.
3. Never write to CUE virtual tracks' shared file fields per track (title of a CUE track lives in the cue sheet): for CUE tracks keep app-only edits and say so.
4. After writing, call `MediaScannerConnection.scanFile` for the path and refresh the library entry. Also clear `tagCache` for that track id.
5. Safety: write to a temp file in the same directory/`cacheDir`, verify it parses again with `TagReader`, then replace. Never leave a half-written file.

**Acceptance checks**
- Editing `plain_tone` (WAV) shows "Saved in MusicAmp only (this format can't be written yet)".
- Editing a CUE track ("Opening") keeps the edit app-only and says so.
- Add a non-CUE FLAC to the test media (extend `tools/testmedia/Gen.java` with a 40-second FLAC without a cue sheet). Editing its title and album writes them to the file: after a rescan, `adb shell content query --uri content://media/external/audio/media --projection title:album` shows the new values, and the file still plays.
- Cancelling the system permission dialog leaves the file unchanged (same size and checksum).

#### T4. A-B repeat
**Goal:** Loop a section of the current track.

**Steps**
1. Controller: `abStartMs: Long?`, `abEndMs: Long?`, `fun cycleAbRepeat()` (1st press sets A at the current position, 2nd sets B, 3rd clears). Clear on track change.
2. In the progress loop, when `abEndMs` is set and `currentPositionMs >= abEndMs`, `seekToMs(abStartMs)`. Disable crossfade triggering while A-B is active.
3. UI: an "A-B" button on the player (near repeat), state shown as A / A-B / off; markers drawn on the waveform seekbar.

**Acceptance checks**
- Set A at 0:05 and B at 0:10 → playback loops between them for at least 3 cycles; third press clears it; next track clears it.

#### T5. Per-output and per-song EQ presets
**Goal:** EQ presets follow the output device (Wired, Speaker, Bluetooth, USB) and optionally a specific Bluetooth/USB device name; a song can have its own preset, which wins.

**Steps**
1. Models: `enum class OutputKind { SPEAKER, WIRED, BLUETOOTH, USB, OTHER }`. Controller maps: `presetForOutput: Map<OutputKind, String>`, `presetForDevice: Map<String /*productName*/, String>`, `presetForTrack: Map<Long, String>` (preset names from `eqPresets`). Persist them.
2. Detect the current output on route changes (reuse `routeCallback` and `getAudioDevicesForAttributes` on API 33+, device list below). Priority: track preset → device preset → output preset → leave EQ as is.
3. Apply with `applyEqPreset()` but without a toast spam (one toast "EQ: <preset> (Bluetooth)").
4. UI: in the EQ preset sheet, an "Assign to…" action with: this output, this device (only when BT/USB connected), this song. Settings › Headset/Bluetooth shows the assignments with remove buttons.
5. The existing `EqPreset.isAssignedDevice` field can be removed or reused; keep `BUILT_IN_EQ_PRESETS` intact.

**Acceptance checks**
- Assign "Bass" to Speaker and "Flat" to a song → playing that song shows Flat, the next song shows Bass.
- Assignments survive restart.

#### T6. Album art: Wi-Fi only option
**Steps:** add `ACCESS_NETWORK_STATE` permission, a setting "Download on Wi-Fi only" (default on) in Album Art settings, and in `AlbumArtFetcher.fetch` skip the network when the active network is metered (`ConnectivityManager.isActiveNetworkMetered`).

**Acceptance checks:** setting exists, persists, and with a metered network no request is made (log or code review).

#### T7. Material You colours
**Steps:** on Android 12+ add an accent option "Material You" in Look and Feel that takes the accent from `dynamicDarkColorScheme(context).primary` / `dynamicLightColorScheme(context).primary` and updates when the wallpaper changes (recompose on configuration change). Below Android 12 the option is hidden.

**Acceptance checks:** on the API 35 emulator selecting "Material You" changes the accent colour; selection persists.

### Phase 2: medium

#### T8. Configurable graphic EQ bands (5–32)
**Goal:** Graphic EQ band count is selectable: 5, 10 (ISO), 15 (ISO), 31 (ISO), or custom N between 5 and 32 with start/end frequency.

**Steps**
1. Replace the fixed 10-band assumptions: `bandFrequencies`, `bandGainsDb` (controller), `GRAPHIC_BAND_HZ` (Dsp.kt), `uiBandHz` and `gainDbAt()` (MediaPlayer path), `EqPreset.bands`. Store frequencies as a list in state and in `DspConfig` (`graphicFreqsHz: List<Float>`).
2. Q for each band: `Q = sqrt(2^bw) / (2^bw - 1)` where `bw` is the octave spacing between bands, so the bands sum smoothly.
3. Converting presets: when the band count changes, resample existing gains by log-frequency interpolation (same idea as `gainDbAt`).
4. Built-in presets are 10-band; interpolate them to the current band count when applied.
5. UI: the EQ screen must stay usable with 31 bands (horizontal scroll or narrower sliders). Setting "EQ bands" in the EQ settings.

**Acceptance checks**
- Switch 10 → 31 bands: the curve looks the same (interpolated), sound continues, no crash; persists.
- Presets still apply. MediaPlayer engine (engine off) still maps the curve to the device equaliser.

#### T9. Parametric EQ: channel selection and Band Pass
**Steps:** add `channel: Channel { BOTH, LEFT, RIGHT }` to `ParametricBand` (persist, default BOTH; AutoEQ import keeps BOTH), add `FilterType.BAND_PASS` (RBJ constant 0 dB peak gain), and process per channel in `Biquad` (add `processLeft/processRight`). Editor UI gets L/R/L+R chips and the new type.

**Acceptance checks**
- A +12 dB peak at 1 kHz on LEFT only raises the left channel. Prove it with `tools/testmedia/DspCheck.java`: copy the `Biquad` coefficient and processing code into it, as `DsdCheck.java` does for the DSD decoder. Run it on a 1 kHz stereo sine and print each channel's level: left about +12 dB, right unchanged.
- The Band Pass filter passes its centre frequency at 0 dB and attenuates one octave away (print both levels in the same check).
- Imported AutoEQ profiles still load with every band on BOTH.

#### T10. More widget sizes
**Steps:** add 4x2 (art + title + artist + progress + 5 buttons incl. shuffle/repeat) and 2x2 (art + play/pause) providers reusing `MusicAmpWidget` logic; register them in the manifest with their own `appwidget-provider` XML; all resizable.

**Acceptance checks:** the three widgets can be added on the emulator launcher; buttons work; they update on track change.

#### T11. Full-screen lockscreen player (optional setting, default off)
**Steps:** new `LockscreenActivity` with `setShowWhenLocked(true)`/`setTurnScreenOn(true)` started when the screen turns off during playback (`ACTION_SCREEN_OFF` receiver registered while playing). Shows art, title, seekbar, prev/play/next; swipe up to dismiss (`KeyguardManager.requestDismissKeyguard` only if the user wants to unlock). Must not show when the setting is off or when nothing is playing.

**Acceptance checks:** with the setting on and music playing, locking the emulator (`adb shell input keyevent 26` twice) shows the player over the lock screen; with it off the normal lock screen shows.

#### T12. Limiter activity indicator and Audio Info dialog with fidelity/DSP score
**Steps**
1. In `DspProcessor.limit`, expose a `@Volatile var limiting: Boolean` (true when gain < 0.99 in the last buffer) and a peak meter; the engine exposes it; the controller polls it in the progress loop at about 10 Hz and holds it for at least 150 ms.
2. The LMT badge / Limit control on the EQ screen lights up while limiting.
3. Audio Info dialog (from the player menu): source format (codec, rate, bits), output (encoding, rate, engine, bit-perfect), DSP stages active, ReplayGain applied (dB), a simple **Fidelity score** (start 100; −20 if resampled by Android, −10 if bit depth reduced, −10 if 44.1k/48k family mismatch, +0 cap 100; bit-perfect = 100) and **DSP count** (number of active stages). Document the formula in code comments.

**Acceptance checks:** pushing +12 dB on a band with the limiter on lights the indicator; the Audio Info dialog shows correct values for the FLAC and DSD test files.

#### T13. Configurable notification and Android Auto buttons
**Steps:** setting with up to 6 actions chosen from Previous, Play/Pause, Next, Shuffle, Repeat, Like, Rewind 10 s, Forward 10 s. Use `Notification.Action` + `PlaybackState.CustomAction` (custom actions show in Android Auto). Keep compact view = prev/play/next.

**Acceptance checks:** selecting Shuffle + Like shows those buttons in the notification and they work.

#### T14. Multi-value tags (artist, genre, composer)
**Steps:** setting "Tag separators" (default `;` and ` feat. `; `/` and `, ` are offered but off by default because they split names such as "AC/DC" and "Earth, Wind & Fire"). Library grouping in Artists/Genres/Composers splits on the separators so a track appears under each value. Do not change the track's displayed artist text.

**Acceptance checks:** a track tagged `A; B` appears under artist A and artist B.

#### T15. Android Auto search (decision first)
Framework `MediaBrowserService` has no in-app search callback; it needs AndroidX Media (`androidx.media:media` `MediaBrowserServiceCompat.onSearch`) or Media3. This adds a dependency and a migration of `PlaybackService`. **Do not start without the owner's approval.** If approved: migrate to `MediaBrowserServiceCompat` + `MediaSessionCompat` keeping every existing behaviour (section 4 items 8), implement `onSearch`, then re-test the full regression list.

### Phase 3: large (only after phases 1–2, one at a time, each optional)

These need native code (NDK/CMake) or external decisions. For each, first write a short design note into `IMPLEMENTATION_STATUS.md` (approach, libraries with licences, risks) and stop for owner approval before coding.

- **T16. Milkdrop visualisations** via projectM (LGPL-2.1; check licence compatibility with how the app is distributed). Render in a `GLSurfaceView` fed by the existing Visualizer PCM.
- **T17. SoX resampler** (libsoxr, LGPL) in the native engine: resample to a user-chosen output rate with selectable quality; integrate as a `PcmSource` wrapper so DSP runs before output.
- **T18. USB exclusive driver** (direct USB Audio Class streaming with `UsbManager` + isochronous transfers, typically via a libusb port). Very large and device-specific; requires real USB DAC hardware to test, which the emulator cannot provide.
- **T19. Chromecast output** needs the Google Cast SDK and a registered receiver app (owner's Google Cast developer account). Blocked on owner decision.
- **T20. DSD remastering** (PCM → DSD with a delta-sigma modulator for DSD DACs). Only useful together with bit-perfect/DoP on real hardware.

---

## 7. Definition of done (per task)

- `:app:assembleDebug` passes with no new warnings in the files you touched that you could reasonably fix.
- The app starts on the emulator, the task's acceptance checks pass, and `logcat -b crash` is clean.
- The regression items your task could affect still pass.
- Every new setting is persisted, reset by "reset settings", and has a truthful subtitle.
- `IMPLEMENTATION_STATUS.md` is updated.

---

## 8. `IMPLEMENTATION_STATUS.md` template

Create this file at the repository root when you start, and update it after every task:

```markdown
# Implementation status

| Task | Status | Commit | Files changed | How it was tested | Known limits |
|---|---|---|---|---|---|
| T1 Playlist import/export | Done / Partial / Blocked / Not started | <hash or "uncommitted"> | PlaylistIo.kt, PowerampController.kt, LibraryAndSearchScreens.kt | Exported 3-track playlist, re-imported, 3 of 3 matched on emulator API 35 | .wpl not supported |

## Notes per task
### T1
- Design decisions:
- Anything the reviewer should look at first:
```

Status meanings: **Done** = all acceptance checks pass; **Partial** = some pass (list which); **Blocked** = needs an owner decision (say which); **Not started**.

---

## 9. How the work will be reviewed

A reviewer will, for each task marked Done or Partial: read the diff, build the APK, run the acceptance checks and the regression list on the emulator, and check the ground rules in section 1 (persistence of settings, privacy defaults, no secrets, no new unapproved dependencies, no unrelated changes). Claims in the status file that cannot be reproduced will be reported back as not done.
