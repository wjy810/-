<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiCard from '@/shared/ui/UiCard.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { confirm } from '@/shared/ui/confirm'
import * as channelApi from '../services/aiApi'
import AiChannelFields from './AiChannelFields.vue'
import type { AiChannelPreference, AiSystemCapability, AiSystemChannel, AiSystemChannelInput } from '../types'

const props = withDefaults(defineProps<{ personal?: boolean }>(), { personal: false })
const preference = ref<AiChannelPreference | null>(null)
const { data: channels, error: loadError, loading, loaded, load } = useLoadState(async () => {
  if (!props.personal) return channelApi.listSystemChannels()
  const [items, selection] = await Promise.all([channelApi.listPersonalChannels(), channelApi.getChannelPreference()])
  preference.value = selection
  return items
})
const capabilities = reactive<Record<string, AiSystemCapability>>({})
const pending = ref('')
const pageError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'ai-system-channels-error')
useToastFeedback(notice, 'success', 'ai-system-channels-notice')
const blankForm = (): AiSystemChannelInput => ({ name: '', providerCode: '', baseUrl: '', apiKey: '', protocol: 'OPENAI_CHAT' })
const form = ref<AiSystemChannelInput>(blankForm())
const editForm = ref<AiSystemChannelInput>(blankForm())
const editing = ref<AiSystemChannel | null>(null)
const canUse = computed(() => !props.personal || preference.value?.subscriptionActive === true)
const channelCount = computed(() => (channels.value ?? []).filter(item => item.status !== 'ARCHIVED').length)
const canCreate = computed(() => canUse.value && (!props.personal || channelCount.value < 5))
const testChannel = (id: string, model: string) => props.personal ? channelApi.testPersonalChannel(id, model) : channelApi.testSystemChannel(id, model)
const testModels = reactive<Record<string, string>>({})
const displayedChannels = computed(() => (channels.value ?? []).map(channel => {
  const local = capabilities[channel.id]
  return { ...channel, capability: local && (local.configVersion === channel.configVersion) && (!channel.capability || local.checkedAt >= channel.capability.checkedAt)
    ? local : channel.capability }
}))
const chosenModel = (channel: AiSystemChannel) => testModels[channel.id] ?? channel.pending?.selectedModel ?? channel.selectedModel ??
  (channel.capability?.status === 'PASSED' ? channel.capability.testedModel ?? '' : '')
const isApplied = (channel: AiSystemChannel) => channel.status === 'ACTIVE' && !channel.pending && !!channel.selectedModel && chosenModel(channel) === channel.selectedModel
const selectionPassed = (channel: AiSystemChannel) => channel.capability?.status === 'PASSED' && channel.capability.testedModel === chosenModel(channel) && channel.capability.configVersion === channel.configVersion
const modelOptions = (channel: AiSystemChannel) => [...new Set([
  ...(channel.capability?.models ?? []), channel.selectedModel, channel.capability?.testedModel, chosenModel(channel),
].filter((model): model is string => !!model))].map(model => ({ value: model, label: model }))

const STATUS_LABELS: Record<string, string> = { DRAFT: '草稿', ACTIVE: '已启用', DISABLED: '已停用', ARCHIVED: '已归档' }
const HEALTH_LABELS: Record<string, string> = { HEALTHY: '测试通过', UNHEALTHY: '测试未通过', UNKNOWN: '尚未测试', DEGRADED: '部分可用' }
const statusText = (channel: AiSystemChannel) => `${STATUS_LABELS[channel.status] ?? channel.status} · ${HEALTH_LABELS[channel.healthStatus] ?? channel.healthStatus}`

async function create(): Promise<void> {
  pending.value = 'create'; pageError.value = ''; notice.value = ''
  try {
    if (props.personal) await channelApi.createPersonalChannel({ ...form.value })
    else await channelApi.createSystemChannel({ ...form.value })
    form.value = blankForm()
    notice.value = '通道已保存为草稿；出于安全考虑，密钥保存后不再显示。'
    await load()
  } catch (reason) { pageError.value = errorMessage(reason, '通道创建失败') }
  finally { pending.value = '' }
}

async function test(channel: AiSystemChannel): Promise<void> {
  pending.value = `test-${channel.id}`; pageError.value = ''; notice.value = ''
  try {
    const result = await testChannel(channel.id, chosenModel(channel))
    capabilities[channel.id] = result
    if (result.status === 'PASSED') notice.value = `模型 ${result.testedModel} 检测通过；点击“启用所选模型”后应用。`
    else pageError.value = `能力测试未通过：${result.detail}`
    await load()
  } catch (reason) { pageError.value = errorMessage(reason, '能力测试失败') }
  finally { pending.value = '' }
}

