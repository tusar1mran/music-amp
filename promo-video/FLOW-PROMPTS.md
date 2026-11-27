# MusicAmp promo: Bangla script and Google Flow (Veo) prompts

Two cuts from the same four scenes (each Veo clip is 8 s; trimmed to about 7 s in the edit, about 28 s total):
- **Landscape**: 16:9, 1920x1080 (YouTube / website)
- **Reel**: 9:16, 1080x1920 (Reels / Shorts / TikTok)

Assumptions:
- Veo speaks the Bangla line inside each clip (native audio). If the Bangla pronunciation is weak, a separate TTS voiceover gets mixed over the clips in the edit.
- On-screen text (the CTA) is added in the edit, not by Veo, because Veo mangles text.
- The real app screenshots are used as Flow "ingredients" so the phone screen looks like the actual app.

## Bangla voiceover script

| # | Scene | Bangla line (spoken) | Meaning |
|---|---|---|---|
| 1 | Hook | গান তো রোজই শোনেন… কিন্তু আসল সাউন্ডটা কখনো শুনেছেন? | You listen to music every day, but have you ever heard the real sound? |
| 2 | 10-band EQ and knobs | দশ-ব্যান্ড ইকুয়ালাইজার, রোটারি নব — প্রতিটা বিট, ঠিক আপনার মতো। | 10-band EQ, rotary knobs: every beat, exactly your way. |
| 3 | Waveform and themes | লাইভ ওয়েভফর্ম, স্পেকট্রাম, আর লাইট থেকে ওলেড ব্ল্যাক — এক ট্যাপে। | Live waveform, spectrum, and Light to OLED Black in one tap. |
| 4 | Outro CTA | মিউজিক-অ্যাম্প। পুরো অফলাইন, কোনো অ্যাড নেই। অ্যান্ড্রয়েডে একদম ফ্রি! | MusicAmp. Fully offline, no ads. Totally free on Android! |

Voice: young male Bangladeshi narrator, energetic, confident, modern tech-ad delivery, clear standard Bangla (Dhaka accent).

## Scene prompts (16:9)

### Scene 1: Hook (ingredient: logo.png)
Cinematic product commercial, 16:9. Pitch-black studio. Slow dolly push-in toward a sleek matte-black Android smartphone floating at a slight angle, screen off. A single soft neon-green (#00FF19) rim light outlines the phone; thin cyan and neon-green sound-wave light ribbons drift through the dark air and pulse once with a deep bass hit. Shallow depth of field, anamorphic lens flare, high contrast, glossy reflections on a black glass floor. Sound: low sub-bass swell, a single heartbeat-like kick, airy synth riser. Off-screen narrator, young Bangladeshi man, energetic and confident, speaking Bengali: "গান তো রোজই শোনেন… কিন্তু আসল সাউন্ডটা কখনো শুনেছেন?" No on-screen text, no subtitles.

### Scene 2: 10-band EQ and rotary knobs (ingredient: screen-eq-dark.png)
Macro product cinematography, 16:9. Camera orbits slowly from left to right around a smartphone showing a dark equalizer app screen with ten vertical sliders whose lower halves glow neon green (#00FF19), and two round metallic rotary knobs labelled Bass and Treble. A finger turns a knob; the glowing green slider bars rise and fall in rhythm. Cut-in macro: brushed-aluminium rotary knob with a glowing green indicator arc clicking into its centre detent. Pure black background, green and cyan rim light, crisp reflections. Sound: punchy modern electronic beat, soft mechanical knob clicks, haptic tick. Off-screen narrator, young Bangladeshi man, energetic, speaking Bengali: "দশ-ব্যান্ড ইকুয়ালাইজার, রোটারি নব — প্রতিটা বিট, ঠিক আপনার মতো।" No subtitles.

### Scene 3: Waveform and Light/Dark themes (ingredients: screen-player-dark.png, screen-player-light.png)
Cinematic 16:9. Smooth slow tracking shot past a smartphone on a minimal stand showing a music player screen: a white waveform seek bar and a live spectrum visualiser bouncing to the beat. Mid-shot, the whole scene transitions in one smooth wipe of light: a bright clean white studio with the phone in a crisp light theme sweeps into a pure OLED-black room where the screen turns black with neon-green (#00FF19) and cyan glow. Soft volumetric light, glossy surfaces. Sound: driving synth-pop groove, a whoosh on the light-to-dark transition. Off-screen narrator, young Bangladeshi man, energetic, speaking Bengali: "লাইভ ওয়েভফর্ম, স্পেকট্রাম, আর লাইট থেকে ওলেড ব্ল্যাক — এক ট্যাপে।" No subtitles.

### Scene 4: Brand outro CTA (ingredient: logo.png)
Cinematic 16:9 brand outro. Pure black background. The app icon (a white play triangle with a lightning-bolt waveform inside a glowing ring that fades from cyan to neon green) assembles from swirling green and cyan light particles at centre frame, then holds steady with a gentle glow pulse. Slow pull-back, subtle light rays, clean negative space below the icon for a title. Sound: uplifting synth resolve, final bass hit, short shimmer tail. Off-screen narrator, young Bangladeshi man, warm and confident, speaking Bengali: "মিউজিক-অ্যাম্প। পুরো অফলাইন, কোনো অ্যাড নেই। অ্যান্ড্রয়েডে একদম ফ্রি!" No on-screen text, no subtitles.

## Reel variant (9:16)
Same four prompts with "16:9" replaced by "vertical 9:16 mobile reel, subject centred, phone fills most of the frame", and in Scene 4 "clean negative space below the icon" kept for the CTA overlay.

## Edit (ffmpeg): `node edit-flow-promo.mjs [landscape|reel]`
- Clips in `output/clips/` (l1–l4 = 16:9, v1–v4 = 9:16). Landscape scene 1 uses Flow's free 1080p upscale; the others are 720p, upscaled with lanczos.
- Flow audio is kept. Joins are 0.5 s video and audio crossfades, and only silent heads/tails are trimmed (several clips have sound up to the last frame).
- Landscape outro was letterboxed by Flow; it is cropped and zoomed to fill.
- CTA "MusicAmp" + "Free on Android" (#00FF19) fades in over the last 3.2 s. Loudness normalised to -14 LUFS, 24 fps, H.264 CRF 18.

## Generated (24/11/2026)
- Google Flow project "MusicAmp Android ...", model Omni 1.2 Flash (Veo 3.1 Fast/Quality are listed as leaving 5 December), 24 credits per 8 s clip, 8 clips = 192 credits.
- Outputs: `output/musicamp-promo-16x9.mp4` (1920x1080, 29.9 s), `output/musicamp-reel-9x16.mp4` (1080x1920, 30.0 s).
