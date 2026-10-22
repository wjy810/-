import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { BatchCreated, Membership, PlanCatalog, RedeemBatch } from '../types'

const api = vi.hoisted(() => ({
  fetchMembership: vi.fn(),
  fetchPlanCatalog: vi.fn(),
  redeemCode: vi.fn(),
  fetchRedeemBatches: vi.fn(),
  createRedeemBatch: vi.fn(),
  revokeRedeemBatch: vi.fn(),
}))
vi.mock('../services/membershipApi', () => api)
vi.mock('@/features/billing/services/commerceApi', () => ({catalog:vi.fn().mockResolvedValue({prices:[],shop:{enabled:false,url:'',name:'链动小铺',description:'外部购买后返回兑换',version:0}})}))
const quota = vi.hoisted(() => ({ fetchAiResumeQuota: vi.fn() }))
vi.mock('@/features/ai-resume/services/aiResumeApi', () => quota)
vi.mock('@/shared/api/capabilities', () => ({
  fetchSiteCapabilities: vi.fn().mockResolvedValue({ features: {}, registrationOpen: true, policies: { published: true, termsVersion: '1', privacyVersion: '1' },
    operator: { name: '', icpRecord: '', publicSecurityRecord: '', contactEmail: 'campus@example.com', contactPhone: '', address: '', feedbackUrl: '' } }),
}))
const dialogs = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('@/shared/ui/confirm', () => dialogs)
const route = vi.hoisted(() => ({ hash: '', fullPath: '/pricing', query: {} }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a :data-to="typeof to === \'string\' ? to : JSON.stringify(to)"><slot /></a>' },
}))
const sonner = vi.hoisted(() => Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn() }))
vi.mock('vue-sonner', () => ({ toast: sonner }))

import MembershipPage from './MembershipPage.vue'
import PricingPage from './PricingPage.vue'
import AdminRedeemCodesPage from './AdminRedeemCodesPage.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import { useSessionStore } from '@/stores/session'

const FREE = { plan: 'FREE' as const, aiMonthlyUnits: 30, activeLinks: 3 }
const PRO = { plan: 'PRO' as const, aiMonthlyUnits: 300, activeLinks: 20 }
const inMonth = new Date(Date.now() + 30 * 86_400_000).toISOString()

function membership(patch: Partial<Membership> = {}): Membership {
  return { plan: 'FREE', entitlements: FREE, free: FREE, pro: PRO, activeLinks: 2, events: [], ...patch }
}

const catalog: PlanCatalog = {
  free: FREE,
  pro: PRO,
  proPrices: [
    { code: 'MONTH', label: '月付', amountFen: 1900, days: 30 },
    { code: 'QUARTER', label: '季付', amountFen: 4900, days: 90 },
    { code: 'YEAR', label: '年付', amountFen: 16800, days: 365 },
  ],
}

const dialogStub = { props: ['open', 'title'], emits: ['close'], template: '<div v-if="open" role="dialog"><h2>{{ title }}</h2><slot /><slot name="footer" /></div>' }

function mountWith(component: object) {
  const pinia = createPinia()
  const wrapper = mount(component, { global: { plugins: [pinia], stubs: { UiIllustration: true, UiDialog: dialogStub, AppDatePicker: true } } })
  return { wrapper, pinia }
}

beforeEach(() => {
  Object.values(api).forEach(mock => mock.mockReset())
  quota.fetchAiResumeQuota.mockReset().mockResolvedValue({ grantedUnits: 30, usedUnits: 12, heldUnits: 0, remainingUnits: 18 })
  dialogs.confirm.mockReset()
  Object.values(sonner).forEach(mock => typeof mock === 'function' && 'mockReset' in mock && mock.mockReset())
  route.hash = ''
})