async function activate(channel: AiSystemChannel): Promise<void> {
  const model = chosenModel(channel)
  if (!model || isApplied(channel)) return
  pending.value = `activate-${channel.id}`; pageError.value = ''; notice.value = ''
  try {
    let current = channel
    if (!selectionPassed(channel)) {
      const result = await testChannel(channel.id, model)
      capabilities[channel.id] = result
      const refreshed = await load()
      if (result.status !== 'PASSED' || result.testedModel !== model) {
        pageError.value = `所选模型未启用：${result.detail}`
        return
      }
      const fresh = refreshed?.find(value => value.id === channel.id)
      if (!fresh) throw new Error('通道状态刷新失败，请重试')
      current = fresh
    }
    if (props.personal) await channelApi.activatePersonalChannel(current, model)
    else await channelApi.activateSystemChannel(current, model)
    notice.value = `已启用模型 ${model}`
    await load()
  } catch (reason) { pageError.value = errorMessage(reason, '模型启用失败') }
  finally { pending.value = '' }
}

async function disable(channel: AiSystemChannel): Promise<void> {
  pending.value = `disable-${channel.id}`; pageError.value = ''; notice.value = ''
  try {
    if (props.personal) await channelApi.disablePersonalChannel(channel)
    else await channelApi.disableSystemChannel(channel)
    notice.value = '通道已停用'
    await load()
  } catch (reason) { pageError.value = errorMessage(reason, '状态更新失败') }
  finally { pending.value = '' }
}

const passLabel = (value: boolean) => value ? '通过' : '未通过'
function edit(channel: AiSystemChannel): void {
  editing.value = channel
  editForm.value = { name: channel.name, providerCode: channel.providerCode, baseUrl: channel.pending?.baseUrl ?? channel.baseUrl,
    protocol: channel.pending?.protocol ?? channel.protocol, apiKey: '', routingWeight: channel.routingWeight ?? 100,
    selectedModel: chosenModel(channel) || null }
}
async function saveEdit(): Promise<void> {
  const channel = editing.value
  if (!channel) return
  pending.value = `edit-${channel.id}`; pageError.value = ''
  try {
    if (props.personal) await channelApi.updatePersonalChannel(channel, { ...editForm.value })
    else await channelApi.updateSystemChannel(channel, { ...editForm.value })
    delete capabilities[channel.id]
    editing.value = null; editForm.value.apiKey = ''
    notice.value = '配置已保存；连接、密钥或模型变更需重新检测并启用后生效。'
    await load()
  } catch (reason) { pageError.value = errorMessage(reason, '配置保存失败') }
  finally { pending.value = '' }
}
async function archive(channel: AiSystemChannel): Promise<void> {
  if (!await confirm({ title: '归档个人通道', message: '归档后不再用于 AI 调用，配置与历史记录将保留。若它是当前选择，请另选通道或确认切换平台。', confirmText: '归档', tone: 'danger' })) return
  pending.value = `archive-${channel.id}`; pageError.value = ''
  try { await channelApi.archivePersonalChannel(channel); notice.value = '通道已归档，历史记录已保留'; await load() }
  catch (reason) { pageError.value = errorMessage(reason, '归档失败') }
  finally { pending.value = '' }
}
async function selectChannel(id: string | null): Promise<void> {
  if (!preference.value || preference.value.personalChannelId === id) return
  if (id === null && !await confirm({ title: '切换为平台 AI 通道', message: '平台调用优先使用月度额度。额度不足时，会在操作前展示价格并征求余额支付确认。个人通道配置与历史记录会继续保留。', confirmText: '确认切换平台' })) return
  pending.value = 'preference'; pageError.value = ''
  try { preference.value = await channelApi.saveChannelPreference(preference.value, id, id === null); notice.value = id ? '已选择个人通道' : '已切换为平台通道' }
  catch (reason) { pageError.value = errorMessage(reason, '通道选择失败') }
  finally { pending.value = '' }
}
onMounted(load)
</script>

