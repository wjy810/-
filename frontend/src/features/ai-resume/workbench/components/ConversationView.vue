<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown, ChevronRight, Info, Plus, ShieldCheck } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import { useScrollSurface } from '@/shared/composables/useScrollSurface'
import { useSessionStore } from '@/stores/session'
import AiResumeChangeSetCard from '../../components/AiResumeChangeSetCard.vue'
import { structuredTitle } from '../structured'
import { useWorkbench } from '../useWorkbench'
import ChatComposer from './ChatComposer.vue'
import ChatMessage from './ChatMessage.vue'
import GuidedCard from './GuidedCard.vue'

const wb = useWorkbench()
const account = useSessionStore()
const conversation = wb.session.conversation
const chat = wb.chat
const guided = wb.guided

const scroller = ref<HTMLElement | null>(null)
const surface = useScrollSurface(scroller)
wb.session.onChatScroll(({ smooth, force, anchor }) => {
  if (anchor === 'prompt' && scrollToPrompt(smooth)) return
  surface.requestBottom(smooth, force)
})

/** Scrolls the latest assistant prompt to the top when the content below it is taller than the viewport. */
function scrollToPrompt(smooth: boolean): boolean {
  const container = scroller.value
  const prompts = container?.querySelectorAll<HTMLElement>('[data-chat-anchor]')
  const prompt = prompts?.[prompts.length - 1]
  if (!container || !prompt) return false
  requestAnimationFrame(() => {
    const top = prompt.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop - 12
    if (container.scrollHeight - top <= container.clientHeight) {
      surface.scrollToBottom(smooth)
      return
    }
    container.scrollTo({ top, behavior: smooth ? 'smooth' : 'auto' })
  })
  return true
}

const userName = computed(() => account.displayName || '你')
const needsConsent = computed(() => Boolean(conversation.value?.aiAvailable) && conversation.value?.consent.status !== 'GRANTED')
</script>

<template>
  <div v-if="conversation" class="conversation">
    <div class="conversation__scroll-shell scroll-edges" :class="{ 'can-scroll-up': surface.edges.canScrollUp, 'can-scroll-down': surface.edges.canScrollDown }">
      <div
        ref="scroller"
        class="conversation__scroll"
        tabindex="0"
        aria-label="AI 对话"
        @scroll.passive="surface.onScroll"
      >
        <div v-if="wb.session.aiReason.value" class="conversation__notice">
          <Info :size="16" aria-hidden="true" /><span>{{ wb.session.aiReason.value }}</span>
        </div>
        <div v-else-if="needsConsent" class="conversation__notice conversation__notice--consent">
          <ShieldCheck :size="16" aria-hidden="true" />
          <span><strong>首次使用 AI 前需要授权</strong>只发送已确认事实和当前问题；照片不会发送给模型。</span>
          <UiButton size="sm" variant="secondary" :pending="wb.session.consentPending.value" @click="wb.session.grantConsent()">阅读并同意</UiButton>
        </div>

        <section class="conversation__thread" aria-label="对话记录">
          <ChatMessage role="assistant" tone="welcome">
            <p>你好，我们可以像聊天一样完成简历。我会一次只问一个问题，把修改拆成可核对的条目；只有你点对勾后才会写入正式简历。</p>
          </ChatMessage>

          <template v-if="chat.showIdentityIntro.value">
            <ChatMessage role="user" :user-name="userName"><p>{{ chat.intro.value.answer }}</p></ChatMessage>
            <ChatMessage role="assistant"><p>{{ chat.intro.value.reply }}</p></ChatMessage>
          </template>

          <template v-for="message in chat.messages.value" :key="message.id">
            <ChatMessage :role="message.role === 'USER' ? 'user' : 'assistant'" :message="message" :user-name="userName" />
            <AiResumeChangeSetCard
              v-for="changeSet in chat.changeSetsFor(message.id)"
              :key="changeSet.id"
              :conversation-id="conversation.id"
              :change-set="changeSet"
              @updated="chat.onChangeSetUpdated"
            />
          </template>

          <template v-if="guided.showGuidedCard.value && guided.guidedCard.value">
            <ChatMessage :key="`prompt-${guided.guidedCard.value.id}`" role="assistant" tone="prompt" active data-chat-anchor>
              <p>{{ guided.prompt.value }}</p>
            </ChatMessage>
            <GuidedCard :key="`card-${guided.guidedCard.value.id}`" :card="guided.guidedCard.value" />
          </template>

          <template v-if="guided.repeatType.value">
            <ChatMessage role="user" :user-name="userName"><p>这段{{ structuredTitle(guided.repeatType.value) }}已经补充好了</p></ChatMessage>
            <ChatMessage role="assistant" tone="prompt" active data-chat-anchor>
              <p>还要再添加一段{{ structuredTitle(guided.repeatType.value) }}吗？可以继续补充，也可以进入下一步。</p>
            </ChatMessage>
            <div class="conversation__choices">
              <button type="button" class="conversation__choice" @click="wb.continueAfterRepeat()">
                不用了，继续下一步<ChevronRight :size="15" aria-hidden="true" />
              </button>
              <button type="button" class="conversation__choice" @click="wb.addAnotherRecord()">
                继续添加{{ structuredTitle(guided.repeatType.value) }}<Plus :size="15" aria-hidden="true" />
              </button>
            </div>
          </template>
        </section>
      </div>

      <Transition name="fade">
        <button v-if="surface.showReturn.value" class="conversation__return" type="button" aria-label="回到最新消息" @click="surface.scrollToBottom(true)">
          <ArrowDown :size="16" aria-hidden="true" />
        </button>
      </Transition>
    </div>

    <ChatComposer />
  </div>
