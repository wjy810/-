<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CalendarDays, Check, CircleAlert, Clock3, LoaderCircle, Sparkles } from 'lucide-vue-next'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
import { errorMessage } from '@/shared/api/types'
import { createCareerLearningPlan } from '../services/careerPlanningApi'
import type { CareerLearningPlan } from '../types'

const props = defineProps<{ open: boolean; sessionId: string; canvasVersion: number; suggestedHours?: number }>()
const emit = defineEmits<{ close: []; created: [plan: CareerLearningPlan] }>()

const weeks = ref(8)
const customWeeks = ref(6)
const intensity = ref<'LIGHT' | 'STANDARD' | 'FOCUSED'>('STANDARD')
const weeklyHours = ref(10)
const learningDays = ref<number[]>([1, 3, 6])
const startDateTime = ref('')
const pending = ref(false)
const message = ref('')

watch(() => props.open, open => {
  if (!open) return
  weeklyHours.value = props.suggestedHours || 10
  message.value = ''
})

const durationWeeks = computed(() => weeks.value === 0 ? customWeeks.value : weeks.value)
const startDate = computed(() => startDateTime.value ? startDateTime.value.slice(0, 10) : new Date().toISOString().slice(0, 10))
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const hourOptions = Array.from({ length: 40 }, (_, index) => ({ value: String(index + 1), label: `${index + 1} 小时 / 周` }))

function toggleDay(day: number): void {
  learningDays.value = learningDays.value.includes(day)
    ? learningDays.value.filter(value => value !== day)
    : [...learningDays.value, day].sort((a, b) => a - b)
}

async function submit(): Promise<void> {
  if (durationWeeks.value < 2 || durationWeeks.value > 24) { message.value = '自定义周期需要在 2 至 24 周之间。'; return }
  if (!learningDays.value.length) { message.value = '请至少选择一个固定学习日。'; return }
  pending.value = true; message.value = ''
  try {
    const plan = await createCareerLearningPlan(props.sessionId, {
      durationWeeks: durationWeeks.value, intensity: intensity.value, weeklyHours: weeklyHours.value,
      learningDays: learningDays.value, startDate: startDate.value, expectedCanvasVersion: props.canvasVersion,
    })
    emit('created', plan)
    emit('close')
  } catch (reason) { message.value = errorMessage(reason, '学习计划生成失败') }
  finally { pending.value = false }
}
</script>

<template>
  <AppModal :open="open" title="行动计划" :width="760" mobile-sheet @close="emit('close')">
    <div class="plan-setup">
      <header><span><Sparkles :size="25" /></span><div><h2>把能力画布转成可执行计划</h2><p>系统按画布前置关系安排任务。已完成的课程不会自动等同于掌握，完成任务后仍需证据与验证。</p></div></header>
      <section><h3>计划周期</h3><div class="period-grid"><button v-for="value in [4,8,12]" :key="value" type="button" :class="{ active: weeks === value }" @click="weeks = value"><strong>{{ value }} 周</strong><small>{{ value === 4 ? '快速验证' : value === 8 ? '标准推进' : '系统提升' }}</small><Check v-if="weeks === value" :size="15" /></button><button class="period-custom" type="button" :class="{ active: weeks === 0 }" @click="weeks = 0"><strong>自定义</strong><small>2 至 24 周</small><Check v-if="weeks === 0" :size="15" /></button></div><label v-if="weeks === 0" class="custom-weeks">自定义周数 <input v-model.number="customWeeks" type="number" min="2" max="24"></label></section>
      <div class="setup-grid">
        <section><h3>计划强度</h3><div class="intensity-list"><button v-for="option in [{value:'LIGHT',label:'轻量',copy:'适合工作繁忙'},{value:'STANDARD',label:'标准',copy:'稳定推进，推荐'},{value:'FOCUSED',label:'集中',copy:'短期高投入'}]" :key="option.value" type="button" :class="{ active: intensity === option.value }" @click="intensity = option.value as typeof intensity"><span></span><p><strong>{{ option.label }}</strong><small>{{ option.copy }}</small></p></button></div></section>
        <section><h3>时间安排</h3><label><span><Clock3 :size="15" />每周投入</span><AppSelect v-model="weeklyHours" :options="hourOptions" aria-label="每周学习时长" /></label><label><span><CalendarDays :size="15" />开始日期</span><AppDatePicker v-model="startDateTime" mode="datetime" label="计划开始日期" placeholder="默认今天开始" /></label></section>
      </div>
      <section><h3>固定学习日</h3><div class="weekday-list"><button v-for="(label,index) in weekdayLabels" :key="label" type="button" :class="{ active: learningDays.includes(index + 1) }" @click="toggleDay(index + 1)">周{{ label }}</button></div></section>
      <p class="plan-preview"><CalendarDays :size="17" />预计 {{ durationWeeks }} 周，每周 {{ weeklyHours }} 小时，共安排 {{ learningDays.length }} 个固定学习日。</p>
      <p v-if="message" class="plan-error" role="alert"><CircleAlert :size="16" />{{ message }}</p>
    </div>
    <template #footer><button class="setup-cancel" type="button" @click="emit('close')">取消</button><button class="setup-custom" type="button" @click="weeks = 0">自定义计划</button><button class="setup-submit" type="button" :disabled="pending" @click="submit"><LoaderCircle v-if="pending" class="spin" :size="17" /><Sparkles v-else :size="17" />生成 {{ durationWeeks }} 周计划</button></template>
  </AppModal>
