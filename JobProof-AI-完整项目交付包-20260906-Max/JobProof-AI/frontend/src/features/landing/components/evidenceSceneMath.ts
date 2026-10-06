export type SceneQuality = 'standard' | 'high' | 'cinematic'
export const sceneQuality = {
  standard: { dpr: 1.25, shadowSize: 512, postprocessing: false },
  high: { dpr: 1.5, shadowSize: 1024, postprocessing: false },
  cinematic: { dpr: 2, shadowSize: 2048, postprocessing: true },
} as const
const smooth = (x: number) => { const t = Math.max(0, Math.min(1, x)); return t * t * (3 - 2 * t) }
// Fixed-seed, continuous value noise: one slowly travelling weather field, not independent clocks.
export function studioWind(time: number, position = 0): number {
  const x = time * 0.16 + position * 0.18
  const cell = Math.floor(x)
  const hash = (n: number) => { const value = Math.sin(n * 127.1 + 19.19) * 43758.5453; return (value - Math.floor(value)) * 2 - 1 }
  return hash(cell) + (hash(cell + 1) - hash(cell)) * smooth(x - cell)
}
export function studioEntry(time: number) {
  return {
    resume: smooth(time / 0.36),
    project: smooth((time - 0.24) / 0.4),
    skills: smooth((time - 0.46) / 0.4),
    path: smooth((time - 0.72) / 0.48),
  }
}
