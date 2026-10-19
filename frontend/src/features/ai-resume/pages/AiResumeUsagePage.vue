<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { FileText, RefreshCw } from 'lucide-vue-next'
import { useLoadState } from '@/shared/lib/useLoadState'
import AppIcon from '@/shared/ui/AppIcon.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { fetchAiResumeQuota } from '../services/aiResumeApi'

const { data: quota, error, loading, loaded, load } = useLoadState(() => fetchAiResumeQuota())

const quotaPercent = computed(() => {
  const total = quota.value?.grantedUnits ?? 0
  const used = quota.value?.usedUnits ?? 0
  return total > 0 ? Math.min(100, Math.max(0, Math.round((used / total) * 100))) : 0
})
const periodLabel = computed(() => {
  const match = quota.value?.periodKey?.match(/^(\d{4})-(\d{2})$/)
  return match ? `${match[1]} 年 ${Number(match[2])} 月` : quota.value?.periodKey || '本月'
})

onMounted(load)
</script>

<template>
  <div class="settings-section usage-page">
    <header class="settings-section__head">
      <div class="settings-section__title">
        <span class="settings-section__icon"><AppIcon name="sparkles" :size="18" /></span>
        <div>
          <h2>AI 使用与授权</h2>
          <p>查看本月额度、调用规则和发送给模型的数据边界。</p>
        </div>
      </div>
      <div class="settings-section__actions">
        <UiButton variant="secondary" :icon="RefreshCw" :pending="loading" @click="load">{{ loading ? '刷新中' : '刷新数据' }}</UiButton>
      </div>
    </header>

    <PageState :loading="loading" :error="error" :loaded="loaded" error-title="AI 额度读取失败" compact @retry="load">
      <template #skeleton>
        <section class="settings-card quota-overview" aria-busy="true">
          <div class="quota-overview__main"><UiSkeleton height="44px" width="60%" /><UiSkeleton height="7px" /></div>
          <div class="quota-metrics"><UiSkeleton height="48px" /><UiSkeleton height="48px" /><UiSkeleton height="48px" /></div>
        </section>
      </template>
      <section v-if="quota" class="settings-card quota-overview">
        <div class="quota-overview__main">
          <div class="quota-overview__title">
            <span><AppIcon name="sparkles" :size="20" /></span>
            <div><small>{{ periodLabel }} AI 额度</small><strong>{{ quota.remainingUnits }} 次可用</strong></div>
            <UiBadge tone="primary">平台提供</UiBadge>
          </div>
          <div class="quota-progress" :aria-label="`本月额度已使用 ${quotaPercent}%`">
            <span><i :style="{ width: `${quotaPercent}%` }" /></span>
            <small>本月已使用 {{ quotaPercent }}%</small>
          </div>
        </div>
        <div class="quota-metrics">
          <div><small>总额度</small><strong>{{ quota.grantedUnits }}</strong><span>次调用</span></div>
          <div><small>已使用</small><strong>{{ quota.usedUnits }}</strong><span>次调用</span></div>
          <div><small>处理中占用</small><strong>{{ quota.heldUnits }}</strong><span>次调用</span></div>
        </div>
      </section>
    </PageState>

    <div class="usage-grid">
      <section class="settings-card policy-card">
        <header class="usage-card__head"><div><h3 class="usage-card__title">调用规则</h3><p class="usage-card__sub">平台统一管理模型和调用额度</p></div></header>
        <div class="usage-card__body policy-list">
          <article>
            <span><AppIcon name="settings" :size="17" /></span>
            <div><strong>模型与密钥</strong><p>模型由平台统一提供和维护，你无需选择模型或填写密钥。</p></div>
          </article>
          <article>
            <span><AppIcon name="refresh" :size="17" /></span>
            <div><strong>失败返还</strong><p>处理中的任务会先占用次数；因系统原因失败或被取消、没有得到可用结果时，占用的次数会自动返还。</p></div>
          </article>
          <article>
            <span><AppIcon name="clock" :size="17" /></span>
            <div><strong>额度结算</strong><p>成功生成一次可用结果后结算；主动重新生成会消耗新的次数。</p></div>
          </article>
        </div>
      </section>

      <section class="settings-card boundary-card">
        <header class="usage-card__head"><div><h3 class="usage-card__title">数据使用边界</h3><p class="usage-card__sub">只在完成任务所需范围内使用数据</p></div><span class="boundary-card__badge"><AppIcon name="shield" :size="15" />受保护</span></header>
        <div class="usage-card__body">
          <ul>
            <li><AppIcon name="check" :size="14" /><span>仅发送已确认事实、当前分支和必要对话摘要</span></li>
            <li><AppIcon name="check" :size="14" /><span>照片、原始文件和未授权资料不会发送给模型</span></li>
            <li><AppIcon name="check" :size="14" /><span>AI 候选不会自动覆盖正式简历内容</span></li>
          </ul>
          <UiButton to="/ai-resume/new" :icon="FileText" block>进入 AI 简历工作台</UiButton>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.quota-overview { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(330px, 1fr); }
