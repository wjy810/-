<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ArrowRight, Filter, Search, SlidersHorizontal } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import AppSelect from '@/shared/ui/AppSelect.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { MODULE_LABELS, TYPE_LABELS, moduleLabel, typeLabel } from '../labels'
import { fetchLatestUpdate, fetchUpdateFacets, listUpdates } from '../services/updatesApi'
import { applyUpdateMetadata, buildUpdateMetadata } from '../metadata'
import { parseUpdateFilters, publishedFromForRange, serializeUpdateFilters } from '../filters'
import type { ReleaseDetail, ReleaseSummary, UpdateFacets } from '../types'
import '../updates.css'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const error = ref('')
useToastFeedback(error, 'error', 'update-list-error')
const items = ref<ReleaseSummary[]>([])
const total = ref(0)
const latest = ref<ReleaseDetail | null>(null)
const facets = ref<UpdateFacets>({ types: {}, modules: {}, versions: [] })
const filterOpen = ref(false)
const initialFilters = parseUpdateFilters(route.query)
const q = ref(initialFilters.q)
const type = ref(initialFilters.type)
const module = ref(initialFilters.module)
const versionFrom = ref(initialFilters.versionFrom)
const versionTo = ref(initialFilters.versionTo)
const publishedRange = ref(initialFilters.range)
const page = ref(initialFilters.page)
const size = 12
const resultRevision = ref(0)
let restoreMetadata: () => void = () => undefined

const typeOptions = computed(() => [
  { value: '', label: '全部类型' },
  ...Object.keys(TYPE_LABELS).map((value) => ({ value, label: typeLabel(value), count: facets.value.types[value] || 0 })),
])
const moduleOptions = computed(() => [
  { value: '', label: '全部模块' },
  ...Object.keys(MODULE_LABELS).map((value) => ({ value, label: moduleLabel(value), count: facets.value.modules[value] || 0 })),
])
const versionOptions = computed(() => [{ value: '', label: '不限版本' }, ...facets.value.versions.map((value) => ({ value, label: value }))])
const rangeOptions = [
  { value: '', label: '全部时间' }, { value: '7', label: '最近 7 天' }, { value: '30', label: '最近 30 天' }, { value: '90', label: '最近 90 天' },
]
const activeTypeIndex = computed(() => Math.max(0, typeOptions.value.findIndex(option => option.value === type.value)))

function dateOnly(value?: string | null): string {
  return value ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value)).replaceAll('/', '-') : '尚未发布'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  const publishedFrom = publishedFromForRange(publishedRange.value)
  try {
    const result = await listUpdates({ q: q.value, type: type.value, module: module.value, versionFrom: versionFrom.value,
      versionTo: versionTo.value, publishedFrom, page: page.value, size })
    items.value = result.items
    total.value = result.total
  } catch (reason) {
    error.value = errorMessage(reason, '更新日志读取失败')
  } finally {
    resultRevision.value += 1
    loading.value = false
  }
}

async function apply(): Promise<void> {
  page.value = 0
  await router.replace({ query: serializeUpdateFilters({ q: q.value, type: type.value, module: module.value,
    versionFrom: versionFrom.value, versionTo: versionTo.value, range: publishedRange.value, page: page.value }) })
  await load()
}

async function chooseType(value: string): Promise<void> { type.value = value; await apply() }
async function turn(delta: number): Promise<void> {
  page.value += delta
  await router.replace({ query: serializeUpdateFilters({ q: q.value, type: type.value, module: module.value,
    versionFrom: versionFrom.value, versionTo: versionTo.value, range: publishedRange.value, page: page.value }) })
  await load()
}

onMounted(async () => {
  restoreMetadata = applyUpdateMetadata(buildUpdateMetadata())
  try {
    const [facetResult, latestResult] = await Promise.all([fetchUpdateFacets(), fetchLatestUpdate()])
    facets.value = facetResult
    latest.value = latestResult
  } catch { /* The timeline remains independently usable. */ }
  await load()
})
onUnmounted(() => restoreMetadata())
</script>

