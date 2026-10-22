<script setup lang="ts">
/** 会员与兑换 (docs/phase2/02): the current plan, what it allows this month, and redeeming a code. */
import { catalog as commerceCatalog } from '@/features/billing/services/commerceApi'
import type { Shop } from '@/features/billing/services/commerceApi'
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, Crown, Gift, Link2, RefreshCw, Sparkles, Ticket } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { toast } from '@/shared/ui/toast'
import { errorMessage, isApiClientError } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import { fetchAiResumeQuota } from '@/features/ai-resume/services/aiResumeApi'
import type { AiQuota } from '@/features/ai-resume/types'
import { fetchMembership, redeemCode } from '../services/membershipApi'
import { benefits, formatCodeInput, fullCode, isCompleteCode, planLabel } from '../labels'
import type { Membership } from '../types'

const shop = ref<Shop>()
const route = useRoute()
const page = useLoadState(() => fetchMembership())
const quota = shallowRef<AiQuota | null>(null)
const code = ref('')
const redeeming = ref(false)
const redeemError = ref('')
const justUpgraded = ref(false)
const codeInput = ref<HTMLInputElement | null>(null)

const membership = computed<Membership | null>(() => page.data.value)
const pro = computed(() => membership.value?.plan === 'PRO')
const rows = computed(() => (membership.value ? benefits(membership.value.free, membership.value.pro) : []))
const daysLeft = computed(() => {
  const expires = membership.value?.expiresAt
  return expires ? Math.max(0, Math.ceil((new Date(expires).getTime() - Date.now()) / 86_400_000)) : 0
})
const aiPercent = computed(() => {
  const value = quota.value
  return value && value.grantedUnits ? Math.min(100, Math.round(((value.usedUnits + value.heldUnits) / value.grantedUnits) * 100)) : 0
})
const overLinkLimit = computed(() => Boolean(membership.value && membership.value.activeLinks > membership.value.entitlements.activeLinks))
const linkPercent = computed(() => {
  const value = membership.value
  return value ? Math.min(100, Math.round((value.activeLinks / value.entitlements.activeLinks) * 100)) : 0
})

async function load(): Promise<void> {
  await page.load()
  try {
    quota.value = await fetchAiResumeQuota()
  } catch {
    quota.value = null
  }
}

function onCodeInput(event: Event): void {
  const target = event.target as HTMLInputElement
  code.value = formatCodeInput(target.value)
  target.value = code.value
  redeemError.value = ''
}

async function redeem(): Promise<void> {
  if (!isCompleteCode(code.value) || redeeming.value) return
  redeeming.value = true
  redeemError.value = ''
  const wasPro = pro.value
  try {
    page.data.value = await redeemCode(fullCode(code.value))
    code.value = ''
    justUpgraded.value = page.data.value?.plan === 'PRO' && !wasPro
    try { quota.value = await fetchAiResumeQuota() } catch { /* the plan card still updates */ }
    toast.success('兑换成功，余额或历史会员权益已到账')
  } catch (reason) {
    redeemError.value = isApiClientError(reason) && reason.reason === 'REDEEM_TOO_MANY_ATTEMPTS'
      ? '尝试次数过多，请一小时后再试'
      : errorMessage(reason, '兑换没有成功')
  } finally {
    redeeming.value = false
  }
}

onMounted(async () => {
  try { shop.value = (await commerceCatalog()).shop } catch { /* shop may be unavailable */ }
  await load()
  if (route.hash === '#redeem') {
    await nextTick()
    document.getElementById('redeem')?.scrollIntoView({ block: 'center' })
    codeInput.value?.focus()
  }
})
</script>

