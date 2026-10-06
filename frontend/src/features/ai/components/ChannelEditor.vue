<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import type { AiChannel, ChannelInput } from '../types'

const props = defineProps<{ open: boolean; channel: AiChannel | null; pending: boolean; error: string }>()
const emit = defineEmits<{ close: []; submit: [value: ChannelInput] }>()
const form = reactive<ChannelInput>({ name: '', providerCode: '', baseUrl: '', apiKey: '', protocol: 'OPENAI_COMPATIBLE', status: 'ACTIVE' })
const errors = reactive({ name: '', providerCode: '', baseUrl: '', apiKey: '' })
const editing = computed(() => Boolean(props.channel))

watch(() => [props.open, props.channel] as const, () => {
  if (!props.open) return
  Object.assign(form, { name: props.channel?.name ?? '', providerCode: props.channel?.providerCode ?? '', baseUrl: props.channel?.baseUrl ?? '', apiKey: '', protocol: props.channel?.protocol ?? 'OPENAI_COMPATIBLE', status: props.channel?.status ?? 'ACTIVE' })
  Object.assign(errors, { name: '', providerCode: '', baseUrl: '', apiKey: '' })
})

function submit(): void {
  errors.name = form.name.trim() ? '' : '请输入通道名称'
  errors.providerCode = form.providerCode.trim() ? '' : '请输入供应商代码'
  errors.apiKey = editing.value || form.apiKey.trim() ? '' : '请输入 API Key'
  try {
    const url = new URL(form.baseUrl)
    errors.baseUrl = url.protocol === 'https:' || ['localhost', '127.0.0.1'].includes(url.hostname) ? '' : 'Base URL 必须使用 HTTPS'
  } catch { errors.baseUrl = '请输入有效的完整 URL' }
  if (!errors.name && !errors.providerCode && !errors.baseUrl && !errors.apiKey) emit('submit', { ...form, name: form.name.trim(), providerCode: form.providerCode.trim(), baseUrl: form.baseUrl.trim(), apiKey: form.apiKey.trim() })
}
</script>

<template>
  <AppModal :open="open" :title="editing ? '编辑个人通道' : '添加个人通道'" :width="620" @close="!pending && emit('close')">
    <AppBanner v-if="error" tone="bad">{{ error }}</AppBanner>
    <form class="editor" novalidate @submit.prevent="submit">
      <div class="editor__grid">
        <AppField id="ai-name" label="通道名称" :error="errors.name"><input id="ai-name" v-model="form.name" class="input" maxlength="80" :disabled="pending" placeholder="例如：我的 OpenAI" /></AppField>
        <AppField id="ai-provider" label="供应商代码" :error="errors.providerCode"><input id="ai-provider" v-model="form.providerCode" class="input" maxlength="40" :disabled="pending || editing" placeholder="OPENAI" /></AppField>
      </div>
      <AppField id="ai-url" label="Base URL" :error="errors.baseUrl" hint="仅允许 HTTPS；本地开发地址由服务端白名单控制。"><input id="ai-url" v-model="form.baseUrl" class="input" type="url" :disabled="pending" placeholder="https://api.example.com/v1" /></AppField>
      <AppField id="ai-key" label="API Key" :error="errors.apiKey" :hint="editing ? '留空表示保持现有密钥；掩码不会作为密钥回传。' : '密钥仅写入，保存后只显示尾号掩码。'"><input id="ai-key" v-model="form.apiKey" class="input" type="password" autocomplete="new-password" :disabled="pending" placeholder="仅在此处写入" /></AppField>
      <div class="editor__grid">
        <AppField id="ai-protocol" label="协议"><AppSelect id="ai-protocol" v-model="form.protocol" ariaLabel="协议" :disabled="pending || editing"><option value="OPENAI_COMPATIBLE">OpenAI Compatible</option><option value="ANTHROPIC">Anthropic</option><option value="GEMINI">Gemini</option></AppSelect></AppField>
        <AppField id="ai-status" label="状态"><AppSelect id="ai-status" v-model="form.status" ariaLabel="状态" :disabled="pending"><option value="ACTIVE">启用</option><option value="DISABLED">停用</option><option value="DRAFT">草稿</option></AppSelect></AppField>
      </div>
    </form>
    <template #footer><AppButton variant="ghost" :disabled="pending" @click="emit('close')">取消</AppButton><AppButton :pending="pending" @click="submit">{{ pending ? '正在保存…' : '保存通道' }}</AppButton></template>
  </AppModal>
</template>

<style scoped>
.editor{display:grid;gap:16px}.editor__grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}@media(max-width:600px){.editor__grid{grid-template-columns:1fr}}
</style>
