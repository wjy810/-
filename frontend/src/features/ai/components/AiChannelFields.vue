<script setup lang="ts">
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import type { AiSystemChannelInput } from '../types'
const form = defineModel<AiSystemChannelInput>({ required: true })
withDefaults(defineProps<{ prefix?: string; editing?: boolean; personal?: boolean }>(), { prefix: 'channel', editing: false, personal: false })
</script>

<template>
  <UiField :id="`${prefix}-name`" label="名称"><UiInput :id="`${prefix}-name`" v-model="form.name" maxlength="128" required /></UiField>
  <UiField :id="`${prefix}-provider`" label="供应商代码（可选）"><UiInput :id="`${prefix}-provider`" v-model="form.providerCode" maxlength="64" placeholder="可留空，仅用于标记供应商" /></UiField>
  <UiField :id="`${prefix}-base-url`" class="wide" label="Base URL"><UiInput :id="`${prefix}-base-url`" v-model="form.baseUrl" type="url" maxlength="1024" placeholder="https://provider.example.com" required /></UiField>
  <UiField :id="`${prefix}-protocol`" label="协议"><UiSelect :id="`${prefix}-protocol`" v-model="form.protocol" aria-label="协议" placeholder="协议"><option value="OPENAI_CHAT">OpenAI Chat</option><option value="OPENAI_RESPONSES">OpenAI Responses</option><option value="ANTHROPIC_MESSAGES">Anthropic Messages</option></UiSelect></UiField>
  <UiField v-if="!personal" :id="`${prefix}-weight`" label="路由权重" hint="默认 100。权重影响同等健康状态下的选择概率，不改变价格。"><UiInput :id="`${prefix}-weight`" :model-value="form.routingWeight ?? 100" type="number" min="1" max="10000" step="1" required @update:model-value="form.routingWeight = Number($event)" /></UiField>
  <UiField :id="`${prefix}-api-key`" class="wide" :label="editing ? '替换 API 密钥（留空保持不变）' : 'API 密钥'"><UiInput :id="`${prefix}-api-key`" v-model="form.apiKey" type="password" autocomplete="new-password" maxlength="4096" :required="!editing" :placeholder="editing ? '留空保留现有密钥' : ''" /></UiField>
</template>
