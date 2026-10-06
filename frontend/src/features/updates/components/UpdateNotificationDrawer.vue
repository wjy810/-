<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, Bell, CheckCheck, FileClock, Sparkles } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import AppDrawer from '@/shared/ui/AppDrawer.vue'
import PageState from '@/shared/ui/PageState.vue'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import { listNotifications, markNotificationRead, markNotificationsReadBatch } from '@/features/notification/services/notificationApi'
import type { NotificationView } from '@/features/notification/types'

const LIMIT = 30
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const router = useRouter()
const pending = ref(false); const actionError = ref(''); const filter = ref<'all' | 'unread'>('all')
const { data, error, loading, loaded, load } = useLoadState(() => listNotifications(0, LIMIT))
const items = computed<NotificationView[]>(() => data.value?.items ?? [])
const truncated = computed(() => (data.value?.total ?? 0) > items.value.length)
const visible = computed(() => filter.value === 'unread' ? items.value.filter(item => item.status === 'DELIVERED') : items.value)
const product = computed(() => visible.value.filter(item => item.type === 'PRODUCT_UPDATE'))
const tasks = computed(() => visible.value.filter(item => item.type !== 'PRODUCT_UPDATE'))
const readableIds = computed(() => items.value.filter(item => item.status === 'DELIVERED').map(item => item.id))

async function markAll(): Promise<void> {
  if (!readableIds.value.length) return
  pending.value = true; actionError.value = ''
  try { await markNotificationsReadBatch(readableIds.value); await load(); emit('changed') }
  catch (cause) { actionError.value = errorMessage(cause, '标记已读失败') }
  finally { pending.value = false }
}
async function openItem(item: NotificationView): Promise<void> { if (item.status === 'DELIVERED') { try { await markNotificationRead(item.id); emit('changed') } catch { /* Navigation remains available. */ } } emit('close'); if (item.actionPath) await router.push(item.actionPath); else await router.push('/notifications') }
watch(() => props.open, open => { if (open) void load() }, { immediate: true })
</script>

<template>
  <AppDrawer :open="open" title="更新与通知" :width="500" @close="emit('close')">
    <div class="notice-drawer__tabs"><div><button :class="{ active: filter==='all' }" @click="filter='all'">全部</button><button :class="{ active: filter==='unread' }" @click="filter='unread'">未读</button></div><button type="button" :disabled="pending||!readableIds.length" @click="markAll"><CheckCheck :size="15" />全部标为已读</button></div>
    <p v-if="actionError" class="banner banner--bad">{{ actionError }}</p>
    <PageState :loading="loading" :error="error" :loaded="loaded" :empty="!visible.length" error-title="通知读取失败" compact @retry="load">
      <template #skeleton><div class="empty-state">正在读取通知…</div></template>
      <template #empty><div class="empty-state"><Bell :size="30" /><p>{{ filter==='unread' ? '没有未读通知' : '还没有通知' }}</p></div></template>
      <section v-if="product.length" class="notice-group"><h4>产品更新</h4><article v-for="item in product" :key="item.id" class="notice-item" :class="{ unread:item.status==='DELIVERED' }" @click="openItem(item)"><span class="notice-item__icon"><Sparkles :size="18" /></span><div><strong>{{ item.title }}</strong><p>{{ item.body }}</p><small>{{ formatWhen(item.createdAt) }}</small><span>查看详情 <ArrowRight :size="14" /></span></div><i v-if="item.status==='DELIVERED'" /></article></section>
      <section v-if="tasks.length" class="notice-group"><h4>任务与安全通知</h4><article v-for="item in tasks" :key="item.id" class="notice-item" :class="{ unread:item.status==='DELIVERED' }" @click="openItem(item)"><span class="notice-item__icon task"><FileClock :size="18" /></span><div><strong>{{ item.title }}</strong><p>{{ item.body }}</p><small>{{ formatWhen(item.createdAt) }}</small></div><i v-if="item.status==='DELIVERED'" /></article></section>
      <p v-if="truncated" class="notice-drawer__more">这里只显示最近 {{ LIMIT }} 条，更早的通知请到完整通知中心查看。</p>
    </PageState>
    <template #footer><RouterLink class="btn btn--ghost" to="/notifications" @click="emit('close')">完整通知中心</RouterLink><RouterLink class="btn btn--primary" to="/updates" @click="emit('close')">查看更新日志 <ArrowRight :size="15" /></RouterLink></template>
  </AppDrawer>
</template>

<style scoped>
.notice-drawer__tabs{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:-8px -4px 18px;padding-bottom:12px;border-bottom:1px solid var(--border)}.notice-drawer__tabs>div{display:flex;gap:4px}.notice-drawer__tabs button{min-height:36px;display:inline-flex;align-items:center;gap:5px;padding:0 10px;border:0;border-radius:8px;background:transparent;color:var(--text-secondary)}.notice-drawer__tabs button.active{background:var(--color-primary-soft);color:var(--color-primary);font-weight:700}.notice-group{display:grid;gap:9px;margin-bottom:22px}.notice-group h4{font-size:12px;color:var(--text-tertiary)}.notice-item{position:relative;display:flex;gap:11px;padding:14px;border:1px solid var(--border);border-radius:10px;background:var(--surface-1);cursor:pointer;transition:transform .2s ease,border-color .2s ease,box-shadow .2s ease}.notice-item:hover{transform:translateY(-1px);border-color:var(--color-primary-border);box-shadow:0 8px 20px rgba(32,61,106,.08)}.notice-item.unread{background:var(--surface-1)}.notice-item__icon{width:34px;height:34px;display:grid;place-items:center;flex:none;border-radius:9px;background:var(--surface-2);color:var(--color-primary)}.notice-item__icon.task{background:var(--surface-2);color:var(--color-success-text)}.notice-item>div{min-width:0}.notice-item strong{font-size:13px}.notice-item p{margin:5px 0;color:var(--text-secondary);font-size:12px;line-height:1.6}.notice-item small{color:var(--text-tertiary)}.notice-item div>span{display:flex;align-items:center;gap:4px;margin-top:7px;color:var(--color-primary);font-size:12px}.notice-item>i{position:absolute;right:12px;top:14px;width:7px;height:7px;border-radius:50%;background:var(--color-primary)}.notice-drawer__more{margin-top:-8px;color:var(--text-tertiary);font-size:12px;text-align:center}.empty-state{min-height:180px;display:grid;place-items:center;align-content:center;gap:8px;color:var(--text-tertiary)}@media(prefers-reduced-motion:reduce){.notice-item{transition:none}}
</style>
