<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UiButton from './UiButton.vue'
import UiDialog from './UiDialog.vue'
import UiInput from './UiInput.vue'
import { confirmState, settleConfirm } from './confirm'

const typed = ref('')
const current = computed(() => confirmState.current)
const blocked = computed(() => Boolean(current.value?.requireText && typed.value.trim() !== current.value.requireText))

watch(current, () => {
  typed.value = ''
})
</script>

<template>
  <UiDialog :open="!!current" :title="current?.title ?? ''" width="sm" @close="settleConfirm(false)">
    <p v-if="current?.message" class="confirm-msg">{{ current.message }}</p>
    <label v-if="current?.requireText" class="confirm-type">
      <span>请输入 <strong>{{ current.requireText }}</strong> 以确认</span>
      <UiInput v-model="typed" autocomplete="off" />
    </label>
    <template #footer>
      <UiButton variant="secondary" @click="settleConfirm(false)">{{ current?.cancelText ?? '取消' }}</UiButton>
      <UiButton :variant="current?.tone === 'danger' ? 'danger' : 'primary'" :disabled="blocked" @click="settleConfirm(true)">
        {{ current?.confirmText ?? '确认' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.confirm-msg {
  color: var(--text-secondary);
  line-height: 1.7;
}

.confirm-type {
  display: grid;
  gap: 8px;
  margin-top: var(--space-4);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.confirm-type strong {
  color: var(--text-primary);
  user-select: all;
}
</style>
