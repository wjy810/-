<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { activateSystemChannel, createSystemChannel, disableSystemChannel, listSystemChannels, testSystemChannel } from '../services/aiApi'
import type { AiSystemCapability, AiSystemChannel, AiSystemChannelInput } from '../types'

const { data: channels, error: loadError, loading, loaded, load } = useLoadState(() => listSystemChannels())
const capabilities = reactive<Record<string, AiSystemCapability>>({})
const pending = ref('')
const pageError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'ai-system-channels-error')
useToastFeedback(notice, 'success', 'ai-system-channels-notice')
const form = reactive<AiSystemChannelInput>({ name: '', providerCode: 'QWEN', baseUrl: '', apiKey: '', protocol: 'OPENAI_CHAT' })
const testModels = reactive<Record<string, string>>({})
const platformModels = reactive<Record<string, string>>({})

const STATUS_LABELS: Record<string, string> = { DRAFT: '草稿', ACTIVE: '已启用', DISABLED: '已停用', ARCHIVED: '已归档' }
const HEALTH_LABELS: Record<string, string> = { HEALTHY: '测试通过', UNHEALTHY: '测试未通过', UNKNOWN: '尚未测试', DEGRADED: '部分可用' }
const statusText = (channel: AiSystemChannel) => `${STATUS_LABELS[channel.status] ?? channel.status} · ${HEALTH_LABELS[channel.healthStatus] ?? channel.healthStatus}`

async function create(): Promise<void> {
  pending.value = 'create'; pageError.value = ''; notice.value = ''
  try {
    await createSystemChannel({ ...form })
    Object.assign(form, { name: '', providerCode: 'QWEN', baseUrl: '', apiKey: '', protocol: 'OPENAI_CHAT' })
    notice.value = '通道已保存为草稿；出于安全考虑，密钥保存后不再显示。'
    await load()
  } catch (reason) { pageError.value = errorMessage(reason, '通道创建失败') }
  finally { pending.value = '' }
}

async function test(channel: AiSystemChannel): Promise<void> {
  pending.value = `test-${channel.id}`; pageError.value = ''; notice.value = ''
  try {
    const result = await testSystemChannel(channel.id, testModels[channel.id] ?? '', platformModels[channel.id] ?? 'qwen-plus')
    capabilities[channel.id] = result
    notice.value = result.status === 'PASSED' ? '五项能力测试全部通过，可以启用。' : `能力测试未通过：${result.detail}`
    await load()
  } catch (reason) { pageError.value = errorMessage(reason, '能力测试失败') }
  finally { pending.value = '' }
}

async function toggle(channel: AiSystemChannel): Promise<void> {
  pending.value = `toggle-${channel.id}`; pageError.value = ''
  try {
    if (channel.status === 'ACTIVE') await disableSystemChannel(channel)
    else await activateSystemChannel(channel)
    await load()
  } catch (reason) { pageError.value = errorMessage(reason, '状态更新失败') }
  finally { pending.value = '' }
}

const passLabel = (value: boolean) => value ? '通过' : '未通过'
onMounted(load)
</script>

