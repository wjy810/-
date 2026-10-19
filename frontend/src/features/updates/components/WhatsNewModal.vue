<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, ShieldCheck } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UpdateContent from './UpdateContent.vue'
import { acknowledgeUpdate, remindUpdateLater } from '../services/updatesApi'
import type { ReleaseDetail } from '../types'
import '../updates.css'

const props = withDefaults(defineProps<{ detail?: ReleaseDetail | null }>(), {
  detail: null,
})
const emit = defineEmits<{ close: [] }>()
type Action = 'remind' | 'details' | 'start'
const router = useRouter(); const pending = ref<Action | ''>('')
const highlights = computed(() => props.detail?.sections.filter(section => section.sectionType === 'HIGHLIGHTS').slice(0, 1) ?? [])

async function remind(): Promise<void> { if (!props.detail) return; pending.value = 'remind'; try { await remindUpdateLater(props.detail.release.id); emit('close') } finally { pending.value = '' } }
async function acknowledge(openWorkspace = false): Promise<void> { if (!props.detail) return; pending.value = openWorkspace ? 'start' : 'details'; try { await acknowledgeUpdate(props.detail.release.id); emit('close'); if (openWorkspace) await router.push(props.detail.release.ctaPath || '/resumes') } finally { pending.value = '' } }
/** The clicked button spins; the others wait until it is done. */
function busy(action: Action): { pending: boolean; disabled: boolean } { return { pending: pending.value === action, disabled: !!pending.value && pending.value !== action } }
async function details(): Promise<void> { if (!props.detail) return; await acknowledge(false); await router.push(`/updates/${props.detail.release.slug}`) }
</script>

<template>
  <UiDialog :open="!!detail" title="版本更新" :width="780" @close="remind">
    <div v-if="detail" class="whats-new__head"><strong>NEW · {{ detail.release.versionLabel }}</strong><h2>发现新版本：{{ detail.release.title }}</h2><p>{{ detail.release.summary }}</p></div>
    <UpdateContent v-if="highlights.length" :sections="highlights" compact />
    <p class="whats-new__safety"><ShieldCheck :size="16" /> 本次更新不会改写你的简历、资料或历史记录</p>
    <template #footer>
      <UiButton variant="secondary" v-bind="busy('remind')" @click="remind">稍后查看</UiButton>
      <UiButton variant="secondary" v-bind="busy('details')" @click="details">查看完整更新</UiButton>
      <UiButton :icon-right="ArrowRight" v-bind="busy('start')" @click="acknowledge(true)">知道了，开始使用</UiButton>
    </template>
  </UiDialog>
</template>
