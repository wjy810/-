<script setup lang="ts">
import { computed, nextTick, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { pinyin } from 'pinyin-pro'
import { DialogContent, DialogOverlay, DialogPortal, DialogRoot, DialogTitle, VisuallyHidden } from 'reka-ui'
import {
  ArrowRight, Compass, CornerDownLeft, Database, FileText, Keyboard, LogOut, Mic, Moon, Palette, Plus, Search, Sparkles, Target,
} from 'lucide-vue-next'
import UiKbd from '@/shared/ui/UiKbd.vue'
import { useCommandPaletteStore } from '@/stores/commandPalette'
import { usePreferencesStore } from '@/stores/preferences'
import { useSessionStore } from '@/stores/session'
import { NAV_GROUPS } from '@/app/layouts/navigation'
import { useResumeList } from '@/features/resume/queries'
import { formatRelative } from '@/shared/lib/datetime'
import { rankCommands } from './commandSearch'

type Command = {
  id: string
  group: '建议' | '页面' | '操作' | '我的简历'
  title: string
  hint?: string
  icon: Component
  keywords?: string[]
  shortcut?: string
  pinyin?: string
  initials?: string
  run: () => unknown
}

const palette = useCommandPaletteStore()
const preferences = usePreferencesStore()
const session = useSessionStore()
const router = useRouter()
const query = ref('')
const activeIndex = ref(0)
const input = ref<HTMLInputElement | null>(null)
const list = ref<HTMLElement | null>(null)
const resumes = useResumeList(computed(() => palette.open))

function withPinyin(command: Omit<Command, 'pinyin' | 'initials'>): Command {
  return {
    ...command,
    pinyin: pinyin(command.title, { toneType: 'none', type: 'array' }).join(''),
    initials: pinyin(command.title, { pattern: 'first', toneType: 'none', type: 'array' }).join(''),
  }
}

const go = (to: string) => () => router.push(to)

const baseCommands = computed<Command[]>(() => {
  const pages = NAV_GROUPS.filter(group => !group.adminOnly || session.isAdmin)
    .flatMap(group => group.items)
    .map(item => withPinyin({ id: `nav:${item.key}`, group: '页面', title: item.label, icon: item.icon, shortcut: item.shortcut, run: go(item.to) }))
  const extraPages: Command[] = [
    withPinyin({ id: 'nav:appearance', group: '页面', title: '外观设置', icon: Palette, keywords: ['主题', '深色', 'theme'], run: go('/account/appearance') }),
    withPinyin({ id: 'nav:ai-usage', group: '页面', title: 'AI 用量', icon: Sparkles, keywords: ['额度', 'quota'], run: go('/account/ai') }),
    withPinyin({ id: 'nav:data-rights', group: '页面', title: '数据与隐私', icon: Database, keywords: ['导出', '删除', '授权'], run: go('/account/data-rights') }),
  ]
  const actions: Command[] = [
    withPinyin({ id: 'act:new-resume', group: '操作', title: '用 AI 新建简历', icon: Plus, keywords: ['create', 'resume'], run: go('/ai-resume/new') }),
    withPinyin({ id: 'act:new-match', group: '操作', title: '新建岗位匹配', icon: Target, keywords: ['JD', 'match'], run: go('/job-match/new') }),
    withPinyin({ id: 'act:new-interview', group: '操作', title: '开始模拟面试', icon: Mic, keywords: ['interview'], run: go('/mock-interviews/new') }),
    withPinyin({ id: 'act:new-canvas', group: '操作', title: '新建能力画布', icon: Compass, keywords: ['规划', 'career'], run: go('/career-planning/new') }),
    withPinyin({
      id: 'act:theme',
      group: '操作',
      title: preferences.resolvedTheme === 'dark' ? '切换到浅色模式' : '切换到深色模式',
      icon: Moon,
      keywords: ['主题', 'theme', 'dark', 'light'],
      run: () => preferences.toggleTheme(),
    }),
    withPinyin({ id: 'act:shortcuts', group: '操作', title: '查看键盘快捷键', icon: Keyboard, shortcut: '?', run: () => palette.openShortcuts() }),
    withPinyin({
      id: 'act:logout',
      group: '操作',
      title: '退出登录',
      icon: LogOut,
      run: async () => {
        await session.logout().catch(() => undefined)
        await router.replace({ name: 'home', query: { auth: 'login', reason: 'logged_out' } })
      },
    }),
  ]
  return [...actions, ...pages, ...extraPages]
})

const resumeCommands = computed<Command[]>(() => (resumes.data.value ?? [])
  .filter(resume => resume.status !== 'ARCHIVED')
  .slice(0, 30)
  .map(resume => withPinyin({
    id: `resume:${resume.id}`,
    group: '我的简历',
    title: resume.title || '未命名简历',
    hint: resume.updatedAt ? `更新于 ${formatRelative(resume.updatedAt)}` : undefined,
    icon: FileText,
    run: go(`/resumes/${encodeURIComponent(resume.id)}`),
  })))

const results = computed<Command[]>(() => {
  const q = query.value.trim()
  if (!q) {
    const all = [...baseCommands.value, ...resumeCommands.value]
    const recent = palette.recent.map(id => all.find(c => c.id === id)).filter((c): c is Command => !!c)
      .map(c => ({ ...c, group: '建议' as const }))
    const recentIds = new Set(recent.map(c => c.id))
    return [
      ...recent,
      ...baseCommands.value.filter(c => c.group === '操作' && !recentIds.has(c.id)).slice(0, 4),
      ...baseCommands.value.filter(c => c.group === '页面' && !recentIds.has(c.id)),
      ...resumeCommands.value.filter(c => !recentIds.has(c.id)).slice(0, 5),
    ]
  }
  return rankCommands([...baseCommands.value, ...resumeCommands.value], q).slice(0, 30)
})

const grouped = computed(() => {
  const groups: Array<{ name: string; items: Array<Command & { index: number }> }> = []
  results.value.forEach((command, index) => {
    let group = groups.find(g => g.name === command.group)
    if (!group) {
      group = { name: command.group, items: [] }
      groups.push(group)
    }
    group.items.push({ ...command, index })
  })
  return groups
})

watch(() => palette.open, async (open) => {
  if (!open) return
  query.value = ''
  activeIndex.value = 0
  await nextTick()
  input.value?.focus()
})

watch(query, () => {
  activeIndex.value = 0
})

function scrollActiveIntoView(): void {
  nextTick(() => {
    list.value?.querySelector<HTMLElement>(`[data-index="${activeIndex.value}"]`)?.scrollIntoView({ block: 'nearest' })
  })
}

function onKeydown(event: KeyboardEvent): void {
  const count = results.value.length
  if (!count) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = (activeIndex.value + 1) % count
    scrollActiveIntoView()
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = (activeIndex.value - 1 + count) % count
    scrollActiveIntoView()
  } else if (event.key === 'Enter' && !event.isComposing) {
    event.preventDefault()
    execute(results.value[activeIndex.value])
  }
}

