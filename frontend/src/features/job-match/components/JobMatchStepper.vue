<script setup lang="ts">
import { computed } from 'vue'
import { Check } from 'lucide-vue-next'
import { stepperTrackProgress } from '../utils/uiPresentation'

const props = defineProps<{ current: number }>()
const steps = ['岗位信息', '选择简历', '补充证据', '确认分析']
const trackProgress = computed(() => stepperTrackProgress(props.current))
</script>

<template>
  <ol class="jm-stepper" aria-label="岗位匹配创建进度" :style="{ '--step-progress': `${trackProgress}%` }">
    <li
      v-for="(label, index) in steps"
      :key="label"
      :class="{ 'is-active': index + 1 === current, 'is-done': index + 1 < current }"
      :aria-current="index + 1 === current ? 'step' : undefined"
    >
      <span><Check v-if="index + 1 < current" :size="15" /><template v-else>{{ index + 1 }}</template></span>
      <strong>{{ label }}</strong>
    </li>
  </ol>
</template>
