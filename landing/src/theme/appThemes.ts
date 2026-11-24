export type ThemeModeId = 'poweramp' | 'dark' | 'oled' | 'light'

export type AccentId =
  | 'poweramp-green'
  | 'berry'
  | 'blue'
  | 'green'
  | 'orange'
  | 'teal'
  | 'purple'
  | 'red'

export interface ThemeModeSpec {
  id: ThemeModeId
  label: string
  shortLabel: string
  isDark: boolean
  bg: string
  bgAlt: string
  surface: string
  pillBg: string
  dialogBg: string
  border: string
  borderSubtle: string
  textPrimary: string
  textSecondary: string
  waveUnplayed: string
}

export interface AccentSpec {
  id: AccentId
  name: string
  dark: string
  main: string
  light: string
  onAccent: string
}

/** Exact color tokens from app/src/main/java/com/musicamp/poweramp/Theme.kt */
export const THEME_MODES: Record<ThemeModeId, ThemeModeSpec> = {
  poweramp: {
    id: 'poweramp',
    label: 'Poweramp',
    shortLabel: 'Poweramp',
    isDark: true,
    bg: '#000000',
    bgAlt: '#09090B',
    surface: '#141416',
    pillBg: '#1A1B1E',
    dialogBg: '#212226',
    border: '#323338',
    borderSubtle: '#222327',
    textPrimary: '#FFFFFF',
    textSecondary: '#9EA0A5',
    waveUnplayed: '#3A3C42',
  },
  dark: {
    id: 'dark',
    label: 'Dark',
    shortLabel: 'Dark',
    isDark: true,
    bg: '#131416',
    bgAlt: '#17181B',
    surface: '#1C1D21',
    pillBg: '#26272C',
    dialogBg: '#202125',
    border: '#34363C',
    borderSubtle: '#27292E',
    textPrimary: '#F2F3F5',
    textSecondary: '#9DA1AB',
    waveUnplayed: '#3A3C42',
  },
  oled: {
    id: 'oled',
    label: 'Black (OLED)',
    shortLabel: 'OLED',
    isDark: true,
    bg: '#000000',
    bgAlt: '#050506',
    surface: '#0E0E10',
    pillBg: '#17181B',
    dialogBg: '#111113',
    border: '#2B2C30',
    borderSubtle: '#1B1C20',
    textPrimary: '#FFFFFF',
    textSecondary: '#9EA0A5',
    waveUnplayed: '#34363C',
  },
  light: {
    id: 'light',
    label: 'Light',
    shortLabel: 'Light',
    isDark: false,
    bg: '#FFFFFF',
    bgAlt: '#F7F8FA',
    surface: '#F7F8FA',
    pillBg: '#F0F2F6',
    dialogBg: '#FFFFFF',
    border: '#E2E4E9',
    borderSubtle: '#ECEEF2',
    textPrimary: '#16171A',
    textSecondary: '#656A75',
    waveUnplayed: '#C5CAD4',
  },
}

export const ACCENT_COLORS: AccentSpec[] = [
  {
    id: 'poweramp-green',
    name: 'Poweramp Green',
    dark: '#00B812',
    main: '#00FF19',
    light: '#6BFF7B',
    onAccent: '#000000',
  },
  {
    id: 'berry',
    name: 'Berry',
    dark: '#7A0B42',
    main: '#9C1554',
    light: '#B81B6C',
    onAccent: '#FFFFFF',
  },
  {
    id: 'blue',
    name: 'Blue',
    dark: '#0D3B8E',
    main: '#1565C0',
    light: '#3B86E8',
    onAccent: '#FFFFFF',
  },
  {
    id: 'green',
    name: 'Green',
    dark: '#0B5D2A',
    main: '#1B8A3F',
    light: '#34B85E',
    onAccent: '#FFFFFF',
  },
  {
    id: 'orange',
    name: 'Orange',
    dark: '#A33A00',
    main: '#D9540B',
    light: '#F58030',
    onAccent: '#FFFFFF',
  },
  {
    id: 'teal',
    name: 'Teal',
    dark: '#00575A',
    main: '#00838F',
    light: '#1CB3C0',
    onAccent: '#FFFFFF',
  },
  {
    id: 'purple',
    name: 'Purple',
    dark: '#4A1A8C',
    main: '#6A2BC0',
    light: '#8D55E6',
    onAccent: '#FFFFFF',
  },
  {
    id: 'red',
    name: 'Red',
    dark: '#8E1010',
    main: '#C62828',
    light: '#E85050',
    onAccent: '#FFFFFF',
  },
]

export function getResolvedAccent(mode: ThemeModeSpec, accent: AccentSpec) {
  // Mirrors Theme.kt: if (AppTheme.isDark && accent != POWERAMP_GREEN) light else main
  // On light mode, Poweramp Green (#00FF19) is very bright for text/buttons, so use dark shade (#00B812) for crisp contrast while keeping neon highlight
  const primary =
    mode.isDark && accent.id !== 'poweramp-green'
      ? accent.light
      : !mode.isDark && accent.id === 'poweramp-green'
        ? '#00A814'
        : accent.main

  const onAccent =
    accent.id === 'poweramp-green' && mode.isDark ? '#000000' : '#FFFFFF'

  const highlight = mode.isDark
    ? `${primary}33`
    : accent.id === 'poweramp-green'
      ? '#6BFF7B70'
      : `${accent.light}40`

  return {
    primary,
    dark: accent.dark,
    light: accent.light,
    onAccent,
    highlight,
    glow: mode.isDark ? `${primary}45` : `${primary}25`,
  }
}
