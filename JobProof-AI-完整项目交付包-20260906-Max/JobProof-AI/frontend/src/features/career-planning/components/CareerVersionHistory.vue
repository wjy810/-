<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Check, ChevronDown, CircleAlert, GitCompareArrows, History, LoaderCircle, RotateCcw, Target } from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import { compareCareerCanvasVersions, fetchCareerCanvasVersion, fetchCareerCanvasVersions, restoreCareerCanvasVersion } from '../services/careerPlanningApi'
import type { CanvasVersion, CanvasVersionDiff, CareerCanvas } from '../types'
import { careerCanvasNodeStatusLabel, careerVersionAuthorLabel, careerVersionReasonLabel } from '../utils/presentation'

const props = defineProps<{ sessionId: string; canvas: CareerCanvas }>()
const emit = defineEmits<{ restored: [canvas: CareerCanvas]; error: [message: string]; notice: [message: string] }>()
const versions = ref<CanvasVersion[]>([])
const selected = ref<CanvasVersion | null>(null)
const preview = ref<CareerCanvas | null>(null)
const loading = ref(true)
const pending = ref('')
const page = ref(0)
const totalElements = ref(0)
const hasNext = ref(false)
const diff = ref<CanvasVersionDiff | null>(null)

const isCurrent = computed(() => selected.value?.version === props.canvas.version)
const addedNodes = computed(() => diff.value?.addedNodes ?? 0)
const removedNodes = computed(() => diff.value?.removedNodes ?? 0)
const changedNodes = computed(() => diff.value?.updatedNodes ?? 0)
const movedNodes = computed(() => diff.value?.movedNodes ?? 0)

watch(() => props.canvas.version, load)
onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchCareerCanvasVersions(props.sessionId)
    versions.value = result.items
    page.value = result.page
    totalElements.value = result.totalElements
    hasNext.value = result.hasNext
    const next = versions.value.find(item => item.version === props.canvas.version) ?? versions.value[0] ?? null
    await choose(next)
  } catch (reason) { emit('error', errorMessage(reason, '版本历史读取失败')) }
  finally { loading.value = false }
}

async function choose(value: CanvasVersion | null): Promise<void> {
  selected.value = value
  preview.value = null
  diff.value = null
  if (!value) return
  pending.value = `preview:${value.version}`
  try {
    const [versionPreview, versionDiff] = await Promise.all([
      value.version === props.canvas.version ? Promise.resolve(props.canvas) : fetchCareerCanvasVersion(props.sessionId, value.version),
      value.version === props.canvas.version ? Promise.resolve(null) : compareCareerCanvasVersions(props.sessionId, value.version, props.canvas.version),
    ])
    preview.value = versionPreview
    diff.value = versionDiff
  }
  catch (reason) { emit('error', errorMessage(reason, '历史版本读取失败')) }
  finally { pending.value = '' }
}

async function loadMore(): Promise<void> {
  if (!hasNext.value || pending.value === 'more') return
  pending.value = 'more'
  try {
    const result = await fetchCareerCanvasVersions(props.sessionId, page.value + 1)
    const existing = new Set(versions.value.map(item => item.versionId))
    versions.value.push(...result.items.filter(item => !existing.has(item.versionId)))
    page.value = result.page
    totalElements.value = result.totalElements
    hasNext.value = result.hasNext
  } catch (reason) { emit('error', errorMessage(reason, '更多版本读取失败')) }
  finally { pending.value = '' }
}

async function restore(): Promise<void> {
  if (!selected.value || isCurrent.value) return
  pending.value = 'restore'
  try {
    const value = await restoreCareerCanvasVersion(props.sessionId, selected.value.version, props.canvas.version)
    emit('restored', value)
    emit('notice', `已从 v${selected.value.version} 恢复并创建 v${value.version}，历史版本未被覆盖。`)
    await load()
  } catch (reason) { emit('error', errorMessage(reason, '版本恢复失败')) }
  finally { pending.value = '' }
}

</script>

