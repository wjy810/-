<script setup lang="ts">
import { computed } from 'vue'
import UiAvatar from '@/shared/ui/UiAvatar.vue'
import type { DisplayMessage } from '../types'
import AssistantAvatar from './AssistantAvatar.vue'

const props = withDefaults(defineProps<{
  role: 'assistant' | 'user'
  message?: DisplayMessage | null
  userName?: string
  active?: boolean
  tone?: 'default' | 'welcome' | 'prompt'
}>(), { message: null, userName: '', active: false, tone: 'default' })

const thinking = computed(() => props.message?.phase === 'loading')
const streaming = computed(() => props.message?.phase === 'streaming')
const animated = computed(() => props.active || Boolean(props.message?.transient) || thinking.value || streaming.value)
</script>

<template>
  <article class="chat-msg" :class="[`chat-msg--${role}`, `chat-msg--${tone}`, { 'is-transient': message?.transient }]">
    <AssistantAvatar v-if="role === 'assistant'" class="chat-msg__avatar" :active="animated && role === 'assistant'" />
    <div class="chat-msg__body">
      <span class="chat-msg__author">{{ role === 'assistant' ? 'AI 简历助手' : '你' }}</span>
      <div class="chat-msg__bubble">
        <slot>
          <template v-if="message">
            <p v-if="thinking && message.content" class="chat-msg__progress" role="status">{{ message.content }}<span class="chat-msg__dots"><i /><i /><i /></span></p>
            <p v-else-if="thinking" class="chat-msg__typing" role="status" aria-label="AI 正在思考"><span class="chat-msg__dots"><i /><i /><i /></span></p>
            <p v-else-if="message.status === 'FAILED'" class="chat-msg__failed">本次调用失败：{{ message.errorCode }}</p>
            <p v-else-if="message.status === 'CANCELLED'" class="chat-msg__muted">本次生成已取消，未采用模型返回内容。</p>
            <p v-else-if="message.content">{{ message.content }}<span v-if="streaming" class="chat-msg__caret" aria-hidden="true" /></p>
            <p v-else class="chat-msg__muted">正文已删除</p>
          </template>
        </slot>
      </div>
      <small v-if="message && role === 'assistant' && message.model" class="chat-msg__meta">{{ message.model }} · {{ message.inputTokens }}/{{ message.outputTokens }} Token</small>
    </div>
    <UiAvatar v-if="role === 'user'" class="chat-msg__avatar" :name="userName || '你'" :size="30" />
  </article>
</template>

<style scoped>
.chat-msg {
  max-width: min(92%, 640px);
  display: grid;
  gap: 10px;
  align-items: start;
}

.chat-msg--assistant {
  justify-self: start;
  grid-template-columns: 32px minmax(0, 1fr);
}

.chat-msg--user {
  justify-self: end;
  grid-template-columns: minmax(0, 1fr) 30px;
}

.chat-msg.is-transient,
.chat-msg--prompt {
  animation: chat-in var(--dur-slow) var(--ease-out) both;
}

.chat-msg__avatar {
  margin-top: 20px;
}

.chat-msg__body {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.chat-msg--user .chat-msg__body {
  justify-items: end;
}

.chat-msg__author {
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 550;
}

.chat-msg__bubble {
  min-width: 0;
  max-width: 100%;
  padding: 10px 14px;
  border-radius: 4px var(--radius-lg) var(--radius-lg) var(--radius-lg);
  background: var(--surface-2);
  color: var(--text-primary);
  font-size: var(--fs-body);
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.chat-msg__bubble :deep(p) {
  margin: 0;
  white-space: pre-wrap;
}

.chat-msg--user .chat-msg__bubble {
  border-radius: var(--radius-lg) 4px var(--radius-lg) var(--radius-lg);
  background: var(--color-primary);
  color: var(--text-on-primary);
}

.chat-msg--welcome .chat-msg__bubble {
  background: var(--gradient-ai-soft);
  box-shadow: inset 0 0 0 1px var(--color-primary-border);
}

.chat-msg__meta {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 10px;
}

.chat-msg__muted {
  color: var(--text-tertiary);
}

.chat-msg__failed {
  color: var(--color-danger-text);
}

.chat-msg__progress {
  color: var(--text-secondary);
}

.chat-msg__typing {
  min-width: 36px;
  min-height: 22px;
  display: grid;
  place-items: center;
}

.chat-msg__dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 4px;
  vertical-align: middle;
}

.chat-msg__dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--text-tertiary);
  animation: chat-dot 1.05s var(--ease-standard) infinite;
}

.chat-msg__dots i:nth-child(2) {
  animation-delay: 0.14s;
}

.chat-msg__dots i:nth-child(3) {
  animation-delay: 0.28s;
}

.chat-msg__caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: currentColor;
  animation: chat-caret 1s steps(2) infinite;
}

@keyframes chat-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
}

@keyframes chat-dot {
  0%,
  60%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@keyframes chat-caret {
  50% {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-msg,
  .chat-msg__dots i,
  .chat-msg__caret {
    animation: none !important;
  }
}
</style>