<template>
  <div class="settings-section membership-page">
    <div class="commerce-links"><UiButton to="/account/wallet">钱包充值与购买</UiButton> <a v-if="shop?.enabled && shop.url" :href="shop.url" target="_blank" rel="noopener noreferrer">{{shop.name}}</a><p v-if="shop?.enabled && shop.url">{{shop.description}}</p></div>
    <header class="settings-section__head">
      <div class="settings-section__title">
        <span class="settings-section__icon"><Crown :size="18" /></span>
        <div>
          <h2>会员与兑换</h2>
          <p>查看当前套餐与本月可用额度，兑换码充值后，可在钱包购买或续期专业版。</p>
        </div>
      </div>
      <div class="settings-section__actions">
        <UiButton variant="secondary" :icon="RefreshCw" :pending="page.loading.value && page.loaded.value" @click="load">刷新</UiButton>
      </div>
    </header>

    <PageState :loading="page.loading.value" :error="page.error.value" :loaded="page.loaded.value" error-title="会员信息读取失败" compact @retry="load">
      <template #skeleton>
        <UiSkeleton height="180px" radius="var(--radius-lg)" />
        <UiSkeleton height="140px" radius="var(--radius-lg)" />
      </template>

      <template v-if="membership">
        <section class="plan-hero" :class="{ 'is-pro': pro, 'is-celebrating': justUpgraded }" aria-label="当前套餐">
          <div class="plan-hero__copy">
            <UiBadge :tone="pro ? 'ai' : 'neutral'"><Crown v-if="pro" :size="12" />{{ planLabel(membership.plan) }}</UiBadge>
            <h3 v-if="pro">专业版还剩 {{ daysLeft }} 天</h3>
            <h3 v-else>你正在使用免费版</h3>
            <p v-if="pro">有效期至 {{ formatWhen(membership.expiresAt) }}。余额续期会在这之后顺延。</p>
            <p v-else-if="membership.expiredAt">专业版已于 {{ formatWhen(membership.expiredAt) }} 到期，数据都还在；在钱包续期即可恢复专业版额度。</p>
            <p v-else>每月 {{ membership.free.aiMonthlyUnits }} 次 AI，{{ membership.free.activeLinks }} 条有效专属链接。专业版提供 {{ membership.pro.aiMonthlyUnits }} 次 AI 和 {{ membership.pro.activeLinks }} 条链接。</p>
            <div class="plan-hero__actions">
              <UiButton v-if="!pro" :icon="Ticket" @click="codeInput?.focus()">使用兑换码</UiButton>
              <UiButton variant="secondary" to="/pricing" :icon-right="ArrowRight">查看套餐</UiButton>
            </div>
          </div>
          <UiIllustration :name="justUpgraded ? 'redeem-success' : pro ? 'membership-pro' : 'redeem-ticket'" :size="148" class="plan-hero__art" float eager />
        </section>

        <section class="settings-card usage" aria-label="本月用量">
          <div class="usage__item">
            <span class="usage__icon"><Sparkles :size="17" /></span>
            <div class="usage__body">
              <div class="usage__line"><strong>本月 AI 次数</strong><span v-if="quota">{{ quota.remainingUnits }} / {{ quota.grantedUnits }} 可用</span><span v-else>—</span></div>
              <span class="usage__bar" aria-hidden="true"><i :style="{ width: `${aiPercent}%` }" /></span>
              <small>岗位匹配、简历 AI 建议等按次数计；升级当月会立即补足到专业版额度。</small>
            </div>
          </div>
          <div class="usage__item">
            <span class="usage__icon is-link"><Link2 :size="17" /></span>
            <div class="usage__body">
              <div class="usage__line"><strong>有效专属链接</strong><span>{{ membership.activeLinks }} / {{ membership.entitlements.activeLinks }} 条</span></div>
              <span class="usage__bar" aria-hidden="true"><i :class="{ 'is-full': linkPercent >= 100 }" :style="{ width: `${linkPercent}%` }" /></span>
              <small v-if="overLinkLimit" class="is-over">已有链接继续有效；撤回到 {{ membership.entitlements.activeLinks }} 条以内后才能新建。<RouterLink to="/applications">去投递追踪</RouterLink></small>
              <small v-else>撤回或过期的链接不占名额。<RouterLink to="/applications">去投递追踪</RouterLink></small>
            </div>
          </div>
        </section>

        <section id="redeem" class="settings-card redeem" aria-labelledby="redeem-title">
          <div class="redeem__head">
            <span class="usage__icon is-gift"><Gift :size="17" /></span>
            <div>
              <h3 id="redeem-title">兑换码</h3>
              <p>新兑换码充值人民币余额，每个码只能使用一次；历史专业版兑换码仍有效。</p>
            </div>
          </div>
          <form class="redeem__form" @submit.prevent="redeem">
            <label class="redeem__field" :class="{ 'is-invalid': redeemError }">
              <span class="redeem__prefix" aria-hidden="true">JP-</span>
              <input
                ref="codeInput"
                :value="code"
                class="redeem__input"
                placeholder="XXXX-XXXX-XXXX-XXXX"
                autocomplete="off"
                autocapitalize="characters"
                spellcheck="false"
                aria-label="兑换码（JP- 之后的 16 位）"
                :aria-invalid="Boolean(redeemError) || undefined"
                aria-describedby="redeem-message"
                @input="onCodeInput"
              />
            </label>
            <UiButton type="submit" :pending="redeeming" :disabled="!isCompleteCode(code)">兑换</UiButton>
          </form>
          <p id="redeem-message" class="redeem__message" :class="{ 'is-error': redeemError }" role="status">
            {{ redeemError || '不区分大小写，空格和短横线可以不输；整段粘贴（带 JP-）也可以。' }}
          </p>
        </section>

        <section class="settings-card compare" aria-labelledby="compare-title">
          <h3 id="compare-title">两个套餐的区别</h3>
          <table>
            <thead><tr><th>权益</th><th>免费版</th><th :class="{ 'is-current': pro }">专业版</th></tr></thead>
            <tbody>
              <tr v-for="row in rows" :key="row.key">
                <td>{{ row.label }}</td>
                <td>{{ row.free }}</td>
                <td :class="{ 'is-current': pro, 'is-better': row.free !== row.pro }">{{ row.pro }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section v-if="membership.events.length" class="settings-card history" aria-labelledby="history-title">
          <h3 id="history-title">开通记录</h3>
          <ol>
            <li v-for="event in membership.events" :key="event.id">
              <span class="history__dot" aria-hidden="true"><Ticket :size="13" /></span>
              <span class="history__text">
                <strong v-if="event.plan === 'BALANCE'">人民币余额充值</strong><strong v-else>{{ event.type === 'PURCHASED' ? '购买' : '兑换' }} {{ event.days }} 天{{ planLabel(event.plan) }}</strong>
                <small>{{ event.detail || '兑换码' }}<template v-if="event.expiresAt"> · 有效期至 {{ formatWhen(event.expiresAt) }}</template></small>
              </span>
              <time :datetime="event.createdAt">{{ formatWhen(event.createdAt) }}</time>
            </li>
          </ol>
        </section>
      </template>
    </PageState>
  </div>
</template>

<style scoped>
.membership-page :deep(.page-state) {
  display: grid;
  gap: var(--space-4);
}

.plan-hero {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl, 16px);
  background:
    radial-gradient(90% 120% at 0% 0%, var(--color-primary-soft), transparent 60%),
    var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.plan-hero.is-pro {
  border-color: color-mix(in srgb, var(--gradient-ai-via) 35%, var(--border-subtle));
  background:
    radial-gradient(90% 140% at 0% 0%, color-mix(in srgb, var(--gradient-ai-via) 18%, transparent), transparent 62%),
    radial-gradient(60% 100% at 100% 100%, color-mix(in srgb, var(--color-warning) 14%, transparent), transparent 70%),
    var(--surface-1);
}

.plan-hero.is-celebrating {
  animation: plan-glow 1.2s var(--ease-out);
}

.plan-hero__copy {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
}

.plan-hero__copy h3 {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
}

.plan-hero__copy p {
  max-width: 52ch;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.plan-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.usage,
.redeem,
.compare,
.history {
  padding: var(--space-5);
}

.usage {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5);
}

.usage__item {
  display: flex;
  gap: var(--space-3);
  min-width: 0;
}

.usage__icon {
  width: 34px;
  height: 34px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
}

.usage__icon.is-link { color: var(--color-success-text); background: var(--color-success-soft); }
.usage__icon.is-gift { color: var(--color-warning-text); background: var(--color-warning-soft); }

.usage__body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 6px;
}

