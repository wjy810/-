<script setup lang="ts">
/**
 * 兑换码运营 (docs/phase2/02): issue redeem codes in batches for schools, partners and promotions,
 * follow how many were used, and stop a batch whose codes leaked. Plain codes exist only in the
 * response that created them; the server keeps hashes, so the codes dialog is the one chance to copy.
 */
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { Ban, Check, Copy, Download, Layers, Plus, RefreshCw, Ticket, TicketCheck, TriangleAlert } from 'lucide-vue-next'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import UiStatTile from '@/shared/ui/UiStatTile.vue'
import { confirm } from '@/shared/ui/confirm'
import { toast } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { safeFilename, saveBlob } from '@/shared/lib/download'
import { useLoadState } from '@/shared/lib/useLoadState'
import { createRedeemBatch, fetchRedeemBatches, revokeRedeemBatch } from '../services/membershipApi'
import { batchStatus, codesCsv, endOfDayIso, redemptionRate } from '../labels'
import type { BatchCreated, RedeemBatch } from '../types'

type Duration = '7' | '30' | '90' | '180' | '365'

const DURATIONS: { value: Duration; label: string }[] = [
  { value: '7', label: '7 天' },
  { value: '30', label: '30 天' },
  { value: '90', label: '90 天' },
  { value: '180', label: '180 天' },
  { value: '365', label: '365 天' },
]
const MAX_QUANTITY = 500

const page = useLoadState(() => fetchRedeemBatches())
const batches = computed<RedeemBatch[]>(() => page.data.value ?? [])
const totals = computed(() => {
  const list = batches.value
  const issued = list.reduce((sum, batch) => sum + batch.quantity, 0)
  const redeemed = list.reduce((sum, batch) => sum + batch.redeemed, 0)
  return {
    active: list.filter(batch => batch.status === 'ACTIVE').length,
    issued,
    redeemed,
    rate: redemptionRate(redeemed, issued),
  }
})

const creating = ref(false)
const submitting = ref(false)
const touched = ref(false)
const form = reactive({ name: '', channel: '', duration: '30' as Duration, quantity: '50', redeemBefore: '' })
const quantity = computed(() => Number(form.quantity))
const nameError = computed(() => (touched.value && !form.name.trim() ? '请填写批次名称，方便之后对账' : null))
const quantityError = computed(() => (touched.value && !(Number.isInteger(quantity.value) && quantity.value >= 1 && quantity.value <= MAX_QUANTITY)
  ? `数量需在 1 到 ${MAX_QUANTITY} 之间` : null))

const created = shallowRef<BatchCreated | null>(null)
const copied = ref(false)
const revoking = ref('')

function openCreate(): void {
  Object.assign(form, { name: '', channel: '', duration: '30', quantity: '50', redeemBefore: '' })
  touched.value = false
  creating.value = true
}

async function submit(): Promise<void> {
  touched.value = true
  if (nameError.value || quantityError.value || submitting.value) return
  submitting.value = true
  try {
    const result = await createRedeemBatch({
      name: form.name.trim(),
      channel: form.channel.trim() || null,
      durationDays: Number(form.duration),
      quantity: quantity.value,
      redeemBefore: form.redeemBefore ? endOfDayIso(form.redeemBefore) : null,
    })
    creating.value = false
    copied.value = false
    created.value = result
    page.data.value = [result.batch, ...batches.value]
  } catch (reason) {
    toast.error(errorMessage(reason, '生成兑换码失败'))
  } finally {
    submitting.value = false
  }
}

async function copyCodes(): Promise<void> {
  if (!created.value) return
  try {
    await navigator.clipboard.writeText(created.value.codes.join('\n'))
    copied.value = true
    toast.success(`已复制 ${created.value.codes.length} 个兑换码`)
  } catch {
    toast.error('浏览器不允许写入剪贴板，请下载 CSV')
  }
}

function downloadCsv(): void {
  const value = created.value
  if (!value) return
  saveBlob(new Blob([codesCsv(value.batch, value.codes)], { type: 'text/csv;charset=utf-8' }), safeFilename(`兑换码-${value.batch.name}.csv`))
  copied.value = true
}

async function closeCodes(): Promise<void> {
  if (!copied.value && !(await confirm({
    title: '还没有保存兑换码',
    message: '关闭后无法再次查看这批兑换码的明文，只能停用后重新生成。确定关闭吗？',
    confirmText: '仍然关闭',
    tone: 'danger',
  }))) return
  created.value = null
}

