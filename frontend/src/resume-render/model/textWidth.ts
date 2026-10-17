/** Matches characters set on a full em: CJK ideographs, kana, Hangul, full-width forms and CJK punctuation. */
const FULL_WIDTH = /[ᄀ-ᇿ⺀-鿿ꥠ-꥿가-퟿豈-﫿︰-﹏＀-｠￠-￦]/

/** Rough width of a short label in em (full-width characters 1em, others about 0.56em). */
export function estimateEm(text: string): number {
  let em = 0
  for (const char of text) em += FULL_WIDTH.test(char) ? 1 : 0.56
  return em
}

/** Widest skill-group name plus its colon, so every row of a skills section starts its items in one column. */
export function skillLabelEm(names: string[], locale: 'zh-CN' | 'en', cap = 9): number | null {
  const widest = Math.max(0, ...names.filter(Boolean).map(estimateEm))
  if (!widest) return null
  const colon = locale === 'en' ? 0.3 : 1
  return Math.round(Math.min(cap, widest + colon) * 100) / 100
}
