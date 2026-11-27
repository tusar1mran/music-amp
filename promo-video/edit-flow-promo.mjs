// Stitches the Google Flow clips in output/clips into the final promo videos.
//   node edit-flow-promo.mjs            -> both cuts
//   node edit-flow-promo.mjs landscape  -> 16:9 only
//   node edit-flow-promo.mjs reel       -> 9:16 only
// Clips keep their own Flow audio (Bangla narration + sound design); joins are 0.5 s
// video/audio crossfades, the CTA is drawn over the outro, audio is normalised to -14 LUFS.
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import ffmpegPath from 'ffmpeg-static'

const CLIPS = path.resolve('output', 'clips')
const OUT = path.resolve('output')
const XFADE = 0.5
const FONT = 'C\\:/Windows/Fonts/segoeuib.ttf'
const GREEN = '0x00FF19'

const CUTS = {
  landscape: {
    out: 'musicamp-promo-16x9.mp4',
    w: 1920,
    h: 1080,
    // [file, start, end] in seconds; trims only silent head/tail parts
    clips: [
      ['l1_hook_1080p.mp4', 0.25, 7.93],
      ['l2_eq.mp4', 0, 8.04],
      ['l3_themes.mp4', 0, 8.04],
      // Flow letterboxed this clip (content 1280x552 at y=84); crop the bars, then fill the frame
      ['l4_outro.mp4', 0, 7.6, 'crop=1280:552:0:84'],
    ],
    title: { size: 92, y: 'h*0.735' },
    sub: { size: 50, y: 'h*0.835' },
  },
  reel: {
    out: 'musicamp-reel-9x16.mp4',
    w: 1080,
    h: 1920,
    clips: [
      ['v1_hook.mp4', 0, 8.04],
      ['v2_eq.mp4', 0, 8.04],
      ['v3_themes.mp4', 0, 8.04],
      ['v4_outro.mp4', 0, 7.3],
    ],
    title: { size: 120, y: 'h*0.70' },
    sub: { size: 64, y: 'h*0.775' },
  },
}

function build(name) {
  const c = CUTS[name]
  const lens = c.clips.map(([, s, e]) => e - s)
  const total = lens.reduce((a, b) => a + b, 0) - XFADE * (lens.length - 1)
  const ctaStart = total - 3.2

  const inputs = c.clips.flatMap(([f]) => ['-i', path.join(CLIPS, f)])
  const parts = []

  c.clips.forEach(([, s, e, crop], i) => {
    parts.push(
      `[${i}:v]trim=${s}:${e},setpts=PTS-STARTPTS,${crop ? crop + ',' : ''}scale=${c.w}:${c.h}:flags=lanczos:force_original_aspect_ratio=increase,crop=${c.w}:${c.h},setsar=1,fps=24,format=yuv420p[v${i}]`,
      `[${i}:a]atrim=${s}:${e},asetpts=PTS-STARTPTS,aresample=48000[a${i}]`
    )
  })

  let vPrev = 'v0'
  let aPrev = 'a0'
  let offset = 0
  for (let i = 1; i < c.clips.length; i++) {
    offset += lens[i - 1] - XFADE
    const vOut = i === c.clips.length - 1 ? 'vcat' : `vx${i}`
    const aOut = i === c.clips.length - 1 ? 'acat' : `ax${i}`
    parts.push(`[${vPrev}][v${i}]xfade=transition=fade:duration=${XFADE}:offset=${offset.toFixed(3)}[${vOut}]`)
    parts.push(`[${aPrev}][a${i}]acrossfade=d=${XFADE}:c1=tri:c2=tri[${aOut}]`)
    vPrev = vOut
    aPrev = aOut
  }

  const alpha = `if(lt(t\\,${ctaStart.toFixed(2)})\\,0\\,min(1\\,(t-${ctaStart.toFixed(2)})/0.5))`
  parts.push(
    `[vcat]drawtext=fontfile='${FONT}':text='MusicAmp':fontsize=${c.title.size}:fontcolor=white:` +
      `x=(w-text_w)/2:y=${c.title.y}:alpha='${alpha}':shadowcolor=black@0.6:shadowx=0:shadowy=4,` +
      `drawtext=fontfile='${FONT}':text='Free on Android':fontsize=${c.sub.size}:fontcolor=${GREEN}:` +
      `x=(w-text_w)/2:y=${c.sub.y}:alpha='${alpha}':shadowcolor=black@0.6:shadowx=0:shadowy=3,` +
      `fade=t=in:st=0:d=0.4,fade=t=out:st=${(total - 0.4).toFixed(2)}:d=0.4[vout]`,
    `[acat]loudnorm=I=-14:TP=-1:LRA=11,aresample=48000,afade=t=in:st=0:d=0.2,afade=t=out:st=${(total - 0.5).toFixed(2)}:d=0.5[aout]`
  )

  const outPath = path.join(OUT, c.out)
  console.log(`[${name}] ${c.w}x${c.h}, ${total.toFixed(2)} s -> ${outPath}`)
  execFileSync(
    ffmpegPath,
    [
      '-y', '-hide_banner', '-loglevel', 'error',
      ...inputs,
      '-filter_complex', parts.join(';'),
      '-map', '[vout]', '-map', '[aout]',
      '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-profile:v', 'high', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '192k',
      '-movflags', '+faststart',
      outPath,
    ],
    { stdio: 'inherit' }
  )
}

const which = process.argv[2]
for (const name of which ? [which] : Object.keys(CUTS)) build(name)
