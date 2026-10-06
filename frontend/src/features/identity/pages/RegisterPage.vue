<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import { errorMessage } from '@/shared/api/types'
import { normalizeEmail, PASSWORD_RULE_HINT, validatePassword } from '@/shared/lib/validation'
import AuthStage from '../components/AuthStage.vue'
import { loginAccount, registerAccount } from '../services/authApi'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const form = reactive({ email: '', password: '', confirm: '' })
const fieldError = reactive({ email: '', password: '', confirm: '' })
const pending = ref(false)
const formError = ref('')

function validate(): boolean {
  const email = normalizeEmail(form.email)
  fieldError.email = email.error ?? ''
  fieldError.password = validatePassword(form.password, email.value) ?? ''
  fieldError.confirm = form.confirm === form.password ? '' : '两次密码不一致'
  return !fieldError.email && !fieldError.password && !fieldError.confirm
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  if (!validate()) {
    return
  }
  pending.value = true
  try {
    const email = normalizeEmail(form.email).value
    await registerAccount(email, form.password)
    const account = await loginAccount(email, form.password)
    useSessionStore().remember(account)
    await router.replace('/onboarding')
  } catch (error) {
    formError.value = errorMessage(error, '注册未成功')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <AuthStage title="创建账号" sub="注册后建立你的求职资料库">
    <AppBanner v-if="formError" tone="bad">{{ formError }}</AppBanner>
    <form novalidate @submit.prevent="onSubmit">
      <AppField id="reg-email" label="邮箱" :error="fieldError.email">
        <div class="input-icon">
          <AppIcon name="mail" :size="16" />
          <input
            id="reg-email"
            v-model="form.email"
            type="email"
            placeholder="请输入邮箱"
            autocomplete="username"
            :disabled="pending"
          />
        </div>
      </AppField>
      <AppField
        id="reg-password"
        label="密码"
        :error="fieldError.password"
        :hint="PASSWORD_RULE_HINT"
      >
        <div class="input-icon">
          <AppIcon name="lock" :size="16" />
          <input
            id="reg-password"
            v-model="form.password"
            type="password"
            placeholder="请设置密码"
            autocomplete="new-password"
            :disabled="pending"
          />
        </div>
      </AppField>
      <AppField id="reg-confirm" label="确认密码" :error="fieldError.confirm">
        <div class="input-icon">
          <AppIcon name="lock" :size="16" />
          <input
            id="reg-confirm"
            v-model="form.confirm"
            type="password"
            placeholder="请再次输入密码"
            autocomplete="new-password"
            :disabled="pending"
          />
        </div>
      </AppField>
      <AppButton class="btn-block" type="submit" :pending="pending">
        {{ pending ? '正在注册…' : '注册并完善资料' }}
      </AppButton>
    </form>
    <div class="auth-alt">
      <span>已有账号？</span>
      <RouterLink class="text-link" :to="{ name: 'home', query: { auth: 'login' } }">去登录</RouterLink>
    </div>
  </AuthStage>
</template>
