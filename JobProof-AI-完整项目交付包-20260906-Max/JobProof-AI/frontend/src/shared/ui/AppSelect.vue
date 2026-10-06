<script setup lang="ts">
import { computed, Fragment, isVNode, nextTick, onBeforeUnmount, onMounted, ref, useId, useSlots, watch } from 'vue'
import type { CSSProperties, VNodeChild } from 'vue'
import AppIcon from './AppIcon.vue'

type SelectValue = string | number

type SelectOption = {
  value: SelectValue
  label: string
  count?: number
  keywords?: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    modelValue: SelectValue
    options?: readonly SelectOption[]
    ariaLabel?: string
    id?: string
    name?: string
    required?: boolean
    disabled?: boolean
    searchable?: boolean
    searchPlaceholder?: string
    emptyText?: string
    menuAlign?: 'start' | 'end'
  }>(),
  {
    options: () => [],
    ariaLabel: '选择选项',
    id: undefined,
    name: undefined,
    required: false,
    disabled: false,
    searchable: false,
    searchPlaceholder: '搜索选项',
    emptyText: '没有匹配选项',
    menuAlign: 'start',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: SelectValue]
  change: [value: SelectValue]
}>()
const slots = useSlots()

const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const searchInput = ref<HTMLInputElement | null>(null)
const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const listId = `app-select-${useId().replaceAll(':', '')}`
const menuPlacement = ref<'top' | 'bottom'>('bottom')
const menuStyle = ref<CSSProperties>({})
let positionFrame = 0

function readVNodeText(children: VNodeChild | VNodeChild[] | null): string {
  if (children == null || typeof children === 'boolean') return ''
  if (typeof children === 'string' || typeof children === 'number') return String(children)
  if (Array.isArray(children)) return children.map((child) => readVNodeText(child)).join('')
  if (isVNode(children)) return readVNodeText(children.children as VNodeChild | VNodeChild[] | null)
  return ''
}

function collectSlotOptions(nodes: VNodeChild[], result: SelectOption[]): void {
  for (const node of nodes) {
    if (Array.isArray(node)) {
      collectSlotOptions(node, result)
      continue
    }
    if (!isVNode(node)) continue
    if (node.type === 'option') {
      const label = readVNodeText(node.children as VNodeChild | VNodeChild[] | null).replace(/\s+/g, ' ').trim()
      result.push({
        value: (node.props?.value ?? label) as SelectValue,
        label,
        disabled: Boolean(node.props?.disabled),
      })
      continue
    }
    if (node.type === Fragment && Array.isArray(node.children)) {
      collectSlotOptions(node.children as VNodeChild[], result)
    }
  }
}

const resolvedOptions = computed<SelectOption[]>(() => {
  if (props.options.length) return [...props.options]
  const options: SelectOption[] = []
  collectSlotOptions((slots.default?.() ?? []) as VNodeChild[], options)
  return options
})
const selectedOption = computed(
  () => resolvedOptions.value.find((option) => option.value === props.modelValue)
    ?? resolvedOptions.value.find((option) => !option.disabled),
)
const searchEnabled = computed(() => props.searchable || resolvedOptions.value.length > 14)
const filteredOptions = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  if (!keyword) return resolvedOptions.value
  return resolvedOptions.value.filter((option) =>
    `${option.label} ${option.keywords ?? ''}`.toLocaleLowerCase('zh-CN').includes(keyword),
  )
})
const activeOptionId = computed(() =>
  filteredOptions.value[activeIndex.value]
    ? `${listId}-option-${activeIndex.value}`
    : undefined,
)

function resetActive(): void {
  const selectedIndex = filteredOptions.value.findIndex((option) => option.value === props.modelValue)
  const firstEnabled = filteredOptions.value.findIndex((option) => !option.disabled)
  activeIndex.value = selectedIndex >= 0 ? selectedIndex : Math.max(0, firstEnabled)
}

async function openMenu(): Promise<void> {
  if (props.disabled || open.value) return
  query.value = ''
  open.value = true
  resetActive()
  await nextTick()
  updatePosition()
  if (searchEnabled.value) searchInput.value?.focus()
}

function closeMenu(restoreFocus = false): void {
  if (!open.value) return
  open.value = false
  query.value = ''
  if (restoreFocus) void nextTick(() => trigger.value?.focus())
}

function toggleMenu(): void {
  if (open.value) closeMenu()
  else void openMenu()
}

function selectOption(option: SelectOption): void {
  if (option.disabled) return
  if (option.value !== props.modelValue) {
    emit('update:modelValue', option.value)
    emit('change', option.value)
  }
  closeMenu(true)
}

function moveActive(step: number): void {
  const length = filteredOptions.value.length
  if (!length) return
  let next = activeIndex.value
  for (let index = 0; index < length; index += 1) {
    next = (next + step + length) % length
    if (!filteredOptions.value[next]?.disabled) break
  }
  activeIndex.value = next
  void nextTick(scrollActiveIntoView)
}

function selectActive(): void {
  const option = filteredOptions.value[activeIndex.value]
  if (option && !option.disabled) selectOption(option)
}

function onTriggerKeydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) void openMenu()
    else moveActive(event.key === 'ArrowDown' ? 1 : -1)
    return
  }
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (open.value) selectActive()
    else void openMenu()
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu(true)
  }
}

function onMenuKeydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    moveActive(event.key === 'ArrowDown' ? 1 : -1)
  } else if (event.key === 'Home') {
    event.preventDefault()
    activeIndex.value = 0
    void nextTick(scrollActiveIntoView)
  } else if (event.key === 'End') {
    event.preventDefault()
    activeIndex.value = Math.max(0, filteredOptions.value.length - 1)
    void nextTick(scrollActiveIntoView)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    selectActive()
  } else if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu(true)
  } else if (event.key === 'Tab') {
    closeMenu()
  }
}

function onPointerDown(event: PointerEvent): void {
  const target = event.target as Node
  if (!root.value?.contains(target) && !menu.value?.contains(target)) closeMenu()
}

function updatePosition(): void {
  if (!open.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const viewportGap = 12
  const menuGap = 6
  const minimumWidth = Math.min(260, viewportWidth - viewportGap * 2)
  const width = Math.min(Math.max(rect.width, minimumWidth), 340, viewportWidth - viewportGap * 2)
  const desiredLeft = props.menuAlign === 'end' ? rect.right - width : rect.left
  const left = Math.min(
    Math.max(desiredLeft, viewportGap),
    Math.max(viewportGap, viewportWidth - width - viewportGap),
  )
  const roomBelow = viewportHeight - rect.bottom - menuGap - viewportGap
  const roomAbove = rect.top - menuGap - viewportGap
  const placeAbove = roomBelow < 220 && roomAbove > roomBelow
  const availableHeight = Math.max(120, placeAbove ? roomAbove : roomBelow)
  const searchHeight = searchEnabled.value ? 47 : 0
  const optionsHeight = Math.max(72, Math.min(264, availableHeight - searchHeight - 2))

  menuPlacement.value = placeAbove ? 'top' : 'bottom'
  menuStyle.value = {
    left: `${Math.round(left)}px`,
    width: `${Math.round(width)}px`,
    top: placeAbove ? 'auto' : `${Math.round(rect.bottom + menuGap)}px`,
    bottom: placeAbove ? `${Math.round(viewportHeight - rect.top + menuGap)}px` : 'auto',
    '--select-options-max-height': `${Math.floor(optionsHeight)}px`,
  } as CSSProperties
}

function schedulePositionUpdate(): void {
  if (!open.value || positionFrame) return
  positionFrame = window.requestAnimationFrame(() => {
    positionFrame = 0
    updatePosition()
  })
}

function scrollActiveIntoView(): void {
  const active = menu.value?.querySelector<HTMLElement>(`#${CSS.escape(activeOptionId.value ?? '')}`)
  active?.scrollIntoView({ block: 'nearest' })
}

function onInvalid(event: Event): void {
  event.preventDefault()
  void openMenu().then(() => trigger.value?.focus())
}

watch(filteredOptions, () => {
  activeIndex.value = 0
})
watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) closeMenu()
  },
)

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  window.addEventListener('resize', schedulePositionUpdate)
  window.addEventListener('scroll', schedulePositionUpdate, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  window.removeEventListener('resize', schedulePositionUpdate)
  window.removeEventListener('scroll', schedulePositionUpdate, true)
  if (positionFrame) window.cancelAnimationFrame(positionFrame)
})
</script>

<template>
  <div
    ref="root"
    class="app-select"
    :class="{ 'app-select--open': open, 'app-select--disabled': disabled }"
  >
    <button
      :id="id"
      ref="trigger"
      type="button"
      class="app-select__trigger"
      role="combobox"
      aria-haspopup="listbox"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      :aria-controls="listId"
      :aria-required="required || undefined"
      :disabled="disabled"
      @click="toggleMenu"
      @keydown="onTriggerKeydown"
    >
      <span :title="selectedOption?.label">{{ selectedOption?.label }}</span>
      <AppIcon name="chevron-down" :size="15" />
    </button>

    <input
      v-if="required || name"
      class="app-select__native"
      type="text"
      :name="name"
      :value="String(modelValue)"
      :required="required"
      :disabled="disabled"
      tabindex="-1"
      aria-hidden="true"
      @invalid="onInvalid"
    />

    <Teleport to="body">
      <Transition name="select-menu">
        <div
          v-if="open"
          ref="menu"
          class="app-select__menu"
          :class="`app-select__menu--${menuPlacement}`"
          :style="menuStyle"
          @keydown="onMenuKeydown"
        >
          <label v-if="searchEnabled" class="app-select__search">
            <AppIcon name="search" :size="15" />
            <input
              ref="searchInput"
              v-model="query"
              type="search"
              :placeholder="searchPlaceholder"
              :aria-label="`${ariaLabel}选项搜索`"
              :aria-activedescendant="activeOptionId"
            />
          </label>

          <div :id="listId" class="app-select__options" role="listbox" :aria-label="ariaLabel">
            <button
              v-for="(option, index) in filteredOptions"
              :id="`${listId}-option-${index}`"
              :key="String(option.value) || '__all__'"
              type="button"
              class="app-select__option"
              :class="{
                'app-select__option--active': activeIndex === index,
                'app-select__option--selected': option.value === modelValue,
              }"
              role="option"
              :aria-selected="option.value === modelValue"
              :aria-disabled="option.disabled || undefined"
              :disabled="option.disabled"
              @mouseenter="activeIndex = index"
              @click="selectOption(option)"
            >
              <span class="app-select__option-label">{{ option.label }}</span>
              <small v-if="option.count !== undefined">{{ option.count }}</small>
              <AppIcon v-if="option.value === modelValue" name="check" :size="15" />
              <span v-else class="app-select__check-space" />
            </button>
            <p v-if="!filteredOptions.length" class="app-select__empty">{{ emptyText }}</p>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.app-select {
  position: relative;
  min-width: 0;
  width: 100%;
  font-size: 13px;
}

