<script setup lang="ts">
/**
 * 套餐与价格 (docs/phase2/02): public. Every number comes from GET /membership/plans, so the page never
 * promises more than the server grants. Online payment is not open yet; the page says so plainly and
 * points to redeem codes, which schools, partners and promotions hand out.
 */
import { catalog as commerceCatalog } from '@/features/billing/services/commerceApi'
import type { Shop } from '@/features/billing/services/commerceApi'
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, Building2, Check, Crown, GraduationCap, Mail, ShieldCheck, Ticket, Users } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import { useCapabilitiesStore } from '@/stores/capabilities'
import { useSessionStore } from '@/stores/session'
import { fetchMembership, fetchPlanCatalog } from '../services/membershipApi'
import { benefits, perMonth, saving, yuan } from '../labels'
import type { Membership, Price } from '../types'

type Period = Price['code']

const shop = ref<Shop>()
const route = useRoute()
const session = useSessionStore()
const capabilities = useCapabilitiesStore()
const catalog = useLoadState(() => fetchPlanCatalog())
const membership = shallowRef<Membership | null>(null)
const period = ref<Period>('QUARTER')

const prices = computed(() => catalog.data.value?.proPrices ?? [])
const monthly = computed(() => prices.value.find(price => price.code === 'MONTH'))
const selected = computed(() => prices.value.find(price => price.code === period.value) ?? prices.value[0])
const periodItems = computed(() => prices.value.map(price => {
  const off = saving(price, monthly.value)
  return { value: price.code, label: off ? `${price.label} · 省 ${off}%` : price.label }
}))
const rows = computed(() => (catalog.data.value ? benefits(catalog.data.value.free, catalog.data.value.pro) : []))
const currentPlan = computed(() => membership.value?.plan ?? null)
const contact = computed(() => capabilities.operator?.contactEmail || '')

const registerFor = (next: string) => ({ name: 'home', query: { auth: 'register', next } })
const redeemTarget = computed(() => (session.signedIn ? '/account/wallet' : registerFor('/account/wallet')))
const freeTarget = computed(() => (session.signedIn ? '/dashboard' : registerFor(route.fullPath)))

const faqs = [
  { q: '免费版会过期吗？', a: '不会。免费版一直可用，每个月按自然月重置 AI 次数。' },
  { q: '专业版到期后数据会怎样？', a: '简历、投递记录、查看回执和资料都会保留。只是额度回到免费版：已经发出的专属链接在各自的有效期内继续可用，不会被撤回；只是有效链接降到免费版上限以内之前，不能再新建。' },
  { q: '兑换码从哪里获得？', a: '请通过官方购码入口获取余额兑换码；每码充值一次。在钱包购买 PRO 或个人通道订阅。历史会员码仍可兑换。' },
  { q: '如何购买或续期？', a: '使用人民币钱包余额购买，购买前确认当前价格。不自动续费；有效期内手动续期会顺延。' },
  { q: '学校或机构可以批量开通吗？', a: '可以。我们按届或按班级批量生成兑换码，每批可以设置兑换截止日期，未使用的码可以随时停用。' },
]

onMounted(async () => {
  try { shop.value = (await commerceCatalog()).shop } catch { /* shop may be unavailable */ }
  void capabilities.load()
  await catalog.load()
  if (session.signedIn && !session.isAdmin) {
    try { membership.value = await fetchMembership() } catch { membership.value = null }
  }
})
</script>

