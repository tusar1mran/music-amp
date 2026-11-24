import React, { useState } from 'react'
import { motion } from 'framer-motion'
import { RotateCcw } from 'lucide-react'

const BANDS = ['31', '62', '125', '250', '500', '1K', '2K', '4K', '8K', '16K']

const PRESETS: Record<string, number[]> = {
  Bass: [8, 7, 5, 2, 0, -1, 1, 2, 3, 4],
  Vocal: [-2, -1, 0, 3, 5, 5, 4, 2, 0, -1],
  Rock: [5, 4, 2, -1, -2, 0, 3, 5, 6, 5],
  Flat: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
}

export const InteractiveEqWidget: React.FC = () => {
  const [preset, setPreset] = useState<string>('Bass')
  const [values, setValues] = useState<number[]>(PRESETS.Bass)

  const applyPreset = (name: string) => {
    setPreset(name)
    setValues(PRESETS[name])
  }

  const handleSlider = (idx: number, val: number) => {
    setPreset('Custom')
    setValues((prev) => prev.map((v, i) => (i === idx ? val : v)))
  }

  return (
    <div className="flex h-full flex-col justify-between rounded-2xl border border-[var(--ma-border)] bg-[var(--ma-bg)] p-4 sm:p-5">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full bg-[var(--ma-accent)] shadow-[0_0_10px_var(--ma-accent)]" />
          <span className="font-mono-tech text-[11px] font-semibold uppercase tracking-wider text-[var(--ma-text-muted)]">
            10-Band Graphic EQ · Live Preview
          </span>
        </div>
        <div className="flex items-center gap-1">
          {Object.keys(PRESETS).map((name) => {
            const active = preset === name
            return (
              <button
                key={name}
                type="button"
                onClick={() => applyPreset(name)}
                className="relative rounded-full px-2.5 py-1 text-[11px] font-semibold transition-colors"
                style={{
                  color: active ? 'var(--ma-on-accent)' : 'var(--ma-text-muted)',
                }}
              >
                {active && (
                  <motion.span
                    layoutId="eqPresetPill"
                    transition={{ type: 'spring', stiffness: 420, damping: 32 }}
                    className="absolute inset-0 rounded-full bg-[var(--ma-accent)]"
                  />
                )}
                <span className="relative z-10">{name}</span>
              </button>
            )
          })}
        </div>
      </div>

      {/* 10 EQ sliders */}
      <div className="relative grid grid-cols-10 gap-1.5 sm:gap-2.5 py-2">
        {/* 0 dB hairline */}
        <div className="pointer-events-none absolute inset-x-0 top-1/2 h-px -translate-y-2 bg-[var(--ma-border)] opacity-70" />
        {BANDS.map((band, i) => {
          const val = values[i]
          const pct = ((val + 12) / 24) * 100
          return (
            <div key={band} className="flex flex-col items-center gap-2">
              <div className="relative flex h-28 w-full items-center justify-center">
                {/* Track */}
                <div className="relative h-full w-1.5 overflow-hidden rounded-full bg-[var(--ma-pill)]">
                  <motion.div
                    className="absolute bottom-0 left-0 right-0 rounded-full bg-[var(--ma-accent)]"
                    animate={{ height: `${pct}%` }}
                    transition={{ type: 'spring', stiffness: 320, damping: 28 }}
                    style={{
                      boxShadow: '0 0 12px var(--ma-glow)',
                    }}
                  />
                </div>
                {/* Knob dot */}
                <motion.div
                  className="pointer-events-none absolute left-1/2 h-3.5 w-3.5 -translate-x-1/2 rounded-full border-2 border-[var(--ma-bg)] bg-[var(--ma-accent)] shadow-sm"
                  animate={{ bottom: `calc(${pct}% - 7px)` }}
                  transition={{ type: 'spring', stiffness: 320, damping: 28 }}
                />
                {/* Invisible range input for real interaction */}
                <input
                  type="range"
                  min={-12}
                  max={12}
                  step={1}
                  value={val}
                  aria-label={`${band} Hz band`}
                  onChange={(e) => handleSlider(i, Number(e.target.value))}
                  className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
                  style={{ writingMode: 'vertical-lr', direction: 'rtl' }}
                />
              </div>
              <span className="font-mono-tech text-[10px] font-medium text-[var(--ma-text-muted)]">
                {band}
              </span>
            </div>
          )
        })}
      </div>

      <div className="mt-2 flex items-center justify-between text-[11px] text-[var(--ma-text-muted)]">
        <span>Drag any band or tap a preset</span>
        <button
          type="button"
          onClick={() => applyPreset('Flat')}
          className="inline-flex items-center gap-1 font-medium text-[var(--ma-text)] hover:text-[var(--ma-accent)] transition-colors"
        >
          <RotateCcw className="h-3 w-3" /> Reset 0 dB
        </button>
      </div>
    </div>
  )
}

