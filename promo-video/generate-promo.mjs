import 'dotenv/config'
import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { GoogleGenAI } from '@google/genai'
import ffmpegPath from 'ffmpeg-static'

const API_KEY = process.env.GEMINI_API_KEY?.trim()
if (!API_KEY) {
  console.error('\n[ERROR] GEMINI_API_KEY set kora nei!')
  console.error('1. https://aistudio.google.com/apikey theke API key copy koro')
  console.error('2. C:\\Projects\\music-amp\\promo-video\\.env file e GEMINI_API_KEY=YOUR_KEY paste koro')
  console.error('3. Tarpor run koro: npm run generate\n')
  process.exit(1)
}

const ai = new GoogleGenAI({ apiKey: API_KEY })
const OUT_DIR = path.resolve('output')
fs.mkdirSync(OUT_DIR, { recursive: true })

// Natural, punchy ~22-second Bangla promotional voiceover script for MusicAmp
const BANGLA_VOICEOVER_SCRIPT = `
আপনার ফোনের মিউজিক প্লেয়ার কি গানের আসল সাউন্ড দিতে পারছে না?
পরিচয় করিয়ে দিচ্ছি মিউজিক-অ্যাম্প — অ্যান্ড্রয়েডের জন্য আল্টিমেট হাই-রেজ অডিও প্লেয়ার।
দশ-ব্যান্ড ইকুয়ালাইজার, প্রিসিশন রোটারি ন্যাবস, আর লাইভ স্পেকট্রাম ভিজ্যুয়ালাইজারের সাথে প্রতিটি বিট অনুভব করুন নতুনভাবে।
কোনো অ্যাড নেই, সম্পূর্ণ অফলাইন, আর নিজের মতো লাইট বা ডার্ক থিম।
মিউজিক-অ্যাম্প — আপনার মিউজিক, এখন স্টুডিও কোয়ালিটিতে।
`.trim()

// 3 Cinematic 16:9 Veo 3.1 scenes (no garbled text, pure cinematic audio-hardware & smartphone visuals + ambient music)
const SCENES = [
  {
    name: 'scene1_hook',
    prompt:
      'Cinematic 16:9 commercial product film, 4K, ultra-sharp lighting. Slow dramatic camera push-in on sleek matte-black audiophile headphones resting on a dark obsidian studio desk. Luminous neon-green (#00FF19) and cyan acoustic light-wave particles ripple smoothly through the dark air around a modern borderless smartphone. Deep minimal electronic ambient synth pulse in the background, no speech, no text.',
  },
  {
    name: 'scene2_eq_dsp',
    prompt:
      'Cinematic 16:9 macro product cinematography, shallow depth of field. Precision dark anodized aluminum rotary audio knobs turning smoothly with subtle glowing neon-green indicator arcs and vertical glowing emerald equalizer light bars pulsing gently in sync with a clean modern electronic beat. High-contrast luxury audio hardware aesthetic, smooth 60fps glide, no speech, no text.',
  },
  {
    name: 'scene3_themes_outro',
    prompt:
      'Cinematic 16:9 tech launch outro shot. Smooth seamless lighting transition from a crisp minimalist sunlit white studio into pure OLED black illuminated by a vibrant neon-green halo ring behind a sleek floating smartphone. Ambient glowing soundwave rings expand outward gracefully. Uplifting modern electronic synth resolve, no speech, no text.',
  },
]

const VEO_MODELS = [
  'veo-3.1-generate-001',
  'veo-3.1-fast-generate-preview',
  'veo-3.0-generate-001',
]

