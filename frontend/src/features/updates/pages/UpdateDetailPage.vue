<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ArrowLeft, CalendarDays, ExternalLink } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { errorMessage } from '@/shared/api/types'
import UpdateContent from '../components/UpdateContent.vue'
import { moduleLabel, sectionLabel, statusLabel, typeLabel } from '../labels'
import { fetchUpdate, markUpdateRead } from '../services/updatesApi'
import { applyUpdateMetadata, buildUpdateMetadata } from '../metadata'
import type { ReleaseDetail } from '../types'
import '../updates.css'

const session = useSessionStore()
const route = useRoute()
const loading = ref(true)
const error = ref('')
const detail = ref<ReleaseDetail | null>(null)
let restoreMetadata: () => void = () => undefined
const release = computed(() => detail.value?.release)

function dateOnly(value?: string | null): string {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long' }).format(new Date(value)) : '尚未发布'
}

onMounted(async () => {
  try {
    detail.value = await fetchUpdate(String(route.params.version))
    restoreMetadata()
    restoreMetadata = applyUpdateMetadata(buildUpdateMetadata(detail.value.release))
    if (session.signedIn) void markUpdateRead(detail.value.release.id)
  } catch (reason) {
    error.value = errorMessage(reason, '版本详情读取失败')
  } finally { loading.value = false }
})
onUnmounted(() => restoreMetadata())
</script>

<template>
      <main class="updates-page">
      <div class="updates-wrap">
        <div v-if="loading" class="updates-empty" aria-busy="true">正在读取版本详情…</div>
        <p v-else-if="error" class="banner banner--bad" role="alert">{{ error }}</p>
        <div v-else-if="detail && release" class="update-detail">
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
