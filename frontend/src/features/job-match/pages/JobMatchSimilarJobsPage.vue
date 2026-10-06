<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CheckCircle2, LoaderCircle, Target, TriangleAlert } from 'lucide-vue-next'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { fetchCareerDirections, fetchJobMatch } from '../services/jobMatchApi'
import type { CareerDirection, JobMatch } from '../types'
import '../job-match.css'

const route=useRoute();const router=useRouter();const match=ref<JobMatch|null>(null);const directions=ref<CareerDirection[]>([]);const loading=ref(true);const pageError=ref('')
useToastFeedback(pageError,'error','job-match-similar-jobs-error')
onMounted(async()=>{try{[match.value,directions.value]=await Promise.all([fetchJobMatch(String(route.params.id)),fetchCareerDirections(String(route.params.id))])}catch(reason){pageError.value=errorMessage(reason,'职业方向建议读取失败')}finally{loading.value=false}})
</script>

<template><main class="jm-page"><div class="jm-shell"><header class="jm-head"><div><button class="jm-text-btn" type="button" @click="router.back()"><ArrowLeft :size="16" />返回报告</button><h1>相似职业方向</h1><p>仅基于现有岗位分类树、当前 JD 和已确认能力生成方向建议，不代表实时招聘职位。</p></div></header><section v-if="loading" class="jm-card jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在计算相关职业方向</section><section v-else class="jm-directions"><article v-for="item in directions" :key="item.taxonomyNodeId" class="jm-card jm-direction"><header><div><small>{{ item.level==='ROLE'?'岗位方向':'岗位类别' }}</small><h2>{{ item.title }}</h2></div><span class="jm-score-ring">{{ item.matchScore }}</span></header><p>{{ item.reason }}</p><h3><CheckCircle2 :size="15" /> 已有信号</h3><div class="jm-chip-row"><span v-for="signal in item.matchedSignals" :key="signal" class="jm-chip">{{ signal }}</span></div><h3 style="margin-top:13px"><TriangleAlert :size="15" /> 需要确认</h3><p v-for="gap in item.gaps" :key="gap">{{ gap }}</p><AppTag tone="gray"><Target :size="13" /> 方向参考</AppTag></article><div v-if="!directions.length" class="jm-card jm-empty" style="grid-column:1/-1"><div><span><Target :size="30" /></span><h2>暂无足够的方向信号</h2><p>补充岗位要求或简历技能后再查看。</p></div></div></section></div></main></template>