function writeWavFromPcm16(pcmBuffer, outPath, sampleRate = 24000, channels = 1) {
  const byteRate = sampleRate * channels * 2
  const blockAlign = channels * 2
  const header = Buffer.alloc(44)
  header.write('RIFF', 0)
  header.writeUInt32LE(36 + pcmBuffer.length, 4)
  header.write('WAVE', 8)
  header.write('fmt ', 12)
  header.writeUInt32LE(16, 16) // PCM chunk size
  header.writeUInt16LE(1, 20) // PCM format
  header.writeUInt16LE(channels, 22)
  header.writeUInt32LE(sampleRate, 24)
  header.writeUInt32LE(byteRate, 28)
  header.writeUInt16LE(blockAlign, 32)
  header.writeUInt16LE(16, 34) // bits per sample
  header.write('data', 36)
  header.writeUInt32LE(pcmBuffer.length, 40)
  fs.writeFileSync(outPath, Buffer.concat([header, pcmBuffer]))
}

async function generateBanglaVoiceover() {
  const wavPath = path.join(OUT_DIR, 'voiceover-bn.wav')
  console.log('\n[1/3] Generating natural Bangla voiceover with Gemini TTS...')

  const response = await ai.models.generateContent({
    model: 'gemini-2.5-flash-preview-tts',
    contents: [
      {
        parts: [
          {
            text: `Speak the following Bengali promotional narration in a warm, confident, modern tech-launch commercial voice at a natural engaging pace:\n\n${BANGLA_VOICEOVER_SCRIPT}`,
          },
        ],
      },
    ],
    config: {
      responseModalities: ['AUDIO'],
      speechConfig: {
        voiceConfig: {
          prebuiltVoiceConfig: { voiceName: 'Charon' },
        },
      },
    },
  })

  const part = response.candidates?.[0]?.content?.parts?.find((p) => p.inlineData?.data)
  if (!part?.inlineData?.data) {
    throw new Error('Gemini TTS did not return audio data.')
  }

  const rawBuf = Buffer.from(part.inlineData.data, 'base64')
  const mime = part.inlineData.mimeType || ''
  if (mime.includes('wav')) {
    fs.writeFileSync(wavPath, rawBuf)
  } else {
    // Gemini TTS typically returns audio/L16;codec=pcm;rate=24000
    const rateMatch = mime.match(/rate=(\d+)/)
    const sampleRate = rateMatch ? Number(rateMatch[1]) : 24000
    writeWavFromPcm16(rawBuf, wavPath, sampleRate, 1)
  }
  console.log(`  ✓ Saved Bangla voiceover: ${wavPath}`)
  return wavPath
}

async function generateVeoScene(scene, index) {
  const outPath = path.join(OUT_DIR, `${scene.name}.mp4`)
  console.log(`\n[2/3] Generating Veo Scene ${index + 1}/${SCENES.length}: ${scene.name}...`)

  let operation = null
  let usedModel = null

  for (const model of VEO_MODELS) {
    try {
      console.log(`  → Calling ${model} (16:9)...`)
      operation = await ai.models.generateVideos({
        model,
        prompt: scene.prompt,
        config: {
          aspectRatio: '16:9',
          numberOfVideos: 1,
        },
      })
      usedModel = model
      break
    } catch (err) {
      console.warn(`  ! ${model} unavailable (${err?.message || err}), trying fallback...`)
    }
  }

  if (!operation) {
    throw new Error(`All Veo models failed for ${scene.name}. Check your API key & billing/credit access.`)
  }

  while (!operation.done) {
    process.stdout.write('.')
    await new Promise((r) => setTimeout(r, 12000))
    operation = await ai.operations.getVideosOperation({ operation })
  }
  console.log(`\n  ✓ ${scene.name} generated with ${usedModel}!`)

  const videoFile = operation.response?.generatedVideos?.[0]?.video
  if (!videoFile) {
    throw new Error(`No video returned for ${scene.name}`)
  }

  // Download via SDK or direct URI with API key
  if (videoFile.uri) {
    const sep = videoFile.uri.includes('?') ? '&' : '?'
    const res = await fetch(`${videoFile.uri}${sep}key=${API_KEY}`)
    if (!res.ok) throw new Error(`Failed downloading video: HTTP ${res.status}`)
    const arrBuf = await res.arrayBuffer()
    fs.writeFileSync(outPath, Buffer.from(arrBuf))
  } else {
    await ai.files.download({ file: videoFile, downloadPath: outPath })
  }

  console.log(`  ✓ Saved ${outPath}`)
  return outPath
}