export const InteractiveKnobWidget: React.FC = () => {
  const [bass, setBass] = useState(64)
  const [treble, setTreble] = useState(48)
  const [stereo, setStereo] = useState(72)

  const knobs = [
    { label: 'BASS', value: bass, set: setBass },
    { label: 'TREBLE', value: treble, set: setTreble },
    { label: 'STEREO', value: stereo, set: setStereo },
  ]

  return (
    <div className="flex h-full flex-col justify-between rounded-2xl border border-[var(--ma-border)] bg-[var(--ma-bg)] p-5">
      <div className="flex items-center justify-between">
        <span className="font-mono-tech text-[11px] font-semibold uppercase tracking-wider text-[var(--ma-text-muted)]">
          Rotary DSP Dials
        </span>
        <span className="rounded-full bg-[var(--ma-pill)] px-2.5 py-0.5 font-mono-tech text-[10px] font-semibold text-[var(--ma-accent)]">
          Haptic Detent
        </span>
      </div>

      <div className="my-5 grid grid-cols-3 gap-3">
        {knobs.map((k) => {
          const angle = -135 + (k.value / 100) * 270
          const dashOffset = 157 - (k.value / 100) * 118
          return (
            <div key={k.label} className="flex flex-col items-center">
              <button
                type="button"
                onClick={() => k.set((v) => (v >= 90 ? 15 : v + 18))}
                title="Click to turn dial"
                className="group relative flex h-20 w-20 items-center justify-center rounded-full focus:outline-none"
              >
                <svg className="h-20 w-20 -rotate-[225deg]" viewBox="0 0 64 64">
                  <circle
                    cx="32"
                    cy="32"
                    r="25"
                    fill="none"
                    stroke="var(--ma-pill)"
                    strokeWidth="4"
                    strokeDasharray="118 157"
                    strokeLinecap="round"
                  />
                  <motion.circle
                    cx="32"
                    cy="32"
                    r="25"
                    fill="none"
                    stroke="var(--ma-accent)"
                    strokeWidth="4"
                    strokeDasharray="157"
                    animate={{ strokeDashoffset: dashOffset }}
                    transition={{ type: 'spring', stiffness: 260, damping: 24 }}
                    strokeLinecap="round"
                  />
                </svg>
                <motion.div
                  animate={{ rotate: angle }}
                  transition={{ type: 'spring', stiffness: 260, damping: 24 }}
                  className="absolute flex h-12 w-12 items-start justify-center rounded-full border border-[var(--ma-border)] bg-[var(--ma-surface)] p-1.5 shadow-inner group-hover:border-[var(--ma-accent)] transition-colors"
                >
                  <span className="h-2 w-1 rounded-full bg-[var(--ma-accent)]" />
                </motion.div>
              </button>
              <span className="mt-1.5 font-mono-tech text-[10px] font-bold tracking-wider text-[var(--ma-text)]">
                {k.label}
              </span>
              <span className="font-mono-tech text-[10px] text-[var(--ma-text-muted)]">
                {k.value}%
              </span>
            </div>
          )
        })}
      </div>

      <p className="text-center text-[11px] text-[var(--ma-text-muted)]">
        Tap any dial to feel the smooth spring glide
      </p>
    </div>
  )
}