<template>
  <main class="page-container system-ai-page">
    <UiPageHeader :title="personal ? '个人 AI 通道' : '平台 AI 通道'" :description="personal ? '使用自己的供应商密钥和模型；订阅期间可保留最多 5 个未归档通道。' : '管理员录入、测试和启停平台提供的 AI 通道；求职者不需要也看不到供应商密钥。'">
      <template #actions>
        <UiButton variant="secondary" :icon="RefreshCw" :pending="loading" :disabled="!!pending && !loading" @click="load">刷新</UiButton>
      </template>
    </UiPageHeader>
    <UiCard v-if="personal && preference" class="channel-preference" :tone="canUse ? 'default' : 'sunken'">
      <div><h2>{{ canUse ? '个人通道订阅有效' : '个人通道订阅未开通或已到期' }}</h2>
        <p v-if="preference.subscriptionEnd">订阅到期：{{ new Date(preference.subscriptionEnd).toLocaleString('zh-CN') }}</p>
        <p v-if="!canUse">配置和历史记录继续保留。续费后可创建、检测、启用和使用个人通道。</p>
        <RouterLink to="/account/personal-channel-subscription">{{ canUse ? '查看订阅与续费' : '开通或续费订阅' }}</RouterLink>
      </div>
      <div><strong>当前使用：{{ preference.source === 'PLATFORM' ? '平台 AI 通道' : channels?.find(item => item.id === preference?.personalChannelId)?.name ?? '所选个人通道' }}</strong>
        <p v-if="preference.source === 'PERSONAL' && (!canUse || channels?.find(item => item.id === preference?.personalChannelId)?.status !== 'ACTIVE')" class="selection-warning">当前选择不可用，AI 调用将暂停。可续费、重新启用，或确认切换平台通道。</p>
        <UiButton v-if="preference.source === 'PERSONAL'" variant="secondary" :disabled="!!pending" @click="selectChannel(null)">切换平台通道</UiButton>
      </div>
    </UiCard>
    <UiCard class="channel-create">
      <header><h2>{{ personal ? `新增个人通道 · ${channelCount}/5` : '新增平台通道' }}</h2><p>填写地址和密钥后保存，自动发现并测试模型；找到可用模型后即可启用，无需配置模型映射。</p></header>
      <p v-if="personal && !canCreate">{{ canUse ? '已达到 5 个未归档通道，请先归档不再使用的通道。' : '开通订阅后可创建个人通道。' }}</p>
      <form v-if="canCreate" @submit.prevent="create">
        <AiChannelFields v-model="form" :personal="personal" />
        <UiButton class="channel-create__submit" type="submit" :pending="pending === 'create'" :disabled="!!pending">保存为草稿</UiButton>
      </form>
    </UiCard>
    <PageState
      class="channel-list"
      :loading="loading"
      :error="loadError"
      :loaded="loaded"
      :empty="!channels?.length"
      error-title="通道列表读取失败"
      compact
      @retry="load"
    >
      <template #empty><UiEmptyState size="sm" illustration="admin-channel" :title="personal ? '还没有个人通道' : '还没有平台通道'" description="在上方填写名称、Base URL 和 API 密钥保存为草稿，测试连通后再启用。" /></template>
      <UiCard v-for="channel in displayedChannels" :key="channel.id" as="article">
        <header>
          <div><strong>{{ channel.name }}</strong><span>{{ [channel.providerCode, channel.protocol, channel.apiKeyMasked].filter(Boolean).join(' · ') }}</span></div>
          <UiBadge :tone="channel.status === 'ACTIVE' ? 'success' : channel.healthStatus === 'UNHEALTHY' ? 'danger' : 'neutral'">{{ statusText(channel) }}</UiBadge>
        </header>
        <p>{{ channel.baseUrl }}</p>
        <p class="applied-model">当前生效模型：<strong>{{ channel.status === 'ACTIVE' ? channel.selectedModel || '尚未设置' : '未启用' }}</strong></p>
        <p v-if="!personal">路由权重：{{ channel.routingWeight ?? 100 }} · 同等健康状态下按权重分配</p>
        <div v-if="channel.pending" class="pending-configuration"><strong>待生效配置 · 版本 {{ channel.pending.configVersion }}</strong><p>{{ channel.pending.baseUrl }}</p><p>{{ channel.pending.protocol }} · {{ channel.pending.apiKeyMasked }}</p><small>{{ channel.status === 'ACTIVE' ? '检测通过并点击启用后应用；当前生效配置继续提供服务。' : '检测通过并点击启用后开始提供服务。' }}</small></div>
        <div v-if="channel.status !== 'ARCHIVED'" class="test-controls">
          <UiSelect :model-value="chosenModel(channel)" :options="modelOptions(channel)" searchable search-placeholder="搜索模型" aria-label="选择模型" placeholder="请先检测可用模型" empty-text="暂无模型，请先点击检测可用模型" :disabled="!!pending || loading || !canUse" @update:model-value="testModels[channel.id] = String($event)" />
          <div class="model-actions">
            <UiButton variant="secondary" :pending="pending === `test-${channel.id}`" :disabled="!!pending || loading || !canUse" @click="test(channel)">检测可用模型</UiButton>
            <UiButton :pending="pending === `activate-${channel.id}`" :disabled="!!pending || loading || !canUse || !chosenModel(channel) || isApplied(channel)" @click="activate(channel)">{{ isApplied(channel) ? '已启用' : '启用所选模型' }}</UiButton>
            <UiButton v-if="channel.status === 'ACTIVE'" variant="secondary" :pending="pending === `disable-${channel.id}`" :disabled="!!pending || loading" @click="disable(channel)">停用</UiButton>
          </div>
        </div>
        <p v-if="channel.status !== 'ARCHIVED' && chosenModel(channel) && !isApplied(channel)" class="pending-model">待启用模型：<strong>{{ chosenModel(channel) }}</strong> · {{ selectionPassed(channel) ? '检测通过，点击启用后生效' : '点击启用时会先检测，通过后生效' }}</p>
        <small v-if="channel.capability?.models.length">已发现 {{ channel.capability.models.length }} 个模型，可展开下拉框查看并搜索；检测结果仅对应本次测试的模型。</small>
        <small v-if="pending === `test-${channel.id}`">正在发现模型并检测{{ chosenModel(channel) ? '所选模型' : '首个可用模型' }}，最多等待 2 分钟。</small>
        <small v-else-if="pending === `activate-${channel.id}`">正在验证并启用所选模型，通过后才会生效。</small>
        <p v-if="channel.capability?.testedModel">最近检测模型：<strong>{{ channel.capability.testedModel }}</strong> · {{ channel.capability.status === 'PASSED' ? '通过' : '未通过' }}</p>
        <dl v-if="channel.capability">
          <div v-for="item in ([['authenticated','鉴权'],['modelDiscovery','模型发现'],['plainResponse','普通响应'],['streamingResponse','流式响应'],['structuredResponse','结构化输出']] as const)" :key="item[0]"><dt>{{ item[1] }}</dt><dd :class="{ pass: channel.capability[item[0]] }">{{ passLabel(channel.capability[item[0]]) }}</dd></div>
        </dl>
        <small v-if="channel.capability">{{ channel.capability.detail }}</small>
        <div v-if="channel.status !== 'ARCHIVED'" class="channel-management">
          <UiButton variant="secondary" :disabled="!!pending || loading" @click="edit(channel)">编辑配置</UiButton>
          <UiButton v-if="personal" variant="secondary" :disabled="!!pending || loading || !canUse || channel.status !== 'ACTIVE' || preference?.personalChannelId === channel.id" @click="selectChannel(channel.id)">{{ preference?.personalChannelId === channel.id ? '当前使用' : '使用此通道' }}</UiButton>
          <UiButton v-if="personal" variant="ghost" :disabled="!!pending || loading" @click="archive(channel)">归档</UiButton>
        </div>
      </UiCard>
    </PageState>
    <UiDrawer :open="!!editing" title="编辑通道配置" description="API 密钥留空保持不变。连接、密钥和模型变更保存为待生效配置，检测并启用后应用。" :dismissible="!pending" @close="editing = null; editForm.apiKey = ''">
      <form id="channel-edit-form" class="edit-form" @submit.prevent="saveEdit"><AiChannelFields v-model="editForm" prefix="edit-channel" editing :personal="personal" /></form>
      <template #footer><UiButton variant="secondary" :disabled="!!pending" @click="editing = null; editForm.apiKey = ''">取消</UiButton><UiButton type="submit" form="channel-edit-form" :pending="pending.startsWith('edit-')" :disabled="!!pending">保存配置</UiButton></template>
    </UiDrawer>
  </main>