function stitchFinalVideo(scenePaths, voiceoverPath) {
  const finalPath = path.join(OUT_DIR, 'musicamp-promo-bangla-16x9.mp4')
  const concatListPath = path.join(OUT_DIR, 'concat.txt')
  const logoPath = path.resolve('..', 'landing', 'public', 'images', 'logo.png')

  console.log('\n[3/3] Stitching scenes + Bangla voiceover + MusicAmp logo with FFmpeg...')

  // Normalize all 3 clips to 1920x1080 30fps with consistent audio stream before concat
  const normPaths = scenePaths.map((p, i) => {
    const np = path.join(OUT_DIR, `norm_${i}.mp4`)
    execFileSync(
      ffmpegPath,
      [
        '-y',
        '-i',
        p,
        '-vf',
        'scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,fps=30,format=yuv420p',
        '-c:v',
        'libx264',
        '-preset',
        'medium',
        '-crf',
        '18',
        '-c:a',
        'aac',
        '-ar',
        '48000',
        '-ac',
        '2',
        np,
      ],
      { stdio: 'pipe' }
    )
    return np
  })

  fs.writeFileSync(
    concatListPath,
    normPaths.map((p) => `file '${p.replace(/\\/g, '/')}'`).join('\n')
  )

  const mergedVideoPath = path.join(OUT_DIR, 'merged_visuals.mp4')
  execFileSync(
    ffmpegPath,
    ['-y', '-f', 'concat', '-safe', '0', '-i', concatListPath, '-c', 'copy', mergedVideoPath],
    { stdio: 'pipe' }
  )

  // Mix subtle Veo ambient music (22% volume) + crisp Bangla voiceover (100% volume) + corner MusicAmp logo badge
  const hasLogo = fs.existsSync(logoPath)
  const args = hasLogo
    ? [
        '-y',
        '-i',
        mergedVideoPath,
        '-i',
        voiceoverPath,
        '-i',
        logoPath,
        '-filter_complex',
        '[2:v]scale=84:84[logo];[0:v][logo]overlay=44:40[vout];[0:a]volume=0.22[bg];[1:a]adelay=800|800,volume=1.15[vo];[bg][vo]amix=inputs=2:duration=longest:dropout_transition=2[aout]',
        '-map',
        '[vout]',
        '-map',
        '[aout]',
        '-c:v',
        'libx264',
        '-preset',
        'medium',
        '-crf',
        '18',
        '-c:a',
        'aac',
        '-b:a',
        '192k',
        '-shortest',
        finalPath,
      ]
    : [
        '-y',
        '-i',
        mergedVideoPath,
        '-i',
        voiceoverPath,
        '-filter_complex',
        '[0:a]volume=0.22[bg];[1:a]adelay=800|800,volume=1.15[vo];[bg][vo]amix=inputs=2:duration=longest:dropout_transition=2[aout]',
        '-map',
        '0:v',
        '-map',
        '[aout]',
        '-c:v',
        'copy',
        '-c:a',
        'aac',
        '-b:a',
        '192k',
        '-shortest',
        finalPath,
      ]

  execFileSync(ffmpegPath, args, { stdio: 'pipe' })
  console.log(`\n🎉 DONE! Final 16:9 Bangla Promo Video saved to:\n   ${finalPath}\n`)
}

async function main() {
  const voiceoverPath = await generateBanglaVoiceover()
  const scenePaths = []
  for (let i = 0; i < SCENES.length; i++) {
    scenePaths.push(await generateVeoScene(SCENES[i], i))
  }
  stitchFinalVideo(scenePaths, voiceoverPath)
}

main().catch((err) => {
  console.error('\n[FAILED]', err?.message || err)
  process.exit(1)
})
