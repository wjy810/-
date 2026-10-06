<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircle2, HelpCircle, LoaderCircle, MessageSquareText, ShieldQuestion } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import JobMatchContext from '../components/JobMatchContext.vue'
import { answerClarifications, fetchJobMatch, fetchMatchCapabilities } from '../services/jobMatchApi'
import type { JobMatch, MatchCapabilities } from '../types'
import { summaryRoute } from '../utils/stage'
import '../job-match.css'

const route = useRoute()
const router = useRouter()
const match = ref<JobMatch | null>(null)
const capabilities = ref<MatchCapabilities | null>(null)
const capabilitiesFailed = ref(false)
const answers = ref<Record<string, { answerCode: string; note: string }>>({})
const pending = ref(false)
const actionError = ref('')
useToastFeedback(actionError, 'error', 'job-match-clarifications-error')

const { error, loading, loaded, load } = useLoadState(async () => {
  const value = await fetchJobMatch(String(route.params.id))
  match.value = value
  value.clarifications.forEach((item) => {
    answers.value[item.id] = { answerCode: item.answerCode || '', note: item.answerNote || '' }
  })
  return value
})
const pendingQuestions = computed(() => match.value?.clarifications.filter(item => item.status === 'PENDING') ?? [])

async function loadCapabilities() {
  try {
    capabilities.value = await fetchMatchCapabilities()
  } catch (reason) {
    capabilitiesFailed.value = true
    console.warn('job-match capabilities unavailable', reason)
  }
}

async function submit(continueWithPending = false) {
  if (!match.value) return
  pending.value = true
  actionError.value = ''
  try {
    const values = pendingQuestions.value.filter(item => answers.value[item.id]?.answerCode).map(item => ({ id: item.id, ...answers.value[item.id] }))
    match.value = await answerClarifications(match.value.id, values, continueWithPending, match.value.version)
    await router.push({ name: 'job-match-analyzing', params: { id: match.value.id } })
  } catch (reason) {
    actionError.value = errorMessage(reason, '确认信息提交失败')
  } finally {
    pending.value = false
  }
}

onMounted(() => {
  void load()
  void loadCapabilities()
})
</script>

<template><main class="jm-page"><div class="jm-shell">
  <header class="jm-head"><div><h1>需要你确认的事实</h1><p>这些信息会影响匹配结论。无法确认可以稍后补充，系统不会把未知当成能力缺口。</p></div></header>
  <PageState :loading="loading" :error="error" :loaded="loaded" :empty="!pendingQuestions.length" error-title="待确认问题读取失败" @retry="load">
    <template #skeleton>
      <section class="jm-card jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在读取待确认问题</section>
    </template>
    <template #empty>
      <section class="jm-card jm-empty">
        <div>
          <span><CheckCircle2 :size="30" /></span>
          <h2>当前没有需要确认的问题</h2>
          <p>问题可能已经回答过，或者分析已经继续。</p>
          <button v-if="match?.status === 'NEEDS_CLARIFICATION'" class="jm-primary" type="button" :disabled="pending" @click="submit(true)">继续分析</button>
          <button v-else-if="match" class="jm-primary" type="button" @click="router.push(summaryRoute(match))">查看这次匹配</button>
        </div>
      </section>
    </template>

    <div class="jm-create-grid"><section class="jm-card jm-wizard"><div class="jm-wizard__body"><header><h2>补充 {{ pendingQuestions.length }} 项关键信息</h2><p>回答将生成新的报告版本，旧版本不会被覆盖。</p></header>
      <div class="jm-detail-list"><article v-for="(question,index) in pendingQuestions" :key="question.id" class="jm-detail-item jm-clarification-item"><span><HelpCircle :size="19" /></span><div><h3>{{ index+1 }}. {{ question.question }}</h3><div class="jm-choice-row jm-clarification-options"><button v-for="option in question.options" :key="option" class="jm-choice" :class="{ 'is-selected':answers[question.id]?.answerCode===option }" type="button" @click="answers[question.id]={answerCode:option,note:answers[question.id]?.note||''}"><strong>{{ option }}</strong></button></div><label class="jm-field"><span>补充说明（只填写真实经历）</span><textarea v-model="answers[question.id].note" rows="3" placeholder="可说明使用场景、项目、时间或可验证结果；不确定可以留空。" /></label></div></article></div>
      <div class="jm-privacy"><ShieldQuestion :size="18" /><p><strong>为什么要确认</strong><span>技能标签和关键词不能升级为强证据；AI 只有获得真实场景或来源后，才能给出强结论或学习计划。</span></p></div>
    </div><footer class="jm-wizard__footer"><button class="jm-secondary" type="button" :disabled="pending" @click="submit(true)">稍后补充，继续分析</button><button class="jm-primary" type="button" :disabled="pending || !pendingQuestions.some(item=>answers[item.id]?.answerCode)" @click="submit(false)"><LoaderCircle v-if="pending" class="jm-spin" :size="16" /><MessageSquareText v-else :size="16" />提交并继续</button></footer></section><JobMatchContext :match="match" :capabilities="capabilities" :capabilities-failed="capabilitiesFailed" /></div>
  </PageState>
</div></main></template>
