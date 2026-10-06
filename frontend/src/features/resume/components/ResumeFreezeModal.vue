<script setup lang="ts">
import { computed, reactive } from 'vue'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import { outcomeResolved } from '../labels'
import type { KeyOutcome } from '../types'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    versionNo: number
    outcomes: KeyOutcome[]
    readyToApply: boolean
    pendingAi: boolean
    templateName?: string
    variantCode?: string
    layoutStatus?: string
    consumedUnits?: number
    canFreeze: boolean
    freezeBlockReason?: string
    freezePending?: boolean
    waivePending?: string
    evidenceError?: string
  }>(),
  {
    templateName: '',
    variantCode: '',
    layoutStatus: '',
    consumedUnits: 0,
    freezeBlockReason: '',
    freezePending: false,
    waivePending: '',
    evidenceError: '',
  },
)

const emit = defineEmits<{
  close: []
  freeze: []
  waive: [outcome: KeyOutcome]
}>()

// 「暂无资料仍要冻结」须逐条人工勾选；此处只记录本弹窗内的勾选
const waiveAck = reactive<Record<string, boolean>>({})

const listed = computed(() => props.outcomes.filter((item) => (item.text ?? '').trim()))
const resolvedCount = computed(() => listed.value.filter((item) => outcomeResolved(item)).length)
const unresolved = computed(() => listed.value.filter((item) => !outcomeResolved(item)))

function outcomeKey(item: KeyOutcome, index: number): string {
  return item.id || `new-${index}`
}

function onWaive(item: KeyOutcome): void {
  if (!item.id || !waiveAck[item.id]) {
    return
  }
  waiveAck[item.id] = false
  emit('waive', item)
}
</script>

<template>
  <AppModal :open="open" title="冻结当前版本" :width="660" @close="emit('close')">
    <div class="freeze">
      <p class="muted">将当前简历内容冻结为不可变版本，供 PDF/DOCX 导出与版本对比使用。</p>

      <section class="freeze__content">
        <span class="freeze__icon"><AppIcon name="file-text" :size="18" /></span>
        <div class="freeze__who">
          <p class="freeze__name">{{ title || '未命名简历' }}</p>
          <p class="fine">当前编辑版本 · v{{ versionNo }}</p>
        </div>
      </section>

      <section v-if="templateName" class="freeze__panel">
        <p class="freeze__panel-title">版式快照</p>
        <div class="check">
          <span class="check__icon check__icon--ok"><AppIcon name="book" :size="15" /></span>
          <span class="check__label">模板</span>
          <span class="check__value">{{ templateName }}</span>
        </div>
        <div class="check">
          <span class="check__icon check__icon--ok"><AppIcon name="star" :size="15" /></span>
          <span class="check__label">颜色变体</span>
          <span class="check__value">{{ variantCode || 'DEFAULT' }}</span>
        </div>
        <div class="check">
          <span
            class="check__icon"
            :class="layoutStatus === 'OVERFLOW' ? 'check__icon--bad' : 'check__icon--ok'"
          >
            <AppIcon :name="layoutStatus === 'OVERFLOW' ? 'x-circle' : 'check-circle'" :size="15" />
          </span>
          <span class="check__label">页面容量</span>
          <span class="check__value">
            {{ layoutStatus === 'OVERFLOW' ? '存在溢出' : `有效 · ${consumedUnits} 单位` }}
          </span>
        </div>
        <div class="check">
          <span class="check__icon check__icon--ok"><AppIcon name="file-text" :size="15" /></span>
          <span class="check__label">导出格式</span>
          <span class="check__value">PDF · DOCX 未开放</span>
        </div>
      </section>

      <section class="freeze__panel">
        <p class="freeze__panel-title">证据检查</p>
        <div class="check">
          <span class="check__icon check__icon--ok"><AppIcon name="check-circle" :size="15" /></span>
          <span class="check__label">关键成果</span>
          <span class="check__value">{{ listed.length }} 条</span>
        </div>
        <div class="check">
          <span class="check__icon check__icon--ok"><AppIcon name="check-circle" :size="15" /></span>
          <span class="check__label">已关联证据或已确认暂无证据</span>
          <span class="check__value">{{ resolvedCount }} 条</span>
        </div>
        <div class="check">
          <span class="check__icon" :class="unresolved.length ? 'check__icon--warn' : 'check__icon--ok'">
            <AppIcon :name="unresolved.length ? 'alert-circle' : 'check-circle'" :size="15" />
          </span>
          <span class="check__label">待补证</span>
          <span class="check__value" :class="{ 'is-warn': unresolved.length }">{{ unresolved.length }} 条</span>
        </div>
        <div class="check">
          <span class="check__icon" :class="readyToApply ? 'check__icon--ok' : 'check__icon--bad'">
            <AppIcon :name="readyToApply ? 'check-circle' : 'x-circle'" :size="15" />
          </span>
          <span class="check__label">主档已标为可导出</span>
          <span class="check__value">{{ readyToApply ? '已就绪' : '未就绪' }}</span>
        </div>
        <div class="check">
          <span class="check__icon" :class="pendingAi ? 'check__icon--bad' : 'check__icon--ok'">
            <AppIcon :name="pendingAi ? 'x-circle' : 'check-circle'" :size="15" />
          </span>
          <span class="check__label">AI 待确认内容已全部处理</span>
          <span class="check__value">{{ pendingAi ? '有未确认' : '已确认' }}</span>
        </div>
      </section>

      <AppBanner v-if="!canFreeze && freezeBlockReason" tone="bad">{{ freezeBlockReason }}</AppBanner>
      <AppBanner v-if="evidenceError" tone="bad">
        {{ evidenceError }}证据列表不可用期间，不能关联或确认「暂无证据」。
      </AppBanner>

      <div v-if="unresolved.length" class="waive-list">
        <div v-for="(item, index) in unresolved" :key="outcomeKey(item, index)" class="waive">
          <p class="waive__text">{{ item.text }}</p>
          <template v-if="item.id">
            <label class="waive__ack">
              <input
                v-model="waiveAck[item.id]"
                type="checkbox"
                :disabled="Boolean(evidenceError) || Boolean(waivePending)"
              />
              <span>我确认暂无证据，仍要冻结这一条。</span>
            </label>
            <AppButton
              variant="ghost"
              :disabled="!waiveAck[item.id] || Boolean(evidenceError)"
              :pending="waivePending === item.id"
              @click="onWaive(item)"
            >
              确认暂无证据仍要冻结
            </AppButton>
          </template>
          <p v-else class="fine">此条尚未保存，先返回保存正式字段后才能处理。</p>
        </div>
      </div>

      <section class="freeze__impact">
        <p class="freeze__panel-title">冻结后的影响</p>
        <div class="impact-grid">
          <div class="impact">
            <span class="impact__icon impact__icon--blue"><AppIcon name="lock" :size="16" /></span>
            <p class="impact__name">内容将被冻结</p>
            <p class="impact__desc">生成不可编辑的快照；快照可查看，不可修改。</p>
          </div>
          <div class="impact">
            <span class="impact__icon impact__icon--green"><AppIcon name="send" :size="16" /></span>
            <p class="impact__name">可用于文件导出</p>
            <p class="impact__desc">PDF 与 DOCX 始终基于同一冻结内容生成。</p>
          </div>
          <div class="impact">
            <span class="impact__icon impact__icon--orange"><AppIcon name="edit" :size="16" /></span>
            <p class="impact__name">后续编辑新建版本</p>
            <p class="impact__desc">继续编辑基于当前主档，不影响已冻结快照。</p>
          </div>
        </div>
      </section>

      <p v-if="unresolved.length" class="fine">
        存在未关联证据的描述，建议先补充证据以提升可信度；确无证据可逐条确认后冻结。
      </p>
    </div>

    <template #footer>
      <AppButton variant="ghost" :disabled="freezePending" @click="emit('close')">返回补证</AppButton>
      <AppButton :disabled="!canFreeze" :pending="freezePending" @click="emit('freeze')">
        <AppIcon name="lock" :size="15" />
        冻结并创建快照
      </AppButton>
    </template>
  </AppModal>
