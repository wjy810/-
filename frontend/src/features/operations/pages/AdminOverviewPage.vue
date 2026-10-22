<script setup lang="ts">
/**
 * 运营概览 (docs/phase2/04): sign-ups and returning users, how far seekers get, Pro and redeem codes,
 * module use and AI. Seekers only. One chart per measure; every number is also in the data table.
 */
import { computed, onMounted } from 'vue'
import { Activity, ArrowRight, Crown, RefreshCw, Sparkles, Users } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import UiStatTile from '@/shared/ui/UiStatTile.vue'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import DailyColumns from '../components/DailyColumns.vue'
import { compact, sum } from '../format'
import { fetchOperationsOverview } from '../services/operationsApi'
import type { OperationsOverview } from '../types'

const page = useLoadState(() => fetchOperationsOverview())
const data = computed<OperationsOverview | null>(() => page.data.value)

const series = computed(() => {
  const days = data.value?.days ?? []
  return {
    signups: days.map(day => ({ day: day.day, value: day.signups })),
    applications: days.map(day => ({ day: day.day, value: day.applications })),
    opens: days.map(day => ({ day: day.day, value: day.opens })),
  }
})
const funnelBase = computed(() => Math.max(1, data.value?.funnel[0]?.accounts ?? 0))
// The API leaves out empty fields, so a missing rate arrives as undefined.
const rateText = (key: string, rate?: number | null) => {
  if (rate == null) return key === 'registered' ? '起点' : '—'
  return key === 'pro' ? `占注册 ${rate}%` : `较上一步 ${rate}%`
}

onMounted(page.load)
</script>

<template>
  <div class="page-container page-container--wide ops">
    <UiPageHeader title="运营概览" description="注册、回访、转化与各模块的使用情况。只统计求职者账号，管理员与运营账号不计入。">
      <template #actions>
        <span v-if="data" class="ops__stamp">更新于 {{ formatWhen(data.generatedAt) }}</span>
        <UiButton variant="secondary" :icon="RefreshCw" :pending="page.loading.value && page.loaded.value" @click="page.load">刷新</UiButton>
      </template>
    </UiPageHeader>

    <PageState :loading="page.loading.value" :error="page.error.value" :loaded="page.loaded.value" error-title="运营数据读取失败" @retry="page.load">
      <template #skeleton>
        <div class="ops__tiles"><UiSkeleton v-for="n in 4" :key="n" height="88px" radius="var(--radius-lg)" /></div>
        <UiSkeleton height="240px" radius="var(--radius-lg)" />
      </template>

      <template v-if="data">
        <div class="ops__tiles" :class="{ 'is-refreshing': page.loading.value }">
          <UiStatTile label="求职者" :value="compact(data.users.total)" :hint="`近 7 天新增 ${data.users.new7}，近 30 天 ${data.users.new30}`" :icon="Users" />
          <UiStatTile label="近 7 天回访" :value="compact(data.users.active7)" :hint="`近 30 天 ${data.users.active30} 人`" :icon="Activity" tone="success" />
          <UiStatTile
            label="专业版用户"
            :value="compact(data.membership.pro)"
            :hint="`${data.membership.proShare == null ? '—' : `占 ${data.membership.proShare}%`} · 近 30 天开通 ${data.membership.redeemed30} 次`"
            :icon="Crown"
            tone="violet"
          />
          <UiStatTile label="本月 AI 次数" :value="compact(data.ai.usedUnits)" :hint="`${data.ai.accounts} 人在用 · 额度 ${data.ai.quotaUnits ?? 0} / 余额 ${data.ai.walletUnits ?? 0} / 个人 ${data.ai.personalUnits ?? 0}`" :icon="Sparkles" tone="accent" />
        </div>

        <section class="ops-card" :class="{ 'is-refreshing': page.loading.value }" aria-labelledby="ops-trend">
          <header class="ops-card__head">
            <h2 id="ops-trend">每日趋势</h2>
            <p>近 14 天合计：新注册 {{ sum(series.signups.map(p => p.value)) }} 人 · 新投递 {{ sum(series.applications.map(p => p.value)) }} 条 · 简历被打开 {{ sum(series.opens.map(p => p.value)) }} 次</p>
          </header>
          <div class="ops__charts">
            <DailyColumns title="新注册" unit="人" :points="series.signups" />
            <DailyColumns title="新投递" unit="条" :points="series.applications" />
            <DailyColumns title="简历被打开" unit="次" :points="series.opens" />
          </div>
          <details class="ops__table">
            <summary>查看每日数据</summary>
            <table>
              <thead><tr><th>日期</th><th>新注册</th><th>新投递</th><th>简历被打开</th></tr></thead>
              <tbody>
                <tr v-for="day in data.days" :key="day.day"><td>{{ day.day }}</td><td>{{ day.signups }}</td><td>{{ day.applications }}</td><td>{{ day.opens }}</td></tr>
              </tbody>
            </table>
          </details>
        </section>

        <div class="ops__split">
          <section class="ops-card" aria-labelledby="ops-funnel">
            <header class="ops-card__head">
              <h2 id="ops-funnel">求职者走到哪一步</h2>
              <p>累计人数；每一步与上一步相比，专业版与注册人数相比。</p>
            </header>
            <ol class="funnel">
              <li v-for="step in data.funnel" :key="step.key">
                <span class="funnel__label">{{ step.label }}</span>
                <span class="funnel__track">
                  <i :style="{ width: `${Math.max(step.accounts > 0 ? 2 : 0, (step.accounts / funnelBase) * 100)}%` }" />
                  <b>{{ compact(step.accounts) }}</b>
                </span>
                <span class="funnel__rate">{{ rateText(step.key, step.rate) }}</span>
              </li>
            </ol>
          </section>

          <section class="ops-card" aria-labelledby="ops-modules">
            <header class="ops-card__head">
              <h2 id="ops-modules">模块使用</h2>
              <p>求职者产生的记录数。</p>
            </header>
            <table class="modules">
              <thead><tr><th>模块</th><th>累计</th><th>近 7 天</th></tr></thead>
              <tbody>
                <tr v-for="module in data.modules" :key="module.key">
                  <td>{{ module.label }}</td>
                  <td>{{ compact(module.total) }}</td>
                  <td :class="{ 'is-up': module.last7 > 0 }">{{ module.last7 > 0 ? `+${compact(module.last7)}` : '0' }}</td>
                </tr>
              </tbody>
            </table>
            <div class="redeem-line">
              <span>平台兑换码库存：已生成 {{ compact(data.membership.codesIssued) }} · 已兑换 {{ compact(data.membership.codesRedeemed) }} · 可兑换批次 {{ data.membership.activeBatches }}</span>
              <UiButton size="sm" variant="ghost" to="/admin/redeem-codes" :icon-right="ArrowRight">管理</UiButton>
            </div>
          </section>
        </div>
      </template>
    </PageState>
  </div>
