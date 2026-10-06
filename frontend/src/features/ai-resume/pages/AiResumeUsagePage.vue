<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { errorMessage } from '@/shared/api/types'
import AppButton from '@/shared/ui/AppButton.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { fetchAiResumeQuota } from '../services/aiResumeApi'
import type { AiQuota } from '../types'

const quota = ref<AiQuota | null>(null)
const loading = ref(true)
const pageError = ref('')
useToastFeedback(pageError, 'error', 'ai-resume-usage-error')

const quotaPercent = computed(() => {
  const total = quota.value?.grantedUnits ?? 0
  const used = quota.value?.usedUnits ?? 0
  return total > 0 ? Math.min(100, Math.max(0, Math.round((used / total) * 100))) : 0
})

async function load(): Promise<void> {
  loading.value = true
  try {
    quota.value = await fetchAiResumeQuota()
    pageError.value = ''
  } catch (reason) {
    pageError.value = errorMessage(reason, 'AI 额度读取失败')
  } finally {
    loading.value = false
  }
}

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
        <AppButton variant="ghost" :pending="loading" :disabled="loading" @click="load">
          <AppIcon name="refresh" :size="15" />{{ loading ? '刷新中' : '刷新数据' }}
        </AppButton>
      </div>
    </header>

    <section class="settings-card quota-overview">
      <div class="quota-overview__main">
        <div class="quota-overview__title">
          <span><AppIcon name="sparkles" :size="20" /></span>
          <div><small>{{ quota?.periodKey || '本月' }} AI 额度</small><strong>{{ loading ? '正在读取…' : `${quota?.remainingUnits ?? 0} 次可用` }}</strong></div>
          <AppTag tone="blue">SYSTEM 通道</AppTag>
        </div>
        <div class="quota-progress" :aria-label="`本月额度已使用 ${quotaPercent}%`">
          <span><i :style="{ width: `${quotaPercent}%` }" /></span>
          <small>本月已使用 {{ quotaPercent }}%</small>
        </div>
      </div>
      <div class="quota-metrics">
        <div><small>总额度</small><strong>{{ quota?.grantedUnits ?? 0 }}</strong><span>次调用</span></div>
        <div><small>已使用</small><strong>{{ quota?.usedUnits ?? 0 }}</strong><span>次调用</span></div>
        <div><small>任务预占</small><strong>{{ quota?.heldUnits ?? 0 }}</strong><span>处理中</span></div>
      </div>
    </section>

    <div class="usage-grid">
      <section class="settings-card policy-card">
        <header class="card__head"><div><h3 class="card__title">调用规则</h3><p class="card__sub">平台统一管理模型和调用额度</p></div></header>
        <div class="card__body policy-list">
          <article>
            <span><AppIcon name="settings" :size="17" /></span>
            <div><strong>模型与密钥</strong><p>由管理员验收 SYSTEM 通道，你无需选择模型或录入个人 Key。</p></div>
          </article>
          <article>
            <span><AppIcon name="refresh" :size="17" /></span>
            <div><strong>失败返还</strong><p>没有产生可用结果的系统失败或取消，会自动返还预占次数。</p></div>
          </article>
          <article>
            <span><AppIcon name="clock" :size="17" /></span>
            <div><strong>额度结算</strong><p>成功生成一次可用结果后结算；主动重新生成会消耗新的次数。</p></div>
          </article>
        </div>
      </section>

      <section class="settings-card boundary-card">
        <header class="card__head"><div><h3 class="card__title">数据使用边界</h3><p class="card__sub">只在完成任务所需范围内使用数据</p></div><span class="boundary-card__badge"><AppIcon name="shield" :size="15" />受保护</span></header>
        <div class="card__body">
          <ul>
            <li><AppIcon name="check" :size="14" /><span>仅发送已确认事实、当前分支和必要对话摘要</span></li>
            <li><AppIcon name="check" :size="14" /><span>照片、原始文件和未授权资料不会发送给模型</span></li>
            <li><AppIcon name="check" :size="14" /><span>AI 候选不会自动覆盖正式简历内容</span></li>
          </ul>
          <RouterLink class="btn btn--primary usage-entry" to="/ai-resume/new"><AppIcon name="file-text" :size="15" />进入 AI 简历工作台</RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.quota-overview { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(330px, 1fr); }
.quota-overview__main { display: grid; align-content: center; gap: 22px; padding: 24px; border-right: 1px solid var(--border); }
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
.quota-metrics > div { min-width: 0; display: grid; justify-items: center; gap: 1px; padding: 12px; border-right: 1px solid var(--border); }
.quota-metrics > div:last-child { border-right: 0; }
.quota-metrics small, .quota-metrics span { color: var(--text-tertiary); font-size: 12px; }
.quota-metrics strong { font-size: 20px; }
.usage-grid { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(320px, 0.92fr); gap: 18px; align-items: start; }
.policy-list { display: grid; gap: 3px; }
.policy-list article { display: flex; align-items: flex-start; gap: 11px; padding: 13px 0; border-bottom: 1px solid var(--border); }
.policy-list article:last-child { border-bottom: 0; }
.policy-list article > span { width: 34px; height: 34px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 8px; background: var(--surface-2); color: var(--color-primary); }
.policy-list strong { font-size: 13px; }
.policy-list p { margin-top: 3px; color: var(--text-secondary); font-size: 12px; line-height: 1.65; }
.boundary-card__badge { display: inline-flex; align-items: center; gap: 5px; padding: 4px 8px; border-radius: 999px; background: var(--success-soft); color: var(--color-success); font-size: 12px; font-weight: 600; }
.boundary-card ul { display: grid; gap: 11px; margin: 0 0 20px; padding: 0; list-style: none; }
.boundary-card li { display: flex; align-items: flex-start; gap: 8px; color: var(--text-secondary); font-size: 12px; line-height: 1.6; }
.boundary-card li .app-icon { margin-top: 3px; flex: 0 0 auto; color: var(--color-success); }
.usage-entry { width: 100%; justify-content: center; }
@media (max-width: 1120px) { .quota-overview, .usage-grid { grid-template-columns: 1fr; } .quota-overview__main { border-right: 0; border-bottom: 1px solid var(--border); } }
@media (max-width: 620px) { .quota-overview__main { padding: 20px 16px; } .quota-overview__title { align-items: flex-start; flex-wrap: wrap; } .quota-overview__title > div { min-width: calc(100% - 60px); } .quota-overview__title strong { font-size: 22px; } .quota-metrics { padding: 12px 6px; } .quota-metrics > div { padding-inline: 6px; } }
@media (prefers-reduced-motion: reduce) { .quota-progress i { transition: none; } }
</style>