function execute(command: Command | undefined): void {
  if (!command) return
  palette.remember(command.id)
  palette.open = false
  void command.run()
}
</script>

<template>
  <DialogRoot v-model:open="palette.open">
    <DialogPortal>
      <DialogOverlay class="cmd-overlay" />
      <DialogContent class="cmd" :aria-describedby="undefined" @keydown="onKeydown">
        <VisuallyHidden><DialogTitle>搜索或跳转</DialogTitle></VisuallyHidden>
        <div class="cmd__search">
          <Search :size="18" class="cmd__search-icon" />
          <input
            ref="input"
            v-model="query"
            class="cmd__input"
            type="text"
            placeholder="搜索页面、操作或你的简历…（支持拼音首字母）"
            aria-label="搜索页面、操作或简历"
            role="combobox"
            aria-expanded="true"
            aria-controls="cmd-results"
            :aria-activedescendant="results.length ? `cmd-item-${activeIndex}` : undefined"
            autocomplete="off"
            spellcheck="false"
          />
          <UiKbd keys="esc" />
        </div>
        <div id="cmd-results" ref="list" class="cmd__list" role="listbox" aria-label="搜索结果">
          <section v-for="group in grouped" :key="group.name" class="cmd__group">
            <h3 class="cmd__group-title">{{ group.name }}</h3>
            <button
              v-for="item in group.items"
              :id="`cmd-item-${item.index}`"
              :key="item.id"
              type="button"
              role="option"
              class="cmd__item"
              :class="{ 'is-active': item.index === activeIndex }"
              :aria-selected="item.index === activeIndex"
              :data-index="item.index"
              @mousemove="activeIndex = item.index"
              @click="execute(item)"
            >
              <span class="cmd__item-icon"><component :is="item.icon" :size="16" :stroke-width="1.9" /></span>
              <span class="cmd__item-title">{{ item.title }}</span>
              <span v-if="item.hint" class="cmd__item-hint">{{ item.hint }}</span>
              <UiKbd v-if="item.shortcut" :keys="item.shortcut.split(' ')" class="cmd__item-kbd" />
              <ArrowRight v-else :size="14" class="cmd__item-go" />
            </button>
          </section>
          <div v-if="!results.length" class="cmd__empty">
            <p>没有找到“{{ query }}”相关的内容</p>
            <small>试试“简历”“jl”“匹配”或“深色”</small>
          </div>
        </div>
        <footer class="cmd__foot">
          <span><UiKbd :keys="['↑', '↓']" />选择</span>
          <span><CornerDownLeft :size="12" />打开</span>
          <span><UiKbd keys="mod+k" />随时唤起</span>
        </footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style>
