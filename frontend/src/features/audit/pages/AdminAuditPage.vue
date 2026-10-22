<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, Search, ShieldCheck } from 'lucide-vue-next'
import { api } from '@/shared/api/client'
import { withQuery } from '@/shared/lib/query'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import PageState from '@/shared/ui/PageState.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiCard from '@/shared/ui/UiCard.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiPagination from '@/shared/ui/UiPagination.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
type Event = { id: string; actorId: string; actorLabel?: string; action: string; objectType: string; objectId?: string; summary: string; result: string; source: string; traceId?: string; createdAt: string }
const actor = ref(''), action = ref(''), objectType = ref(''), objectId = ref(''), from = ref(''), to = ref(''), page = ref(0)
const selected = ref<Event | null>(null)
const state = useLoadState(() => api<{ items: Event[]; total: number }>(withQuery('/api/v1/admin/audit-events', {
  actor: actor.value, action: action.value, objectType: objectType.value, objectId: objectId.value,
  from: from.value ? new Date(from.value).toISOString() : undefined,
  to: to.value ? new Date(to.value).toISOString() : undefined, page: page.value, size: 20,
})))
const data = computed(() => state.data.value)
async function search() { page.value = 0; await state.load() }
async function changePage(value: number) { page.value = value; await state.load() }
onMounted(state.load)
</script>
<template>
  <div class="page-container page-container--wide audit-page">
    <UiPageHeader title="审计日志" description="查看关键操作的时间、操作人和结果，追溯配置与交易变更。">
      <template #actions><UiButton variant="secondary" :icon="RefreshCw" :pending="state.loading.value" @click="state.load">刷新</UiButton></template>
    </UiPageHeader>
    <UiCard><form class="audit-filters" @submit.prevent="search">
      <UiField id="audit-actor" label="操作人"><UiInput id="audit-actor" v-model="actor" placeholder="账号 ID 或邮箱" /></UiField>
      <UiField id="audit-action" label="动作"><UiInput id="audit-action" v-model="action" placeholder="如 WALLET_REDEEMED" /></UiField>
      <UiField id="audit-object" label="对象类型"><UiInput id="audit-object" v-model="objectType" placeholder="如 REDEEM_CODE" /></UiField>
      <UiField id="audit-id" label="对象编号"><UiInput id="audit-id" v-model="objectId" placeholder="对象 ID" /></UiField>
      <UiField id="audit-from" label="开始时间"><AppDatePicker id="audit-from" v-model="from" mode="datetime" label="开始时间" /></UiField>
      <UiField id="audit-to" label="截止时间（不含）"><AppDatePicker id="audit-to" v-model="to" mode="datetime" label="截止时间（不含）" /></UiField>
      <UiButton type="submit" :icon="Search" :pending="state.loading.value">查询日志</UiButton>
    </form></UiCard>
    <PageState :loading="state.loading.value" :error="state.error.value" :loaded="state.loaded.value" @retry="state.load">
      <UiCard padding="none">
        <UiEmptyState v-if="!data?.items.length" :icon="ShieldCheck" title="没有匹配的操作记录" description="关键操作完成后会自动记录，也可以调整筛选条件。" />
        <template v-else>
        <p class="audit-table-hint">左右滑动查看完整记录，点击“查看”打开详情。</p>
        <div class="audit-table-wrap" role="region" aria-label="审计日志记录" tabindex="0"><table class="audit-table"><thead><tr><th>时间</th><th>操作人</th><th>动作 / 对象</th><th>摘要</th><th>详情</th></tr></thead>
          <tbody><tr v-for="event in data.items" :key="event.id"><td>{{ formatWhen(event.createdAt) }}</td><td>{{ event.actorLabel || event.actorId || '系统' }}</td><td><strong>{{ event.action }}</strong><small>{{ event.objectType }} · {{ event.result === 'RECORDED' ? '已记录' : event.result }}</small></td><td>{{ event.summary || '—' }}</td><td><UiButton size="sm" variant="ghost" @click="selected=event">查看</UiButton></td></tr></tbody></table></div>
        </template>
      </UiCard>
      <UiPagination v-if="data && data.total > 20" :page="page" :page-count="Math.ceil(data.total / 20)" :disabled="state.loading.value" @update:page="changePage">共 {{ data.total }} 条</UiPagination>
    </PageState>
    <UiDrawer :open="!!selected" title="操作详情" description="日志只保留必要信息，敏感内容已隐藏。" @close="selected=null">
      <dl v-if="selected" class="audit-detail"><dt>记录编号</dt><dd>{{ selected.id }}</dd><dt>发生时间</dt><dd>{{ formatWhen(selected.createdAt) }}</dd><dt>操作人</dt><dd>{{ selected.actorLabel || '系统' }}<small>{{ selected.actorId }}</small></dd><dt>动作</dt><dd>{{ selected.action }}</dd><dt>对象</dt><dd>{{ selected.objectType }}<small>{{ selected.objectId }}</small></dd><dt>结果</dt><dd>{{ selected.result === 'RECORDED' ? '已记录' : selected.result }}</dd><dt>摘要</dt><dd>{{ selected.summary || '—' }}</dd><template v-if="selected.traceId"><dt>请求编号</dt><dd>{{ selected.traceId }}</dd></template></dl>
    </UiDrawer>
  </div>
</template>
<style scoped>
.audit-page { display: grid; grid-template-columns: minmax(0,1fr); gap: var(--space-5); }
.audit-filters { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); align-items: end; gap: var(--space-4); }
.audit-table-wrap { overflow-x: auto; }
.audit-table-wrap:focus-visible { outline: none; box-shadow: var(--focus-ring); border-radius: var(--radius-lg); }
.audit-table-hint { display: none; }
.audit-table { width: 100%; min-width: 900px; border-collapse: collapse; font-size: var(--fs-sm); }
.audit-table th,.audit-table td { text-align: left; padding: var(--space-4); border-bottom: 1px solid var(--border-subtle); vertical-align: top; }
.audit-table th { color: var(--text-secondary); font-weight: 500; background: var(--surface-2); }
.audit-table td { max-width: 360px; overflow-wrap: anywhere; }
.audit-table th:first-child { min-width: 160px; }
.audit-table th:last-child { min-width: 80px; }
.audit-table small,.audit-detail small { display: block; color: var(--text-tertiary); margin-top: var(--space-1); }
.audit-detail { display: grid; gap: var(--space-2); overflow-wrap: anywhere; }
.audit-detail dt { color: var(--text-tertiary); font-size: var(--fs-xs); margin-top: var(--space-3); }
.audit-detail dd { margin: 0; }
@media(max-width: 900px) {
  .audit-filters { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .audit-table-hint { display: block; margin: 0; padding: var(--space-3) var(--space-4); color: var(--text-secondary); font-size: var(--fs-xs); }
}
@media(max-width: 500px) { .audit-filters { grid-template-columns: 1fr; } }
</style>
