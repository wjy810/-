<script setup lang="ts">
/**
 * Terms / privacy. The text comes from the server: the operator's official document when
 * configured, otherwise the built-in reference text, which is labelled as such.
 */
import { onMounted, ref, watch } from 'vue'
import { ArrowLeft, FileText, Info, ShieldCheck } from 'lucide-vue-next'
import BrandMark from '@/shared/ui/BrandMark.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import OperatorInfo from '@/features/site/OperatorInfo.vue'
import { fetchPolicyDocument, type PolicyDocument } from '@/shared/api/capabilities'

const props = defineProps<{ kind: 'terms' | 'privacy' }>()
const document = ref<PolicyDocument | null>(null)
const loading = ref(true)
const error = ref<unknown>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try { document.value = await fetchPolicyDocument(props.kind) }
  catch (reason) { error.value = reason }
  finally { loading.value = false }
}

watch(() => props.kind, () => void load())
onMounted(load)
</script>

<template>
  <div class="policy-page">
    <header class="policy-topbar">
      <RouterLink class="policy-brand" to="/" aria-label="JobProof AI 首页"><BrandMark :size="28" /></RouterLink>
      <RouterLink class="policy-back" to="/"><ArrowLeft :size="16" />返回首页</RouterLink>
    </header>
    <main>
      <article>
        <div v-if="loading" class="policy-skeleton" aria-busy="true"><UiSkeleton height="32px" width="50%" /><UiSkeleton :lines="6" /></div>
        <UiErrorState v-else-if="error || !document" :error="error" title="文档读取失败" @retry="load" />
        <template v-else>
          <header>
            <span><FileText v-if="kind === 'terms'" :size="17" /><ShieldCheck v-else :size="17" />{{ kind === 'terms' ? '服务规则' : '信息保护' }}</span>
            <h1>{{ document.title }}</h1>
            <p>版本 {{ document.version }}</p>
          </header>

          <aside v-if="!document.published"><Info :size="18" />这是平台提供的参考文本，运营方尚未发布正式版本。正式版本发布后会替换本页内容，注册时以当时显示的版本为准。</aside>

          <section v-for="(section, index) in document.sections" :key="index">
            <h2 v-if="section.heading">{{ section.heading }}</h2>
            <template v-for="(block, blockIndex) in section.blocks" :key="blockIndex">
              <p v-if="block.type === 'paragraph'">{{ block.text }}</p>
              <ul v-else><li>{{ block.text }}</li></ul>
            </template>
          </section>
          <footer><OperatorInfo /></footer>
        </template>
      </article>
    </main>
  </div>
</template>

<style scoped>
.policy-page { min-height: 100vh; color: var(--text-primary); }
.policy-topbar { position: sticky; top: 0; z-index: var(--z-topbar); height: 64px; display: flex; align-items: center; justify-content: space-between; padding: 0 max(22px, calc((100vw - 900px) / 2)); border-bottom: 1px solid var(--border-subtle); background: color-mix(in srgb, var(--bg-app) 84%, transparent); backdrop-filter: blur(14px); }
.policy-brand, .policy-back { display: inline-flex; align-items: center; gap: 8px; color: var(--text-primary); }
.policy-back { color: var(--text-secondary); font-size: var(--fs-sm); }
.policy-back:hover { color: var(--text-primary); }
.policy-page main { width: min(900px, calc(100% - 32px)); margin: 0 auto; padding: var(--space-12) 0 var(--space-16); animation: jp-rise-in var(--dur-slow) var(--ease-out) both; }
.policy-page article { padding: var(--space-10) var(--space-12); border: 1px solid var(--border-subtle); border-radius: var(--radius-xl); background: var(--surface-1); box-shadow: var(--shadow-sm); }
.policy-skeleton { display: grid; gap: var(--space-4); }
.policy-page article > header span { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px; color: var(--color-primary-text); background: var(--color-primary-soft); font-size: var(--fs-xs); font-weight: 650; }
.policy-page h1 { margin: var(--space-3) 0 6px; font-size: 30px; line-height: 1.3; letter-spacing: -0.02em; }
.policy-page article > header p { color: var(--text-tertiary); font-size: var(--fs-xs); }
.policy-page aside { display: flex; align-items: flex-start; gap: 8px; margin: var(--space-6) 0 var(--space-8); padding: 14px 16px; border-radius: var(--radius-md); color: var(--color-info-text); background: var(--color-info-soft); font-size: var(--fs-sm); line-height: 1.7; }
.policy-page aside svg { flex: 0 0 auto; margin-top: 3px; }
.policy-page section { padding: var(--space-5) 0; border-top: 1px solid var(--border-subtle); }
.policy-page section h2 { margin: 0 0 8px; font-size: 17px; }
.policy-page section p, .policy-page section li { color: var(--text-secondary); font-size: var(--fs-body); line-height: 1.85; }
.policy-page section p + p { margin-top: var(--space-2); }
.policy-page section ul { margin: 4px 0; padding-left: 20px; }
.policy-page article > footer { padding-top: var(--space-5); border-top: 1px solid var(--border-subtle); }
@media (max-width: 640px) {
  .policy-topbar { height: 58px; padding: 0 16px; }
  .policy-page main { width: 100%; padding: 0; }
  .policy-page article { padding: 28px 20px 48px; border-width: 0; border-radius: 0; box-shadow: none; }
  .policy-page h1 { font-size: 24px; }
}
</style>