</template>

<style scoped>
.system-ai-page{max-width:1200px;margin:0 auto;display:grid;gap:16px;align-content:start}.channel-create{padding:18px;background:var(--surface-1);border:1px solid var(--border-default);border-radius:8px}.channel-create header{margin-bottom:14px;display:grid;gap:3px}.channel-create h2{font-size:15px}.channel-create p{color:var(--text-tertiary);font-size:12px}.channel-create form{display:grid;grid-template-columns:1fr 1fr;gap:12px}.channel-create .wide{grid-column:1/-1}.channel-create form>.channel-create__submit{width:fit-content}.channel-list{display:grid;gap:10px}.channel-list article{padding:16px;display:grid;gap:12px;background:var(--surface-1);border:1px solid var(--border-default);border-radius:8px}.channel-list article>header{display:flex;justify-content:space-between;gap:12px}.channel-list header>div{display:grid;gap:3px}.channel-list header span,.channel-list article>p,.channel-list article>small{color:var(--text-tertiary);font-size:12px;overflow-wrap:anywhere}.test-controls{display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:8px}.channel-list dl{display:grid;grid-template-columns:repeat(5,1fr);gap:6px}.channel-list dl div{padding:8px;background:var(--surface-2);text-align:center}.channel-list dt{color:var(--text-tertiary);font-size:11.5px}.channel-list dd{color:var(--color-danger);font-size:12px;font-weight:600}.channel-list dd.pass{color:var(--color-success)}.empty-state{min-height:120px;display:grid;place-items:center;color:var(--text-tertiary)}@media(max-width:800px){.channel-create form{grid-template-columns:1fr}.channel-create .wide{grid-column:auto}.test-controls{grid-template-columns:1fr}.channel-list dl{grid-template-columns:1fr 1fr}.channel-list article>header{align-items:flex-start;flex-direction:column}}