<template>
  <section class="version-workspace">
    <header><div><span><History :size="24" /></span><div class="version-heading-copy"><h1>规划版本记录</h1><small>所有画布变更都生成不可变版本；恢复操作会继续创建新版本。</small></div></div></header>
    <div v-if="loading" class="version-loading"><LoaderCircle class="spin" :size="20" />正在读取版本历史</div>
    <div v-else class="version-layout">
      <aside class="version-list-panel">
        <header><strong>版本时间线</strong><small>共 {{ totalElements }} 个版本</small></header>
        <ol class="version-timeline">
          <li v-for="item in versions" :key="item.versionId" :class="{ active: selected?.versionId === item.versionId, current: item.version === canvas.version }"><span></span><button type="button" @click="choose(item)"><header><strong>v{{ item.version }}</strong><em v-if="item.version === canvas.version">当前版本</em><small>{{ careerVersionReasonLabel(item.reason) }}</small></header><p>{{ item.changeSummary || careerVersionReasonLabel(item.reason) }}</p><footer><time>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</time><small>{{ careerVersionAuthorLabel(item.createdBy) }}</small></footer></button></li>
        </ol>
        <button v-if="hasNext" class="load-more" type="button" :disabled="pending === 'more'" @click="loadMore"><LoaderCircle v-if="pending === 'more'" class="spin" :size="15" /><ChevronDown v-else :size="15" />加载更多版本</button>
      </aside>
      <Transition name="version-detail" mode="out-in"><main v-if="selected" :key="selected.versionId" class="version-detail">
        <header><div><span><GitCompareArrows :size="22" /></span><p><strong>v{{ selected.version }} 与当前 v{{ canvas.version }}</strong><small>{{ selected.changeSummary || careerVersionReasonLabel(selected.reason) }}</small></p></div><button v-if="!isCurrent" type="button" :disabled="pending === 'restore'" @click="restore"><LoaderCircle v-if="pending === 'restore'" class="spin" :size="16" /><RotateCcw v-else :size="16" />恢复为新版本</button><em v-else><Check :size="14" />正在使用</em></header>
        <section class="version-stats"><div><small>节点总数</small><strong>{{ preview?.nodes.length ?? '—' }}</strong></div><div><small>相对新增</small><strong class="positive">+{{ addedNodes }}</strong></div><div><small>相对移除</small><strong class="negative">-{{ removedNodes }}</strong></div><div><small>内容变化</small><strong>{{ changedNodes }}</strong></div><div><small>位置变化</small><strong>{{ movedNodes }}</strong></div></section>
        <section class="version-preview"><header><h2>版本画布摘要</h2><span v-if="pending.startsWith('preview')"><LoaderCircle class="spin" :size="15" />载入中</span></header><div v-if="preview" class="version-node-groups"><article v-for="node in preview.nodes.filter(item => item.type === 'DOMAIN')" :key="node.logicalNodeId"><span><Target :size="16" /></span><p><strong>{{ node.title }}</strong><small>{{ careerCanvasNodeStatusLabel(node.status) }} · {{ preview.relations.filter(rel => rel.type === 'TREE_PARENT' && rel.toNodeId === node.logicalNodeId).length }} 个子节点</small></p></article><p v-if="!preview.nodes.some(item => item.type === 'DOMAIN')" class="no-domains">该版本只有目标根节点，完整能力树尚未生成。</p></div><div v-else class="version-preview-skeleton" aria-hidden="true"><i /><i /><i /><i /></div></section>
        <p class="restore-boundary"><CircleAlert :size="17" /><span><strong>恢复边界</strong>恢复后会创建新的当前版本，用户锁定节点、备注和旧版本仍完整保留。</span></p>
      </main></Transition>
    </div>
  </section>
</template>