</template>

<style scoped>
.conversation {
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
}

.conversation__scroll-shell {
  position: relative;
  min-height: 0;
}

.conversation__scroll {
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-5) var(--space-5) var(--space-2);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.conversation__scroll > * {
  flex: none;
}

.conversation__thread {
  display: grid;
  gap: var(--space-4);
}

.conversation__notice {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--color-info-soft);
  color: var(--color-info-text);
  font-size: var(--fs-sm);
  line-height: 1.55;
}

.conversation__notice svg {
  flex: none;
}

.conversation__notice--consent {
  background: var(--gradient-ai-soft);
  color: var(--text-primary);
  box-shadow: inset 0 0 0 1px var(--color-primary-border);
}

.conversation__notice--consent span {
  flex: 1;
  display: grid;
  gap: 2px;
  color: var(--text-secondary);
}

.conversation__notice--consent strong {
  color: var(--text-primary);
}

.conversation__choices {
  width: min(calc(100% - 42px), 440px);
  margin-left: 42px;
  display: grid;
  gap: var(--space-2);
  animation: choices-in var(--dur-slow) var(--ease-out) 80ms both;
}

.conversation__choice {
  min-height: 44px;
  padding: 0 var(--space-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  color: var(--text-primary);
  font-size: var(--fs-sm);
  font-weight: 600;
  transition: border-color var(--dur-fast), background-color var(--dur-fast), transform var(--dur-fast) var(--ease-out);
}

.conversation__choice:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  transform: translateX(2px);
}

.conversation__choice:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.conversation__return {
  position: absolute;
  right: var(--space-4);
  bottom: var(--space-3);
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-default);
  border-radius: 50%;
  background: var(--surface-1);
  color: var(--text-secondary);
  box-shadow: var(--shadow-md);
}

.conversation__return:hover {
  color: var(--color-primary-text);
}

.conversation__thread :deep(.change-set) {
  margin-left: 42px;
  width: auto;
}

@keyframes choices-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
}

@media (max-width: 640px) {
  .conversation__scroll {
    padding: var(--space-3);
  }

  .conversation__choices,
  .conversation__thread :deep(.change-set) {
    width: 100%;
    margin-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .conversation__choices {
    animation: none;
  }
}
</style>