.usage__line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: var(--fs-sm);
}

.usage__line span {
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.usage__bar {
  height: 6px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: var(--surface-3);
}

.usage__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary);
  transition: width var(--dur-slow, 400ms) var(--ease-out);
}

.usage__bar i.is-full {
  background: var(--color-warning);
}

.usage__body small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.usage__body small.is-over {
  color: var(--color-warning-text);
}

.usage__body small a {
  margin-left: 4px;
  color: var(--color-primary-text);
}

.redeem {
  display: grid;
  gap: var(--space-3);
  scroll-margin-top: 96px;
}

.redeem__head {
  display: flex;
  gap: var(--space-3);
}

.redeem__head h3 {
  font-size: var(--fs-body-lg);
  font-weight: 650;
}

.redeem__head p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.redeem__form {
  display: flex;
  gap: var(--space-2);
}

.redeem__field {
  flex: 1;
  min-width: 0;
  height: var(--control-md, 40px);
  display: flex;
  align-items: center;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  font-family: var(--font-mono);
  font-size: 15px;
  letter-spacing: 0.08em;
  cursor: text;
  transition: border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-base) var(--ease-out);
}

.redeem__field:focus-within {
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}

.redeem__field.is-invalid {
  border-color: var(--color-danger);
}

.redeem__prefix {
  flex: none;
  padding-left: 14px;
  color: var(--text-tertiary);
  user-select: none;
}

