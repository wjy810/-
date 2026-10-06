/** Chat with the AI resume assistant: optimistic messages, streaming replies, cancellation and change sets. */
import { computed, nextTick, ref } from 'vue'
import { errorMessage } from '@/shared/api/types'
import { addAiResumeMessage, cancelAiResumeResponse, streamAiResume } from '../services/aiResumeApi'
import type { AiResumeChangeSet } from '../types'
import { identityIntro } from './copy'
import type { DisplayMessage } from './types'
import type { DescriptionAssist } from './useDescriptionAssist'
import type { WorkbenchSession } from './useWorkbenchSession'

type StreamPayload = Record<string, unknown> & { message?: unknown; delta?: unknown; detail?: unknown; changeSet?: unknown }

function payloadOf(value: unknown): StreamPayload {
  return value && typeof value === 'object' ? value as StreamPayload : {}
}

export function useConversation(session: WorkbenchSession, assist: DescriptionAssist) {
  const draft = ref('')
  const transient = ref<DisplayMessage[]>([])
  const cancelPending = ref(false)
  const requestId = ref('')
  let controller: AbortController | null = null

  const conversation = session.conversation
  const pending = session.messagePending

  const messages = computed<DisplayMessage[]>(() => {
    const persisted = conversation.value?.messages ?? []
    const ids = new Set(persisted.map(message => message.id))
    return [...persisted, ...transient.value.filter(message => !ids.has(message.id))]
  })
  // Imported or legacy resumes have no chosen identity: never put an answer in the user's mouth.
  const showIdentityIntro = computed(() => Boolean(conversation.value?.identityType) && conversation.value?.messages.length === 0)
  const intro = computed(() => identityIntro(conversation.value?.identityType))
  const canSend = computed(() => Boolean(draft.value.trim()) && !pending.value && !assist.fieldGenerationPending.value)

  function changeSetsFor(messageId: string): AiResumeChangeSet[] {
    return conversation.value?.changeSets.filter(changeSet => changeSet.messageId === messageId) ?? []
  }

  function upsertChangeSet(next: AiResumeChangeSet): void {
    const sets = conversation.value?.changeSets
    if (!sets) return
    const index = sets.findIndex(changeSet => changeSet.id === next.id)
    if (index >= 0) sets.splice(index, 1, next)
    else sets.push(next)
  }

  async function onChangeSetUpdated(next: AiResumeChangeSet): Promise<void> {
    if (!conversation.value) return
    upsertChangeSet(next)
    await session.load()
    session.notify(next.status === 'REJECTED'
      ? '该组修改已处理，拒绝项没有改变正式简历。'
      : '修改状态已更新，已接受内容已写入正式简历并生成新修订。')
  }

  function replaceTransient(id: string, message: DisplayMessage): void {
    const index = transient.value.findIndex(item => item.id === id)
    if (index >= 0) transient.value.splice(index, 1, message)
  }

  function updateTransient(id: string, patch: Partial<DisplayMessage>): void {
    const message = transient.value.find(item => item.id === id)
    if (message) Object.assign(message, patch)
  }

  async function send(): Promise<void> {
    const text = draft.value.trim()
    const current = conversation.value
    if (!text || !current || pending.value || assist.fieldGenerationPending.value) return
    if (current.aiAvailable && current.consent.status !== 'GRANTED') {
      session.fail('首次调用 AI 前需要明确授权。')
      return
    }
    const clientId = crypto.randomUUID()
    const now = new Date().toISOString()
    const localUserId = `local-user-${clientId}`
    const localAssistantId = `local-assistant-${clientId}`
    let accepted = false
    let terminal = false
    let streamError = ''
    pending.value = true
    session.clearError()
    draft.value = ''
    requestId.value = clientId
    transient.value.push({
      id: localUserId, sequence: Number.MAX_SAFE_INTEGER - 1, role: 'USER', messageType: 'TEXT', status: 'PENDING',
      content: text, inputTokens: 0, outputTokens: 0, createdAt: now, completedAt: null, transient: true, phase: 'sending',
    })
    if (current.aiAvailable) {
      transient.value.push({
        id: localAssistantId, sequence: Number.MAX_SAFE_INTEGER, role: 'ASSISTANT', messageType: 'TEXT', status: 'PENDING',
        content: '', inputTokens: 0, outputTokens: 0, createdAt: now, completedAt: null, transient: true, phase: 'loading',
      })
    }
    await nextTick()
    session.requestChatScroll(true)
    try {
      if (current.aiAvailable) {
        controller = new AbortController()
        await streamAiResume(current.id, text, clientId, (event) => {
          const payload = payloadOf(event.data)
          const message = payload.message as DisplayMessage | undefined
          if (event.type === 'user.accepted' && message) {
            accepted = true
            replaceTransient(localUserId, { ...message, transient: true })
          } else if (event.type === 'assistant.started') {
            updateTransient(localAssistantId, { phase: 'loading', status: 'PENDING' })
          } else if (event.type === 'assistant.progress') {
            updateTransient(localAssistantId, { phase: 'loading', status: 'PENDING', content: typeof payload.message === 'string' ? payload.message : '' })
          } else if (event.type === 'assistant.delta' && typeof payload.delta === 'string') {
            const existing = transient.value.find(item => item.id === localAssistantId)
            updateTransient(localAssistantId, { phase: 'streaming', status: 'PENDING', content: `${existing?.content ?? ''}${payload.delta}` })
          } else if (event.type === 'assistant.completed' && message) {
            terminal = true
            replaceTransient(localAssistantId, { ...message, transient: true })
          } else if (event.type === 'change-set.created' && payload.changeSet) {
            upsertChangeSet(payload.changeSet as AiResumeChangeSet)
          } else if (event.type === 'assistant.cancelled' && message) {
            terminal = true
            replaceTransient(localAssistantId, { ...message, transient: true })
          } else if (event.type === 'assistant.failed') {
            terminal = true
            streamError = typeof payload.detail === 'string' ? payload.detail : 'AI 回复失败，额度已返还'
            if (message) replaceTransient(localAssistantId, { ...message, transient: true })
          } else if (event.type === 'request.failed') {
            terminal = true
            streamError = typeof payload.message === 'string' ? payload.message : '消息发送失败'
          }
          // Deltas only follow the bottom when the reader has not scrolled up.
          void nextTick(() => session.requestChatScroll(event.type !== 'assistant.delta', false))
        }, controller.signal)
        if (!terminal) throw new Error('AI 流式响应意外中断，请重试')
        if (streamError) session.fail(streamError)
        await session.load()
        transient.value = transient.value.filter(message => !message.id.includes(clientId))
      } else {
        const saved = await addAiResumeMessage(current.id, text, clientId)
        accepted = true
        replaceTransient(localUserId, { ...saved, transient: true })
        await session.load()
        transient.value = transient.value.filter(message => message.id !== saved.id)
        session.notify('问题已保存到长期对话。AI 不可用，因此没有生成回复或扣除额度。')
      }
    } catch (reason) {
      session.fail(errorMessage(reason, '消息发送失败，正式简历没有变化'))
      transient.value = transient.value.filter(message => !message.id.includes(clientId))
      // Give the text back when the server never accepted it, unless the user already typed something new.
      if (!accepted && !draft.value) draft.value = text
      await session.load()
    } finally {
      controller = null
      pending.value = false
      cancelPending.value = false
      requestId.value = ''
    }
  }

  async function cancel(): Promise<void> {
    const current = conversation.value
    if (!current || !requestId.value || cancelPending.value) return
    cancelPending.value = true
    session.clearError()
    try {
      const result = await cancelAiResumeResponse(current.id, requestId.value)
      session.notify(result.accepted ? '已请求停止生成；模型返回的迟到内容会被丢弃，额度将返还。' : '本次生成已经结束，无需取消。')
    } catch (reason) {
      session.fail(errorMessage(reason, '停止生成失败'))
      cancelPending.value = false
    }
  }

  function dispose(): void {
    controller?.abort()
  }

  return {
    draft,
    messages,
    pending,
    cancelPending,
    requestId,
    canSend,
    showIdentityIntro,
    intro,
    changeSetsFor,
    onChangeSetUpdated,
    send,
    cancel,
    dispose,
  }
}

export type WorkbenchConversation = ReturnType<typeof useConversation>
