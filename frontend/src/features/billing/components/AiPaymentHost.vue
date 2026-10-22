<script setup lang="ts">
import { computed } from 'vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import { paymentState, settlePayment } from '../payment'
const current = computed(() => paymentState.current)
const money = (value: number) => Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 8 })
</script>
<template>
  <UiDialog :open="!!current" title="确认本次 AI 使用" width="md" @close="settlePayment(false)">
    <div v-if="current" class="ai-payment">
      <p class="ai-payment__caption">平台通道 · {{ current.quote.name }}</p>
      <div class="ai-payment__amount">¥{{ money(current.quote.total) }}<span>本次 {{ current.quote.units }} 次</span></div>
      <p>剩余 {{ current.quote.remainingQuota }} 次月度额度不足以完成本次任务。本次确认后使用余额，保留剩余额度；失败部分不收费。</p>
      <dl><dt>每次价格</dt><dd>¥{{ money(current.quote.unitPrice) }}</dd><dt>可用余额</dt><dd>¥{{ money(current.quote.availableBalance) }}</dd></dl>
      <p v-if="current.quote.unavailable" role="alert" class="ai-payment__notice">{{ current.quote.unavailable }}</p>
      <div class="ai-payment__links">
        <RouterLink to="/account/wallet" @click="settlePayment(false)">前往钱包充值</RouterLink>
        <a v-if="current.shop?.enabled && current.shop.url" :href="current.shop.url" target="_blank" rel="noopener noreferrer">{{ current.shop.name }} ↗</a>
      </div>
      <p v-if="current.shop?.enabled" class="ai-payment__caption">{{ current.shop.description }}</p>
    </div>
    <template #footer><UiButton variant="secondary" @click="settlePayment(false)">取消</UiButton><UiButton :disabled="!!current?.quote.unavailable" @click="settlePayment(true)">确认使用余额 ¥{{ money(current?.quote.total ?? 0) }}</UiButton></template>
  </UiDialog>
</template>
<style scoped>
.ai-payment { display: grid; gap: var(--space-4); }
.ai-payment p { color: var(--text-secondary); font-size: var(--fs-sm); line-height: 1.7; }
.ai-payment__amount { font-family: var(--font-sans); font-size: var(--fs-h1); display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-3); }
.ai-payment__amount span { font-family: var(--font-sans); font-size: var(--fs-sm); color: var(--text-secondary); }
.ai-payment dl { display: grid; grid-template-columns: 1fr auto; gap: var(--space-2); padding: var(--space-4); background: var(--surface-2); border-radius: var(--radius-md); }
.ai-payment dd { margin: 0; font-variant-numeric: tabular-nums; }
.ai-payment__links { display: flex; flex-wrap: wrap; gap: var(--space-4); }
.ai-payment__links a { color: var(--color-primary-text); text-decoration: underline; text-underline-offset: 3px; }
.ai-payment__notice { padding: var(--space-3); border: 1px solid var(--border-default); border-radius: var(--radius-md); }
</style>