</template>

<style scoped>
.freeze {
  display: grid;
  gap: 14px;
}

.freeze__content {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 14px;
}

.freeze__icon {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: var(--success-soft);
  color: var(--color-success);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.freeze__who {
  display: grid;
}

.freeze__name {
  font-size: 14px;
  font-weight: 500;
}

.freeze__panel {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 14px;
  display: grid;
  gap: 8px;
}

.freeze__panel-title {
  font-size: 13px;
  font-weight: 600;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.check__icon {
  display: inline-flex;
  flex-shrink: 0;
}

.check__icon--ok {
  color: var(--color-success);
}

.check__icon--warn {
  color: var(--color-warning);
}

.check__icon--bad {
  color: var(--color-danger);
}

.check__label {
  flex: 1;
  color: var(--text-secondary);
}

.check__value {
  font-weight: 500;
}

.check__value.is-warn {
  color: var(--color-warning);
}

.waive-list {
  display: grid;
  gap: 10px;
}

.waive {
  border: 1px solid color-mix(in srgb, var(--color-warning) 35%, var(--border-subtle));
  background: var(--warning-soft);
  border-radius: var(--radius);
  padding: 10px 12px;
  display: grid;
  gap: 8px;
  justify-items: start;
}

.waive__text {
  font-size: 13px;
  color: var(--text);
}

.waive__ack {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
}

.freeze__impact {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 14px;
  display: grid;
  gap: 10px;
}

.impact-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.impact {
  display: grid;
  gap: 4px;
  justify-items: start;
}

.impact__icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
}

.impact__icon--blue {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.impact__icon--green {
  background: var(--success-soft);
  color: var(--color-success);
}

.impact__icon--orange {
  background: var(--warning-soft);
  color: var(--color-warning);
}

.impact__name {
  font-size: 13px;
  font-weight: 500;
}

.impact__desc {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.5;
}

@media (max-width: 640px) {
  .impact-grid {
    grid-template-columns: 1fr;
  }
}
</style>
