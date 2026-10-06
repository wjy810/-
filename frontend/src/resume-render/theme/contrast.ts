/** WCAG contrast and OKLCH lightness adjustment for user-chosen accent colours. */

export function parseHex(hex: string): [number, number, number] | null {
  const match = /^#?([0-9a-f]{6})$/i.exec(hex.trim())
  if (!match) return null
  const value = parseInt(match[1]!, 16)
  return [(value >> 16) & 255, (value >> 8) & 255, value & 255]
}

export function toHex([r, g, b]: [number, number, number]): string {
  return `#${[r, g, b].map(v => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0')).join('')}`
}

function linear(channel: number): number {
  const c = channel / 255
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}
function gamma(value: number): number {
  const v = Math.min(1, Math.max(0, value))
  return (v <= 0.0031308 ? v * 12.92 : 1.055 * v ** (1 / 2.4) - 0.055) * 255
}

export function luminance(hex: string): number {
  const rgb = parseHex(hex)
  if (!rgb) return 0
  const [r, g, b] = rgb.map(linear) as [number, number, number]
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

export function contrastRatio(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number]
  return (hi + 0.05) / (lo + 0.05)
}

type Oklch = [number, number, number]

function toOklch(hex: string): Oklch {
  const [r, g, b] = (parseHex(hex) ?? [0, 0, 0]).map(linear) as [number, number, number]
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
  const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
  return [L, Math.hypot(A, B), Math.atan2(B, A)]
}

function fromOklch([L, C, H]: Oklch): string {
  const A = C * Math.cos(H)
  const B = C * Math.sin(H)
  const l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3
  const m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3
  const s = (L - 0.0894841775 * A - 1.291485548 * B) ** 3
  return toHex([
    gamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    gamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    gamma(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s),
  ])
}

/** Darkens `color` (keeping its hue and chroma) until it reaches `ratio` against `background`. */
export function ensureContrast(color: string, background: string, ratio = 4.5): { color: string; adjusted: boolean } {
  if (!parseHex(color)) return { color: '#1f2328', adjusted: true }
  if (contrastRatio(color, background) >= ratio) return { color: color.toLowerCase(), adjusted: false }
  const [L, C, H] = toOklch(color)
  for (let next = L - 0.02; next > 0; next -= 0.02) {
    const candidate = fromOklch([next, C * Math.min(1, next / L + 0.15), H])
    if (contrastRatio(candidate, background) >= ratio) return { color: candidate, adjusted: true }
  }
  return { color: '#111111', adjusted: true }
}

/** A light tint of `color` over `paper` (for sidebars, chips, soft fills). */
export function tint(color: string, paper: string, amount: number): string {
  const a = parseHex(color) ?? [0, 0, 0]
  const b = parseHex(paper) ?? [255, 255, 255]
  return toHex([0, 1, 2].map(i => a[i]! * amount + b[i]! * (1 - amount)) as [number, number, number])
}