async function revoke(batch: RedeemBatch): Promise<void> {
  const unused = batch.quantity - batch.redeemed
  if (!(await confirm({
    title: `停用「${batch.name}」？`,
    message: `剩余 ${unused} 个未兑换的码将立即失效，已经兑换的会员不受影响。停用无法撤销。`,
    confirmText: '停用',
    tone: 'danger',
  }))) return
  revoking.value = batch.id
  try {
    const updated = await revokeRedeemBatch(batch.id)
    page.data.value = batches.value.map(item => (item.id === updated.id ? updated : item))
    toast.success('已停用，未兑换的码不能再使用')
  } catch (reason) {
    toast.error(errorMessage(reason, '停用失败'))
  } finally {
    revoking.value = ''
  }
}

onMounted(page.load)
</script>

<template>
  <div class="page-container page-container--wide redeem-admin">
    <UiPageHeader title="兑换码" description="按批次生成专业版兑换码，发给学校、合作机构或活动。明文只在生成时显示一次，系统只保存哈希。">
      <template #actions>
        <UiButton variant="secondary" :icon="RefreshCw" :pending="page.loading.value && page.loaded.value" @click="page.load">刷新</UiButton>
        <UiButton :icon="Plus" @click="openCreate">新建批次</UiButton>
      </template>
    </UiPageHeader>

    <PageState :loading="page.loading.value" :error="page.error.value" :loaded="page.loaded.value" error-title="兑换码批次读取失败" @retry="page.load">
      <template #skeleton>
        <div class="redeem-admin__stats"><UiSkeleton v-for="n in 4" :key="n" height="76px" radius="var(--radius-lg)" /></div>
        <UiSkeleton height="260px" radius="var(--radius-lg)" />
      </template>

      <div class="redeem-admin__stats">
        <UiStatTile label="可兑换批次" :value="totals.active" :icon="Layers" />
        <UiStatTile label="已生成兑换码" :value="totals.issued" :icon="Ticket" tone="accent" />
        <UiStatTile label="已兑换" :value="totals.redeemed" :icon="TicketCheck" tone="success" />
        <UiStatTile label="整体兑换率" :value="`${totals.rate}%`" :icon="Check" tone="violet" />
      </div>

      <UiEmptyState
        v-if="!batches.length"
        illustration="redeem-ticket"
        title="还没有兑换码批次"
        description="给一所学校或一次活动建一个批次：设定专业版天数和数量，生成后复制或下载发放。"
        bordered
      >
        <UiButton :icon="Plus" @click="openCreate">新建批次</UiButton>
      </UiEmptyState>

      <div v-else class="redeem-admin__table">
        <table>
          <thead>
            <tr><th>批次</th><th>专业版</th><th>兑换进度</th><th>兑换截止</th><th>状态</th><th>创建时间</th><th><span class="sr-only">操作</span></th></tr>
          </thead>
          <tbody>
            <tr v-for="batch in batches" :key="batch.id">
              <td>
                <strong class="batch-name">{{ batch.name }}</strong>
                <small v-if="batch.channel" class="batch-channel">{{ batch.channel }}</small>
              </td>
              <td class="num">{{ batch.durationDays }} 天</td>
              <td>
                <div class="batch-progress">
                  <span class="batch-progress__bar" aria-hidden="true"><i :style="{ width: `${redemptionRate(batch.redeemed, batch.quantity)}%` }" /></span>
                  <span class="num">{{ batch.redeemed }} / {{ batch.quantity }}</span>
                </div>
              </td>
              <td>{{ batch.redeemBefore ? formatWhen(batch.redeemBefore) : '不限' }}</td>
              <td><UiBadge :tone="batchStatus(batch.status).tone" size="sm" dot>{{ batchStatus(batch.status).label }}</UiBadge></td>
              <td>{{ formatWhen(batch.createdAt) }}</td>
              <td class="actions">
                <UiButton
                  v-if="batch.status === 'ACTIVE' || batch.status === 'EXPIRED'"
                  variant="ghost"
                  size="sm"
                  :icon="Ban"
                  :pending="revoking === batch.id"
                  :disabled="Boolean(revoking) && revoking !== batch.id"
                  @click="revoke(batch)"
                >停用</UiButton>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </PageState>

    <UiDialog
      :open="creating"
      title="新建兑换码批次"
      description="每个码可兑换一次专业版；已是专业版的用户兑换后会顺延。"
      width="md"
      :dismissible="!submitting"
      @close="creating = false"
    >
      <form id="redeem-batch-form" class="batch-form" novalidate @submit.prevent="submit">
        <UiField v-slot="{ id }" label="批次名称" required :error="nameError">
          <UiInput :id="id" v-model="form.name" maxlength="120" placeholder="例如：2027 届计算机学院" />
        </UiField>
        <UiField v-slot="{ id }" label="发放渠道" optional hint="写给谁、通过什么方式发的，方便以后对账。">
          <UiInput :id="id" v-model="form.channel" maxlength="120" placeholder="例如：某某大学就业指导中心" />
        </UiField>
        <UiField label="专业版天数">
          <UiSegmented v-model="form.duration" :items="DURATIONS" aria-label="专业版天数" block />
        </UiField>
        <div class="batch-form__row">
          <UiField v-slot="{ id }" label="数量" required :error="quantityError" :hint="`单批最多 ${MAX_QUANTITY} 个`">
            <UiInput :id="id" v-model="form.quantity" type="number" min="1" :max="MAX_QUANTITY" inputmode="numeric" />
          </UiField>
          <UiField v-slot="{ id }" label="兑换截止" optional hint="当天 23:59 后不能再兑换">
            <AppDatePicker :id="id" v-model="form.redeemBefore" mode="date" label="兑换截止" placeholder="不限" />
          </UiField>
        </div>
      </form>
      <template #footer>
        <UiButton variant="secondary" :disabled="submitting" @click="creating = false">取消</UiButton>
        <UiButton type="submit" form="redeem-batch-form" :pending="submitting" :icon="Ticket">生成兑换码</UiButton>
      </template>
    </UiDialog>

    <UiDialog
      :open="Boolean(created)"
      :title="created ? `「${created.batch.name}」的 ${created.codes.length} 个兑换码` : ''"
      width="md"
      :dismissible="false"
      @close="closeCodes"
    >
      <template v-if="created">
        <p class="codes-warning"><TriangleAlert :size="16" />这是唯一一次显示明文。请现在复制或下载，关闭后系统也无法找回。</p>
        <ol class="codes" aria-label="兑换码列表">
          <li v-for="code in created.codes" :key="code"><code>{{ code }}</code></li>
        </ol>
      </template>
      <template #footer>
        <UiButton variant="secondary" :icon="Download" @click="downloadCsv">下载 CSV</UiButton>
        <UiButton variant="secondary" :icon="Copy" @click="copyCodes">复制全部</UiButton>
        <UiButton @click="closeCodes">完成</UiButton>
      </template>
    </UiDialog>
  </div>
