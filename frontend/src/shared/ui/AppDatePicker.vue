<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import type { CSSProperties } from 'vue'
import AppIcon from './AppIcon.vue'
import {
  buildCalendarDays,
  datetimeValue,
  dayValue,
  formatDateTimeDisplay,
  formatMonthDisplay,
  monthValue,
  parseDateTimeValue,
  parseMonthValue,
} from './datePicker'

type PickerMode = 'month' | 'datetime'
type PickerPanel = 'days' | 'months' | 'years'

const props = withDefaults(defineProps<{
  modelValue?: string
  mode?: PickerMode
  id?: string
  name?: string
  label?: string
  placeholder?: string
  disabled?: boolean
  required?: boolean
}>(), {
  modelValue: '',
  mode: 'month',
  id: undefined,
  name: undefined,
  label: '日期',
  placeholder: '',
  disabled: false,
  required: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const popup = ref<HTMLElement | null>(null)
const open = ref(false)
const placement = ref<'top' | 'bottom' | 'overlay'>('bottom')
const popupStyle = ref<CSSProperties>({})
const panel = ref<PickerPanel>('months')
const viewYear = ref(new Date().getFullYear())
const viewMonth = ref(new Date().getMonth())
const selectedDay = ref('')
const hour = ref('09')
const minute = ref('00')
const popupId = `app-date-picker-${useId().replaceAll(':', '')}`
const monthLabels = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
let positionFrame = 0

const displayValue = computed(() => props.mode === 'month'
  ? formatMonthDisplay(props.modelValue)
  : formatDateTimeDisplay(props.modelValue))
const displayPlaceholder = computed(() => props.placeholder || (props.mode === 'month' ? '请选择月份' : '请选择日期和时间'))
const selectedMonth = computed(() => parseMonthValue(props.modelValue))
const yearStart = computed(() => Math.floor(viewYear.value / 12) * 12)
const years = computed(() => Array.from({ length: 12 }, (_, index) => yearStart.value + index))
const calendarDays = computed(() => buildCalendarDays(viewYear.value, viewMonth.value, selectedDay.value))
const headerLabel = computed(() => {
  if (panel.value === 'years') return `${yearStart.value} - ${yearStart.value + 11}`
  if (panel.value === 'months') return `${viewYear.value}年`
  return `${viewYear.value}年${viewMonth.value + 1}月`
})

function emitValue(value: string): void {
  emit('update:modelValue', value)
  emit('change', value)
}

function syncDraft(): void {
  const now = new Date()
  if (props.mode === 'month') {
    const parsed = parseMonthValue(props.modelValue)
    viewYear.value = parsed?.year ?? now.getFullYear()
    viewMonth.value = parsed?.month ?? now.getMonth()
    panel.value = 'months'
    return
  }
  const parsed = parseDateTimeValue(props.modelValue)
  viewYear.value = parsed?.year ?? now.getFullYear()
  viewMonth.value = parsed?.month ?? now.getMonth()
  selectedDay.value = parsed ? dayValue(parsed.year, parsed.month, parsed.day) : ''
  hour.value = String(parsed?.hour ?? now.getHours()).padStart(2, '0')
  minute.value = String(parsed?.minute ?? now.getMinutes()).padStart(2, '0')
  panel.value = 'days'
}

async function openPicker(): Promise<void> {
  if (props.disabled || open.value) return
  syncDraft()
  open.value = true
  await nextTick()
  updatePosition()
  const preferred = popup.value?.querySelector<HTMLButtonElement>('.is-selected, .is-today')
  preferred?.focus({ preventScroll: true })
}

function closePicker(restoreFocus = false): void {
  if (!open.value) return
  open.value = false
  if (restoreFocus) void nextTick(() => trigger.value?.focus())
}

function togglePicker(): void {
  if (open.value) closePicker()
  else void openPicker()
}

function togglePanel(): void {
  if (panel.value === 'years') panel.value = 'months'
  else if (panel.value === 'months') panel.value = 'years'
  else panel.value = 'months'
  void nextTick(updatePosition)
}

function movePeriod(step: number): void {
  if (panel.value === 'years') {
    viewYear.value += step * 12
  } else if (panel.value === 'months' || props.mode === 'month') {
    viewYear.value += step
  } else {
    const date = new Date(viewYear.value, viewMonth.value + step, 1)
    viewYear.value = date.getFullYear()
    viewMonth.value = date.getMonth()
  }
}

function selectYear(year: number): void {
  viewYear.value = year
  panel.value = 'months'
  void nextTick(updatePosition)
}

function selectMonth(month: number): void {
  viewMonth.value = month
  if (props.mode === 'month') {
    emitValue(monthValue(viewYear.value, month))
    closePicker(true)
  } else {
    panel.value = 'days'
    void nextTick(updatePosition)
  }
}

function selectCalendarDay(day: (typeof calendarDays.value)[number]): void {
  selectedDay.value = day.value
  viewYear.value = day.year
  viewMonth.value = day.month
}

function clearValue(): void {
  emitValue('')
  closePicker(true)
}

function chooseCurrent(): void {
  const now = new Date()
  viewYear.value = now.getFullYear()
  viewMonth.value = now.getMonth()
  if (props.mode === 'month') {
    emitValue(monthValue(now.getFullYear(), now.getMonth()))
    closePicker(true)
    return
  }
  selectedDay.value = dayValue(now.getFullYear(), now.getMonth(), now.getDate())
  hour.value = String(now.getHours()).padStart(2, '0')
  minute.value = String(now.getMinutes()).padStart(2, '0')
  panel.value = 'days'
}

function timeInput(target: 'hour' | 'minute', event: Event): void {
  const value = (event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 2)
  if (target === 'hour') hour.value = value
  else minute.value = value
}

function normalizeTime(target: 'hour' | 'minute'): void {
  const maximum = target === 'hour' ? 23 : 59
  const current = target === 'hour' ? hour.value : minute.value
  const normalized = String(Math.min(maximum, Math.max(0, Number(current) || 0))).padStart(2, '0')
  if (target === 'hour') hour.value = normalized
  else minute.value = normalized
}

function confirmDateTime(): void {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(selectedDay.value)
  if (!match) return
  normalizeTime('hour')
  normalizeTime('minute')
  emitValue(datetimeValue({
    year: Number(match[1]),
    month: Number(match[2]) - 1,
    day: Number(match[3]),
    hour: Number(hour.value),
    minute: Number(minute.value),
  }))
  closePicker(true)
}

function onTriggerKeydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    void openPicker()
  } else if (event.key === 'Escape') {
    closePicker(true)
  }
}

function onPopupKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    closePicker(true)
  }
}

function onPointerDown(event: PointerEvent): void {
  const target = event.target as Node
  if (!root.value?.contains(target) && !popup.value?.contains(target)) closePicker()
}

function updatePosition(): void {
  if (!open.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const viewportGap = 12
  const popupGap = 6
  const width = Math.min(
    Math.max(rect.width, props.mode === 'datetime' ? 344 : 320),
    window.innerWidth - viewportGap * 2,
  )
  const left = Math.min(
    Math.max(rect.left, viewportGap),
    Math.max(viewportGap, window.innerWidth - width - viewportGap),
  )
  const roomBelow = window.innerHeight - rect.bottom - popupGap - viewportGap
  const roomAbove = rect.top - popupGap - viewportGap
  const expectedHeight = props.mode === 'datetime' ? 466 : 322
  const overlay = roomBelow < expectedHeight
    && roomAbove < expectedHeight
    && window.innerHeight - viewportGap * 2 >= expectedHeight
  const above = !overlay && roomBelow < expectedHeight && roomAbove > roomBelow
  const availableHeight = overlay
    ? window.innerHeight - viewportGap * 2
    : Math.max(180, above ? roomAbove : roomBelow)
  placement.value = overlay ? 'overlay' : above ? 'top' : 'bottom'
  const overlayTop = Math.min(
    Math.max(rect.top - expectedHeight / 2, viewportGap),
    window.innerHeight - expectedHeight - viewportGap,
  )
  popupStyle.value = {
    left: `${Math.round(left)}px`,
    width: `${Math.round(width)}px`,
    top: above ? 'auto' : `${Math.round(overlay ? overlayTop : rect.bottom + popupGap)}px`,
    bottom: above ? `${Math.round(window.innerHeight - rect.top + popupGap)}px` : 'auto',
    maxHeight: `${Math.floor(availableHeight)}px`,
  }
}

function schedulePositionUpdate(): void {
  if (!open.value || positionFrame) return
  positionFrame = window.requestAnimationFrame(() => {
    positionFrame = 0
    updatePosition()
  })
}

function onInvalid(event: Event): void {
  event.preventDefault()
  void openPicker().then(() => trigger.value?.focus())
}

watch(() => props.disabled, (disabled) => { if (disabled) closePicker() })
watch(() => props.modelValue, () => { if (open.value) syncDraft() })

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
  <span ref="root" class="app-date-picker" :class="{ 'app-date-picker--open': open, 'app-date-picker--disabled': disabled }">
    <button
      :id="id"
      ref="trigger"
      type="button"
      class="app-date-picker__trigger"
      :aria-label="label"
      aria-haspopup="dialog"
      :aria-expanded="open"
      :aria-controls="popupId"
      :aria-required="required || undefined"
      :disabled="disabled"
      @click="togglePicker"
      @keydown="onTriggerKeydown"
    >
      <span :class="{ placeholder: !displayValue }">{{ displayValue || displayPlaceholder }}</span>
      <span class="app-date-picker__icon"><AppIcon name="calendar" :size="16" /></span>
    </button>

    <input
      v-if="required || name"
      class="app-date-picker__native"
      type="text"
      :name="name"
      :value="modelValue"
      :required="required"
      :disabled="disabled"
      tabindex="-1"
      aria-hidden="true"
      @invalid="onInvalid"
    />

    <Teleport to="body">
      <Transition name="date-picker-popover">
        <section
          v-if="open"
          :id="popupId"
          ref="popup"
          class="app-date-picker__popover"
          :class="`app-date-picker__popover--${placement}`"
          :style="popupStyle"
          role="dialog"
          :aria-label="`${label}选择器`"
          @keydown="onPopupKeydown"
        >
          <header class="app-date-picker__header">
            <button type="button" :aria-label="panel === 'days' ? '上个月' : '上一组'" @click="movePeriod(-1)"><AppIcon name="chevron-left" :size="17" /></button>
            <button type="button" class="app-date-picker__title" :aria-label="`切换日期层级，当前${headerLabel}`" @click="togglePanel">{{ headerLabel }}</button>
            <button type="button" :aria-label="panel === 'days' ? '下个月' : '下一组'" @click="movePeriod(1)"><AppIcon name="chevron-right" :size="17" /></button>
          </header>

          <div v-if="panel === 'years'" class="app-date-picker__year-grid">
            <button
              v-for="year in years"
              :key="year"
              type="button"
              :class="{ 'is-selected': year === viewYear }"
              @click="selectYear(year)"
            >{{ year }}</button>
          </div>

          <div v-else-if="panel === 'months'" class="app-date-picker__month-grid">
            <button
              v-for="(month, index) in monthLabels"
              :key="month"
              type="button"
              :class="{
                'is-selected': selectedMonth?.year === viewYear && selectedMonth.month === index,
                'is-current': new Date().getFullYear() === viewYear && new Date().getMonth() === index,
              }"
              @click="selectMonth(index)"
            >{{ month }}</button>
          </div>

          <template v-else>
            <div class="app-date-picker__weekdays" aria-hidden="true"><span v-for="weekday in weekdays" :key="weekday">{{ weekday }}</span></div>
            <div class="app-date-picker__day-grid">
              <button
                v-for="day in calendarDays"
                :key="day.value"
                type="button"
                :class="{
                  'is-outside': !day.inCurrentMonth,
                  'is-selected': day.selected,
                  'is-today': day.today,
                }"
                :aria-label="`${day.year}年${day.month + 1}月${day.day}日${day.today ? '，今天' : ''}`"
                :aria-pressed="day.selected"
                @click="selectCalendarDay(day)"
              >{{ day.day }}</button>
            </div>
            <div class="app-date-picker__time">
              <AppIcon name="clock" :size="16" />
              <span>时间</span>
              <input :value="hour" inputmode="numeric" maxlength="2" aria-label="小时" @input="timeInput('hour', $event)" @blur="normalizeTime('hour')" />
              <b>:</b>
              <input :value="minute" inputmode="numeric" maxlength="2" aria-label="分钟" @input="timeInput('minute', $event)" @blur="normalizeTime('minute')" />
            </div>
          </template>

          <footer class="app-date-picker__footer">
            <button type="button" class="app-date-picker__text-action" :disabled="!modelValue" @click="clearValue">清除</button>
            <div>
              <button type="button" class="app-date-picker__text-action" @click="chooseCurrent">{{ mode === 'month' ? '本月' : '今天' }}</button>
              <button v-if="mode === 'datetime'" type="button" class="app-date-picker__confirm" :disabled="!selectedDay" @click="confirmDateTime">确定</button>
            </div>
          </footer>
        </section>
      </Transition>
    </Teleport>
  </span>