<template>
      <main class="updates-page">
      <div class="updates-wrap">
        <header class="updates-hero">
          <div><h1>系统更新日志</h1><p>记录每一次进步，让求职准备更清晰、更可信。</p></div>
        </header>
        <div class="updates-toolbar">
          <form class="updates-search" @submit.prevent="apply"><Search :size="18" /><input v-model.trim="q" placeholder="搜索版本或更新内容" /><button class="sr-only" type="submit">搜索</button></form>
          <div class="updates-chips" :style="{ '--chip-count': typeOptions.length, '--chip-index': activeTypeIndex }" aria-label="更新类型">
            <span class="updates-chip-indicator" aria-hidden="true" />
            <button v-for="option in typeOptions" :key="option.value" class="updates-chip" :class="{ 'is-active': type === option.value }" :aria-current="type === option.value ? 'page' : undefined" type="button" @click="chooseType(String(option.value))">{{ option.label }}</button>
          </div>
          <button class="updates-filter-button" type="button" :aria-expanded="filterOpen" @click="filterOpen = !filterOpen"><Filter :size="17" />筛选</button>
        </div>
        <Transition name="updates-filter">
        <section v-if="filterOpen" class="updates-filter-panel">
          <label>更新类型<AppSelect v-model="type" :options="typeOptions" aria-label="更新类型" /></label>
          <label>产品模块<AppSelect v-model="module" :options="moduleOptions" aria-label="产品模块" searchable /></label>
          <label>版本范围<div style="display:flex;gap:6px"><AppSelect v-model="versionFrom" :options="versionOptions" aria-label="起始版本" /><AppSelect v-model="versionTo" :options="versionOptions" aria-label="结束版本" /></div></label>
          <label>发布时间<AppSelect v-model="publishedRange" :options="rangeOptions" aria-label="发布时间" /></label>
          <div style="grid-column:1/-1;display:flex;justify-content:flex-end;gap:8px"><button class="btn btn--ghost" type="button" @click="q='';type='';module='';versionFrom='';versionTo='';publishedRange='';apply()">清空条件</button><button class="btn btn--primary" type="button" @click="filterOpen=false;apply()">应用筛选</button></div>
        </section>
        </Transition>
        <div class="updates-grid">
          <section>
            <Transition name="updates-results" mode="out-in">
            <div v-if="loading" key="loading" class="updates-timeline" aria-busy="true"><article v-for="n in 4" :key="n" class="update-card"><div class="bone" /><div><div class="bone" /><div class="bone bone--short" /></div></article></div>
            <div v-else-if="!items.length" :key="`empty-${resultRevision}`" class="updates-empty"><SlidersHorizontal :size="34" /><h2>暂时没有符合条件的版本</h2><p>可以调整筛选条件；尚未发布真实版本时，这里保持为空。</p></div>
            <div v-else :key="`items-${resultRevision}`" class="updates-timeline">
              <article v-for="item in items" :key="item.id" class="update-card">
                <div class="update-card__version"><strong>{{ item.versionLabel }}</strong><time>{{ dateOnly(item.publishedAt) }}</time><span class="update-pill is-status">{{ item.status === 'ARCHIVED' ? '已归档' : '正式发布' }}</span></div>
                <div class="update-card__main"><h2>{{ item.title }}</h2><p>{{ item.summary }}</p><div class="update-card__modules"><span class="update-pill">{{ typeLabel(item.releaseType) }}</span><span v-for="tag in item.modules" :key="tag" class="update-pill">{{ moduleLabel(tag) }}</span></div></div>
                <RouterLink class="update-card__link" :to="`/updates/${item.slug}`">查看详情 <ArrowRight :size="17" /></RouterLink>
              </article>
            </div>
            </Transition>
            <div v-if="total > size" class="updates-pager"><button class="btn btn--ghost" :disabled="page<=0||loading" @click="turn(-1)">上一页</button><span>第 {{ page + 1 }} 页</span><button class="btn btn--ghost" :disabled="(page+1)*size>=total||loading" @click="turn(1)">下一页</button></div>
          </section>
          <aside class="updates-aside">
            <section v-if="latest" class="latest-card"><small>最新版本</small><h2>{{ latest.release.versionLabel }}</h2><span class="update-pill is-status">正式发布</span><h3>{{ latest.release.title }}</h3><p>{{ latest.release.summary }}</p><RouterLink class="btn btn--primary" :to="`/updates/${latest.release.slug}`">查看完整更新 <ArrowRight :size="16" /></RouterLink></section>
            <section class="updates-note"><h3>持续透明地改进</h3><p>这里仅展示已经正式发布的真实变更。草稿、计划和未验证内容不会提前对外出现。</p></section>
          </aside>
        </div>
      </div>
    </main>
</template>

<style scoped>
.updates-chips {
  position: relative;
  display: grid;
  grid-template-columns: repeat(var(--chip-count), 104px);
  isolation: isolate;
}
.updates-chip-indicator {
  position: absolute;
  z-index: 0;
  top: 2px;
  bottom: 2px;
  left: 2px;
  width: 104px;
  border-radius: 999px;
  background: var(--updates-blue);
  box-shadow: 0 6px 14px color-mix(in srgb, var(--color-primary) 18%, transparent);
  transform: translateX(calc(var(--chip-index) * 112px));
  transition: transform var(--motion-slow) var(--motion-ease), box-shadow var(--motion-base) var(--motion-ease);
}
.updates-chip {
  position: relative;
  z-index: 1;
  width: 104px;
  padding-inline: 10px;
}
.updates-chip.is-active {
  border-color: transparent;
  background: transparent;
}
.updates-filter-panel { animation: none; }
.updates-filter-enter-active,
.updates-filter-leave-active,
.updates-results-enter-active,
.updates-results-leave-active {
  transition: opacity var(--motion-base) ease, transform var(--motion-base) var(--motion-ease);
}
.updates-filter-enter-from { opacity: 0; transform: translateY(-7px); }
.updates-filter-leave-to { opacity: 0; transform: translateY(-5px); }
.updates-results-enter-from { opacity: 0; transform: translateY(7px); }
.updates-results-leave-to { opacity: 0; transform: translateY(-3px); }

@media (max-width: 700px) {
  .updates-chip,
  .updates-filter-button,
  .updates-filter-panel .btn,
  .updates-filter-panel:deep(.app-select__trigger) {
    min-height: 44px;
  }
  .update-card__link,
  .latest-card .btn {
    min-height: 44px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .updates-chip-indicator,
  .updates-filter-enter-active,
  .updates-filter-leave-active,
  .updates-results-enter-active,
  .updates-results-leave-active { transition: none; }
}
</style>
