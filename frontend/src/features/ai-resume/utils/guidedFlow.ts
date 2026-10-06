export type GuidedCardLike = {
  cardType: string
  status: string
}

export function suggestGuidedCard<T extends GuidedCardLike>(
  identityType: string | undefined,
  cards: T[],
): T | null {
  const unresolved = (card: T | undefined): card is T => Boolean(card)
    && card?.status !== 'CONFIRMED'
    && card?.status !== 'SKIPPED'
  const target = cards.find((card) => card.cardType === 'TARGET_JOB')
  const education = cards.find((card) => card.cardType === 'EDUCATION')
  const experience = cards.find((card) => card.cardType === 'EXPERIENCE')

  if (identityType === 'STUDENT' || identityType === 'GRADUATE') {
    if (unresolved(education)) return education
    if (unresolved(target)) return target
    if (unresolved(experience)) return experience
  } else {
    if (unresolved(target)) return target
    if (unresolved(experience)) return experience
    if (unresolved(education)) return education
  }

  return cards.find((card) => unresolved(card)) ?? null
}
