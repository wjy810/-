<script setup lang="ts">
import { computed, ref } from 'vue'
import { useTextareaAutosize } from '@vueuse/core'
import { ArrowUp, Library, LoaderCircle, Square } from 'lucide-vue-next'
import UiTooltip from '@/shared/ui/UiTooltip.vue'
import { useWorkbench } from '../useWorkbench'

const wb = useWorkbench()
const chat = wb.chat
const session = wb.session
const conversation = session.conversation

const textarea = ref<HTMLTextAreaElement | null>(null)
useTextareaAutosize({ element: textarea, input: chat.draft, styleProp: 'minHeight' })

const evidence = computed(() => conversation.value?.careerLibraryEvidence)
const sendTitle = computed(() => {
  if (chat.pending.value) return chat.cancelPending.value ? '正在停止' : '停止生成'
  if (wb.assist.fieldGenerationPending.value) return '结构化内容正在生成，请稍后发送'
  return conversation.value?.aiAvailable ? '发送给 AI（Enter）' : '保存到对话（Enter）'
})

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  void chat.send()
}

function onAction(): void {
  if (chat.pending.value) void chat.cancel()
  else void chat.send()
}
</script>

<template>
  <form v-if="conversation" class="composer" aria-label="AI 对话输入" @submit.prevent="chat.send()">
    <div class="composer__box">
      <textarea
        ref="textarea"
        v-model="chat.draft.value"
        data-workbench-composer
        class="composer__input"
        rows="1"
        maxlength="8000"
        aria-label="给 AI 简历助手的消息"
        :placeholder="conversation.aiAvailable ? '直接告诉 AI 你的目标、经历，或提出简历问题…' : 'AI 通道暂不可用，可先记录想问的问题…'"
        @keydown="onKeydown"
      />
      <div class="composer__bar">
        <UiTooltip content="AI 仅引用相关且已确认的结构化资料">
          <button
            type="button"
            role="switch"
            class="composer__evidence"
            :class="{ 'is-on': evidence?.enabled }"
            :aria-checked="Boolean(evidence?.enabled)"
            :disabled="session.careerEvidencePending.value"
            @click="session.toggleCareerEvidence()"
          >
            <LoaderCircle v-if="session.careerEvidencePending.value" class="composer__spin" :size="13" aria-hidden="true" />
            <Library v-else :size="13" aria-hidden="true" />
            <span>资料库证据</span>
            <small>{{ evidence?.enabled ? `已启用 · v${evidence.snapshotVersion}` : '未启用' }}</small>
          </button>
        </UiTooltip>
        <span class="composer__status" :class="{ 'is-online': conversation.aiAvailable }">
          <i aria-hidden="true" />{{ conversation.aiAvailable ? 'AI 会先追问，不会编造事实' : '通道暂不可用，消息只会保存' }}
        </span>
        <span class="composer__hint" aria-hidden="true">Enter 发送 · Shift+Enter 换行</span>
        <UiTooltip :content="sendTitle">
          <button
            class="composer__send"
            :class="{ 'is-stop': chat.pending.value }"
            :type="chat.pending.value ? 'button' : 'submit'"
            :aria-label="sendTitle"
            :disabled="chat.pending.value ? chat.cancelPending.value || !chat.requestId.value : !chat.canSend.value"
            @click.prevent="onAction"
          >
            <LoaderCircle v-if="chat.cancelPending.value" class="composer__spin" :size="16" aria-hidden="true" />
            <Square v-else-if="chat.pending.value" :size="13" fill="currentColor" aria-hidden="true" />
            <ArrowUp v-else :size="17" :stroke-width="2.4" aria-hidden="true" />
          </button>
        </UiTooltip>
      </div>
    </div>
  </form>
</template>

<style scoped>
.composer {
  padding: var(--space-3) var(--space-4) var(--space-4);
  background: linear-gradient(to bottom, transparent, var(--surface-1) 30%);
}

.composer__box {
  display: grid;
  gap: 6px;
  padding: 10px 10px 8px 14px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  transition: border-color var(--dur-fast) var(--ease-standard), box-shadow var(--dur-base) var(--ease-out);
}

.composer__box:focus-within {
  border-color: var(--color-primary-border);
  box-shadow: var(--shadow-md), 0 0 0 3px var(--color-primary-soft);
}

.composer__input {
  width: 100%;
  min-height: 44px;
  max-height: 180px;
  resize: none;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--fs-body);
  line-height: 1.6;
}

.composer__input::placeholder {
  color: var(--text-tertiary);
}

.composer__bar {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.composer__evidence {
  height: 26px;
  padding: 0 9px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
  transition: background-color var(--dur-fast), border-color var(--dur-fast), color var(--dur-fast);
}

.composer__evidence small {
  color: var(--text-tertiary);
  font-size: 10.5px;
  font-weight: 500;
}

.composer__evidence:hover:not(:disabled) {
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.composer__evidence.is-on {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.composer__evidence.is-on small {
  color: var(--color-primary-text);
}

.composer__evidence:focus-visible,
.composer__send:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.composer__status {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer__status i {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
  background: var(--text-disabled);
}

.composer__status.is-online i {
  background: var(--color-success);
  box-shadow: 0 0 0 3px var(--color-success-soft);
}

.composer__hint {
  margin-left: auto;
  color: var(--text-tertiary);
  font-size: 11px;
  white-space: nowrap;
}

.composer__send {
  width: 34px;
  height: 34px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--text-on-primary);
  box-shadow: var(--shadow-primary);
  transition: transform var(--dur-instant) var(--ease-standard), background-color var(--dur-fast), opacity var(--dur-fast);
}

.composer__send:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.composer__send:active:not(:disabled) {
  transform: scale(0.94);
}

.composer__send:disabled {
  opacity: 0.4;
  box-shadow: none;
  cursor: not-allowed;
}

.composer__send.is-stop {
  background: var(--color-danger);
  box-shadow: none;
}

.composer__spin {
  animation: composer-spin 0.9s linear infinite;
}

@keyframes composer-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1180px) {
  .composer__hint {
    display: none;
  }

  .composer__status {
    margin-right: auto;
  }
}

@media (max-width: 640px) {
  .composer {
    padding: var(--space-2) var(--space-3) calc(var(--space-3) + env(safe-area-inset-bottom));
  }

  .composer__status {
    display: none;
  }

  .composer__evidence {
    margin-right: auto;
  }
}
</style>
