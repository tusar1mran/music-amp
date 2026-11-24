import React, { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import {
  Check,
  Plus,
  Minus,
  Download,
  Sliders,
  Disc3,
  Palette,
  Waves,
  Headphones,
  Sparkles,
  Sun,
  Moon,
  ChevronRight,
  Pause,
  Play,
  Music,
  ShieldCheck,
  ArrowUpRight,
  Smartphone,
} from 'lucide-react'
import {
  ACCENT_COLORS,
  THEME_MODES,
  getResolvedAccent,
  type AccentId,
  type ThemeModeId,
} from './theme/appThemes'
import { PhoneMockup } from './components/PhoneMockup'
import {
  InteractiveEqWidget,
  InteractiveKnobWidget,
} from './components/InteractiveWidgets'

gsap.registerPlugin(ScrollTrigger)

const WALKTHROUGH_STEPS = [
  {
    id: 'player',
    label: 'Live FFT Spectrum & Waveform',
    caption: 'Long-press cover art for real-time spectrum bars and waveform seek.',
  },
  {
    id: 'eq',
    label: '10-Band EQ & Rotary Knobs',
    caption: 'Shape every band with preamp, limiter, bass & treble dials.',
  },
  {
    id: 'library',
    label: 'Instant Local Library',
    caption: 'Browse songs, folders, albums, artists, genres, and playlists.',
  },
  {
    id: 'settings',
    label: 'Look & Feel Customization',
    caption: 'Switch Poweramp, Dark, OLED, or Light mode & animations.',
  },
] as const

const FAQ_GROUPS = [
  {
    id: 'getting-started',
    num: '01',
    title: 'Getting started',
    icon: Sparkles,
    items: [
      {
        q: 'What is MusicAmp?',
        a: 'MusicAmp is an offline-first Android music player inspired by Poweramp, built with Kotlin, Jetpack Compose, and Material 3. It pairs your local library with a 10-band equalizer, rotary DSP knobs, waveform seekbar, and live FFT visualizer.',
      },
      {
        q: 'Is MusicAmp free? Are there any ads?',
        a: 'Yes, MusicAmp is completely free with zero ads, zero trackers, and no account required. Install the APK, grant audio access, and start listening right away.',
      },
      {
        q: 'Which Android devices are supported?',
        a: 'MusicAmp supports Android 8.0 (API 26) and up, with adaptive high refresh rate (60/90/120Hz+) UI tuned for modern Android phones.',
      },
    ],
  },
  {
    id: 'audio-dsp',
    num: '02',
    title: 'Equalizer & DSP',
    icon: Sliders,
    items: [
      {
        q: 'How does the 10-band equalizer and limiter work?',
        a: 'All 10 EQ bands, bass/treble shelves, preamp (LoudnessEnhancer), stereo expand (Virtualizer), tempo (0.5x–3x with pitch kept), and room reverb run on a single shared Android AudioFX session across every track. The limiter automatically prevents clipping when you boost bands.',
      },
      {
        q: 'What is special about the rotary knobs?',
        a: 'Each rotary knob picks one gesture mode per touch: outer ring turns like a physical dial, center drags vertically. Balance and 0 dB points include a magnetic center detent with a subtle haptic tick.',
      },
      {
        q: 'Does the live FFT spectrum record my microphone?',
        a: 'Never. The optional microphone permission is used solely by Android’s Visualizer API to read MusicAmp’s own playback session. Without it, MusicAmp still renders a smooth synthetic spectrum.',
      },
    ],
  },
  {
    id: 'themes-playback',
    num: '03',
    title: 'Themes & playback',
    icon: Palette,
    items: [
      {
        q: 'Which themes and accent colors are included?',
        a: 'MusicAmp includes 5 theme modes (Poweramp default, Light, Dark, Black OLED, and Follow System) plus 8 accent colors: Poweramp Green, Berry, Blue, Green, Orange, Teal, Purple, and Red.',
      },
      {
        q: 'Does it remember my queue, EQ, and playback position?',
        a: 'Yes. MusicAmp saves your last track and exact position, EQ presets, DSP knobs, reverb, playlists, bookmarks, ratings, play counts, and queue between runs.',
      },
      {
        q: 'Does it work with Bluetooth, lock-screen, and calls?',
        a: 'Yes. Playback runs in a foreground MediaSession service with lock-screen controls, headset/Bluetooth media buttons, automatic pause on unplug, and audio focus ducking for calls and notifications.',
      },
    ],
  },
]

export default function App() {
  // Default to Light mode (just like loafloafloaf.com's clean airy entry) with instant toggle to Poweramp / Dark / OLED!
  const [themeId, setThemeId] = useState<ThemeModeId>('light')
  const [accentId, setAccentId] = useState<AccentId>('poweramp-green')
  const [paletteOpen, setPaletteOpen] = useState(false)

  // Loaf-style interactive walkthrough carousel state
  const [walkIdx, setWalkIdx] = useState(0)
  const [walkPlaying, setWalkPlaying] = useState(true)

  // FAQ state
  const [openFaq, setOpenFaq] = useState<string | null>(FAQ_GROUPS[0].items[0].q)

  const rootRef = useRef<HTMLDivElement>(null)
  const progressRef = useRef<HTMLDivElement>(null)

  const theme = THEME_MODES[themeId]
  const accent = ACCENT_COLORS.find((a) => a.id === accentId) ?? ACCENT_COLORS[0]
  const resolved = getResolvedAccent(theme, accent)

  // Sync CSS custom properties with MusicAmp's exact Theme.kt palette
  useEffect(() => {
    const r = document.documentElement
    r.style.setProperty('--ma-bg', theme.bg)
    r.style.setProperty('--ma-bg-alt', theme.bgAlt)
    r.style.setProperty('--ma-surface', theme.surface)
    r.style.setProperty('--ma-pill', theme.pillBg)
    r.style.setProperty('--ma-dialog', theme.dialogBg)
    r.style.setProperty('--ma-border', theme.border)
    r.style.setProperty('--ma-border-subtle', theme.borderSubtle)
    r.style.setProperty('--ma-text', theme.textPrimary)
    r.style.setProperty('--ma-text-muted', theme.textSecondary)
    r.style.setProperty('--ma-wave-unplayed', theme.waveUnplayed)

    r.style.setProperty('--ma-accent', resolved.primary)
    r.style.setProperty('--ma-accent-dark', resolved.dark)
    r.style.setProperty('--ma-accent-light', resolved.light)
    r.style.setProperty('--ma-on-accent', resolved.onAccent)
    r.style.setProperty('--ma-highlight', resolved.highlight)
    r.style.setProperty('--ma-glow', resolved.glow)

    // Soft Loaf-style organic blob & note band tints derived from app theme + accent
    const blob1 = theme.isDark ? `${resolved.primary}22` : `${resolved.primary}1a`
    const blob2 = theme.isDark ? `${resolved.light}14` : `${resolved.dark}12`
    const noteBand = theme.isDark ? `${resolved.primary}12` : `${resolved.primary}10`
    r.style.setProperty('--ma-blob-1', blob1)
    r.style.setProperty('--ma-blob-2', blob2)
    r.style.setProperty('--ma-note-band', noteBand)
  }, [theme, resolved])

  // Auto-advance Loaf-style step carousel
  useEffect(() => {
    if (!walkPlaying) return
    const timer = window.setInterval(() => {
      setWalkIdx((prev) => (prev + 1) % WALKTHROUGH_STEPS.length)
    }, 3600)
    return () => window.clearInterval(timer)
  }, [walkPlaying])

  // GSAP entrance + ScrollTrigger + floating blobs/doodles choreography
  useEffect(() => {
    const ctx = gsap.context(() => {
      // Scroll progress bar
      if (progressRef.current) {
        gsap.to(progressRef.current, {
          scaleX: 1,
          ease: 'none',
          scrollTrigger: {
            trigger: document.body,
            start: 'top top',
            end: 'bottom bottom',
            scrub: 0.25,
          },
        })
      }

      // Hero staggered reveal
      gsap.from('.gsap-hero', {
        y: 26,
        opacity: 0,
        duration: 0.85,
        stagger: 0.08,
        ease: 'power3.out',
        clearProps: 'transform',
      })

      gsap.from('.gsap-hero-phone', {
        y: 38,
        opacity: 0,
        scale: 0.96,
        duration: 1.0,
        delay: 0.15,
        ease: 'power3.out',
        clearProps: 'transform',
      })

      // Gentle ambient float on Loaf-style doodles
      gsap.to('.gsap-float-slow', {
        y: -10,
        rotation: 4,
        duration: 3.2,
        repeat: -1,
        yoyo: true,
        ease: 'sine.inOut',
        stagger: 0.4,
      })

      // Scroll-triggered reveals
      gsap.utils.toArray<HTMLElement>('.gsap-reveal').forEach((el) => {
        gsap.from(el, {
          y: 32,
          opacity: 0,
          duration: 0.8,
          ease: 'power3.out',
          clearProps: 'transform',
          scrollTrigger: {
            trigger: el,
            start: 'top 85%',
            once: true,
          },
        })
      })

      // Subtle parallax on organic blobs
      gsap.utils.toArray<HTMLElement>('.gsap-blob').forEach((blob, i) => {
        gsap.fromTo(
          blob,
          { y: i % 2 === 0 ? 22 : -18 },
          {
            y: i % 2 === 0 ? -22 : 18,
            ease: 'none',
            scrollTrigger: {
              trigger: blob.parentElement,
              start: 'top bottom',
              end: 'bottom top',
              scrub: 0.8,
            },
          }
        )
      })
    }, rootRef)

    return () => ctx.revert()
  }, [])

  // Real emulator screenshots (clean spectrum player + EQ + Library + Light/Dark Settings)
  const screens = {
    player: '/images/screen-player-light.png', // Clean spectrum + waveform, no toast overlay
    eq: '/images/screen-eq-light.png', // Live neon green EQ + spectrum bar
    library: '/images/screen-library-dark.png',
    settings: theme.isDark
      ? '/images/screen-settings-dark.png'
      : '/images/screen-settings-light.png',
  }

  const activeWalk = WALKTHROUGH_STEPS[walkIdx]
  const toggleLightDark = () =>
    setThemeId((prev) => (THEME_MODES[prev].isDark ? 'light' : 'poweramp'))

  return (
    <div
      ref={rootRef}
      className="min-h-screen bg-[var(--ma-bg)] text-[var(--ma-text)] selection:bg-[var(--ma-accent)] selection:text-[var(--ma-on-accent)]"
    >
      {/* GSAP Scroll Progress Line */}
      <div
        ref={progressRef}
        className="fixed left-0 top-0 z-50 h-[2.5px] w-full origin-left scale-x-0 bg-[var(--ma-accent)]"
      />

      {/* ==================== LOAF-STYLE STICKY NAVBAR ==================== */}
      <header className="sticky top-0 z-40 border-b border-[var(--ma-border-subtle)] bg-[var(--ma-bg)]/88 backdrop-blur-md">
        <div className="mx-auto flex h-[70px] max-w-[1120px] items-center justify-between px-5 sm:px-8">
          {/* Left: App Icon + Wordmark */}
          <a href="#" className="flex items-center gap-2.5 group">
            <img
              src="/images/logo.png"
              alt="MusicAmp App Icon"
              className="h-9 w-9 rounded-[11px] object-cover shadow-sm ring-1 ring-black/10 transition-transform duration-300 group-hover:scale-105"
            />
            <span className="font-display text-[19px] font-extrabold tracking-tight">
              Music<span className="text-[var(--ma-accent)]">Amp</span>
            </span>
          </a>

          {/* Center: Clean minimal links (just like Loaf's Features / Guides / Support) */}
          <nav className="hidden items-center gap-8 text-sm font-medium text-[var(--ma-text-muted)] md:flex">
            <a href="#dsp" className="hover:text-[var(--ma-text)] transition-colors">
              Features
            </a>
            <a href="#walkthrough" className="hover:text-[var(--ma-text)] transition-colors">
              Screens
            </a>
            <a href="#themes" className="hover:text-[var(--ma-text)] transition-colors">
              Themes
            </a>
            <a href="#faq" className="hover:text-[var(--ma-text)] transition-colors">
              FAQ
            </a>
          </nav>

          {/* Right: Theme Switcher + Loaf-style Store/APK Download Badge */}
          <div className="flex items-center gap-2">
            {/* Quick Light / Dark (Poweramp) toggle */}
            <button
              type="button"
              onClick={toggleLightDark}
              aria-label="Toggle Light and Dark Theme"
              title={theme.isDark ? 'Switch to Light Mode' : 'Switch to Poweramp Dark Mode'}
              className="flex h-9 w-9 items-center justify-center rounded-xl border border-[var(--ma-border)] bg-[var(--ma-surface)] text-[var(--ma-text)] transition-colors hover:border-[var(--ma-accent)]"
            >
              {theme.isDark ? (
                <Sun className="h-4 w-4 text-[var(--ma-accent)]" />
              ) : (
                <Moon className="h-4 w-4" />
              )}
            </button>

            {/* App Theme & Accent Popover */}
            <div className="relative">
              <button
                type="button"
                onClick={() => setPaletteOpen((o) => !o)}
                className="flex h-9 items-center gap-1.5 rounded-xl border border-[var(--ma-border)] bg-[var(--ma-surface)] px-2.5 text-xs font-semibold text-[var(--ma-text)] transition-colors hover:border-[var(--ma-accent)]"
              >
                <span
                  className="h-3 w-3 rounded-full ring-1 ring-black/15"
                  style={{ backgroundColor: resolved.primary }}
                />
                <span className="hidden sm:inline">{theme.shortLabel}</span>
                <Palette className="h-3.5 w-3.5 text-[var(--ma-text-muted)]" />
              </button>

              <AnimatePresence>
                {paletteOpen && (
                  <motion.div
                    initial={{ opacity: 0, y: 8, scale: 0.96 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: 6, scale: 0.96 }}
                    transition={{ duration: 0.18 }}
                    className="absolute right-0 mt-2 w-64 rounded-2xl border border-[var(--ma-border)] bg-[var(--ma-dialog)] p-4 shadow-2xl"
                  >
                    <p className="mb-2 text-[11px] font-bold uppercase tracking-wider text-[var(--ma-text-muted)]">
                      App Theme (Theme.kt)
                    </p>
                    <div className="mb-4 grid grid-cols-2 gap-1.5">
                      {(Object.keys(THEME_MODES) as ThemeModeId[]).map((id) => {
                        const m = THEME_MODES[id]
                        const active = themeId === id
                        return (
                          <button
                            key={id}
                            type="button"
                            onClick={() => setThemeId(id)}
                            className={`rounded-xl border px-2.5 py-1.5 text-left text-xs font-semibold transition-all ${
                              active
                                ? 'border-[var(--ma-accent)] bg-[var(--ma-pill)] text-[var(--ma-text)]'
                                : 'border-[var(--ma-border-subtle)] bg-transparent text-[var(--ma-text-muted)] hover:text-[var(--ma-text)]'
                            }`}
                          >
                            {m.label}
                          </button>
                        )
                      })}
                    </div>

                    <p className="mb-2 text-[11px] font-bold uppercase tracking-wider text-[var(--ma-text-muted)]">
                      Accent Color
                    </p>
                    <div className="grid grid-cols-4 gap-2">
                      {ACCENT_COLORS.map((c) => {
                        const active = c.id === accentId
                        return (
                          <button
                            key={c.id}
                            type="button"
                            title={c.name}
                            onClick={() => setAccentId(c.id)}
                            className="flex flex-col items-center justify-center rounded-lg p-1 hover:bg-[var(--ma-pill)]"
                          >
                            <span
                              className={`flex h-6 w-6 items-center justify-center rounded-full transition-transform ${
                                active
                                  ? 'scale-110 ring-2 ring-[var(--ma-text)] ring-offset-2 ring-offset-[var(--ma-dialog)]'
                                  : ''
                              }`}
                              style={{ backgroundColor: c.main }}
                            >
                              {active && (
                                <Check
                                  className="h-3.5 w-3.5"
                                  style={{ color: c.onAccent }}
                                />
                              )}
                            </span>
                          </button>
                        )
                      })}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* Loaf-style dark Android APK badge button */}
            <a
              href="/app-debug.apk"
              download="MusicAmp.apk"
              className="hidden sm:inline-flex items-center gap-2 rounded-xl bg-[var(--ma-text)] px-3.5 py-1.5 text-[var(--ma-bg)] transition-transform hover:scale-[1.02] active:scale-[0.98]"
            >
              <Smartphone className="h-4 w-4" />
              <div className="flex flex-col leading-none">
                <span className="text-[9px] font-medium opacity-75">Download for</span>
                <span className="text-xs font-bold tracking-tight">Android APK</span>
              </div>
            </a>
          </div>
        </div>
      </header>

      {/* ==================== LOAF-STYLE HERO SECTION ==================== */}
      <section className="relative overflow-hidden py-14 sm:py-20 lg:py-24">
        <div className="mx-auto grid max-w-[1080px] grid-cols-1 items-center gap-12 px-5 sm:px-8 lg:grid-cols-12">
          {/* Left Copy Column */}
          <div className="lg:col-span-6">
            {/* Soft outlined pill badge (Loaf signature!) */}
            <div className="gsap-hero inline-flex items-center gap-2 rounded-full border border-[var(--ma-border)] bg-[var(--ma-highlight)] px-4 py-1.5 text-xs font-semibold text-[var(--ma-text)]">
              <span className="h-2 w-2 rounded-full bg-[var(--ma-accent)]" />
              Free Hi-Res music player for Android
            </div>

            {/* Big Two-Tone Headline (Loaf signature: dark line 1, accent line 2) */}
            <h1 className="gsap-hero mt-6 font-display text-[44px] font-extrabold leading-[1.05] tracking-tight sm:text-[56px]">
              Your music’s soul,
              <br />
              <span className="text-[var(--ma-accent)]">amplified.</span>
            </h1>

            <p className="gsap-hero mt-5 max-w-[440px] text-lg leading-relaxed text-[var(--ma-text-muted)]">
              10-band equalizer, tactile rotary DSP knobs, waveform seekbar, and
              live FFT spectrum — built for your local tracks.{' '}
              <span className="inline-block text-[var(--ma-accent)]">✦</span>
            </p>

            {/* Prominent Download Button (Loaf App Store badge aesthetic) */}
            <div className="gsap-hero mt-8 flex flex-wrap items-center gap-4">
              <motion.a
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
                href="/app-debug.apk"
                download="MusicAmp.apk"
                className="inline-flex items-center gap-3 rounded-2xl bg-[var(--ma-text)] px-5 py-3 text-[var(--ma-bg)] shadow-lg"
              >
                <Download className="h-5 w-5" />
                <div className="flex flex-col text-left leading-tight">
                  <span className="text-[10px] font-medium opacity-75">
                    Direct APK · build-1013
                  </span>
                  <span className="text-sm font-bold">Download MusicAmp</span>
                </div>
              </motion.a>

              <a
                href="#themes"
                className="inline-flex items-center gap-1.5 rounded-2xl border border-[var(--ma-border)] bg-[var(--ma-surface)] px-4 py-3.5 text-sm font-semibold text-[var(--ma-text)] transition-colors hover:border-[var(--ma-accent)]"
              >
                <Palette className="h-4 w-4 text-[var(--ma-accent)]" />
                Try App Themes
              </a>
            </div>

            <p className="gsap-hero mt-4 text-xs font-medium text-[var(--ma-text-muted)]">
              Free offline playback &amp; 10-band DSP. Zero ads.
            </p>

            <a
              href="#dsp"
              className="gsap-hero mt-4 inline-flex items-center gap-1 text-sm font-semibold text-[var(--ma-accent)] hover:underline underline-offset-4"
            >
              Switching from a stock player? Try the live EQ below
              <ChevronRight className="h-4 w-4" />
            </a>
          </div>

          {/* Right Phone Column with Loaf-style Organic Blobs & Floating Doodles */}
          <div className="relative flex items-center justify-center lg:col-span-6">
            {/* Organic Soft Tinted Blobs behind phone */}
            <div
              className="gsap-blob pointer-events-none absolute -top-4 h-[390px] w-[330px] rounded-[58%_42%_48%_52%/46%_54%_46%_54%] transition-colors duration-500"
              style={{ backgroundColor: 'var(--ma-blob-1)' }}
            />
            <div
              className="gsap-blob pointer-events-none absolute -bottom-6 -left-2 h-[220px] w-[220px] rounded-full transition-colors duration-500 sm:left-8"
              style={{ backgroundColor: 'var(--ma-blob-2)' }}
            />

            {/* Floating minimal line-art audio doodles (echoing Loaf's moon, bottle, sparkles, heart) */}
            <div className="gsap-float-slow pointer-events-none absolute left-4 top-8 text-[var(--ma-accent)] opacity-80 sm:left-10">
              <Music className="h-6 w-6 stroke-[1.75]" />
            </div>
            <div className="gsap-float-slow pointer-events-none absolute right-4 top-16 flex flex-col items-end gap-1 text-[var(--ma-accent)] opacity-80 sm:right-10">
              <span className="text-lg font-bold leading-none">+</span>
              <span className="text-xs font-bold leading-none opacity-60">+</span>
            </div>
            <div className="gsap-float-slow pointer-events-none absolute bottom-12 right-6 text-[var(--ma-accent)] opacity-75 sm:right-12">
              <Headphones className="h-6 w-6 stroke-[1.75]" />
            </div>
            <div className="gsap-float-slow pointer-events-none absolute bottom-16 left-2 text-[var(--ma-text-muted)] opacity-60 sm:left-10">
              <Waves className="h-6 w-6 stroke-[1.75]" />
            </div>

            {/* Real Emulator Screenshot inside Phone */}
            <div className="gsap-hero-phone relative z-10 w-[265px] sm:w-[295px]">
              <PhoneMockup
                src={screens.player}
                alt="MusicAmp Now Playing with Live FFT Spectrum"
              />
            </div>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-[1040px] border-t border-[var(--ma-border-subtle)]" />

      {/* ==================== SECTION 1: TEXT LEFT + SOFT INTERACTIVE EQ CARD RIGHT (Loaf "Today" style) ==================== */}
      <section id="dsp" className="py-20 sm:py-28">
        <div className="mx-auto grid max-w-[1080px] grid-cols-1 items-center gap-12 px-5 sm:px-8 lg:grid-cols-12">
          <div className="gsap-reveal lg:col-span-5">
            <h2 className="font-display text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-[42px]">
              Every frequency,
              <br />
              <span className="text-[var(--ma-accent)]">shaped your way.</span>
            </h2>
            <p className="mt-4 text-base leading-relaxed text-[var(--ma-text-muted)]">
              Drag any of the 10 graphic bands or tap a preset right here. Inside
              MusicAmp, every change maps directly onto a shared Android AudioFX
              session with zero stutter.
            </p>
            <a
              href="#walkthrough"
              className="mt-5 inline-flex items-center gap-1 text-sm font-semibold text-[var(--ma-text)] hover:text-[var(--ma-accent)] transition-colors"
            >
              See the full Equalizer screen
              <ChevronRight className="h-4 w-4" />
            </a>
          </div>

          <div className="gsap-reveal lg:col-span-7">
            <div className="rounded-[28px] border border-[var(--ma-border)] bg-[var(--ma-surface)] p-4 sm:p-6 shadow-sm">
              <InteractiveEqWidget />
            </div>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-[1040px] border-t border-[var(--ma-border-subtle)]" />

      {/* ==================== SECTION 2: PHONE + BLOBS LEFT, TEXT RIGHT (Loaf "Patterns" style) ==================== */}
      <section className="overflow-hidden py-20 sm:py-28">
        <div className="mx-auto grid max-w-[1080px] grid-cols-1 items-center gap-12 px-5 sm:px-8 lg:grid-cols-12">
          <div className="gsap-reveal relative order-2 flex items-center justify-center lg:order-1 lg:col-span-6">
            <div
              className="gsap-blob pointer-events-none absolute h-[380px] w-[320px] rounded-[46%_54%_58%_42%/52%_44%_56%_48%] transition-colors duration-500"
              style={{ backgroundColor: 'var(--ma-blob-1)' }}
            />
            <div
              className="gsap-blob pointer-events-none absolute -right-2 bottom-8 h-[180px] w-[180px] rounded-full transition-colors duration-500 sm:right-10"
              style={{ backgroundColor: 'var(--ma-blob-2)' }}
            />

            {/* Bold '+' accent marks (Loaf signature) */}
            <div className="pointer-events-none absolute right-6 top-1/2 -translate-y-8 text-4xl font-light text-[var(--ma-accent)] opacity-75 sm:right-12">
              +
            </div>
            <div className="pointer-events-none absolute right-2 top-1/2 translate-y-12 text-2xl font-bold text-[var(--ma-accent)] opacity-60 sm:right-6">
              +
            </div>

            <div className="relative z-10 w-[260px] sm:w-[290px]">
              <PhoneMockup
                src={screens.eq}
                alt="MusicAmp 10-Band Equalizer and Rotary Knobs"
              />
            </div>
          </div>

          <div className="gsap-reveal order-1 lg:order-2 lg:col-span-6 lg:pl-6">
            <h2 className="font-display text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-[42px]">
              When stock EQs clip,
              <br />
              <span className="text-[var(--ma-accent)]">MusicAmp stays pure.</span>
            </h2>
            <p className="mt-4 max-w-md text-base leading-relaxed text-[var(--ma-text-muted)]">
              Boost sub-bass, lift vocals, widen the stereo field, or dial in
              hall reverb without harsh distortion. The built-in Limiter
              automatically reins in peaks so your headphones always sound clean.
            </p>

            {/* Interactive Rotary Knobs Mini-Playground */}
            <div className="mt-7 max-w-md">
              <InteractiveKnobWidget />
            </div>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-[1040px] border-t border-[var(--ma-border-subtle)]" />

      {/* ==================== SECTION 3: TEXT LEFT, AUTO-STEP PHONE CAROUSEL RIGHT (Loaf signature!) ==================== */}
      <section id="walkthrough" className="overflow-hidden py-20 sm:py-28">
        <div className="mx-auto grid max-w-[1080px] grid-cols-1 items-center gap-12 px-5 sm:px-8 lg:grid-cols-12">
          <div className="gsap-reveal lg:col-span-5">
            <h2 className="font-display text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-[42px]">
              Effortless control.
              <br />
              Even in the dark.
            </h2>
            <p className="mt-4 text-base leading-relaxed text-[var(--ma-text-muted)]">
              Every screen in MusicAmp is crafted for one-handed speed — from
              outer-ring rotary dials and magnetic 0 dB detents to instant
              MediaStore folder browsing and live cover spectrums.
            </p>

            {/* Quick step jump links (echoing Loaf's two inline guide links) */}
            <div className="mt-6 flex flex-wrap gap-x-6 gap-y-2 text-sm font-semibold">
              <button
                type="button"
                onClick={() => {
                  setWalkIdx(0)
                  setWalkPlaying(false)
                }}
                className="inline-flex items-center gap-1 text-[var(--ma-text)] hover:text-[var(--ma-accent)] transition-colors"
              >
                Player &amp; Spectrum <ChevronRight className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                onClick={() => {
                  setWalkIdx(1)
                  setWalkPlaying(false)
                }}
                className="inline-flex items-center gap-1 text-[var(--ma-text)] hover:text-[var(--ma-accent)] transition-colors"
              >
                Equalizer &amp; DSP <ChevronRight className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>

          {/* Right: Phone + Loaf's Signature Segmented Step Bar & Play/Pause */}
          <div className="gsap-reveal flex flex-col items-center lg:col-span-7">
            <div className="relative flex items-center justify-center">
              <div
                className="gsap-blob pointer-events-none absolute h-[370px] w-[310px] rounded-[55%_45%_45%_55%/50%_52%_48%_50%] transition-colors duration-500"
                style={{ backgroundColor: 'var(--ma-blob-2)' }}
              />
              <div
                className="gsap-blob pointer-events-none absolute -bottom-4 -right-4 h-[190px] w-[190px] rounded-full transition-colors duration-500"
                style={{ backgroundColor: 'var(--ma-blob-1)' }}
              />

              <div className="relative z-10 w-[255px] sm:w-[285px]">
                <PhoneMockup
                  src={screens[activeWalk.id]}
                  alt={activeWalk.label}
                />
              </div>
            </div>

            {/* Loaf-style segmented progress bar + Pause/Play circle + numbered label */}
            <div className="mt-8 w-full max-w-[330px]">
              <div className="flex items-center gap-2">
                <div className="grid flex-1 grid-cols-4 gap-1.5">
                  {WALKTHROUGH_STEPS.map((st, idx) => {
                    const active = idx === walkIdx
                    return (
                      <button
                        key={st.id}
                        type="button"
                        aria-label={st.label}
                        onClick={() => {
                          setWalkIdx(idx)
                          setWalkPlaying(false)
                        }}
                        className="h-1.5 overflow-hidden rounded-full bg-[var(--ma-pill)]"
                      >
                        <motion.div
                          className="h-full rounded-full bg-[var(--ma-accent)]"
                          initial={false}
                          animate={{
                            width: active ? '100%' : idx < walkIdx ? '100%' : '0%',
                            opacity: active ? 1 : idx < walkIdx ? 0.35 : 0,
                          }}
                          transition={{ duration: active && walkPlaying ? 3.4 : 0.3 }}
                        />
                      </button>
                    )
                  })}
                </div>
                <button
                  type="button"
                  onClick={() => setWalkPlaying((p) => !p)}
                  aria-label={walkPlaying ? 'Pause walkthrough' : 'Play walkthrough'}
                  className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-[var(--ma-border)] bg-[var(--ma-surface)] text-[var(--ma-text-muted)] hover:border-[var(--ma-accent)] hover:text-[var(--ma-text)] transition-colors"
                >
                  {walkPlaying ? (
                    <Pause className="h-3 w-3" />
                  ) : (
                    <Play className="h-3 w-3 ml-0.5" />
                  )}
                </button>
              </div>

              <div className="mt-3 flex items-center gap-2.5">
                <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[var(--ma-accent)] font-mono-tech text-xs font-bold text-[var(--ma-on-accent)]">
                  {walkIdx + 1}
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-bold text-[var(--ma-text)]">
                    {activeWalk.label}
                  </p>
                  <p className="text-xs text-[var(--ma-text-muted)]">
                    {activeWalk.caption}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-[1040px] border-t border-[var(--ma-border-subtle)]" />

      {/* ==================== SECTION 4: PHONE LEFT, THEMES + NUMBERED STEPS RIGHT (Loaf "Import" style) ==================== */}
      <section id="themes" className="overflow-hidden py-20 sm:py-28">
        <div className="mx-auto grid max-w-[1080px] grid-cols-1 items-center gap-12 px-5 sm:px-8 lg:grid-cols-12">
          {/* Left: Real Look & Feel Screenshot (switches Light/Dark with the page!) */}
          <div className="gsap-reveal relative order-2 flex items-center justify-center lg:order-1 lg:col-span-6">
            <div
              className="gsap-blob pointer-events-none absolute h-[380px] w-[320px] rounded-[50%_50%_46%_54%/55%_45%_55%_45%] transition-colors duration-500"
              style={{ backgroundColor: 'var(--ma-blob-1)' }}
            />
            <div
              className="gsap-blob pointer-events-none absolute -bottom-6 left-2 h-[180px] w-[180px] rounded-full transition-colors duration-500 sm:left-10"
              style={{ backgroundColor: 'var(--ma-blob-2)' }}
            />

            <div className="relative z-10 w-[260px] sm:w-[290px]">
              <PhoneMockup
                src={screens.settings}
                alt={`MusicAmp Look and Feel (${theme.label} Mode)`}
                caption={`Live preview: ${theme.label} · ${accent.name}`}
              />
            </div>
          </div>

          {/* Right: Copy + Live Theme Switcher + 3 Numbered Steps */}
          <div className="gsap-reveal order-1 lg:order-2 lg:col-span-6 lg:pl-6">
            <h2 className="font-display text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-[42px]">
              Make it look
              <br />
              <span className="text-[var(--ma-accent)]">like your sound.</span>
            </h2>
            <p className="mt-4 max-w-md text-base leading-relaxed text-[var(--ma-text-muted)]">
              Pick from MusicAmp’s real built-in skins — daylight Light, charcoal
              Dark, battery-saving Black (OLED), or signature Poweramp — and pair
              them with 8 vibrant accents from{' '}
              <code className="font-mono-tech text-xs text-[var(--ma-accent)]">
                Theme.kt
              </code>
              .
            </p>

            {/* Interactive Theme Mode Selector */}
            <div className="mt-6 flex flex-wrap gap-2">
              {(Object.keys(THEME_MODES) as ThemeModeId[]).map((id) => {
                const m = THEME_MODES[id]
                const active = themeId === id
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setThemeId(id)}
                    className={`inline-flex items-center gap-2 rounded-full border px-4 py-2 text-xs font-bold transition-all ${
                      active
                        ? 'border-[var(--ma-accent)] bg-[var(--ma-accent)] text-[var(--ma-on-accent)] shadow-sm'
                        : 'border-[var(--ma-border)] bg-[var(--ma-surface)] text-[var(--ma-text-muted)] hover:text-[var(--ma-text)]'
                    }`}
                  >
                    <span
                      className="h-2.5 w-2.5 rounded-full border border-black/20"
                      style={{ backgroundColor: m.bg }}
                    />
                    {m.label}
                  </button>
                )
              })}
            </div>

            {/* 8 Accent Swatches */}
            <div className="mt-4 flex flex-wrap gap-2">
              {ACCENT_COLORS.map((c) => {
                const active = c.id === accentId
                return (
                  <button
                    key={c.id}
                    type="button"
                    onClick={() => setAccentId(c.id)}
                    className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold transition-all ${
                      active
                        ? 'border-[var(--ma-accent)] bg-[var(--ma-surface)] text-[var(--ma-text)]'
                        : 'border-[var(--ma-border-subtle)] bg-transparent text-[var(--ma-text-muted)] hover:border-[var(--ma-border)] hover:text-[var(--ma-text)]'
                    }`}
                  >
                    <span
                      className="h-3 w-3 rounded-full"
                      style={{ backgroundColor: c.main }}
                    />
                    {c.name.replace('Poweramp ', '')}
                  </button>
                )
              })}
            </div>

            {/* Loaf-style 3 numbered steps */}
            <ol className="mt-7 space-y-3 text-sm font-medium text-[var(--ma-text)]">
              {[
                'Open Settings → Look and Feel inside MusicAmp',
                'Choose Poweramp, Light, Dark, Black (OLED), or Follow System',
                'Pick any of the 8 accent colors — every screen recomposes instantly',
              ].map((line, idx) => (
                <li key={line} className="flex items-center gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[var(--ma-accent)] font-mono-tech text-xs font-bold text-[var(--ma-on-accent)]">
                    {idx + 1}
                  </span>
                  <span>{line}</span>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-[1040px] border-t border-[var(--ma-border-subtle)]" />

      {/* ==================== 2x2 MINIMAL FEATURE GRID (Loaf "What matters when you actually use it") ==================== */}
      <section className="py-20 sm:py-24">
        <div className="mx-auto max-w-[1000px] px-5 sm:px-8">
          <h2 className="gsap-reveal font-display text-3xl font-extrabold tracking-tight sm:text-[38px]">
            What matters when you actually listen
          </h2>

          <div className="mt-10 grid grid-cols-1 gap-x-12 gap-y-2 md:grid-cols-2">
            {[
              {
                icon: ShieldCheck,
                title: '100% offline, zero ads.',
                desc: 'No account walls, no streaming clutter, and no tracking. Your local FLAC, MP3, and WAV files stay completely yours.',
              },
              {
                icon: Waves,
                title: 'Cached waveform & live FFT',
                desc: 'Decoded audio waveforms cache to disk for instant scrubbing, plus a live FFT spectrum overlay right over album art.',
              },
              {
                icon: Headphones,
                title: 'Built for everyday listening',
                desc: 'Foreground MediaSession service with lock-screen controls, Bluetooth buttons, call ducking, and auto-pause on unplug.',
              },
              {
                icon: Disc3,
                title: 'Remembers every detail',
                desc: 'Exact track position, EQ presets, DSP knobs, reverb, queue, bookmarks, sleep timer, and playlists persist between runs.',
              },
            ].map((item) => (
              <div
                key={item.title}
                className="gsap-reveal flex items-start gap-4 border-t border-[var(--ma-border-subtle)] py-7"
              >
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-[var(--ma-highlight)] text-[var(--ma-accent)]">
                  <item.icon className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="font-display text-base font-bold text-[var(--ma-text)]">
                    {item.title}
                  </h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-[var(--ma-text-muted)]">
                    {item.desc}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ==================== FULL-BLEED TINTED "NOTE FROM THE BUILDER" (Loaf's Most Iconic Section!) ==================== */}
      <section
        className="py-20 sm:py-28 transition-colors duration-500"
        style={{ backgroundColor: 'var(--ma-note-band)' }}
      >
        <div className="mx-auto max-w-[680px] px-5 sm:px-8">
          <h2 className="gsap-reveal text-center font-display text-3xl font-extrabold tracking-tight text-[var(--ma-accent)] sm:text-[42px]">
            A note from
            <br />
            the builder
          </h2>

          <motion.div
            whileHover={{ y: -3 }}
            transition={{ type: 'spring', stiffness: 260, damping: 24 }}
            className="gsap-reveal mt-10 rounded-[28px] border border-[var(--ma-border)] bg-[var(--ma-dialog)] p-8 shadow-xl sm:p-12"
          >
            <div className="font-editorial space-y-5 text-[18px] leading-[1.7] text-[var(--ma-text)] sm:text-[19px]">
              <p>
                I built MusicAmp because modern music apps forgot how good it
                feels to actually own your music library — and have real tactile
                control over how it sounds.
              </p>
              <p>
                Inspired by classic Poweramp, every gesture in MusicAmp is
                obsessed over: rotary knobs that track your finger with zero
                lag, a magnetic center detent with a subtle haptic tick at 0 dB,
                and a 10-band AudioFX engine that never clips.
              </p>
              <p>
                Whether you prefer pure OLED black with neon green at 2 a.m. or
                a clean daylight Light theme on your commute, MusicAmp is built
                to stay fast, offline, and completely free of ads.
              </p>
              <p>
                I hope it makes rediscovering your favorite albums feel special
                again.
              </p>
            </div>

            <div className="mt-8">
              <div className="font-signature text-4xl font-bold text-[var(--ma-accent)]">
                MusicAmp,
              </div>
              <div className="mt-0.5 text-xs font-medium text-[var(--ma-text-muted)]">
                built with Kotlin &amp; Jetpack Compose
              </div>
            </div>
          </motion.div>
        </div>
      </section>

      {/* ==================== STICKY LEFT / GROUPED FAQ RIGHT (Loaf Signature!) ==================== */}
      <section id="faq" className="py-20 sm:py-28">
        <div className="mx-auto grid max-w-[1080px] grid-cols-1 gap-12 px-5 sm:px-8 lg:grid-cols-12">
          {/* Left Sticky Column */}
          <div className="lg:col-span-4">
            <div className="lg:sticky lg:top-24">
              <span className="inline-block rounded-full bg-[var(--ma-highlight)] px-3.5 py-1 text-xs font-semibold text-[var(--ma-accent)]">
                Good to know
              </span>
              <h2 className="mt-4 font-display text-4xl font-extrabold leading-[1.06] tracking-tight sm:text-[44px]">
                Your
                <br />
                questions,
                <br />
                answered.
              </h2>
              <p className="mt-4 text-sm leading-relaxed text-[var(--ma-text-muted)]">
                Straight answers about MusicAmp’s 10-band equalizer, offline
                library, themes, and Android support.
              </p>

              {/* Numbered section jump list */}
              <div className="mt-7 divide-y divide-[var(--ma-border-subtle)] border-y border-[var(--ma-border-subtle)]">
                {FAQ_GROUPS.map((g) => (
                  <a
                    key={g.id}
                    href={`#faq-${g.id}`}
                    className="flex items-center justify-between py-3 text-xs font-semibold text-[var(--ma-text)] hover:text-[var(--ma-accent)] transition-colors"
                  >
                    <span>
                      <span className="mr-2.5 font-mono-tech text-[var(--ma-text-muted)]">
                        {g.num}
                      </span>
                      {g.title}
                    </span>
                    <ArrowUpRight className="h-3.5 w-3.5 text-[var(--ma-text-muted)]" />
                  </a>
                ))}
              </div>
            </div>
          </div>

          {/* Right Grouped Accordion Cards */}
          <div className="space-y-6 lg:col-span-8">
            {FAQ_GROUPS.map((group) => (
              <div
                key={group.id}
                id={`faq-${group.id}`}
                className="gsap-reveal rounded-[24px] border border-[var(--ma-border)] bg-[var(--ma-surface)] p-6 sm:p-7"
              >
                <div className="mb-3 flex items-center gap-2.5">
                  <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-[var(--ma-highlight)] text-[var(--ma-accent)]">
                    <group.icon className="h-4 w-4" />
                  </span>
                  <h3 className="font-display text-base font-bold">
                    {group.title}
                  </h3>
                </div>

                <div className="divide-y divide-[var(--ma-border-subtle)]">
                  {group.items.map((item) => {
                    const isOpen = openFaq === item.q
                    return (
                      <div key={item.q} className="py-4 first:pt-2 last:pb-1">
                        <button
                          type="button"
                          onClick={() => setOpenFaq(isOpen ? null : item.q)}
                          className="flex w-full items-center justify-between gap-4 text-left"
                        >
                          <span className="text-sm font-semibold text-[var(--ma-text)] sm:text-[15px]">
                            {item.q}
                          </span>
                          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[var(--ma-highlight)] text-[var(--ma-accent)]">
                            {isOpen ? (
                              <Minus className="h-3.5 w-3.5" />
                            ) : (
                              <Plus className="h-3.5 w-3.5" />
                            )}
                          </span>
                        </button>
                        <AnimatePresence initial={false}>
                          {isOpen && (
                            <motion.div
                              initial={{ height: 0, opacity: 0 }}
                              animate={{ height: 'auto', opacity: 1 }}
                              exit={{ height: 0, opacity: 0 }}
                              transition={{ duration: 0.24, ease: 'easeOut' }}
                              className="overflow-hidden"
                            >
                              <p className="pt-3 pr-8 text-sm leading-relaxed text-[var(--ma-text-muted)]">
                                {item.a}
                              </p>
                            </motion.div>
                          )}
                        </AnimatePresence>
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ==================== CLEAN FOOTER CTA ==================== */}
      <footer className="border-t border-[var(--ma-border-subtle)] bg-[var(--ma-surface)] py-16">
        <div className="mx-auto max-w-[1080px] px-5 sm:px-8">
          <div className="flex flex-col items-center justify-between gap-8 rounded-[28px] border border-[var(--ma-border)] bg-[var(--ma-bg)] p-8 text-center sm:flex-row sm:p-10 sm:text-left">
            <div className="flex flex-col items-center gap-4 sm:flex-row">
              <img
                src="/images/logo.png"
                alt="MusicAmp Logo"
                className="h-14 w-14 rounded-2xl shadow-md"
              />
              <div>
                <h3 className="font-display text-2xl font-extrabold tracking-tight">
                  Ready to hear your library properly?
                </h3>
                <p className="mt-1 text-sm text-[var(--ma-text-muted)]">
                  Free Android APK · 10-band EQ · 5 themes · Zero ads.
                </p>
              </div>
            </div>

            <motion.a
              whileHover={{ scale: 1.03 }}
              whileTap={{ scale: 0.97 }}
              href="/app-debug.apk"
              download="MusicAmp.apk"
              className="inline-flex shrink-0 items-center gap-2.5 rounded-2xl bg-[var(--ma-accent)] px-6 py-3.5 text-sm font-bold text-[var(--ma-on-accent)] shadow-lg"
            >
              <Download className="h-4 w-4" />
              Download MusicAmp APK
            </motion.a>
          </div>

          <div className="mt-10 flex flex-col items-center justify-between gap-4 text-xs text-[var(--ma-text-muted)] sm:flex-row">
            <div className="flex items-center gap-2">
              <img
                src="/images/logo-round.png"
                alt=""
                className="h-5 w-5 rounded-full"
              />
              <span className="font-display font-bold text-[var(--ma-text)]">
                MusicAmp
              </span>
              <span>· Built with Kotlin, Jetpack Compose &amp; Android AudioFX</span>
            </div>
            <div className="flex items-center gap-6">
              <a href="#dsp" className="hover:text-[var(--ma-text)]">
                Features
              </a>
              <a href="#walkthrough" className="hover:text-[var(--ma-text)]">
                Screens
              </a>
              <a href="#themes" className="hover:text-[var(--ma-text)]">
                Themes
              </a>
              <a href="#faq" className="hover:text-[var(--ma-text)]">
                FAQ
              </a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
