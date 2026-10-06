<script setup lang="ts">
import { ref } from 'vue'
import { BriefcaseBusiness, FileText, PanelRightOpen, ShieldCheck, Sparkles } from 'lucide-vue-next'
import AppDrawer from '@/shared/ui/AppDrawer.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import type { JobMatch, MatchCapabilities } from '../types'
import { statusLabel, statusTone } from '../utils/stage'

defineProps<{ match?: JobMatch | null; capabilities?: MatchCapabilities | null }>()
const mobileOpen = ref(false)
</script>

<template>
  <button class="jm-context-trigger" type="button" aria-label="打开本次匹配摘要" @click="mobileOpen=true">
    <PanelRightOpen :size="17" />
    <span>本次匹配</span>
  </button>
  <aside class="jm-context" aria-label="本次匹配摘要">
    <header><h2>本次匹配</h2><AppTag v-if="match" :tone="statusTone(match.status)">{{ statusLabel(match.status) }}</AppTag></header>
    <div class="jm-context__item"><span class="is-blue"><BriefcaseBusiness :size="18" /></span><p><small>目标岗位</small><strong>{{ match?.title || '尚未确认' }}</strong><em>{{ match?.company || '未填写公司' }}</em></p></div>
    <div class="jm-context__item"><span class="is-cyan"><FileText :size="18" /></span><p><small>投递简历</small><strong>{{ match?.resume?.title || '尚未选择' }}</strong><em>{{ match?.resume ? '正式修订已冻结' : '支持站内或本地简历' }}</em></p></div>
    <div class="jm-context__item"><span class="is-green"><ShieldCheck :size="18" /></span><p><small>资料授权</small><strong>{{ match?.authorizationId ? '已确认范围' : '默认不授权' }}</strong><em>敏感字段始终排除</em></p></div>
    <div class="jm-context__item"><span class="is-purple"><Sparkles :size="18" /></span><p><small>AI 分析额度</small><strong>{{ capabilities ? `${capabilities.quota.remainingUnits} 次可用` : '读取中' }}</strong><em>成功分析后消耗 1 次</em></p></div>
    <div class="jm-privacy"><ShieldCheck :size="17" /><p><strong>事实与隐私门禁</strong><span>AI 只读取确认后的 JD、简历与授权证据，不接收照片、文件字节或敏感字段。</span></p></div>
  </aside>
  <AppDrawer :open="mobileOpen" title="本次匹配摘要" :width="420" @close="mobileOpen=false">
    <div class="jm-context-mobile">
      <header><h2>输入与状态</h2><AppTag v-if="match" :tone="statusTone(match.status)">{{ statusLabel(match.status) }}</AppTag></header>
      <div class="jm-context__item"><span class="is-blue"><BriefcaseBusiness :size="18" /></span><p><small>目标岗位</small><strong>{{ match?.title || '尚未确认' }}</strong><em>{{ match?.company || '未填写公司' }}</em></p></div>
      <div class="jm-context__item"><span class="is-cyan"><FileText :size="18" /></span><p><small>投递简历</small><strong>{{ match?.resume?.title || '尚未选择' }}</strong><em>{{ match?.resume ? '正式修订已冻结' : '支持站内或本地简历' }}</em></p></div>
      <div class="jm-context__item"><span class="is-green"><ShieldCheck :size="18" /></span><p><small>资料授权</small><strong>{{ match?.authorizationId ? '已确认范围' : '默认不授权' }}</strong><em>敏感字段始终排除</em></p></div>
      <div class="jm-context__item"><span class="is-purple"><Sparkles :size="18" /></span><p><small>AI 分析额度</small><strong>{{ capabilities ? `${capabilities.quota.remainingUnits} 次可用` : '读取中' }}</strong><em>成功分析后消耗 1 次</em></p></div>
      <div class="jm-privacy"><ShieldCheck :size="17" /><p><strong>事实与隐私门禁</strong><span>AI 只读取确认后的 JD、简历与授权证据，不接收照片、文件字节或敏感字段。</span></p></div>
    </div>
  </AppDrawer>
</template>