.test-controls{grid-template-columns:minmax(180px,1fr) auto;align-items:center}.model-actions{display:flex;gap:8px;flex-wrap:wrap}.channel-list .applied-model strong{color:var(--text-primary)}.channel-list .pending-model{color:var(--color-primary-text)}@media(max-width:800px){.test-controls{grid-template-columns:1fr}.model-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}.model-actions>*:last-child:nth-child(3){grid-column:1/-1}}

.channel-create,
.channel-list article,
.channel-list .empty-state {
  animation: system-channel-in var(--dur-base) var(--ease-out) both;
}
.channel-list article {
  transition: border-color var(--dur-base) ease, box-shadow var(--dur-base) var(--ease-standard), transform var(--dur-fast) var(--ease-standard);
}
.channel-list article:hover {
  border-color: var(--border-default);
  box-shadow: 0 6px 18px rgba(16, 24, 40, .07);
  transform: translateY(-1px);
}
.channel-list dl {
  animation: channel-capability-in var(--dur-base) var(--ease-out) both;
}
.channel-list dl div,
.channel-list :deep(.ui-badge) {
  transition: background-color var(--dur-base) ease, color var(--dur-base) ease, border-color var(--dur-base) ease;
}
@keyframes system-channel-in {
  from { opacity: 0; transform: translateY(7px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes channel-capability-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}
@media (prefers-reduced-motion: reduce) {
  .channel-create,
  .channel-list article,
  .channel-list .empty-state,
  .channel-list dl { animation: none; }
  .channel-list article,
  .channel-list dl div,
  .channel-list :deep(.ui-badge) { transition: none; }
  .channel-list article:hover { transform: none; }
}
</style>

<style scoped>
.channel-preference { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-5); }
.channel-preference > div { display: grid; align-content: start; gap: var(--space-2); }
.channel-preference h2 { font-size: var(--fs-body); }
.channel-preference p, .pending-configuration p, .pending-configuration small { color: var(--text-secondary); font-size: var(--fs-sm); overflow-wrap: anywhere; }
.channel-preference a { color: var(--color-primary-text); font-size: var(--fs-sm); width: fit-content; }
.channel-preference .selection-warning { color: var(--color-danger); }
.pending-configuration { display: grid; gap: var(--space-2); padding: var(--space-3); background: var(--surface-2); border-left: 3px solid var(--color-primary); border-radius: var(--radius-sm); }
.pending-configuration strong { font-size: var(--fs-sm); }
.channel-management { display: flex; gap: var(--space-2); flex-wrap: wrap; padding-top: var(--space-3); border-top: 1px solid var(--border-subtle); }
.edit-form { display: grid; gap: var(--space-4); }
@media (max-width: 800px) { .channel-preference { grid-template-columns: 1fr; } }
</style>
