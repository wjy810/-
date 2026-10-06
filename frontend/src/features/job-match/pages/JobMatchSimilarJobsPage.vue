<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CheckCircle2, LoaderCircle, Target } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import { fetchCareerDirections } from '../services/jobMatchApi'
import '../job-match.css'

const route = useRoute()
const router = useRouter()
const matchId = computed(() => String(route.params.id))

const { data, error, loading, loaded, load } = useLoadState(() => fetchCareerDirections(matchId.value))
const directions = computed(() => data.value?.directions ?? [])
const resumeSkills = computed(() => data.value?.resumeSkills ?? [])

onMounted(load)
</script>

<template>
  <main class="jm-page"><div class="jm-shell">
    <header class="jm-head">
      <div>
        <button class="jm-text-btn" type="button" @click="router.push({ name: 'job-match-report', params: { id: matchId } })"><ArrowLeft :size="16" />返回报告</button>
        <h1>相似职业方向</h1>
        <p>把投递简历里的技能与岗位分类逐项对照，只列出有共同技能的方向。这些是方向参考，不是实时招聘职位。</p>
      </div>
    </header>

    <PageState :loading="loading" :error="error" :loaded="loaded" :empty="!directions.length" error-title="相似方向读取失败" @retry="load">
      <template #skeleton>
        <section class="jm-card jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在对照简历技能</section>
      </template>
      <template #empty>
        <section class="jm-card jm-empty">
          <div v-if="!resumeSkills.length">
            <span><Target :size="30" /></span>
            <h2>投递简历里还没有技能条目</h2>
            <p>在简历的“专业技能”中补充技能后，再回来查看有共同技能的方向。</p>
          </div>
          <div v-else>
            <span><Target :size="30" /></span>
            <h2>没有找到与简历技能直接对应的其他方向</h2>
            <p>已对照 {{ resumeSkills.length }} 项技能：{{ resumeSkills.slice(0, 8).join('、') }}{{ resumeSkills.length > 8 ? ' 等' : '' }}。</p>
          </div>
        </section>
      </template>

      <section class="jm-card jm-resume-skills" aria-label="对照依据">
        <h2>对照依据：投递简历中的 {{ resumeSkills.length }} 项技能</h2>
        <div class="jm-chip-row"><span v-for="skill in resumeSkills" :key="skill" class="jm-chip">{{ skill }}</span></div>
      </section>
      <section class="jm-directions">
        <article v-for="item in directions" :key="item.taxonomyNodeId" class="jm-card jm-direction">
          <header>
            <div><small>{{ item.category }}</small><h2>{{ item.title }}</h2></div>
          </header>
          <div class="jm-direction__skills">
            <h3><CheckCircle2 :size="15" /> 共同技能 {{ item.sharedSkills.length }} 项</h3>
            <div class="jm-chip-row"><span v-for="skill in item.sharedSkills" :key="skill" class="jm-chip">{{ skill }}</span></div>
          </div>
        </article>
      </section>
    </PageState>
  </div></main>
</template>