.redeem__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 14px 0 2px;
  border: 0;
  color: var(--text-primary);
  background: transparent;
  font: inherit;
  letter-spacing: inherit;
}

.redeem__input:focus-visible {
  outline: none;
}

.redeem__input::placeholder {
  color: var(--text-tertiary);
  opacity: 0.6;
}

.redeem__message {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.redeem__message.is-error {
  color: var(--color-danger-text);
}

.compare h3,
.history h3 {
  margin-bottom: var(--space-3);
  font-size: var(--fs-body-lg);
  font-weight: 650;
}

.compare table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-sm);
}

.compare th,
.compare td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
}

.compare th {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 600;
}

.compare th:not(:first-child),
.compare td:not(:first-child) {
  width: 22%;
  text-align: center;
}

.compare td.is-better {
  color: var(--color-primary-text);
  font-weight: 650;
}

.compare .is-current {
  background: var(--color-primary-soft);
}

.history ol {
  list-style: none;
  display: grid;
  gap: var(--space-2);
}

.history li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--fs-sm);
}

.history__dot {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--color-warning-text);
  background: var(--color-warning-soft);
}

.history__text {
  display: grid;
  min-width: 0;
}

.history__text small,
.history time {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

@keyframes plan-glow {
  0% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--color-primary) 40%, transparent); }
  100% { box-shadow: 0 0 0 18px transparent; }
}

@media (max-width: 720px) {
  .plan-hero { grid-template-columns: 1fr; padding: var(--space-5); }
  .plan-hero__art { display: none; }
  .usage { grid-template-columns: 1fr; }
  .history li { grid-template-columns: auto minmax(0, 1fr); }
  .history time { grid-column: 2; }
}

@media (prefers-reduced-motion: reduce) {
  .plan-hero.is-celebrating { animation: none; }
}
</style>
