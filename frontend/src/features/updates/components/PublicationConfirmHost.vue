<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import { formatWhen } from '@/shared/lib/datetime'
import { publicationState, settlePublication } from '../publication'
const current = computed(() => publicationState.current)
const notify = ref(false)
watch(current, value => { notify.value = value?.preview.sendNotification ?? false })
</script>
<template>
  <UiDialog :open="!!current" :title="current?.scheduledAt ? '确认定时发布' : '确认发布更新'" width="md" @close="settlePublication(null)">
    <div v-if="current" class="publication-confirm">
      <strong>{{ current.preview.versionLabel }} · {{ current.preview.title }}</strong>
      <dl><dt>发布时间</dt><dd>{{ current.scheduledAt ? formatWhen(current.scheduledAt) : '立即发布' }}</dd>
        <dt>内容受众</dt><dd>{{ current.preview.audience === 'PUBLIC' ? '所有访问者' : '登录用户' }}</dd>
        <dt>版本亮点弹窗</dt><dd>{{ current.preview.showWhatsNew ? '开启' : '关闭' }}</dd></dl>
      <label class="publication-confirm__notify"><input v-model="notify" type="checkbox" />同时发送站内通知</label>
      <p>{{ notify ? `预计通知 ${current.preview.recipientCount} 个有效账号；实际人数以发送时为准。` : '仅发布更新内容，不发送站内通知。' }}</p>
    </div>
    <template #footer><UiButton variant="secondary" @click="settlePublication(null)">取消</UiButton>
      <UiButton @click="settlePublication(notify)">{{ current?.scheduledAt ? '确认定时发布' : notify ? '发布并通知' : '仅发布' }}</UiButton></template>
  </UiDialog>
</template>
<style scoped>
.publication-confirm { display: grid; gap: var(--space-4); }
.publication-confirm dl { display: grid; grid-template-columns: auto 1fr; gap: var(--space-2) var(--space-5); font-size: var(--fs-sm); }
.publication-confirm dt, .publication-confirm p { color: var(--text-secondary); }
.publication-confirm dd { margin: 0; }
.publication-confirm__notify { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-3); background: var(--surface-2); border-radius: var(--radius-md); }
</style>