</template>

<style scoped>
.redeem-admin {
  display: grid;
  gap: var(--space-5);
}

.redeem-admin :deep(.page-state) {
  display: grid;
  gap: var(--space-5);
}

.redeem-admin__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.redeem-admin__table {
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.redeem-admin__table table {
  width: 100%;
  min-width: 860px;
  border-collapse: collapse;
  font-size: var(--fs-sm);
}

.redeem-admin__table th,
.redeem-admin__table td {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
}

.redeem-admin__table tbody tr:last-child td {
  border-bottom: 0;
}

.redeem-admin__table th {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 600;
  background: var(--surface-2);
}

.redeem-admin__table tbody tr {
  transition: background-color var(--dur-fast) var(--ease-out);
}

.redeem-admin__table tbody tr:hover {
  background: var(--surface-2);
}

.batch-name {
  display: block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 600;
}

.batch-channel {
  display: block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.num {
  font-variant-numeric: tabular-nums;
}

.batch-progress {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.batch-progress__bar {
  width: 96px;
  height: 6px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: var(--surface-3);
}

.batch-progress__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-success);
  transition: width var(--dur-slow) var(--ease-out);
}

.actions {
  text-align: right;
}

.batch-form {
  display: grid;
  gap: var(--space-4);
}

.batch-form__row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.codes-warning {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: var(--space-3);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  color: var(--color-warning-text);
  background: var(--color-warning-soft);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.codes-warning svg {
  flex: none;
  margin-top: 2px;
}

.codes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 6px;
  max-height: 46vh;
  margin: 0;
  padding: var(--space-3);
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-2);
  list-style: none;
}

.codes code {
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.04em;
  user-select: all;
}

@media (max-width: 1050px) {
  .redeem-admin__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .batch-form__row {
    grid-template-columns: 1fr;
  }

  .redeem-admin__stats {
    gap: var(--space-2);
  }
}
</style>