<template>
  <main class="page-container system-ai-page">
    <UiPageHeader title="平台 AI 通道" description="管理员录入、测试和启停平台提供的 AI 通道；求职者不需要也看不到供应商密钥。">
      <template #actions>
        <UiButton variant="secondary" :icon="RefreshCw" :pending="loading" :disabled="!!pending && !loading" @click="load">刷新</UiButton>
      </template>
    </UiPageHeader>
    <section class="channel-create">
      <header><h2>新增平台通道</h2><p>请使用新生成的密钥。通道先保存为草稿，五项能力测试全部通过后才能启用。</p></header>
      <form @submit.prevent="create">
        <UiField id="channel-name" label="名称"><UiInput id="channel-name" v-model="form.name" maxlength="128" required /></UiField>
        <UiField id="channel-provider" label="供应商代码"><UiInput id="channel-provider" v-model="form.providerCode" maxlength="64" required /></UiField>
        <UiField id="channel-base-url" class="wide" label="Base URL"><UiInput id="channel-base-url" v-model="form.baseUrl" type="url" placeholder="https://provider.example.com" required /></UiField>
        <UiField id="channel-protocol" label="协议"><UiSelect id="channel-protocol" v-model="form.protocol" aria-label="协议" placeholder="协议"><option value="OPENAI_CHAT">OpenAI Chat</option><option value="OPENAI_RESPONSES">OpenAI Responses</option><option value="ANTHROPIC_MESSAGES">Anthropic Messages</option></UiSelect></UiField>
        <UiField id="channel-api-key" class="wide" label="API 密钥"><UiInput id="channel-api-key" v-model="form.apiKey" type="password" autocomplete="new-password" required /></UiField>
        <UiButton class="channel-create__submit" type="submit" :pending="pending === 'create'">保存为草稿</UiButton>
      </form>
    </section>
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
      <template #empty><UiEmptyState size="sm" illustration="admin-channel" title="还没有平台通道" description="在上方填写名称、Base URL 和 API 密钥保存为草稿，测试连通后再启用。" /></template>
      <article v-for="channel in channels ?? []" :key="channel.id">
        <header>
          <div><strong>{{ channel.name }}</strong><span>{{ channel.providerCode }} · {{ channel.protocol }} · {{ channel.apiKeyMasked }}</span></div>
          <UiBadge :tone="channel.status === 'ACTIVE' ? 'success' : channel.healthStatus === 'UNHEALTHY' ? 'danger' : 'neutral'">{{ statusText(channel) }}</UiBadge>
        </header>
        <p>{{ channel.baseUrl }}</p>
        <div class="test-controls">
          <UiInput v-model="testModels[channel.id]" placeholder="供应商模型，留空自动选择" aria-label="供应商模型" />
          <UiSelect v-model="platformModels[channel.id]" aria-label="平台模型" placeholder="平台模型"><option value="qwen-plus">映射 qwen-plus</option><option value="qwen-max">映射 qwen-max</option><option value="deepseek-chat">映射 deepseek-chat</option><option value="deepseek-reasoner">映射 deepseek-reasoner</option></UiSelect>
          <UiButton variant="secondary" :pending="pending === `test-${channel.id}`" @click="test(channel)">能力测试</UiButton>
          <UiButton :pending="pending === `toggle-${channel.id}`" @click="toggle(channel)">{{ channel.status === 'ACTIVE' ? '停用' : '启用' }}</UiButton>
        </div>
        <dl v-if="capabilities[channel.id]">
          <div v-for="item in ([['authenticated','鉴权'],['modelDiscovery','模型发现'],['plainResponse','普通响应'],['streamingResponse','流式响应'],['structuredResponse','结构化输出']] as const)" :key="item[0]"><dt>{{ item[1] }}</dt><dd :class="{ pass: capabilities[channel.id]![item[0]] }">{{ passLabel(capabilities[channel.id]![item[0]]) }}</dd></div>
        </dl>
        <small v-if="capabilities[channel.id]">{{ capabilities[channel.id]!.detail }}</small>
      </article>
    </PageState>
  </main>
</template>

<style scoped>
.system-ai-page{max-width:1200px;margin:0 auto;display:grid;gap:16px;align-content:start}.channel-create{padding:18px;background:var(--surface-1);border:1px solid var(--border-default);border-radius:8px}.channel-create header{margin-bottom:14px;display:grid;gap:3px}.channel-create h2{font-size:15px}.channel-create p{color:var(--text-tertiary);font-size:12px}.channel-create form{display:grid;grid-template-columns:1fr 1fr;gap:12px}.channel-create .wide{grid-column:1/-1}.channel-create form>.channel-create__submit{width:fit-content}.channel-list{display:grid;gap:10px}.channel-list article{padding:16px;display:grid;gap:12px;background:var(--surface-1);border:1px solid var(--border-default);border-radius:8px}.channel-list article>header{display:flex;justify-content:space-between;gap:12px}.channel-list header>div{display:grid;gap:3px}.channel-list header span,.channel-list article>p,.channel-list article>small{color:var(--text-tertiary);font-size:12px;overflow-wrap:anywhere}.test-controls{display:grid;grid-template-columns:1fr 180px auto auto;gap:8px}.channel-list dl{display:grid;grid-template-columns:repeat(5,1fr);gap:6px}.channel-list dl div{padding:8px;background:var(--surface-2);text-align:center}.channel-list dt{color:var(--text-tertiary);font-size:11.5px}.channel-list dd{color:var(--color-danger);font-size:12px;font-weight:600}.channel-list dd.pass{color:var(--color-success)}.empty-state{min-height:120px;display:grid;place-items:center;color:var(--text-tertiary)}@media(max-width:800px){.channel-create form{grid-template-columns:1fr}.channel-create .wide{grid-column:auto}.test-controls{grid-template-columns:1fr}.channel-list dl{grid-template-columns:1fr 1fr}.channel-list article>header{align-items:flex-start;flex-direction:column}}

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