</template>

<style scoped>
.plan-setup{display:grid;gap:20px;color:var(--text-primary)}.plan-setup>header{display:flex;gap:12px;padding:14px;border-radius:10px;background:var(--surface-2)}.plan-setup>header>span{display:grid;place-items:center;flex:0 0 44px;height:44px;border-radius:11px;color:var(--color-primary);background:var(--surface-1)}.plan-setup h2,.plan-setup h3{margin:0}.plan-setup h2{font-size:19px}.plan-setup h3{margin-bottom:10px;font-size:14px}.plan-setup p{margin:5px 0 0;color:var(--text-secondary);line-height:1.55}.period-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:9px}.period-grid button{position:relative;display:grid;gap:5px;min-height:76px;padding:12px;border:1px solid var(--border-subtle);border-radius:9px;text-align:left;color:var(--text-primary);background:var(--surface-1);transition:border-color .2s ease,background-color .2s ease,transform .2s ease}.period-grid button:hover{transform:none}.period-grid button.active{border-color:var(--color-primary);background:var(--surface-2);box-shadow:0 0 0 2px color-mix(in srgb, var(--color-primary) 6%, transparent)}.period-grid small,.intensity-list small{color:var(--text-tertiary)}.period-grid svg{position:absolute;right:9px;top:9px;color:var(--color-primary)}.custom-weeks{display:flex;align-items:center;gap:10px;margin-top:10px;color:var(--text-secondary);font-size:13px}.custom-weeks input{width:96px;padding:8px;border:1px solid var(--border-subtle);border-radius:7px}.setup-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.intensity-list{display:grid;gap:7px}.intensity-list button{display:flex;align-items:center;gap:9px;min-height:52px;padding:8px 11px;border:1px solid var(--border-subtle);border-radius:8px;text-align:left;background:var(--surface-1);transition:border-color .2s ease,background-color .2s ease}.intensity-list button>span{width:16px;height:16px;border:1px solid var(--border-strong);border-radius:50%}.intensity-list button.active{border-color:var(--color-primary-border);background:var(--surface-2)}.intensity-list button.active>span{border:5px solid var(--color-primary)}.intensity-list p{display:grid;margin:0}.setup-grid section:last-child{display:grid;align-content:start;gap:12px}.setup-grid label{display:grid;gap:7px}.setup-grid label>span{display:flex;align-items:center;gap:6px;color:var(--text-secondary);font-size:13px}.weekday-list{display:flex;flex-wrap:wrap;gap:8px}.weekday-list button{min-width:70px;height:38px;border:1px solid var(--border-subtle);border-radius:19px;color:var(--text-secondary);background:var(--surface-1)}.weekday-list button.active{color:var(--color-primary);border-color:var(--color-primary-border);background:var(--surface-2)}.plan-preview,.plan-error{display:flex;align-items:center;gap:7px!important;margin:0!important;padding:11px 13px;border-radius:8px;color:var(--color-primary)!important;background:var(--surface-2)}.plan-error{color:var(--color-danger-text)!important;background:var(--surface-2)}.setup-cancel,.setup-custom,.setup-submit{min-height:40px;padding:0 17px;border:1px solid var(--border-subtle);border-radius:8px;background:var(--surface-1)}.setup-custom{display:none;color:var(--color-primary);border-color:var(--color-primary)}.setup-submit{display:flex;align-items:center;justify-content:center;gap:7px;color:var(--text-on-primary);border-color:var(--color-primary);background:var(--color-primary)}.setup-submit:disabled{opacity:.55}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:700px){.plan-setup{gap:18px}.plan-setup>header{padding:0;background:transparent}.plan-setup>header>span{background:var(--surface-2)}.plan-setup h2{font-size:21px}.period-grid{grid-template-columns:1fr}.period-grid button{min-height:88px;padding:15px 18px;border-radius:12px}.period-grid .period-custom{display:none}.setup-grid{grid-template-columns:1fr}.weekday-list{display:grid;grid-template-columns:repeat(7,1fr);gap:7px}.weekday-list button{min-width:0;width:100%;height:42px;padding:0;border-radius:50%}.plan-preview{align-items:flex-start!important}.setup-cancel{display:none}.setup-custom{display:flex;align-items:center;justify-content:center;flex:1}.setup-submit{flex:1.5}.intensity-list button{min-height:58px;border-radius:10px}}
@media(prefers-reduced-motion:reduce){.period-grid button,.intensity-list button{transition:none}.period-grid button:hover{transform:none}}
</style>