<style scoped>
.version-heading-copy{display:grid}
.version-workspace{min-height:calc(100vh - 144px);padding:18px 20px 96px;color:#172643;background:var(--bg)}.version-workspace>header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.version-workspace>header>div{display:flex;align-items:center;gap:10px}.version-workspace>header>div>span{display:grid;place-items:center;width:44px;height:44px;border-radius:11px;color:var(--primary);background:#eaf2ff}.version-workspace h1,.version-workspace p{margin:0}.version-workspace h1{font-size:26px}.version-workspace header p{display:grid}.version-workspace header small{margin-top:3px;color:#7a899f}.version-workspace>header>button,.version-detail>header>button{display:flex;align-items:center;gap:6px;min-height:40px;padding:0 13px;border:1px solid #dce4ef;border-radius:8px;color:var(--primary);background:#fff}.version-loading{display:flex;align-items:center;justify-content:center;gap:8px;min-height:500px;color:#718098}.version-layout{display:grid;grid-template-columns:340px minmax(0,1fr);gap:14px}.version-timeline{margin:0;padding:16px;border:1px solid #e0e7f1;border-radius:10px;list-style:none;background:#fff}.version-timeline li{position:relative;padding:0 0 13px 24px}.version-timeline li:before{content:'';position:absolute;left:5px;top:12px;bottom:-6px;width:1px;background:#d9e3f0}.version-timeline li:last-child:before{display:none}.version-timeline li>span{position:absolute;left:0;top:11px;width:11px;height:11px;border:2px solid #a9b8cd;border-radius:50%;background:#fff}.version-timeline li.current>span{border-color:#15ad68;background:#15ad68}.version-timeline li.active>span{border-color:var(--primary)}.version-timeline button{width:100%;padding:12px;border:1px solid #e0e7f1;border-radius:8px;text-align:left;color:#273b59;background:#fff}.version-timeline li.active button{border-color:var(--primary);background:#f7faff}.version-timeline header{display:flex;align-items:center;gap:7px}.version-timeline em{padding:3px 6px;border-radius:4px;color:#0b9557;background:#eaf9f1;font-size:12px;font-style:normal}.version-timeline header small{margin-left:auto}.version-timeline p{margin:7px 0;color:#596b84;font-size:13px}.version-timeline footer{display:flex;justify-content:space-between;color:#8995a7;font-size:12px}.version-detail{display:grid;align-content:start;gap:13px}.version-detail>header,.version-detail>section,.restore-boundary{border:1px solid #e0e7f1;border-radius:10px;background:#fff}.version-detail>header{display:flex;align-items:center;justify-content:space-between;padding:15px}.version-detail>header>div{display:flex;align-items:center;gap:9px}.version-detail>header>div>span{display:grid;place-items:center;width:40px;height:40px;border-radius:10px;color:var(--primary);background:#edf4ff}.version-detail>header p{display:grid}.version-detail>header em{display:flex;align-items:center;gap:5px;color:#0b9657;font-size:12px;font-style:normal}.version-stats{display:grid;grid-template-columns:repeat(4,1fr);padding:0!important;overflow:hidden}.version-stats div{display:grid;gap:4px;padding:16px;border-right:1px solid #e8edf4}.version-stats div:last-child{border-right:0}.version-stats small{color:#7f8ca0}.version-stats strong{font-size:22px}.version-stats .positive{color:#10a663}.version-stats .negative{color:#d44a55}.version-preview{padding:16px}.version-preview>header{display:flex;align-items:center;justify-content:space-between}.version-preview h2{margin:0;font-size:18px}.version-preview>header span{display:flex;align-items:center;gap:5px;color:#7b899e;font-size:12px}.version-node-groups{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin-top:12px}.version-node-groups article{display:flex;align-items:center;gap:8px;padding:10px;border:1px solid #e2e8f1;border-radius:7px}.version-node-groups article>span{display:grid;place-items:center;width:32px;height:32px;border-radius:8px;color:#16a66a;background:#eaf9f1}.version-node-groups article p{display:grid}.version-node-groups article small{color:#8793a6}.no-domains{grid-column:1/-1;padding:30px;text-align:center;color:#7b899f}.restore-boundary{display:flex;gap:8px;margin:0;padding:13px;color:#755717;background:#fff8e9}.restore-boundary span{display:grid;font-size:12px;line-height:1.55}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:900px){.version-workspace{padding:12px 10px 100px}.version-workspace>header{align-items:flex-start}.version-workspace>header>button{font-size:0;width:40px;padding:0;justify-content:center}.version-layout{grid-template-columns:1fr}.version-timeline{max-height:330px;overflow:auto}.version-stats{grid-template-columns:1fr 1fr}.version-stats div:nth-child(2){border-right:0}.version-stats div:nth-child(-n+2){border-bottom:1px solid #e8edf4}.version-node-groups{grid-template-columns:1fr}}
.version-list-panel{align-self:start;border:1px solid #e0e7f1;border-radius:10px;background:#fff}.version-list-panel>header{display:flex;align-items:center;justify-content:space-between;padding:14px 16px 0}.version-list-panel>header small{color:#7a899f}.version-list-panel .version-timeline{max-height:none;margin:0;padding:16px;border:0;border-radius:0;overflow:visible;background:transparent}.load-more{display:flex;align-items:center;justify-content:center;gap:6px;width:calc(100% - 32px);min-height:38px;margin:0 16px 16px;border:1px solid #dce5f1;border-radius:7px;color:var(--primary);background:#fff}.version-stats{grid-template-columns:repeat(5,1fr)}
@media(max-width:900px){.version-list-panel{overflow:visible}.version-list-panel .version-timeline{max-height:none;overflow:visible}.version-stats{grid-template-columns:1fr 1fr}.version-stats div{border-bottom:1px solid #e8edf4}.version-stats div:nth-child(2n){border-right:0}.version-stats div:last-child{grid-column:1/-1;border-bottom:0}.version-node-groups{grid-template-columns:1fr}}
.version-workspace>header>div>span,.version-list-panel,.version-timeline,.version-detail>header,.version-detail>section,.restore-boundary{border-radius:8px}.version-detail{min-height:520px}.version-preview{min-height:210px}.version-preview-skeleton{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px}.version-preview-skeleton i{height:54px;border-radius:7px;background:linear-gradient(90deg,#f0f3f8 25%,#f8faff 50%,#f0f3f8 75%);background-size:200% 100%;animation:version-skeleton 1.2s ease-in-out infinite}.version-timeline button{transition:border-color .18s ease,background-color .18s ease,box-shadow .18s ease,transform .18s ease}.version-timeline button:hover{transform:translateX(2px);border-color:#b7c9e3;box-shadow:0 6px 16px rgba(38,65,103,.06)}.version-timeline button:focus-visible,.version-detail button:focus-visible{outline:2px solid rgba(37,99,235,.42);outline-offset:2px}.version-detail-enter-active,.version-detail-leave-active{transition:opacity .18s ease,transform .22s cubic-bezier(.2,.8,.2,1)}.version-detail-enter-from,.version-detail-leave-to{opacity:0;transform:translateY(8px)}@keyframes version-skeleton{to{background-position:-200% 0}}
@media(max-width:900px){.version-detail{min-height:620px}.version-preview-skeleton{grid-template-columns:1fr}}
@media(prefers-reduced-motion:reduce){.version-timeline button,.version-detail-enter-active,.version-detail-leave-active{transition:none}.version-timeline button:hover,.version-detail-enter-from,.version-detail-leave-to{transform:none}.version-preview-skeleton i,.spin{animation:none}}
</style>