.app-select__trigger {
  width: 100%;
  min-width: 0;
  height: 40px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 0 10px 0 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
  text-align: left;
  transition:
    border-color var(--motion-fast) ease,
    box-shadow var(--motion-base) var(--motion-ease),
    background-color var(--motion-fast) ease;
}

.app-select__trigger > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select__trigger :deep(.app-icon) {
  color: var(--text-3);
  transition:
    transform var(--motion-base) var(--motion-ease),
    color var(--motion-fast) ease;
}

.app-select__trigger:hover:not(:disabled) {
  border-color: #aeb8c6;
  background: #fbfcfe;
}

.app-select--open .app-select__trigger {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.app-select--open .app-select__trigger :deep(.app-icon) {
  color: var(--primary);
  transform: rotate(180deg);
}

.app-select--disabled .app-select__trigger {
  cursor: not-allowed;
  background: #f2f4f7;
  color: var(--text-3);
}

.app-select__menu {
  position: fixed;
  z-index: 250;
  overflow: hidden;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: 0 14px 34px rgba(16, 24, 40, 0.16), 0 3px 8px rgba(16, 24, 40, 0.08);
}

.app-select__native {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  border: 0;
  opacity: 0;
  pointer-events: none;
}

.app-select__search {
  height: 46px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-bottom: 1px solid var(--border);
  color: var(--text-3);
}

.app-select__search input {
  min-width: 0;
  height: 32px;
  padding: 0 8px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font-size: 13px;
}

.app-select__search input::placeholder {
  color: var(--text-3);
}

.app-select__options {
  max-height: var(--select-options-max-height, 264px);
  overflow-y: auto;
  padding: 5px;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: #b6bec9 transparent;
}

.app-select__options::-webkit-scrollbar {
  width: 6px;
}

.app-select__options::-webkit-scrollbar-track {
  background: transparent;
}

.app-select__options::-webkit-scrollbar-thumb {
  border: 1px solid transparent;
  border-radius: 999px;
  background: #b6bec9;
  background-clip: padding-box;
}

.app-select__options::-webkit-scrollbar-thumb:hover {
  background: #8f9cad;
  background-clip: padding-box;
}

.app-select__option {
  width: 100%;
  min-height: 36px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 16px;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-2);
  text-align: left;
  transition:
    background-color var(--motion-fast) ease,
    color var(--motion-fast) ease,
    transform var(--motion-fast) var(--motion-ease);
}

.app-select__option:hover,
.app-select__option--active {
  background: #f1f4f8;
  color: var(--text);
  transform: translateX(2px);
}

.app-select__option:disabled {
  cursor: not-allowed;
  color: var(--text-3);
  opacity: .68;
}

.app-select__option--selected {
  background: var(--primary-soft);
  color: var(--primary);
  font-weight: 600;
}

.app-select__option--selected:hover,
.app-select__option--selected.app-select__option--active {
  background: #dce8fd;
}

.app-select__option-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select__option small {
  min-width: 24px;
  color: var(--text-3);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.app-select__option--selected small,
.app-select__option--selected :deep(.app-icon) {
  color: var(--primary);
}

.app-select__check-space {
  width: 16px;
}

.app-select__empty {
  padding: 22px 12px;
  color: var(--text-3);
  font-size: 12px;
  text-align: center;
}

.select-menu-enter-active,
.select-menu-leave-active {
  transition:
    opacity var(--motion-fast) ease,
    transform var(--motion-base) var(--motion-ease-out);
}

.app-select__menu--bottom { transform-origin: top center; }
.app-select__menu--top { transform-origin: bottom center; }

.select-menu-enter-from,
.select-menu-leave-to {
  opacity: 0;
  transform: translateY(-5px) scaleY(0.98);
}

.app-select__menu--top.select-menu-enter-from,
.app-select__menu--top.select-menu-leave-to {
  transform: translateY(5px) scaleY(0.98);
}

@media (max-width: 640px) {
  .app-select__options {
    max-height: 240px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-select__trigger,
  .app-select__trigger :deep(.app-icon),
  .app-select__option,
  .select-menu-enter-active,
  .select-menu-leave-active {
    transition: none;
  }

  .app-select__option:hover,
  .app-select__option--active { transform: none; }
}
</style>