.cmd-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-command);
  background: var(--scrim);
  backdrop-filter: blur(3px);
  animation: jp-fade-in var(--dur-base) var(--ease-standard);
}

.cmd {
  position: fixed;
  z-index: var(--z-command);
  top: 12vh;
  left: 50%;
  width: min(640px, calc(100vw - 24px));
  max-height: min(560px, 76vh);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-overlay);
  box-shadow: var(--shadow-lg), 0 0 0 1px rgba(255, 255, 255, 0.02);
  transform: translateX(-50%);
  animation: cmd-in var(--dur-base) var(--ease-out);
  outline: none;
}

.cmd__search {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.cmd__search-icon {
  color: var(--text-tertiary);
}

.cmd__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 16px;
}

.cmd__input:focus-visible {
  box-shadow: none;
}

.cmd__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 8px 8px;
  scroll-padding: 8px;
}

.cmd__group + .cmd__group {
  margin-top: 4px;
}

.cmd__group-title {
  padding: 10px 10px 6px;
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.cmd__item {
  position: relative;
  width: 100%;
  height: 42px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px;
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: var(--fs-body);
  text-align: left;
}

.cmd__item.is-active {
  background: var(--surface-3);
}

.cmd__item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 2px;
  border-radius: 2px;
  background: var(--color-primary);
}

.cmd__item-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-secondary);
  box-shadow: inset 0 0 0 1px var(--border-subtle);
}

.cmd__item.is-active .cmd__item-icon {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.cmd__item-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cmd__item-hint {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  white-space: nowrap;
}

.cmd__item-go {
  color: var(--text-tertiary);
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity var(--dur-fast), transform var(--dur-fast) var(--ease-out);
}

.cmd__item.is-active .cmd__item-go {
  opacity: 1;
  transform: none;
}

.cmd__empty {
  display: grid;
  gap: 4px;
  padding: 40px 16px;
  color: var(--text-secondary);
  text-align: center;
}

.cmd__empty small {
  color: var(--text-tertiary);
}

.cmd__foot {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 38px;
  padding: 0 16px;
  border-top: 1px solid var(--border-subtle);
  background: var(--surface-2);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.cmd__foot span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@keyframes cmd-in {
  from {
    opacity: 0;
    transform: translateX(-50%) scale(0.98);
  }
}

@media (max-width: 640px) {
  .cmd {
    top: 8px;
    max-height: calc(100vh - 16px);
  }

  .cmd__foot {
    display: none;
  }
}
</style>