</template>

<style scoped>
.ops {
  display: grid;
  gap: var(--space-5);
}

.ops :deep(.page-state) {
  display: grid;
  gap: var(--space-5);
}

.ops__stamp {
  align-self: center;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.ops__tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.is-refreshing {
  opacity: 0.6;
  transition: opacity var(--dur-base) var(--ease-out);
}

.ops-card {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  min-width: 0;
}

.ops-card__head h2 {
  font-size: var(--fs-h3);
  font-weight: 650;
}

.ops-card__head p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.ops__charts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-6);
}

.ops__table summary {
  width: fit-content;
  color: var(--color-primary-text);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.ops__table table,
.modules {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-sm);
  font-variant-numeric: tabular-nums;
}

.ops__table table {
  margin-top: var(--space-3);
}

.ops__table th,
.ops__table td,
.modules th,
.modules td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-subtle);
  text-align: right;
}

.ops__table th:first-child,
.ops__table td:first-child,
.modules th:first-child,
.modules td:first-child {
  text-align: left;
}

.ops__table th,
.modules th {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 600;
}

.modules td.is-up {
  color: var(--color-success-text);
  font-weight: 600;
}

.ops__split {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.funnel {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.funnel li {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) 96px;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--fs-sm);
}

.funnel__label {
  color: var(--text-secondary);
}

.funnel__track {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.funnel__track i {
  display: block;
  height: 14px;
  border-radius: 0 4px 4px 0;
  background: var(--color-primary);
  opacity: 0.85;
}

.funnel__track b {
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.funnel__rate {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  text-align: right;
}

.redeem-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding-top: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

@media (max-width: 1100px) {
  .ops__tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ops__split {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .ops__charts {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .ops__tiles {
    gap: var(--space-2);
  }

  .funnel li {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .funnel__rate {
    grid-column: 2;
    text-align: left;
  }
}
</style>