</template>

<style scoped>
.app-date-picker { position: relative; display: block; width: 100%; min-width: 0; font-size: 13px; }
.app-date-picker__trigger { width: 100%; min-width: 0; height: 40px; padding: 0 6px 0 12px; display: grid; grid-template-columns: minmax(0, 1fr) 30px; align-items: center; gap: 8px; color: var(--text); background: var(--surface); border: 1px solid var(--border-strong); border-radius: var(--radius); text-align: left; transition: border-color 150ms ease, box-shadow 150ms ease, background 150ms ease; }
.app-date-picker__trigger:hover:not(:disabled) { border-color: #aeb8c6; background: #fbfcfe; }
.app-date-picker--open .app-date-picker__trigger { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(37, 99, 235, .12); }
.app-date-picker__trigger > span:first-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.app-date-picker__trigger .placeholder { color: var(--text-3); font-weight: 400; }
.app-date-picker__icon { width: 28px; height: 28px; display: grid; place-items: center; color: #47698f; background: #edf4fe; border-radius: 5px; }
.app-date-picker--disabled .app-date-picker__trigger { cursor: not-allowed; color: var(--text-3); background: #f2f4f7; }
.app-date-picker--disabled .app-date-picker__icon { color: #98a2b3; background: #e9edf2; }
.app-date-picker__native { position: absolute; width: 1px; height: 1px; padding: 0; border: 0; opacity: 0; pointer-events: none; }
.app-date-picker__popover { position: fixed; z-index: 260; overflow: auto; overscroll-behavior: contain; padding: 10px; color: var(--text); background: var(--surface); border: 1px solid var(--border-strong); border-radius: 8px; box-shadow: 0 18px 42px rgba(16, 24, 40, .18), 0 3px 10px rgba(16, 24, 40, .08); scrollbar-width: thin; scrollbar-color: #b6bec9 transparent; }
.app-date-picker__popover::-webkit-scrollbar { width: 6px; }
.app-date-picker__popover::-webkit-scrollbar-track { background: transparent; }
.app-date-picker__popover::-webkit-scrollbar-thumb { border: 1px solid transparent; border-radius: 999px; background: #b6bec9; background-clip: padding-box; }
.app-date-picker__header { height: 38px; display: grid; grid-template-columns: 34px minmax(0, 1fr) 34px; align-items: center; gap: 5px; margin-bottom: 6px; }
.app-date-picker__header button { height: 32px; display: grid; place-items: center; color: var(--text-2); background: transparent; border: 0; border-radius: 6px; }
.app-date-picker__header button:hover { color: var(--primary); background: #f1f4f8; }
.app-date-picker__header .app-date-picker__title { font-size: 14px; font-weight: 700; }
.app-date-picker__month-grid, .app-date-picker__year-grid { min-height: 204px; display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; padding: 7px 2px 10px; }
.app-date-picker__month-grid button, .app-date-picker__year-grid button { min-width: 0; height: 48px; color: var(--text-2); background: transparent; border: 1px solid transparent; border-radius: 6px; font-size: 13px; }
.app-date-picker__month-grid button:hover, .app-date-picker__year-grid button:hover { color: var(--text); background: #f1f4f8; }
.app-date-picker__month-grid button.is-current { border-color: #b9cdf1; color: var(--primary); }
.app-date-picker__month-grid button.is-selected, .app-date-picker__year-grid button.is-selected { color: #fff; background: var(--primary); border-color: var(--primary); font-weight: 700; }
.app-date-picker__weekdays, .app-date-picker__day-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 3px; }
.app-date-picker__weekdays { height: 26px; align-items: center; color: var(--text-3); font-size: 11px; font-weight: 600; text-align: center; }
.app-date-picker__day-grid button { aspect-ratio: 1; min-width: 0; min-height: 32px; display: grid; place-items: center; color: var(--text-2); background: transparent; border: 1px solid transparent; border-radius: 6px; font-size: 12px; font-variant-numeric: tabular-nums; }
.app-date-picker__day-grid button:hover { color: var(--text); background: #f1f4f8; }
.app-date-picker__day-grid button.is-outside { color: #b3bac5; }
.app-date-picker__day-grid button.is-today { border-color: #b9cdf1; color: var(--primary); font-weight: 700; }
.app-date-picker__day-grid button.is-selected { color: #fff; background: var(--primary); border-color: var(--primary); font-weight: 700; }
.app-date-picker__time { min-height: 46px; margin-top: 8px; padding: 7px 8px; display: grid; grid-template-columns: 18px 1fr 42px 8px 42px; align-items: center; gap: 5px; color: var(--text-2); background: #f6f8fb; border: 1px solid var(--border); border-radius: 7px; }
.app-date-picker__time > span { font-size: 12px; font-weight: 600; }
.app-date-picker__time input { width: 42px; height: 30px; padding: 0 5px; color: var(--text); background: #fff; border: 1px solid var(--border-strong); border-radius: 5px; outline: 0; font-size: 12px; font-variant-numeric: tabular-nums; text-align: center; }
.app-date-picker__time input:focus { border-color: var(--primary); box-shadow: 0 0 0 2px rgba(37, 99, 235, .1); }
.app-date-picker__time b { color: var(--text-3); text-align: center; }
.app-date-picker__footer { min-height: 44px; margin-top: 8px; padding: 8px 2px 0; display: flex; align-items: center; justify-content: space-between; gap: 8px; border-top: 1px solid var(--border); }
.app-date-picker__footer > div { display: flex; align-items: center; gap: 6px; }
.app-date-picker__text-action, .app-date-picker__confirm { height: 32px; padding: 0 10px; border: 0; border-radius: 6px; font-size: 12px; font-weight: 600; }
.app-date-picker__text-action { color: var(--primary); background: transparent; }
.app-date-picker__text-action:hover:not(:disabled) { background: var(--primary-soft); }
.app-date-picker__text-action:disabled { color: var(--text-3); cursor: not-allowed; }
.app-date-picker__confirm { min-width: 58px; color: #fff; background: var(--primary); }
.app-date-picker__confirm:hover:not(:disabled) { background: var(--primary-hover); }
.app-date-picker__confirm:disabled { opacity: .5; cursor: not-allowed; }
.date-picker-popover-enter-active, .date-picker-popover-leave-active { transition: opacity 140ms ease, transform 140ms ease; }
.app-date-picker__popover--bottom { transform-origin: top center; }
.app-date-picker__popover--top { transform-origin: bottom center; }
.app-date-picker__popover--overlay { transform-origin: center; }
.date-picker-popover-enter-from, .date-picker-popover-leave-to { opacity: 0; transform: translateY(-5px) scale(.99); }
@media (max-width: 480px) {
  .app-date-picker__popover { padding: 9px; }
  .app-date-picker__day-grid button { min-height: 34px; }
}
@media (prefers-reduced-motion: reduce) {
  .app-date-picker__trigger, .date-picker-popover-enter-active, .date-picker-popover-leave-active { transition: none; }
}
</style>
