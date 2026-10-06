<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '@/shared/api/types'
import AppIcon from '@/shared/ui/AppIcon.vue'
import { ensureAiResume } from '../services/aiResumeApi'

const route = useRoute()
const router = useRouter()
const error = ref('')

onMounted(async () => {
  const masterId = String(route.params.id ?? '')
  if (typeof route.query.versionId === 'string') {
    await router.replace({ path: `/resumes/${masterId}/manual`, query: route.query })
    return
  }
  try {
    const conversation = await ensureAiResume(masterId)
    await router.replace(`/ai-resume/${conversation.id}`)
  } catch (reason) {
    error.value = errorMessage(reason, 'AI 工作台初始化失败')
  }
})
</script>

<template>
  <main class="resume-redirect" :aria-busy="!error">
    <AppIcon :name="error ? 'alert-circle' : 'loader'" :size="24" />
    <h1>{{ error ? '无法打开简历' : '正在打开 AI 工作台' }}</h1>
    <p>{{ error || '正在读取长期主对话和当前简历版本…' }}</p>
    <RouterLink v-if="error" class="btn btn--ghost" to="/resumes">返回简历列表</RouterLink>
  </main>
</template>

<style scoped>
.resume-redirect { min-height: 100vh; display: grid; place-content: center; justify-items: center; gap: 10px; padding: 24px; text-align: center; background: var(--bg); }
.resume-redirect h1 { font-size: 18px; }
.resume-redirect p { color: var(--text-2); }
.resume-redirect .app-icon { color: var(--primary); }
</style>