.quota-overview__main { display: grid; align-content: center; gap: 22px; padding: 24px; border-right: 1px solid var(--border-default); }
.quota-overview__title { display: flex; align-items: center; gap: 13px; }
.quota-overview__title > span:first-child { width: 44px; height: 44px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 10px; background: var(--color-primary); color: var(--text-on-primary); box-shadow: 0 7px 16px color-mix(in srgb, var(--color-primary) 20%, transparent); }
.quota-overview__title > div { display: grid; flex: 1; }
.quota-overview__title small { color: var(--text-tertiary); font-size: 12.5px; }
.quota-overview__title strong { margin-top: 1px; font-size: 26px; letter-spacing: 0; }
.quota-progress { display: grid; gap: 7px; }
.quota-progress > span { height: 7px; overflow: hidden; border-radius: 999px; background: var(--surface-2); }
.quota-progress i { display: block; height: 100%; border-radius: inherit; background: var(--color-primary); transition: width 380ms cubic-bezier(0.2, 0.8, 0.2, 1); }
.quota-progress small { color: var(--text-tertiary); font-size: 12px; }
.quota-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); align-items: center; padding: 22px 18px; background: var(--surface-1); }
.quota-metrics > div { min-width: 0; display: grid; justify-items: center; gap: 1px; padding: 12px; border-right: 1px solid var(--border-default); }
.quota-metrics > div:last-child { border-right: 0; }
.quota-metrics small, .quota-metrics span { color: var(--text-tertiary); font-size: 12px; }
.quota-metrics strong { font-size: 20px; }
.usage-card__head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-3); padding: var(--space-5) var(--space-5) var(--space-1); }
.usage-card__title { font-size: var(--fs-h3); font-weight: 600; line-height: var(--lh-h3); }
.usage-card__sub { margin-top: 2px; color: var(--text-tertiary); font-size: var(--fs-xs); }
.usage-card__body { padding: var(--space-3) var(--space-5) var(--space-5); }
.usage-grid { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(320px, 0.92fr); gap: 18px; align-items: start; }
.policy-list { display: grid; gap: 3px; }
.policy-list article { display: flex; align-items: flex-start; gap: 11px; padding: 13px 0; border-bottom: 1px solid var(--border-default); }
.policy-list article:last-child { border-bottom: 0; }
.policy-list article > span { width: 34px; height: 34px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 8px; background: var(--surface-2); color: var(--color-primary); }
.policy-list strong { font-size: 13px; }
.policy-list p { margin-top: 3px; color: var(--text-secondary); font-size: 12px; line-height: 1.65; }
.boundary-card__badge { display: inline-flex; align-items: center; gap: 5px; padding: 4px 8px; border-radius: 999px; background: var(--color-success-soft); color: var(--color-success); font-size: 12px; font-weight: 600; }
.boundary-card ul { display: grid; gap: 11px; margin: 0 0 20px; padding: 0; list-style: none; }
.boundary-card li { display: flex; align-items: flex-start; gap: 8px; color: var(--text-secondary); font-size: 12px; line-height: 1.6; }
.boundary-card li .app-icon { margin-top: 3px; flex: 0 0 auto; color: var(--color-success); }
@media (max-width: 1120px) { .quota-overview, .usage-grid { grid-template-columns: 1fr; } .quota-overview__main { border-right: 0; border-bottom: 1px solid var(--border-default); } }
@media (max-width: 620px) { .quota-overview__main { padding: 20px 16px; } .quota-overview__title { align-items: flex-start; flex-wrap: wrap; } .quota-overview__title > div { min-width: calc(100% - 60px); } .quota-overview__title strong { font-size: 22px; } .quota-metrics { padding: 12px 6px; } .quota-metrics > div { padding-inline: 6px; } }
@media (prefers-reduced-motion: reduce) { .quota-progress i { transition: none; } }
</style>
