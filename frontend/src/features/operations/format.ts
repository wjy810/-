/** 1,284 · 1.3万 · 12万: compact enough for a tile, exact below ten thousand. */
export function compact(value: number): string {
  if (Math.abs(value) < 10_000) return value.toLocaleString('zh-CN')
  const wan = value / 10_000
  return `${wan >= 100 ? Math.round(wan) : Number(wan.toFixed(1))}万`
}

/** "10-19" from "2026-10-19"; the axis shows month and day only. */
export function shortDay(day: string): string {
  return day.length >= 10 ? day.slice(5) : day
}

/** A clean top for a column axis: 1, 2, 5 × 10ⁿ at or above the largest value (never 0). */
export function niceMax(values: number[]): number {
  const max = Math.max(0, ...values)
  if (max <= 4) return 4
  const power = 10 ** Math.floor(Math.log10(max))
  const step = [1, 2, 5, 10].map(n => n * power).find(n => n >= max) ?? 10 * power
  return step
}

export function sum(values: number[]): number {
  return values.reduce((total, value) => total + value, 0)
}
