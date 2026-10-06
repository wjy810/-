import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CareerProfile } from '@/features/career-library/types'

const api = vi.hoisted(() => ({ fetchCareerProfile: vi.fn(), saveCareerProfile: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('@/features/career-library/services/careerLibraryApi', () => api)
vi.mock('vue-sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), dismiss: vi.fn() },
  Toaster: { template: '<div />' },
}))
vi.mock('vue-router', () => ({
  useRouter: () => router,
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a :href="to"><slot /></a>' },
}))

import OnboardingPage from './OnboardingPage.vue'

function profile(overrides: Partial<CareerProfile> = {}): CareerProfile {
  return {
    accountId: 'account-1',
    basics: { links: ['https://example.com'] },
    intentions: { targetJob: '数据分析' },
    preferences: { targetCity: '杭州', workMode: '远程' },
    summary: null,
    snapshotVersion: 1,
    version: 3,
    updatedAt: '2026-10-01T00:00:00Z',
    completeness: 20,
    missingItems: [],
    ...overrides,
  }
}

const stubs = {
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  UiIllustration: true,
  UiSelect: {
    props: ['modelValue', 'options'],
    emits: ['update:modelValue'],
    template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option value="" /><option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option></select>',
  },
}

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find(item => item.text().includes(text))
  if (!button) throw new Error(`没有找到按钮：${text}`)
  return button
}

describe('OnboardingPage', () => {
  beforeEach(() => {
    api.fetchCareerProfile.mockReset().mockResolvedValue(profile())
    api.saveCareerProfile.mockReset().mockImplementation(async (value: CareerProfile) => ({ ...value, version: value.version + 1 }))
    router.push.mockReset()
  })

  it('skips a step without saving anything', async () => {
    const wrapper = mount(OnboardingPage, { global: { stubs } })
    await flushPromises()

    await wrapper.get('input').setValue('一个不会被保存的方向')
    await buttonByText(wrapper, '跳过').trigger('click')
    await flushPromises()

    expect(api.saveCareerProfile).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('补充基础信息')
    await buttonByText(wrapper, '跳过').trigger('click')
    expect(api.saveCareerProfile).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('添加你的求职资料')
  })

  it('saves the direction and basics into the career profile, then offers real ways to add materials', async () => {
    const wrapper = mount(OnboardingPage, { global: { stubs } })
    await flushPromises()

    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('REMOTE')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.saveCareerProfile).toHaveBeenCalledTimes(1)
    expect(api.saveCareerProfile.mock.calls[0][0]).toMatchObject({
      version: 3,
      intentions: { targetJob: '数据分析' },
      preferences: { targetCity: '杭州', workMode: 'REMOTE' },
    })
    expect(wrapper.text()).toContain('补充基础信息')

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('张三')
    await inputs[1].setValue('zhangsan@example.com')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.saveCareerProfile).toHaveBeenCalledTimes(2)
    expect(api.saveCareerProfile.mock.calls[1][0]).toMatchObject({
      version: 4,
      basics: { links: ['https://example.com'], name: '张三', email: 'zhangsan@example.com' },
    })
    const links = wrapper.findAll('.material-action').map(link => link.attributes('href'))
    expect(links).toEqual(['/career-library?view=files', '/career-library?view=records', '/ai-resume/new'])
  })

  it('keeps the user on the step and explains a failed save', async () => {
    api.saveCareerProfile.mockRejectedValue(new Error('保存冲突'))
    const wrapper = mount(OnboardingPage, { global: { stubs } })
    await flushPromises()

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('先确定你的求职方向')
    expect(wrapper.text()).toContain('保存冲突')
  })

  it('shows an error with retry instead of an empty form when the profile cannot be read', async () => {
    api.fetchCareerProfile.mockReset().mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(profile())
    const wrapper = mount(OnboardingPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(true)
  })
})
