<script setup lang="ts">
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import type { JobProofIconName } from '@/shared/ui/jobProofIcons'

export type CareerLibraryView = 'profile' | 'records' | 'files'

defineProps<{ modelValue: CareerLibraryView }>()
const emit = defineEmits<{ 'update:modelValue': [value: CareerLibraryView] }>()

const tabs: Array<{ value: CareerLibraryView; label: string; icon: JobProofIconName }> = [
  { value: 'profile', label: '个人信息', icon: 'library-personal-info' },
  { value: 'records', label: '经历成果', icon: 'library-experience-achievements' },
  { value: 'files', label: '文件资料', icon: 'library-documents' },
]
</script>

<template>
  <nav class="career-tabs" :data-active="modelValue" aria-label="求职资料库页签">
    <button
      v-for="tab in tabs"
      :key="tab.value"
      type="button"
      role="tab"
      :class="{ active: modelValue === tab.value }"
      :aria-selected="modelValue === tab.value"
      @click="emit('update:modelValue', tab.value)"
    >
      <JobProofIcon :name="tab.icon" :size="18" />
      {{ tab.label }}
    </button>
  </nav>
</template>
