<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { ArrowLeft, CalendarDays, ExternalLink } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { isNotFound } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import PageState from '@/shared/ui/PageState.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UpdateContent from '../components/UpdateContent.vue'
import { moduleLabel, sectionLabel, statusLabel, typeLabel } from '../labels'
import { fetchUpdate, markUpdateRead } from '../services/updatesApi'
import { applyUpdateMetadata, buildUpdateMetadata } from '../metadata'
import '../updates.css'

const session = useSessionStore()
const route = useRoute()
const version = computed(() => String(route.params.version ?? ''))
const { data, error, loading, load } = useLoadState(() => fetchUpdate(version.value))
/** The loaded release, only while it is the one in the URL (the page instance is reused across versions). */
const detail = computed(() => {
  const key = version.value.toLowerCase()
  const value = data.value
  return value && (value.release.slug.toLowerCase() === key || value.release.versionLabel.toLowerCase() === key) ? value : null
})
const notFound = computed(() => !detail.value && isNotFound(error.value))
let restoreMetadata: () => void = () => undefined
const release = computed(() => detail.value?.release)

function dateOnly(value?: string | null): string {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long' }).format(new Date(value)) : '尚未发布'
}

watch(detail, (value) => {
  if (!value) return
  restoreMetadata()
  restoreMetadata = applyUpdateMetadata(buildUpdateMetadata(value.release))
  if (session.signedIn) void markUpdateRead(value.release.id).catch(() => undefined)
})
// Previous/next links reuse this page instance, so a param change must reload.
watch(version, () => { void load() })
onMounted(load)
onUnmounted(() => restoreMetadata())
</script>

<template>
  <main class="updates-page">
    <div class="updates-wrap">
      <UiEmptyState v-if="notFound" title="没有找到这个版本" description="它可能已下线，或链接有误。" illustration="empty-search">
        <UiButton variant="secondary" :icon="ArrowLeft" to="/updates">返回更新日志</UiButton>
      </UiEmptyState>
      <PageState v-else :loading="loading" :error="error" :loaded="Boolean(detail)" error-title="版本详情读取失败" @retry="load">
        <div v-if="detail && release" class="update-detail">
          <article>
            <header class="update-detail__head">
              <RouterLink class="update-breadcrumb" to="/updates"><ArrowLeft :size="17" /> 返回更新日志</RouterLink>
              <div class="update-title-line"><span class="update-pill is-status">{{ statusLabel(release.status) }}</span><h1>{{ release.versionLabel }} {{ release.title }}</h1></div>
              <div class="update-detail__meta"><span><CalendarDays :size="15" /> {{ dateOnly(release.publishedAt) }}</span><span>{{ typeLabel(release.releaseType) }}</span><span>{{ release.modules.map(moduleLabel).join(' · ') }}</span></div>
              <p class="update-detail__summary">{{ release.summary }}</p>
              <div class="update-detail__actions"><a v-if="release.ctaPath" class="btn btn--primary" :href="release.ctaPath">{{ release.ctaLabel || '立即体验' }} <ExternalLink :size="15" /></a></div>
            </header>
            <UpdateContent :sections="detail.sections" />
          </article>
          <aside class="update-detail__toc">
            <nav class="toc-card" aria-label="版本目录"><h3>目录</h3><a v-for="(section,index) in detail.sections" :key="section.id" :href="`#section-${section.id || index}`">{{ section.title || sectionLabel(section.sectionType) }}</a></nav>
            <div class="version-nav"><RouterLink v-if="detail.nextVersion" :to="detail.nextVersion.path">下一版本<br /><strong>{{ detail.nextVersion.versionLabel }}</strong></RouterLink><RouterLink v-if="detail.previousVersion" :to="detail.previousVersion.path">上一版本<br /><strong>{{ detail.previousVersion.versionLabel }}</strong></RouterLink></div>
          </aside>
        </div>
      </PageState>
    </div>
  </main>
</template>

<style scoped>
@media (max-width: 700px) {
  .update-breadcrumb,
  .update-detail__actions .btn,
  .toc-card a {
    min-height: 44px;
  }
  .update-breadcrumb,
  .toc-card a {
    display: flex;
    align-items: center;
  }
}
</style>
