<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import { errorMessage } from '@/shared/api/types'
import { normalizeEmail, PASSWORD_RULE_HINT, validateCode, validatePassword } from '@/shared/lib/validation'
import AuthStage from '../components/AuthStage.vue'
import { confirmPasswordReset, fetchDevMailbox, requestPasswordReset } from '../services/authApi'

const router = useRouter()
const step = ref<'request' | 'confirm'>('request')
const form = reactive({ email: '', code: '', password: '', confirm: '' })
const fieldError = reactive({ email: '', code: '', password: '', confirm: '' })
const pending = ref(false)
const peeking = ref(false)
const formError = ref('')
const formInfo = ref('')

function validateRequest(): boolean {
  fieldError.email = normalizeEmail(form.email).error ?? ''
  return !fieldError.email
}

function validateConfirm(): boolean {
  const email = normalizeEmail(form.email)
  fieldError.email = email.error ?? ''
  fieldError.code = validateCode(form.code) ?? ''
  fieldError.password = validatePassword(form.password, email.value) ?? ''
  fieldError.confirm = form.confirm === form.password ? '' : '两次密码不一致'
  return !fieldError.email && !fieldError.code && !fieldError.password && !fieldError.confirm
}

async function onRequest(): Promise<void> {
  formError.value = ''
  formInfo.value = ''
  if (!validateRequest()) {
    return
  }
  pending.value = true
  try {
    await requestPasswordReset(normalizeEmail(form.email).value)
    step.value = 'confirm'
    formInfo.value = '若该邮箱已注册，验证码已发出（约 10 分钟有效）。'
  } catch (error) {
    formError.value = errorMessage(error, '申请验证码失败')
  } finally {
    pending.value = false
  }
}

async function onPeek(): Promise<void> {
  formError.value = ''
  formInfo.value = ''
  const email = normalizeEmail(form.email)
  if (email.error) {
    fieldError.email = email.error
    return
  }
  peeking.value = true
  try {
    const mail = await fetchDevMailbox(email.value)
    form.code = mail.code
    formInfo.value = `开发邮箱已读到用途 ${mail.purpose} 的验证码。生产环境没有此入口。`
  } catch (error) {
    formError.value = errorMessage(error, '开发邮箱不可用')
  } finally {
    peeking.value = false
  }
}

async function onConfirm(): Promise<void> {
  formError.value = ''
  if (!validateConfirm()) {
    return
  }
  pending.value = true
  try {
    await confirmPasswordReset(normalizeEmail(form.email).value, form.code.trim(), form.password)
    await router.replace({ name: 'home', query: { auth: 'login', reason: 'password_reset' } })
  } catch (error) {
    formError.value = errorMessage(error, '验证码无效或已过期')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <AuthStage title="重置密码" sub="通过邮箱验证码重置，成功后全部会话失效">
    <AppBanner v-if="formInfo" tone="ok">{{ formInfo }}</AppBanner>
    <AppBanner v-if="formError" tone="bad">{{ formError }}</AppBanner>
    <form v-if="step === 'request'" novalidate @submit.prevent="onRequest">
      <AppField id="reset-email" label="邮箱" :error="fieldError.email">
        <div class="input-icon">
          <AppIcon name="mail" :size="16" />
          <input
            id="reset-email"
            v-model="form.email"
            type="email"
            placeholder="请输入注册邮箱"
            autocomplete="username"
            :disabled="pending"
          />
        </div>
      </AppField>
      <AppButton class="btn-block" type="submit" :pending="pending">
        {{ pending ? '正在申请…' : '申请验证码' }}
      </AppButton>
    </form>
    <form v-else novalidate @submit.prevent="onConfirm">
      <AppField id="reset-email-2" label="邮箱" :error="fieldError.email">
        <div class="input-icon">
          <AppIcon name="mail" :size="16" />
          <input
            id="reset-email-2"
            v-model="form.email"
            type="email"
            autocomplete="username"
            :disabled="pending"
          />
        </div>
      </AppField>
      <AppField id="reset-code" label="6 位验证码" :error="fieldError.code">
        <input
          id="reset-code"
          v-model="form.code"
          inputmode="numeric"
          placeholder="请输入验证码"
          autocomplete="one-time-code"
          maxlength="6"
          :disabled="pending"
        />
      </AppField>
      <AppField
        id="reset-password"
        label="新密码"
        :error="fieldError.password"
        :hint="`${PASSWORD_RULE_HINT}成功后全部会话失效。`"
      >
        <div class="input-icon">
          <AppIcon name="lock" :size="16" />
          <input
            id="reset-password"
            v-model="form.password"
            type="password"
            placeholder="请设置新密码"
            autocomplete="new-password"
            :disabled="pending"
          />
        </div>
      </AppField>
      <AppField id="reset-confirm" label="确认新密码" :error="fieldError.confirm">
        <div class="input-icon">
          <AppIcon name="lock" :size="16" />
          <input
            id="reset-confirm"
            v-model="form.confirm"
            type="password"
            placeholder="请再次输入新密码"
            autocomplete="new-password"
            :disabled="pending"
          />
        </div>
      </AppField>
      <div class="btn-row">
        <AppButton type="submit" :pending="pending">{{ pending ? '正在确认…' : '确认改密' }}</AppButton>
        <AppButton variant="ghost" :pending="peeking" @click="onPeek">
          {{ peeking ? '正在读取…' : '开发邮箱：读取验证码' }}
        </AppButton>
      </div>
    </form>
    <div class="auth-alt">
      <span>想起密码了？</span>
      <RouterLink class="text-link" :to="{ name: 'home', query: { auth: 'login' } }">去登录</RouterLink>
    </div>
  </AuthStage>
</template>
