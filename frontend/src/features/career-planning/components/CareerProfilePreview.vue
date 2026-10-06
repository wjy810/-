<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircle2, CircleAlert, Database, Sparkles } from 'lucide-vue-next'
import type { CareerPlanningProfile, EvidencePermission } from '../types'
import { careerProfileSectionLabel } from '../utils/presentation'

const props = defineProps<{ profile: CareerPlanningProfile; permissions: EvidencePermission[] }>()
const basics = computed(() => props.profile.basics)
const facts = computed(() => props.profile.items.filter(item => item.confirmed || item.claimType !== 'INFERENCE'))
const inferences = computed(() => props.profile.items.filter(item => item.claimType === 'INFERENCE'))
const completeness = computed(() => {
  const basicKeys = ['identity', 'education', 'experienceYears', 'weeklyLearningHours']
  const filled = basicKeys.filter(key => String(basics.value[key] ?? '').trim()).length
  return Math.min(100, Math.round((filled / basicKeys.length) * 55 + Math.min(45, props.profile.items.length * 9)))
})
const sourceCount = computed(() => props.permissions.filter(item => item.status === 'ACTIVE').length)
</script>

<template>
  <aside class="cp-preview">
    <header class="cp-preview__head"><h2>职业画像预览</h2><div class="cp-progress"><span>完整度</span><strong>{{ completeness }}%</strong></div></header>
    <section class="cp-preview__block">
      <h3><CheckCircle2 :size="18" /> 已填写信息</h3>
      <dl class="cp-fact-list">
        <div v-if="basics.identity"><dt>当前身份</dt><dd>{{ basics.identity }}</dd><small>用户填写</small></div>
        <div v-if="basics.currentRole"><dt>当前职业</dt><dd>{{ basics.currentRole }}</dd><small>用户填写</small></div>
        <div v-if="basics.education"><dt>最高学历</dt><dd>{{ basics.education }}</dd><small>用户填写</small></div>
        <div v-if="basics.major"><dt>所学专业</dt><dd>{{ basics.major }}</dd><small>用户填写</small></div>
        <div v-if="basics.experienceYears"><dt>工作年限</dt><dd>{{ basics.experienceYears }}</dd><small>用户填写</small></div>
        <div v-for="item in facts" :key="item.id"><dt>{{ careerProfileSectionLabel(item.section) }}</dt><dd>{{ item.title }}</dd><small>{{ item.confirmed ? '已确认' : '待确认' }}</small></div>
      </dl>
      <p v-if="!facts.length && !Object.keys(basics).length" class="cp-preview__empty">填写后，这里会实时形成结构化画像。</p>
    </section>
    <section v-if="inferences.length" class="cp-preview__block cp-preview__block--inference">
      <h3><Sparkles :size="18" /> AI 推断</h3>
      <p v-for="item in inferences" :key="item.id">{{ item.title }}</p>
      <small>推断不会作为正式事实，需由你确认。</small>
    </section>
    <section class="cp-preview__block cp-preview__block--sources">
      <h3><Database :size="18" /> 资料来源</h3>
      <p>{{ sourceCount ? `本次已授权 ${sourceCount} 项结构化资料` : '当前未授权求职资料库记录' }}</p>
    </section>
    <footer><CircleAlert :size="16" /> 未经确认的信息不会进入职业推荐，原始文件不会发送给模型。</footer>
  </aside>
</template>

