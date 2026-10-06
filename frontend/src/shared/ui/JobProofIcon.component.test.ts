import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import JobProofIcon from './JobProofIcon.vue'
import { jobProofIconIds } from './jobProofIcons'

describe('JobProofIcon', () => {
  it('renders a typed sprite reference with inherited presentation attributes', () => {
    const wrapper = mount(JobProofIcon, {
      props: { name: jobProofIconIds.careerLibrary, size: 24 },
    })

    const icon = wrapper.get('svg')
    expect(icon.attributes('width')).toBe('24')
    expect(icon.attributes('height')).toBe('24')
    expect(icon.attributes('stroke')).toBe('currentColor')
    expect(icon.attributes('stroke-width')).toBe('1.75')
    expect(icon.attributes('aria-hidden')).toBe('true')
    expect(icon.attributes('focusable')).toBe('false')
    expect(icon.attributes('data-jobproof-icon')).toBe('nav-career-library')
    expect(wrapper.get('use').attributes('href')).toContain('#nav-career-library')
  })

  it('fails closed when an unpublished icon name reaches the component at runtime', () => {
    const wrapper = mount(JobProofIcon, {
      props: { name: 'not-published' as never },
    })

    expect(wrapper.find('svg').exists()).toBe(false)
  })
})
