<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, ShieldCheck } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import AppModal from '@/shared/ui/AppModal.vue'
import UpdateContent from './UpdateContent.vue'
import { acknowledgeUpdate, remindUpdateLater } from '../services/updatesApi'
import type { ReleaseDetail } from '../types'
import '../updates.css'

const props = withDefaults(defineProps<{ detail?: ReleaseDetail | null }>(), {
  detail: null,
})
const emit = defineEmits<{ close: [] }>()
const router = useRouter(); const pending = ref(false)
const highlights = computed(() => props.detail?.sections.filter(section => section.sectionType === 'HIGHLIGHTS').slice(0, 1) ?? [])

async function remind(): Promise<void> { if (!props.detail) return; pending.value = true; try { await remindUpdateLater(props.detail.release.id); emit('close') } finally { pending.value = false } }
async function acknowledge(openWorkspace = false): Promise<void> { if (!props.detail) return; pending.value = true; try { await acknowledgeUpdate(props.detail.release.id); emit('close'); if (openWorkspace) await router.push(props.detail.release.ctaPath || '/resumes') } finally { pending.value = false } }
async function details(): Promise<void> { if (!props.detail) return; await acknowledge(false); await router.push(`/updates/${props.detail.release.slug}`) }
</script>

<template>
  <AppModal :open="!!detail" title="版本更新" :width="780" @close="remind">
    <div v-if="detail" class="whats-new__head"><strong>NEW · {{ detail.release.versionLabel }}</strong><h2>发现新版本：{{ detail.release.title }}</h2><p>{{ detail.release.summary }}</p></div>
    <UpdateContent v-if="highlights.length" :sections="highlights" compact />
    <p class="whats-new__safety"><ShieldCheck :size="16" /> 本次更新不会改写你的简历、资料或历史记录</p>
    <template #footer><button class="btn btn--ghost" :disabled="pending" @click="remind">稍后查看</button><button class="btn btn--ghost" :disabled="pending" @click="details">查看完整更新</button><button class="btn btn--primary" :disabled="pending" @click="acknowledge(true)">知道了，开始使用 <ArrowRight :size="15" /></button></template>
  </AppModal>
</template>
