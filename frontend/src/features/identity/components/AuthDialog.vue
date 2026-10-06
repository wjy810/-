<script setup lang="ts">
/**
 * Login / register / password-reset dialog (docs/01 AUTH-01..05, docs/04 §9.1).
 * Opened from the landing page via `?auth=login|register|reset`; keeps `next` for redirect after sign-in.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Check, CheckCircle2, Eye, EyeOff, KeyRound, LockKeyhole, Mail, Phone, ShieldCheck, X } from 'lucide-vue-next'
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import BrandMark from '@/shared/ui/BrandMark.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiIllustration from '@/shared/ui/UiIllustration.vue'
import type { AuthLandingMode } from '@/features/identity/authLanding'
import { safeNextPath } from '@/features/identity/safeNext'
import {
  confirmContactVerification,
  confirmPasswordReset,
  fetchDevMailbox,
  fetchVerificationCapabilities,
  loginAccount,
  registerAccount,
  requestContactVerification,
} from '@/features/identity/services/authApi'
import type { VerificationCapabilities, VerificationChallenge, VerificationChannel } from '@/features/identity/types'
import { useSessionStore } from '@/stores/session'
import { errorMessage } from '@/shared/api/types'
import { normalizeEmail, validateCode, validatePassword } from '@/shared/lib/validation'

type ResetStep = 'verify' | 'password' | 'complete'

const props = defineProps<{ mode: AuthLandingMode; next?: string; reason?: string }>()
const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const session = useSessionStore()
const primaryInput = ref<HTMLInputElement | null>(null)
const capabilities = ref<VerificationCapabilities | null>(null)
const channel = ref<VerificationChannel>('EMAIL')
const challenge = ref<VerificationChallenge | null>(null)
const challengeDestination = ref('')
const verificationToken = ref('')
const resetStep = ref<ResetStep>('verify')
const pending = ref('')
const countdown = ref(0)
const showPassword = ref(false)
const rememberMe = ref(true)
const acceptedAgreements = ref(false)
const formError = ref('')
const formInfo = ref('')
const devMode = import.meta.env.DEV
let countdownTimer: number | undefined

const form = reactive({ identifier: '', destination: '', code: '', password: '', confirm: '' })
const fieldError = reactive({ identifier: '', destination: '', code: '', password: '', confirm: '', agreements: '' })

const title = computed(() => {
  if (props.mode === 'login') return '欢迎回来'
  if (props.mode === 'register') return '创建你的求职工作台'
  if (resetStep.value === 'complete') return '密码已重置'
  return '找回账号密码'
})
const subtitle = computed(() => {
  if (props.mode === 'login') return '登录后继续你的简历与求职准备'
  if (props.mode === 'register') return '一分钟注册，保存简历、资料与匹配报告'
  if (resetStep.value === 'verify') return '先验证账号联系方式，再设置新密码'
  if (resetStep.value === 'password') return '身份验证已通过，请设置新的登录密码'
  return '现在可以使用新密码重新登录'
})
const formKey = computed(() => (props.mode === 'reset' ? `reset-${resetStep.value}` : props.mode))
const purpose = computed(() => (props.mode === 'register' ? 'REGISTER' : 'LOGIN_RECOVERY'))
const emailAvailable = computed(() => capabilities.value?.emailEnabled ?? false)
const smsAvailable = computed(() => capabilities.value?.smsEnabled ?? false)
const selectedChannelAvailable = computed(() => (channel.value === 'EMAIL' ? emailAvailable.value : smsAvailable.value))
const contactLabel = computed(() => (channel.value === 'EMAIL' ? '邮箱' : '手机号'))
const contactPlaceholder = computed(() => (channel.value === 'EMAIL' ? 'name@example.com' : '11 位中国大陆手机号'))
const sendLabel = computed(() => (countdown.value > 0 ? `${countdown.value}s 后重发` : challenge.value ? '重新获取' : '获取验证码'))
const passwordStrength = computed(() => {
  const password = form.password
  let score = 0
  if (password.length >= 8) score++
  if (/[A-Za-z]/.test(password) && /\d/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++
  if (password.length >= 12) score++
  return score
})
const passwordStrengthText = computed(() => ['未设置', '较弱', '一般', '良好', '很强'][passwordStrength.value])
const notice = computed(() => {
  if (props.mode !== 'login') return ''
  if (props.reason === 'unauthenticated') return '登录后即可继续刚才的操作。'
  if (props.reason === 'session_expired') return '登录已过期，请重新登录。你未保存的编辑内容仍保留在本机。'
  if (props.reason === 'password_changed') return '密码已更改，请使用新密码登录。'
  if (props.reason === 'password_reset') return '密码重置成功，请使用新密码登录。'
  if (props.reason === 'logged_out') return '你已安全退出当前账号。'
  return ''
})

function clearFeedback(): void {
  formError.value = ''
  formInfo.value = ''
  Object.keys(fieldError).forEach((key) => {
    fieldError[key as keyof typeof fieldError] = ''
  })
}

function normalizePhone(raw: string): { value: string; error: string | null } {
  let value = raw.trim().replace(/[\s()-]/g, '')
  if (value.startsWith('0086')) value = value.slice(4)
  else if (value.startsWith('+86')) value = value.slice(3)
  else if (value.startsWith('86') && value.length === 13) value = value.slice(2)
  if (!/^1[3-9]\d{9}$/.test(value)) return { value, error: '请输入正确的中国大陆手机号' }
  return { value: `+86${value}`, error: null }
}

function normalizedDestination(): { value: string; error: string | null } {
  return channel.value === 'EMAIL' ? normalizeEmail(form.destination) : normalizePhone(form.destination)
}

function validateContact(): string | null {
  const result = normalizedDestination()
  fieldError.destination = result.error ?? ''
  return result.error ? null : result.value
}

function validatePasswordFields(): boolean {
  fieldError.password = validatePassword(form.password, normalizedDestination().value) ?? ''
  fieldError.confirm = form.confirm === form.password ? '' : '两次输入的密码不一致'
  return !fieldError.password && !fieldError.confirm
}

function validateVerification(): string | null {
  const destination = validateContact()
  fieldError.code = validateCode(form.code) ?? ''
  if (!challenge.value || destination !== challengeDestination.value) fieldError.code = '请先获取当前联系方式的验证码'
  return destination && !fieldError.code ? destination : null
}

function startCountdown(seconds: number): void {
  window.clearInterval(countdownTimer)
  countdown.value = Math.max(0, Math.round(seconds))
  countdownTimer = window.setInterval(() => {
    countdown.value = Math.max(0, countdown.value - 1)
    if (countdown.value === 0) window.clearInterval(countdownTimer)
  }, 1000)
}

async function loadCapabilities(): Promise<void> {
  try {
    capabilities.value = await fetchVerificationCapabilities()
    if (!emailAvailable.value && smsAvailable.value) channel.value = 'SMS'
    if (!emailAvailable.value && !smsAvailable.value && props.mode !== 'login') {
      formError.value = '邮箱和短信验证通道暂时不可用，请稍后重试。'
    }
  } catch (error) {
    if (props.mode !== 'login') formError.value = errorMessage(error, '验证通道读取失败')
  }
}

async function requestCode(): Promise<void> {
  clearFeedback()
  const destination = validateContact()
  if (!destination || countdown.value > 0 || !selectedChannelAvailable.value) return
  pending.value = 'send-code'
  try {
    challenge.value = await requestContactVerification(channel.value, destination, purpose.value)
    challengeDestination.value = destination
    form.code = ''
    startCountdown(challenge.value.resendAfterSeconds)
    formInfo.value = `验证码已发送至 ${challenge.value.destinationMasked}`
  } catch (error) {
    formError.value = errorMessage(error, '验证码发送失败')
  } finally {
    pending.value = ''
  }
}

async function peekCode(): Promise<void> {
  clearFeedback()
  const destination = validateContact()
  if (!destination) return
  pending.value = 'peek-code'
  try {
    form.code = (await fetchDevMailbox(destination)).code
    formInfo.value = '已读取本地开发验证码。'
  } catch (error) {
    formError.value = errorMessage(error, '本地开发验证码不可用')
  } finally {
    pending.value = ''
  }
}

async function onLogin(): Promise<void> {
  clearFeedback()
  fieldError.identifier = form.identifier.trim() ? '' : '请输入邮箱或手机号'
  fieldError.password = form.password ? '' : '请输入密码'
  if (fieldError.identifier || fieldError.password) return
  pending.value = 'login'
  try {
    const account = await loginAccount(form.identifier.trim(), form.password, rememberMe.value)
    session.remember(account)
    await router.replace(account.role === 'ADMIN' && !props.next ? '/admin/resume-templates' : safeNextPath(props.next))
  } catch (error) {
    formError.value = errorMessage(error, '邮箱、手机号或密码不正确')
  } finally {
    pending.value = ''
  }
}

async function onRegister(): Promise<void> {
  clearFeedback()
  const destination = validateVerification()
  const passwordValid = validatePasswordFields()
  fieldError.agreements = acceptedAgreements.value ? '' : '请先阅读并同意用户协议和隐私政策'
  if (!destination || !passwordValid || fieldError.agreements || !challenge.value) return
  pending.value = 'register'
  try {
    const confirmed = await confirmContactVerification(challenge.value.challengeId, destination, form.code.trim())
    const account = await registerAccount({
      channel: channel.value,
      destination,
      verificationToken: confirmed.verificationToken,
      password: form.password,
      acceptedTerms: true,
      acceptedPrivacy: true,
    })
    session.remember(account)
    await router.replace(props.next ? safeNextPath(props.next) : '/dashboard')
  } catch (error) {
    formError.value = errorMessage(error, '账号创建失败')
  } finally {
    pending.value = ''
  }
}

async function onResetVerify(): Promise<void> {
  clearFeedback()
  const destination = validateVerification()
  if (!destination || !challenge.value) return
  pending.value = 'verify-reset'
  try {
    const confirmed = await confirmContactVerification(challenge.value.challengeId, destination, form.code.trim())
    verificationToken.value = confirmed.verificationToken
    resetStep.value = 'password'
    form.password = ''
    form.confirm = ''
    await nextTick()
    primaryInput.value?.focus()
  } catch (error) {
    formError.value = errorMessage(error, '验证码无效或已过期')
  } finally {
    pending.value = ''
  }
}

async function onResetPassword(): Promise<void> {
  clearFeedback()
  if (!validatePasswordFields() || !verificationToken.value) return
  pending.value = 'reset-password'
  try {
    await confirmPasswordReset({
      channel: channel.value,
      destination: normalizedDestination().value,
      verificationToken: verificationToken.value,
      newPassword: form.password,
    })
    resetStep.value = 'complete'
  } catch (error) {
    formError.value = errorMessage(error, '密码重置失败，请重新验证身份')
  } finally {
    pending.value = ''
  }
}

async function switchMode(mode: AuthLandingMode, reason?: string): Promise<void> {
  const query: Record<string, string> = { auth: mode }
  if (props.next) query.next = props.next
  if (reason) query.reason = reason
  await router.replace({ name: 'home', query })
}

function selectChannel(next: VerificationChannel): void {
  if (next === channel.value || (next === 'EMAIL' ? !emailAvailable.value : !smsAvailable.value)) return
  channel.value = next
  form.destination = ''
  form.code = ''
  challenge.value = null
  challengeDestination.value = ''
  countdown.value = 0
  clearFeedback()
  void nextTick(() => primaryInput.value?.focus())
}

function resetState(): void {
  clearFeedback()
  challenge.value = null
  challengeDestination.value = ''
  verificationToken.value = ''
  resetStep.value = 'verify'
  acceptedAgreements.value = false
  form.code = ''
  form.password = ''
  form.confirm = ''
  countdown.value = 0
  window.clearInterval(countdownTimer)
}

function onOpenChange(open: boolean): void {
  if (!open && !pending.value) emit('close')
}

watch(() => props.mode, async () => {
  resetState()
  await nextTick()
  primaryInput.value?.focus()
})

onMounted(async () => {
  await loadCapabilities()
  await nextTick()
  primaryInput.value?.focus()
})

onBeforeUnmount(() => window.clearInterval(countdownTimer))

const HIGHLIGHTS = ['AI 只给候选，你点对勾才写入', '每条经历都标注事实来源', '实时 A4 预览，一键导出 PDF']
</script>

<template>
  <DialogRoot :open="true" @update:open="onOpenChange">
    <DialogPortal>
      <DialogOverlay class="auth-overlay" />
      <DialogContent class="auth" @open-auto-focus.prevent>
        <aside class="auth__aside" aria-hidden="true">
          <div class="auth__glow" />
          <BrandMark :size="30" class="auth__brand" />
          <div class="auth__pitch">
            <h2>写一份<br />每一句都站得住的简历</h2>
            <ul>
              <li v-for="item in HIGHLIGHTS" :key="item"><Check :size="15" :stroke-width="2.6" />{{ item }}</li>
            </ul>
          </div>
          <div class="auth__art">
            <UiIllustration name="hero-resume" :size="170" class="auth__art-resume" eager />
            <UiIllustration name="hero-check" :size="86" class="auth__art-check" eager />
            <UiIllustration name="hero-sparkles" :size="70" class="auth__art-spark" eager />
          </div>
        </aside>

        <section class="auth__main">
          <DialogClose class="auth__close" aria-label="关闭" :disabled="!!pending"><X :size="18" /></DialogClose>
          <header class="auth__head">
            <span class="auth__eyebrow"><ShieldCheck :size="13" />{{ mode === 'register' ? '免费注册' : mode === 'reset' ? '账号安全' : '安全登录' }}</span>
            <DialogTitle class="auth__title">{{ title }}</DialogTitle>
            <DialogDescription class="auth__subtitle">{{ subtitle }}</DialogDescription>
          </header>

          <ol v-if="mode === 'reset'" class="auth__steps" aria-label="找回密码进度">
            <li :class="{ 'is-active': resetStep === 'verify', 'is-done': resetStep !== 'verify' }"><span><Check v-if="resetStep !== 'verify'" :size="12" :stroke-width="3" /><template v-else>1</template></span>验证身份</li>
            <li :class="{ 'is-active': resetStep === 'password', 'is-done': resetStep === 'complete' }"><span><Check v-if="resetStep === 'complete'" :size="12" :stroke-width="3" /><template v-else>2</template></span>设置新密码</li>
            <li :class="{ 'is-active': resetStep === 'complete' }"><span>3</span>完成</li>
          </ol>

          <Transition name="fade">
            <p v-if="notice" class="auth__msg auth__msg--info" role="status">{{ notice }}</p>
          </Transition>
          <Transition name="fade">
            <p v-if="formInfo" class="auth__msg auth__msg--ok" role="status">{{ formInfo }}</p>
          </Transition>
          <Transition name="fade">
            <p v-if="formError" class="auth__msg auth__msg--bad" role="alert">{{ formError }}</p>
          </Transition>

          <Transition name="rise" mode="out-in">
            <form v-if="mode === 'login'" :key="formKey" class="auth__form" novalidate autocomplete="on" @submit.prevent="onLogin">
              <label class="auth__label" for="auth-identifier">邮箱或手机号</label>
              <div class="auth-input" :class="{ 'has-error': fieldError.identifier }">
                <Mail :size="17" />
                <input id="auth-identifier" ref="primaryInput" v-model="form.identifier" name="username" type="text" placeholder="name@example.com 或手机号" autocomplete="username" :disabled="!!pending" :aria-invalid="!!fieldError.identifier" />
              </div>
              <small v-if="fieldError.identifier" class="auth__field-error">{{ fieldError.identifier }}</small>
              <label class="auth__label" for="auth-password">密码</label>
              <div class="auth-input" :class="{ 'has-error': fieldError.password }">
                <LockKeyhole :size="17" />
                <input id="auth-password" v-model="form.password" name="password" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" :disabled="!!pending" :aria-invalid="!!fieldError.password" />
                <button type="button" class="auth-input__action" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                  <EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" />
                </button>
              </div>
              <small v-if="fieldError.password" class="auth__field-error">{{ fieldError.password }}</small>
              <div class="auth__options">
                <UiCheckbox v-model="rememberMe" :disabled="!!pending" label="保持登录" />
                <button type="button" class="auth__link" @click="switchMode('reset')">忘记密码？</button>
              </div>
              <UiButton type="submit" size="lg" block :pending="pending === 'login'">{{ pending === 'login' ? '正在登录…' : '登录' }}</UiButton>
            </form>

            <form v-else-if="mode === 'register'" :key="formKey" class="auth__form" novalidate autocomplete="on" @submit.prevent="onRegister">
              <div class="auth__channels" role="group" aria-label="注册方式">
                <button type="button" :class="{ 'is-active': channel === 'EMAIL' }" :disabled="!emailAvailable || !!pending" @click="selectChannel('EMAIL')"><Mail :size="15" />邮箱注册</button>
                <button type="button" :class="{ 'is-active': channel === 'SMS' }" :disabled="!smsAvailable || !!pending" @click="selectChannel('SMS')"><Phone :size="15" />手机注册</button>
              </div>
              <template v-if="capabilities?.registrationEnabled">
                <label class="auth__label" for="auth-contact">{{ contactLabel }}</label>
                <div class="auth-input" :class="{ 'has-error': fieldError.destination }">
                  <component :is="channel === 'EMAIL' ? Mail : Phone" :size="17" />
                  <input id="auth-contact" ref="primaryInput" v-model="form.destination" :name="channel === 'EMAIL' ? 'email' : 'tel'" :type="channel === 'EMAIL' ? 'email' : 'tel'" :inputmode="channel === 'EMAIL' ? 'email' : 'tel'" :placeholder="contactPlaceholder" :autocomplete="channel === 'EMAIL' ? 'email' : 'tel'" :disabled="!!pending" />
                </div>
                <small v-if="fieldError.destination" class="auth__field-error">{{ fieldError.destination }}</small>
                <label class="auth__label" for="auth-code">验证码</label>
                <div class="auth-input auth-input--code" :class="{ 'has-error': fieldError.code }">
                  <KeyRound :size="17" />
                  <input id="auth-code" v-model="form.code" name="one-time-code" inputmode="numeric" maxlength="6" placeholder="6 位验证码" autocomplete="one-time-code" :disabled="!!pending" />
                  <button type="button" class="auth-input__send" :disabled="countdown > 0 || !!pending || !selectedChannelAvailable" @click="requestCode">{{ pending === 'send-code' ? '发送中…' : sendLabel }}</button>
                </div>
                <small v-if="fieldError.code" class="auth__field-error">{{ fieldError.code }}</small>
                <button v-if="devMode && challenge" class="auth__link auth__dev" type="button" :disabled="!!pending" @click="peekCode">{{ pending === 'peek-code' ? '读取中…' : '读取本地验证码' }}</button>
                <label class="auth__label" for="auth-new-password">设置密码</label>
                <div class="auth-input" :class="{ 'has-error': fieldError.password }">
                  <LockKeyhole :size="17" />
                  <input id="auth-new-password" v-model="form.password" name="new-password" :type="showPassword ? 'text' : 'password'" placeholder="8–72 位，建议包含字母、数字和符号" autocomplete="new-password" :disabled="!!pending" />
                  <button type="button" class="auth-input__action" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" /></button>
                </div>
                <small v-if="fieldError.password" class="auth__field-error">{{ fieldError.password }}</small>
                <div class="auth__strength" :data-level="passwordStrength" :aria-label="`密码强度：${passwordStrengthText}`">
                  <span v-for="n in 4" :key="n" :class="{ 'is-on': n <= passwordStrength }" /><em>{{ passwordStrengthText }}</em>
                </div>
                <label class="auth__label" for="auth-confirm">确认密码</label>
                <div class="auth-input" :class="{ 'has-error': fieldError.confirm }">
                  <LockKeyhole :size="17" />
                  <input id="auth-confirm" v-model="form.confirm" name="confirm-password" type="password" placeholder="再次输入密码" autocomplete="new-password" :disabled="!!pending" />
                </div>
                <small v-if="fieldError.confirm" class="auth__field-error">{{ fieldError.confirm }}</small>
                <UiCheckbox v-model="acceptedAgreements" class="auth__agree" :disabled="!!pending">
                  我已阅读并同意 <RouterLink to="/terms" target="_blank">《用户协议》</RouterLink>和<RouterLink to="/privacy" target="_blank">《隐私政策》</RouterLink>
                </UiCheckbox>
                <small v-if="fieldError.agreements" class="auth__field-error">{{ fieldError.agreements }}</small>
                <UiButton type="submit" size="lg" block class="auth__submit" :pending="pending === 'register'" :disabled="!selectedChannelAvailable">{{ pending === 'register' ? '正在创建…' : '创建账号' }}</UiButton>
              </template>
              <p v-else class="auth__msg auth__msg--info"><ShieldCheck :size="16" />注册暂未开放，请稍后再来。</p>
            </form>

            <form v-else-if="resetStep === 'verify'" :key="formKey" class="auth__form" novalidate autocomplete="on" @submit.prevent="onResetVerify">
              <div class="auth__channels" role="group" aria-label="找回方式">
                <button type="button" :class="{ 'is-active': channel === 'EMAIL' }" :disabled="!emailAvailable || !!pending" @click="selectChannel('EMAIL')"><Mail :size="15" />邮箱找回</button>
                <button type="button" :class="{ 'is-active': channel === 'SMS' }" :disabled="!smsAvailable || !!pending" @click="selectChannel('SMS')"><Phone :size="15" />手机号找回</button>
              </div>
              <label class="auth__label" for="auth-reset-contact">注册时使用的{{ contactLabel }}</label>
              <div class="auth-input" :class="{ 'has-error': fieldError.destination }">
                <component :is="channel === 'EMAIL' ? Mail : Phone" :size="17" />
                <input id="auth-reset-contact" ref="primaryInput" v-model="form.destination" :type="channel === 'EMAIL' ? 'email' : 'tel'" :placeholder="contactPlaceholder" :autocomplete="channel === 'EMAIL' ? 'email' : 'tel'" :disabled="!!pending" />
              </div>
              <small v-if="fieldError.destination" class="auth__field-error">{{ fieldError.destination }}</small>
              <label class="auth__label" for="auth-reset-code">验证码</label>
              <div class="auth-input auth-input--code" :class="{ 'has-error': fieldError.code }">
                <KeyRound :size="17" />
                <input id="auth-reset-code" v-model="form.code" inputmode="numeric" maxlength="6" placeholder="6 位验证码" autocomplete="one-time-code" :disabled="!!pending" />
                <button type="button" class="auth-input__send" :disabled="countdown > 0 || !!pending || !selectedChannelAvailable" @click="requestCode">{{ pending === 'send-code' ? '发送中…' : sendLabel }}</button>
              </div>
              <small v-if="fieldError.code" class="auth__field-error">{{ fieldError.code }}</small>
              <button v-if="devMode && challenge" class="auth__link auth__dev" type="button" :disabled="!!pending" @click="peekCode">{{ pending === 'peek-code' ? '读取中…' : '读取本地验证码' }}</button>
              <p class="auth__tip"><ShieldCheck :size="14" />验证码 {{ capabilities?.codeTtlMinutes ?? 10 }} 分钟内有效，请勿转发给他人</p>
              <UiButton type="submit" size="lg" block class="auth__submit" :pending="pending === 'verify-reset'">{{ pending === 'verify-reset' ? '正在验证…' : '下一步' }}</UiButton>
            </form>

            <form v-else-if="resetStep === 'password'" :key="formKey" class="auth__form" novalidate autocomplete="on" @submit.prevent="onResetPassword">
              <button class="auth__link auth__back" type="button" :disabled="!!pending" @click="resetStep = 'verify'"><ArrowLeft :size="15" />重新验证身份</button>
              <label class="auth__label" for="auth-reset-password">新密码</label>
              <div class="auth-input" :class="{ 'has-error': fieldError.password }">
                <LockKeyhole :size="17" />
                <input id="auth-reset-password" ref="primaryInput" v-model="form.password" name="new-password" :type="showPassword ? 'text' : 'password'" placeholder="8–72 位，建议包含字母、数字和符号" autocomplete="new-password" :disabled="!!pending" />
                <button type="button" class="auth-input__action" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" /></button>
              </div>
              <small v-if="fieldError.password" class="auth__field-error">{{ fieldError.password }}</small>
              <div class="auth__strength" :data-level="passwordStrength"><span v-for="n in 4" :key="n" :class="{ 'is-on': n <= passwordStrength }" /><em>{{ passwordStrengthText }}</em></div>
              <label class="auth__label" for="auth-reset-confirm">确认新密码</label>
              <div class="auth-input" :class="{ 'has-error': fieldError.confirm }">
                <LockKeyhole :size="17" />
                <input id="auth-reset-confirm" v-model="form.confirm" type="password" placeholder="再次输入新密码" autocomplete="new-password" :disabled="!!pending" />
              </div>
              <small v-if="fieldError.confirm" class="auth__field-error">{{ fieldError.confirm }}</small>
              <UiButton type="submit" size="lg" block class="auth__submit" :pending="pending === 'reset-password'">{{ pending === 'reset-password' ? '正在重置…' : '重置密码' }}</UiButton>
            </form>

            <div v-else :key="formKey" class="auth__complete">
              <span class="auth__complete-icon"><CheckCircle2 :size="34" /></span>
              <p>新密码已生效，其他设备上的登录已全部退出。</p>
              <UiButton size="lg" block @click="switchMode('login', 'password_reset')">返回登录</UiButton>
            </div>
          </Transition>

          <p v-if="mode === 'login'" class="auth__switch">还没有账号？<button type="button" class="auth__link" @click="switchMode('register')">免费注册</button></p>
          <p v-else-if="mode === 'register'" class="auth__switch">已有账号？<button type="button" class="auth__link" @click="switchMode('login')">直接登录</button></p>
          <p v-else-if="resetStep !== 'complete'" class="auth__switch">想起密码了？<button type="button" class="auth__link" @click="switchMode('login')">返回登录</button></p>
          <p class="auth__privacy"><ShieldCheck :size="13" />登录信息加密传输，不会发送给 AI 模型</p>
        </section>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style>
.auth-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-dialog);
  background: var(--scrim);
  backdrop-filter: blur(6px);
  animation: jp-fade-in var(--dur-slow) var(--ease-standard);
}

.auth {
  position: fixed;
  z-index: var(--z-dialog);
  top: 50%;
  left: 50%;
  width: min(880px, calc(100vw - 32px));
  max-height: calc(100svh - 32px);
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-2xl);
  background: var(--surface-overlay);
  box-shadow: var(--shadow-lg);
  transform: translate(-50%, -50%);
  animation: ui-dialog-in var(--dur-slow) var(--ease-out);
  outline: none;
}

.auth__aside {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 28px;
  color: #fff;
  background: linear-gradient(160deg, #6f6af0 0%, #4a44d9 46%, #2b2a74 100%);
}

.auth__glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(60% 50% at 80% 90%, rgba(250, 140, 85, 0.45), transparent 70%),
    radial-gradient(50% 40% at 10% 10%, rgba(255, 255, 255, 0.18), transparent 70%);
}

.auth__brand {
  position: relative;
  color: #fff;
}

.auth__brand .brand__word em {
  background: linear-gradient(120deg, #ffd9c2, #fff);
  -webkit-background-clip: text;
  background-clip: text;
}

.auth__pitch {
  position: relative;
  margin-top: 40px;
}

.auth__pitch h2 {
  color: #fff;
  font-size: 24px;
  line-height: 34px;
  font-weight: 750;
  letter-spacing: -0.02em;
}

.auth__pitch ul {
  list-style: none;
  display: grid;
  gap: 10px;
  margin-top: 20px;
}

.auth__pitch li {
  display: flex;
  align-items: center;
  gap: 9px;
  color: rgba(255, 255, 255, 0.86);
  font-size: var(--fs-sm);
}

.auth__pitch li svg {
  flex-shrink: 0;
  padding: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  color: #ffd9c2;
}

.auth__art {
  position: relative;
  flex: 1;
  min-height: 190px;
}

.auth__art-resume {
  position: absolute;
  bottom: -26px;
  left: 50%;
  width: 170px;
  height: auto;
  transform: translateX(-50%) rotate(-6deg);
  filter: drop-shadow(0 24px 30px rgba(10, 8, 40, 0.35));
  animation: jp-float 7s ease-in-out infinite;
  --float-rotate: -6deg;
}

.auth__art-check {
  position: absolute;
  bottom: 70px;
  right: 18px;
  width: 86px;
  height: auto;
  animation: jp-float 6s ease-in-out -2s infinite;
  --float-distance: -12px;
}

.auth__art-spark {
  position: absolute;
  bottom: 150px;
  left: 18px;
  width: 64px;
  height: auto;
  animation: jp-float 5s ease-in-out -1s infinite;
}

.auth__main {
  position: relative;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 40px 24px;
}

.auth__close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  transition: background-color var(--dur-fast), color var(--dur-fast), transform var(--dur-base) var(--ease-out);
}

.auth__close:hover {
  background: var(--surface-3);
  color: var(--text-primary);
  transform: rotate(90deg);
}

.auth__head {
  padding-right: 32px;
}

.auth__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  font-size: var(--fs-xs);
  font-weight: 650;
}

.auth__title {
  margin: 12px 0 0;
  font-size: 26px;
  line-height: 34px;
  font-weight: 750;
  letter-spacing: -0.02em;
}

.auth__subtitle {
  margin: 4px 0 20px;
  color: var(--text-secondary);
  font-size: var(--fs-body);
}

.auth__msg {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  padding: 9px 12px;
  border-radius: var(--radius-md);
  font-size: var(--fs-sm);
  line-height: var(--lh-sm);
}

.auth__msg--info {
  background: var(--color-info-soft);
  color: var(--color-info-text);
}

.auth__msg--ok {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.auth__msg--bad {
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
}

.auth__form {
  display: grid;
}

.auth__label {
  margin: 12px 0 6px;
  color: var(--text-primary);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.auth__field-error {
  margin-top: 5px;
  color: var(--color-danger-text);
  font-size: var(--fs-xs);
}

.auth-input {
  height: var(--control-lg);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 6px 0 13px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-tertiary);
  box-shadow: var(--shadow-xs);
  transition: border-color var(--dur-fast), box-shadow var(--dur-base) var(--ease-out), color var(--dur-fast);
}

.auth-input:hover {
  border-color: var(--border-strong);
}

.auth-input:focus-within {
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
  color: var(--color-primary-text);
}

.auth-input.has-error {
  border-color: var(--color-danger);
}

.auth-input svg {
  flex-shrink: 0;
}

.auth-input input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--fs-body);
}

.auth-input input:focus-visible {
  box-shadow: none;
}

.auth-input__action {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
}

.auth-input__action:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}

.auth-input__send {
  height: 32px;
  padding: 0 12px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  font-size: var(--fs-xs);
  font-weight: 650;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  transition: background-color var(--dur-fast);
}

.auth-input__send:hover:not(:disabled) {
  background: var(--color-primary-soft-hover);
}

.auth-input__send:disabled {
  opacity: 0.55;
}

.auth__channels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3px;
  padding: 3px;
  border-radius: var(--radius-md);
  background: var(--surface-3);
}

.auth__channels button {
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: calc(var(--radius-md) - 3px);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 600;
  transition: background-color var(--dur-base) var(--ease-out), color var(--dur-fast), box-shadow var(--dur-base);
}

.auth__channels button.is-active {
  background: var(--surface-1);
  color: var(--text-primary);
  box-shadow: var(--shadow-xs), 0 0 0 1px var(--border-subtle);
}

.auth__channels button:disabled:not(.is-active) {
  opacity: 0.45;
}

.auth__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 0 18px;
}

.auth__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-primary-text);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.auth__link:hover {
  color: var(--color-primary-hover);
}

.auth__dev {
  justify-self: end;
  margin-top: 6px;
  font-size: var(--fs-xs);
}

.auth__back {
  justify-self: start;
  margin-bottom: 4px;
}

.auth__strength {
  display: grid;
  grid-template-columns: repeat(4, 1fr) auto;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}

.auth__strength span {
  height: 4px;
  border-radius: 999px;
  background: var(--surface-3);
  transition: background-color var(--dur-base) var(--ease-out);
}

.auth__strength[data-level='1'] span.is-on {
  background: var(--color-danger);
}

.auth__strength[data-level='2'] span.is-on {
  background: var(--color-warning);
}

.auth__strength[data-level='3'] span.is-on,
.auth__strength[data-level='4'] span.is-on {
  background: var(--color-success);
}

.auth__strength em {
  margin-left: 8px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-style: normal;
}

.auth__agree {
  margin-top: 16px;
}

.auth__agree .ui-checkbox__label {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.auth__submit {
  margin-top: 18px;
}

.auth__tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.auth__steps {
  list-style: none;
  display: flex;
  gap: 18px;
  margin: 0 0 18px;
}

.auth__steps li {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
}

.auth__steps span {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-3);
  font-size: 11px;
  font-weight: 700;
}

.auth__steps li.is-active,
.auth__steps li.is-done {
  color: var(--text-primary);
  font-weight: 600;
}

.auth__steps li.is-active span,
.auth__steps li.is-done span {
  background: var(--color-primary);
  color: #fff;
}

.auth__complete {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 12px 0;
  text-align: center;
}

.auth__complete-icon {
  width: 68px;
  height: 68px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-success-soft);
  color: var(--color-success);
  animation: jp-pop-in var(--dur-slow) var(--ease-spring);
}

.auth__complete p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.auth__switch {
  margin-top: 18px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  text-align: center;
}

.auth__privacy {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
  color: var(--text-tertiary);
  font-size: 11px;
}

.auth__privacy svg {
  color: var(--color-success);
}

@media (max-width: 760px) {
  .auth {
    top: auto;
    bottom: 0;
    left: 0;
    width: 100vw;
    max-height: 94svh;
    grid-template-columns: 1fr;
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    transform: none;
    animation: ui-sheet-in var(--dur-slow) var(--ease-out);
  }

  .auth__aside {
    display: none;
  }

  .auth__main {
    padding: 24px 20px max(20px, env(safe-area-inset-bottom));
  }
}
</style>