<template>
  <div class="page-container pricing-page">
    <div class="commerce-links"><UiButton to="/account/wallet">钱包充值与购买</UiButton> <a v-if="shop?.enabled && shop.url" :href="shop.url" target="_blank" rel="noopener noreferrer">{{shop.name}}</a><p v-if="shop?.enabled && shop.url">{{shop.description}}</p></div>
    <header class="pricing-hero">
      <UiBadge tone="ai"><Crown :size="12" />套餐与价格</UiBadge>
      <h1>免费开始，求职旺季再升级</h1>
      <p>投递追踪、查看回执和全部模板永远免费。专业版给你更多 AI 次数和更多同时有效的专属链接，适合密集投递的那几个月。</p>
    </header>

    <PageState :loading="catalog.loading.value" :error="catalog.error.value" :loaded="catalog.loaded.value" error-title="套餐信息读取失败" @retry="catalog.load">
      <template #skeleton>
        <div class="plans"><UiSkeleton height="420px" radius="var(--radius-xl, 16px)" /><UiSkeleton height="420px" radius="var(--radius-xl, 16px)" /></div>
      </template>

      <template v-if="catalog.data.value">
        <div v-if="prices.length > 1" class="period">
          <UiSegmented v-model="period" :items="periodItems" aria-label="付费周期" />
        </div>

        <div class="plans">
          <article class="plan" aria-labelledby="plan-free">
            <header class="plan__head">
              <h2 id="plan-free">免费版</h2>
              <UiBadge v-if="currentPlan === 'FREE'" tone="neutral" size="sm">当前套餐</UiBadge>
            </header>
            <p class="plan__price"><strong>¥0</strong><span>永久免费</span></p>
            <p class="plan__lead">记录每一次投递，知道谁打开了你的简历。</p>
            <ul class="plan__list">
              <li><Check :size="16" />每月 {{ catalog.data.value.free.aiMonthlyUnits }} 次 AI（岗位匹配、简历建议）</li>
              <li><Check :size="16" />同时 {{ catalog.data.value.free.activeLinks }} 条有效专属简历链接</li>
              <li><Check :size="16" />查看回执：打开、读者数、设备、阅读时长</li>
              <li><Check :size="16" />投递看板、漏斗和下一步提醒</li>
              <li><Check :size="16" />全部简历模板与 PDF 导出</li>
            </ul>
            <UiButton class="plan__cta" variant="secondary" block :to="freeTarget" :icon-right="ArrowRight">
              {{ session.signedIn ? '去工作台' : '免费注册' }}
            </UiButton>
          </article>

          <article class="plan is-pro" aria-labelledby="plan-pro">
            <UiIllustration name="membership-pro" :size="96" class="plan__art" float />
            <header class="plan__head">
              <h2 id="plan-pro">专业版</h2>
              <UiBadge v-if="currentPlan === 'PRO'" tone="ai" size="sm">当前套餐</UiBadge>
              <UiBadge v-else tone="warning" size="sm">求职季推荐</UiBadge>
            </header>
            <p v-if="selected" class="plan__price">
              <strong>¥{{ yuan(selected.amountFen) }}</strong>
              <span>/ {{ selected.label }}<template v-if="selected.code !== 'MONTH'"> · 约 ¥{{ perMonth(selected) }}/月</template></span>
            </p>
            <p class="plan__lead">投递高峰期不用省着用 AI，每家公司都能有自己的链接。</p>
            <ul class="plan__list">
              <li><Check :size="16" />每月 <strong>{{ catalog.data.value.pro.aiMonthlyUnits }}</strong> 次 AI，开通当月立即补足</li>
              <li><Check :size="16" />同时 <strong>{{ catalog.data.value.pro.activeLinks }}</strong> 条有效专属简历链接</li>
              <li><Check :size="16" />包含免费版的全部功能</li>
              <li><Check :size="16" />到期自动回到免费版，数据全部保留</li>
            </ul>
            <UiButton class="plan__cta" variant="ai" block :to="redeemTarget" :icon="Ticket">
              {{ currentPlan === 'PRO' ? '余额续期' : '前往钱包购买' }}
            </UiButton>
            <p class="plan__note">使用人民币钱包余额购买；价格与有效期以支付确认时为准，不会自动续费。</p>
          </article>
        </div>

        <section class="pricing-section" aria-labelledby="compare-title">
          <h2 id="compare-title">权益对比</h2>
          <div class="compare">
            <table>
              <thead><tr><th>权益</th><th>免费版</th><th>专业版</th></tr></thead>
              <tbody>
                <tr v-for="row in rows" :key="row.key">
                  <td>{{ row.label }}</td>
                  <td>{{ row.free }}</td>
                  <td :class="{ 'is-better': row.free !== row.pro }">{{ row.pro }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="campus" aria-labelledby="campus-title">
          <div class="campus__copy">
            <UiBadge tone="primary"><Building2 :size="12" />高校与机构</UiBadge>
            <h2 id="campus-title">为一届毕业生批量开通</h2>
            <p>就业指导中心和培训机构可以按届、按班级领取兑换码，学生自行兑换，不需要收集任何个人信息。</p>
            <ul>
              <li><GraduationCap :size="16" /><span><strong>按批次发放</strong>每批可设兑换截止日期，分发渠道一目了然</span></li>
              <li><Users :size="16" /><span><strong>兑换进度可见</strong>已兑换多少、还剩多少，随时可查</span></li>
              <li><ShieldCheck :size="16" /><span><strong>随时停用</strong>名单外流时一键停用整批未使用的码</span></li>
            </ul>
            <UiButton v-if="contact" variant="secondary" :href="`mailto:${contact}?subject=${encodeURIComponent('高校/机构批量开通咨询')}`" :icon="Mail">联系我们</UiButton>
          </div>
          <UiIllustration name="campus-partner" :size="220" class="campus__art" />
        </section>

        <section class="pricing-section" aria-labelledby="faq-title">
          <h2 id="faq-title">常见问题</h2>
          <div class="faq">
            <details v-for="item in faqs" :key="item.q">
              <summary>{{ item.q }}</summary>
              <p>{{ item.a }}</p>
            </details>
          </div>
        </section>
      </template>
    </PageState>
  </div>
</template>

<style scoped>
.pricing-page {
  display: grid;
  gap: var(--space-8);
  max-width: 1080px;
}

.pricing-page :deep(.page-state) {
  display: grid;
  gap: var(--space-8);
}

.pricing-hero {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  padding-top: var(--space-4);
  text-align: center;
}

.pricing-hero h1 {
  font-size: clamp(26px, 4vw, 36px);
  font-weight: 750;
  letter-spacing: -0.01em;
  line-height: 1.25;
}

.pricing-hero p {
  max-width: 60ch;
  color: var(--text-secondary);
  font-size: var(--fs-body-lg);
  line-height: 1.75;
}

.period {
  display: flex;
  justify-content: center;
  margin-bottom: calc(var(--space-4) * -1);
}

.plans {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5);
  align-items: stretch;
}