describe('membership page', () => {
  it('shows the free plan with this month\'s usage', async () => {
    api.fetchMembership.mockResolvedValue(membership())
    const { wrapper } = mountWith(MembershipPage)
    await flushPromises()
    expect(wrapper.get('.plan-hero').text()).toContain('你正在使用免费版')
    expect(wrapper.text()).toContain('18 / 30 可用')
    expect(wrapper.text()).toContain('2 / 3 条')
    expect(wrapper.findAll('.compare tbody tr').length).toBeGreaterThan(3)
  })

  it('formats a typed code and redeems it into the pro plan', async () => {
    api.fetchMembership.mockResolvedValue(membership())
    api.redeemCode.mockResolvedValue(membership({ plan: 'PRO', entitlements: PRO, expiresAt: inMonth,
      events: [{ id: 'e1', type: 'REDEEMED', plan: 'PRO', days: 30, expiresAt: inMonth, detail: '2027 届', createdAt: new Date().toISOString() }] }))
    const { wrapper } = mountWith(MembershipPage)
    await flushPromises()
    const submit = wrapper.get('.redeem__form button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
    await wrapper.get('.redeem__input').setValue('jp abcd efgh jkmn pqrs')
    expect((wrapper.get('.redeem__input').element as HTMLInputElement).value).toBe('ABCD-EFGH-JKMN-PQRS')
    await wrapper.get('.redeem__form').trigger('submit')
    await flushPromises()
    expect(api.redeemCode).toHaveBeenCalledWith('JP-ABCD-EFGH-JKMN-PQRS')
    expect(wrapper.get('.plan-hero').classes()).toContain('is-pro')
    expect(wrapper.get('.plan-hero').text()).toContain('专业版还剩')
    expect(wrapper.text()).toContain('兑换 30 天专业版')
    expect(sonner.success).toHaveBeenCalled()
  })

  it('explains a refused code next to the field', async () => {
    api.fetchMembership.mockResolvedValue(membership())
    api.redeemCode.mockRejectedValue(new ApiClientError({ category: 'RATE_LIMITED', reason: 'REDEEM_TOO_MANY_ATTEMPTS', message: 'x' }, 429))
    const { wrapper } = mountWith(MembershipPage)
    await flushPromises()
    await wrapper.get('.redeem__input').setValue('ABCDEFGHJKMNPQRS')
    await wrapper.get('.redeem__form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('#redeem-message').text()).toBe('尝试次数过多，请一小时后再试')
    expect(wrapper.get('.redeem__input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('.redeem__field').classes()).toContain('is-invalid')
  })
})

describe('pricing page', () => {
  it('prices the pro plan per period and sends a guest to register before redeeming', async () => {
    api.fetchPlanCatalog.mockResolvedValue(catalog)
    const { wrapper } = mountWith(PricingPage)
    await flushPromises()
    const pro = wrapper.get('.plan.is-pro')
    expect(pro.get('.plan__price').text()).toContain('¥49')
    expect(pro.get('.plan__price').text()).toContain('约 ¥16.3/月')
    wrapper.getComponent(UiSegmented).vm.$emit('update:modelValue', 'YEAR')
    await wrapper.vm.$nextTick()
    expect(pro.get('.plan__price').text()).toContain('¥168')
    expect(pro.text()).toContain('每月 300 次 AI')
    expect(pro.get('.plan__cta').attributes('data-to')).toContain('register')
    expect(wrapper.text()).toContain('使用人民币钱包余额购买')
    expect(wrapper.get('.campus a').attributes('href')).toContain('mailto:campus@example.com')
    expect(api.fetchMembership).not.toHaveBeenCalled()
  })

  it('marks the signed-in account\'s current plan', async () => {
    api.fetchPlanCatalog.mockResolvedValue(catalog)
    api.fetchMembership.mockResolvedValue(membership({ plan: 'PRO', entitlements: PRO, expiresAt: inMonth }))
    const pinia = createPinia()
    const session = useSessionStore(pinia)
    session.account = { id: 'u1', role: 'SEEKER' } as unknown as typeof session.account
    const wrapper = mount(PricingPage, { global: { plugins: [pinia], stubs: { UiIllustration: true } } })
    await flushPromises()
    expect(wrapper.get('.plan.is-pro').text()).toContain('当前套餐')
    expect(wrapper.get('.plan.is-pro .plan__cta').text()).toContain('余额续期')
    expect(wrapper.get('.plan.is-pro .plan__cta').attributes('data-to')).toBe('/account/wallet')
  })
})

describe('redeem code admin', () => {
  const batch: RedeemBatch = { id: 'b1', name: '2027 届计算机学院', channel: '就业指导中心', plan: 'PRO', durationDays: 90, quantity: 3,
    redeemed: 1, status: 'ACTIVE', createdAt: new Date().toISOString() }

  it('creates a batch and shows its codes once', async () => {
    api.fetchRedeemBatches.mockResolvedValue([])
    const created: BatchCreated = { batch: { ...batch, id: 'b2', redeemed: 0 }, codes: ['JP-AAAA-BBBB-CCCC-DDDD', 'JP-EEEE-FFFF-GGGG-HHHH'] }
    api.createRedeemBatch.mockResolvedValue(created)
    dialogs.confirm.mockResolvedValue(false)
    const { wrapper } = mountWith(AdminRedeemCodesPage)
    await flushPromises()
    expect(wrapper.text()).toContain('还没有兑换码批次')

    await wrapper.findAll('button').find(button => button.text() === '新建批次')!.trigger('click')
    await wrapper.get('#redeem-batch-form').trigger('submit')
    expect(api.createRedeemBatch).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请填写批次名称')

    await wrapper.get('#redeem-batch-form input').setValue('2027 届计算机学院')
    await wrapper.get('#redeem-batch-form').trigger('submit')
    await flushPromises()
    expect(api.createRedeemBatch).toHaveBeenCalledWith({ name: '2027 届计算机学院', channel: null, balanceAmount: 50, quantity: 50, redeemBefore: null })
    expect(wrapper.findAll('.codes li').map(item => item.text())).toEqual(created.codes)
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)

    await wrapper.findAll('button').find(button => button.text() === '完成')!.trigger('click')
    await flushPromises()
    expect(dialogs.confirm).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.codes').exists()).toBe(true)
  })

  it('revokes a batch after confirmation', async () => {
    api.fetchRedeemBatches.mockResolvedValue([batch])
    api.revokeRedeemBatch.mockResolvedValue({ ...batch, status: 'REVOKED', revokedAt: new Date().toISOString() })
    dialogs.confirm.mockResolvedValue(true)
    const { wrapper } = mountWith(AdminRedeemCodesPage)
    await flushPromises()
    expect(wrapper.get('tbody tr').text()).toContain('1 / 3')
    expect(wrapper.text()).toContain('33%')
    await wrapper.findAll('button').find(button => button.text() === '停用')!.trigger('click')
    await flushPromises()
    expect(dialogs.confirm.mock.calls[0]![0].message).toContain('剩余 2 个')
    expect(api.revokeRedeemBatch).toHaveBeenCalledWith('b1')
    expect(wrapper.get('tbody tr').text()).toContain('已停用')
    expect(wrapper.findAll('button').some(button => button.text() === '停用')).toBe(false)
  })
})
