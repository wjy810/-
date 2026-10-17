<script setup lang="ts">
import { computed, ref } from 'vue'
import { CircleX, Download, Scale, TriangleAlert } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import { confirm } from '@/shared/ui/confirm'
import { toast } from '@/shared/ui/toast'
import { errorMessage, isForbidden } from '@/shared/api/types'
import type { LoadState } from '@/shared/lib/useLoadState'
import type { DeletionRequests } from '../composables/useDeletionRequests'
import type { DeletionPreview } from '../types'

const props = defineProps<{ previewState: LoadState<DeletionPreview>; requests: DeletionRequests; busyReason?: string }>()
const emit = defineEmits<{ forbidden: [error: unknown]; export: [] }>()

const ackImpact = ref(false)
const ackLegal = ref(false)
const submitting = ref(false)
const error = ref('')
const preview = computed(() => props.previewState.data.value)
// The impact summary must have loaded: the user is confirming they have read it.
const canSubmit = computed(() => Boolean(preview.value) && ackImpact.value && ackLegal.value && !props.busyReason && !submitting.value)

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) return
  error.value = ''
  const ok = await confirm({
    title: '确认注销账号？',
    message: '提交后你将无法再次登录，分享链接立即失效，求职资料、简历和全部记录会被永久删除。此操作无法撤销。',
    confirmText: '提交注销申请',
    tone: 'danger',
    requireText: '注销账号',
  })
  if (!ok) return
  submitting.value = true
  try {
    await props.requests.submit({ scope: 'ACCOUNT', confirmationAck: true })
    toast.success('注销申请已提交', { description: '处理进度会显示在“删除申请”中。' })
    ackImpact.value = false
    ackLegal.value = false
  } catch (reason) {
    if (isForbidden(reason)) emit('forbidden', reason)
    else error.value = errorMessage(reason, '提交注销申请失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section id="deletion" class="dr-card dr-card--danger" aria-labelledby="deletion-title">
    <header class="dr-card__head">
      <span class="dr-card__icon is-danger"><CircleX :size="18" /></span>
      <div class="dr-card__heading">
        <h3 id="deletion-title">注销账号</h3>
        <p>永久删除账号与全部求职数据。这是不可逆的高风险操作，请先导出需要保留的内容。</p>
      </div>
    </header>

    <div id="preview" class="danger-impact">
      <PageState :loading="previewState.loading.value" :error="previewState.error.value" :loaded="previewState.loaded.value" error-title="删除影响说明读取失败" compact @retry="previewState.load">
        <template v-if="preview">
          <div class="danger-impact__box is-wipe">
            <TriangleAlert :size="16" />
            <div>
              <strong>会被删除</strong>
              <p>{{ preview.impactSummary || '暂未取得删除影响说明，请刷新后再确认。' }}</p>
            </div>
          </div>
          <div class="danger-impact__box is-keep">
            <Scale :size="16" />
            <div>
              <strong>依法保留</strong>
              <p>{{ preview.legalExceptionNote || '暂未取得法定保留范围说明。' }}</p>
            </div>
          </div>
          <p v-if="preview.statusHint" class="dr-fine">{{ preview.statusHint }}</p>
        </template>
      </PageState>
    </div>

    <div class="danger-confirm">
      <UiCheckbox v-model="ackImpact" :disabled="submitting || Boolean(busyReason)" label="我已阅读上方说明，理解提交后账号会进入删除流程并无法再登录。" />
      <UiCheckbox v-model="ackLegal" :disabled="submitting || Boolean(busyReason)" label="我了解处理期间部分功能会受限，依法需要保留的数据不会提前删除。" />
      <p v-if="!preview" class="dr-note dr-note--warn">删除影响说明还没有读取成功，读取后才能提交注销申请。</p>
      <p v-if="busyReason" class="dr-note dr-note--warn">{{ busyReason }}</p>
      <p v-if="error" class="dr-note dr-note--bad" role="alert">{{ error }}</p>
      <div class="danger-confirm__actions">
        <UiButton variant="ghost" :icon="Download" @click="emit('export')">先导出我的数据</UiButton>
        <UiButton variant="danger" :disabled="!canSubmit" :pending="submitting" @click="onSubmit">申请注销账号</UiButton>
      </div>
    </div>
  </section>
</template>

<style scoped>
.danger-impact {
  display: grid;
  gap: var(--space-3);
}

.danger-impact :deep(.page-state),
.danger-impact > :deep(*) {
  min-width: 0;
}

.danger-impact__box {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.danger-impact__box + .danger-impact__box {
  margin-top: var(--space-3);
}

.danger-impact__box > svg {
  flex: none;
  margin-top: 4px;
}

.danger-impact__box strong {
  display: block;
  margin-bottom: 2px;
  font-size: var(--fs-sm);
}

.danger-impact__box p {
  color: var(--text-secondary);
  white-space: pre-wrap;
}

.danger-impact__box.is-wipe {
  background: var(--color-danger-soft);
}

.danger-impact__box.is-wipe > svg {
  color: var(--color-danger);
}

.danger-impact__box.is-keep {
  background: var(--surface-2);
}

.danger-impact__box.is-keep > svg {
  color: var(--text-tertiary);
}

.danger-confirm {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px dashed color-mix(in srgb, var(--color-danger) 30%, transparent);
}

.danger-confirm__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

@media (max-width: 640px) {
  .danger-confirm__actions {
    flex-direction: column-reverse;
  }
}
</style>
