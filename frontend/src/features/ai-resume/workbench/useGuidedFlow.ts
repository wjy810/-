/** Which card the conversation asks about next, and which card the module editor shows. */
import { computed, ref } from 'vue'
import type { AiResumeCard } from '../types'
import { suggestGuidedCard } from '../utils/guidedFlow'
import { STRUCTURED_CARD_TYPES } from './cardConfig'
import { assistantPrompt } from './copy'
import type { WorkbenchSession } from './useWorkbenchSession'

export function useGuidedFlow(session: WorkbenchSession) {
  const selectedCardId = ref('')
  /** User chose "继续添加": keep asking about this card instead of the suggested one. */
  const overrideCardId = ref('')
  /** A structured card was just confirmed: ask whether to add another record of this type. */
  const repeatType = ref('')

  const conversation = session.conversation
  const editableCards = computed(() => conversation.value?.cards.filter(card => card.cardType !== 'IDENTITY') ?? [])
  const selectedCard = computed(() => editableCards.value.find(card => card.id === selectedCardId.value) ?? editableCards.value[0] ?? null)
  const suggestedCard = computed(() => suggestGuidedCard(conversation.value?.identityType, editableCards.value))
  const guidedCard = computed(() => editableCards.value.find(card => card.id === overrideCardId.value) ?? suggestedCard.value)
  const showGuidedCard = computed(() => Boolean(guidedCard.value) && !repeatType.value)
  const prompt = computed(() => assistantPrompt(guidedCard.value?.cardType, conversation.value?.identityType))
  const confirmedCount = computed(() => editableCards.value.filter(card => card.status === 'CONFIRMED').length)
  const resolvedCount = computed(() => editableCards.value.filter(card => card.status === 'CONFIRMED' || card.status === 'SKIPPED').length)

  session.onApply((next) => {
    if (!selectedCardId.value || !next.cards.some(card => card.id === selectedCardId.value)) {
      selectedCardId.value = next.cards.find(card => card.cardType === 'TARGET_JOB')?.id ?? next.cards[0]?.id ?? ''
    }
  })

  function select(card: AiResumeCard): void {
    selectedCardId.value = card.id
  }

  function afterConfirm(card: AiResumeCard): void {
    overrideCardId.value = ''
    if (STRUCTURED_CARD_TYPES.has(card.cardType)) {
      repeatType.value = card.cardType
      return
    }
    const following = suggestedCard.value
    if (following && following.id !== card.id) selectedCardId.value = following.id
  }

  function afterSkip(): void {
    overrideCardId.value = ''
    repeatType.value = ''
    if (suggestedCard.value) selectedCardId.value = suggestedCard.value.id
  }

  /** "继续添加": returns the card that should receive a new blank record. */
  function addAnother(): AiResumeCard | null {
    const card = editableCards.value.find(item => item.cardType === repeatType.value) ?? null
    if (!card) return null
    overrideCardId.value = card.id
    repeatType.value = ''
    return card
  }

  function continueAfterRepeat(): void {
    repeatType.value = ''
    overrideCardId.value = ''
    if (suggestedCard.value) selectedCardId.value = suggestedCard.value.id
  }

  return {
    selectedCardId,
    repeatType,
    editableCards,
    selectedCard,
    suggestedCard,
    guidedCard,
    showGuidedCard,
    prompt,
    confirmedCount,
    resolvedCount,
    select,
    afterConfirm,
    afterSkip,
    addAnother,
    continueAfterRepeat,
  }
}

export type GuidedFlow = ReturnType<typeof useGuidedFlow>