.plan {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl, 16px);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.plan.is-pro {
  overflow: hidden;
  border-color: color-mix(in srgb, var(--gradient-ai-via) 40%, var(--border-subtle));
  background:
    radial-gradient(100% 70% at 100% 0%, color-mix(in srgb, var(--gradient-ai-via) 16%, transparent), transparent 70%),
    var(--surface-1);
  box-shadow: var(--shadow-md);
}

.plan__art {
  position: absolute;
  top: var(--space-4);
  right: var(--space-4);
  pointer-events: none;
}

.plan__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.plan__head h2 {
  font-size: 18px;
  font-weight: 700;
}

.plan__price {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 48px;
}

.plan__price strong {
  font-size: 38px;
  font-weight: 750;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.plan__price span {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.plan__lead {
  max-width: 34ch;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.plan__list {
  display: grid;
  gap: 10px;
  margin: var(--space-2) 0 var(--space-4);
  padding: var(--space-4) 0 0;
  border-top: 1px dashed var(--border-subtle);
  list-style: none;
}

.plan__list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.plan__list svg {
  flex: none;
  margin-top: 3px;
  color: var(--color-success);
}

.plan.is-pro .plan__list svg {
  color: var(--gradient-ai-via);
}

.plan__cta {
  margin-top: auto;
}

.plan__note {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.pricing-section {
  display: grid;
  gap: var(--space-4);
}

.pricing-section > h2,
.campus h2 {
  font-size: 20px;
  font-weight: 700;
}

.compare {
  overflow-x: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
}

.compare table {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
  font-size: var(--fs-sm);
}

.compare th,
.compare td {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
}

.compare tr:last-child td {
  border-bottom: 0;
}

.compare th {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 600;
  background: var(--surface-2);
}

.compare th:not(:first-child),
.compare td:not(:first-child) {
  width: 22%;
}

.compare td.is-better {
  color: var(--color-primary-text);
  font-weight: 650;
}

.campus {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-6);
  padding: var(--space-6) var(--space-8);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl, 16px);
  background:
    radial-gradient(80% 120% at 0% 100%, var(--color-primary-soft), transparent 65%),
    var(--surface-1);
}

.campus__copy {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
}

.campus__copy > p {
  max-width: 56ch;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.75;
}

.campus ul {
  display: grid;
  gap: 10px;
  margin: 0 0 var(--space-2);
  padding: 0;
  list-style: none;
}

.campus li {
  display: flex;
  gap: 10px;
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.campus li svg {
  flex: none;
  margin-top: 3px;
  color: var(--color-primary);
}

.campus li strong {
  margin-right: 8px;
}

.campus li span {
  color: var(--text-secondary);
}

.campus li strong {
  color: var(--text-primary);
}

.faq {
  display: grid;
  gap: var(--space-2);
}

.faq details {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  transition: border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-base) var(--ease-out);
}

.faq details[open] {
  border-color: var(--border-default);
  box-shadow: var(--shadow-sm);
}

.faq summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 14px 18px;
  font-size: var(--fs-body);
  font-weight: 600;
  list-style: none;
  cursor: pointer;
}

.faq summary::-webkit-details-marker {
  display: none;
}

.faq summary::after {
  content: '+';
  flex: none;
  color: var(--text-tertiary);
  font-size: 18px;
  font-weight: 400;
  transition: transform var(--dur-base) var(--ease-out);
}

.faq details[open] summary::after {
  transform: rotate(45deg);
}

.faq summary:focus-visible {
  outline: none;
  border-radius: var(--radius-lg);
  box-shadow: var(--focus-ring);
}

.faq details p {
  padding: 0 18px 16px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.75;
}

@media (max-width: 860px) {
  .plans {
    grid-template-columns: 1fr;
  }

  .campus {
    grid-template-columns: 1fr;
    padding: var(--space-6);
  }

  .campus__art {
    display: none;
  }
}

@media (max-width: 560px) {
  /* The comparison fits the screen instead of hiding the pro column behind a sideways scroll. */
  .compare table {
    min-width: 0;
  }

  .compare th,
  .compare td {
    padding: 10px 12px;
  }

  .compare th:not(:first-child),
  .compare td:not(:first-child) {
    width: 64px;
    white-space: nowrap;
  }

  .period :deep([role='group']) {
    flex-wrap: wrap;
  }

  .plan {
    padding: var(--space-5);
  }

  .plan__art {
    width: 72px;
    height: 72px;
  }
}
</style>
