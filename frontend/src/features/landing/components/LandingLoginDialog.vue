<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  Check,
  Eye,
  EyeOff,
  KeyRound,
  LockKeyhole,
  Mail,
  Phone,
  ShieldCheck,
  X,
} from 'lucide-vue-next'
import brandLogo from '@/assets/jobproof-ai-logo.png'
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
import { useSessionStore } from '@/stores/session'
import type {
  VerificationCapabilities,
  VerificationChallenge,
  VerificationChannel,
} from '@/features/identity/types'
import { errorMessage } from '@/shared/api/types'
import { normalizeEmail, validateCode, validatePassword } from '@/shared/lib/validation'

type ResetStep = 'verify' | 'password' | 'complete'

const props = defineProps<{ mode: AuthLandingMode; next?: string; reason?: string }>()
const emit = defineEmits<{ close: [] }>()
const router = useRouter()
const dialogRef = ref<HTMLElement | null>(null)
const primaryInput = ref<HTMLInputElement | null>(null)
const previousFocus = ref<HTMLElement | null>(null)
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

const form = reactive({
  identifier: '',
  destination: '',
  code: '',
  password: '',
  confirm: '',
})
const fieldError = reactive({
  identifier: '',
  destination: '',
  code: '',
  password: '',
  confirm: '',
  agreements: '',
})

const title = computed(() => {
  if (props.mode === 'login') return '欢迎回来'
  if (props.mode === 'register') return '开启你的求职成长空间'
  if (resetStep.value === 'complete') return '密码已重置'
  return '找回账号密码'
})
const subtitle = computed(() => {
  if (props.mode === 'login') return '登录后继续完成你的求职准备'
  if (props.mode === 'register') return '保存简历、求职资料与岗位信息'
  if (resetStep.value === 'verify') return '先验证账号联系方式，再设置新密码'
  if (resetStep.value === 'password') return '身份验证已通过，请设置新的登录密码'
  return '现在可以使用新密码重新登录'
})
const purpose = computed(() => props.mode === 'register' ? 'REGISTER' : 'LOGIN_RECOVERY')
const emailAvailable = computed(() => capabilities.value?.emailEnabled ?? false)
const smsAvailable = computed(() => capabilities.value?.smsEnabled ?? false)
const selectedChannelAvailable = computed(() => channel.value === 'EMAIL' ? emailAvailable.value : smsAvailable.value)
const contactLabel = computed(() => channel.value === 'EMAIL' ? '邮箱' : '中国大陆手机号')
const contactPlaceholder = computed(() => channel.value === 'EMAIL' ? '请输入常用邮箱' : '请输入 11 位手机号')
const sendLabel = computed(() => countdown.value > 0 ? `${countdown.value}s 后重发` : challenge.value ? '重新获取' : '获取验证码')
const passwordStrength = computed(() => {
  const password = form.password
  let score = 0
  if (password.length >= 8) score++
  if (/[A-Za-z]/.test(password) && /\d/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++
  if (password.length >= 12) score++
  return score
})
const passwordStrengthText = computed(() => ['未设置', '较弱', '一般', '良好', '强'][passwordStrength.value])
const notice = computed(() => {
  if (props.mode !== 'login') return ''
  if (props.reason === 'unauthenticated') return '登录后即可继续刚才的操作。'
  if (props.reason === 'session_expired') return '会话已失效，请重新登录后继续。'
  if (props.reason === 'password_changed') return '密码已更改，请使用新密码登录。'
  if (props.reason === 'password_reset') return '密码重置成功，请使用新密码登录。'
  if (props.reason === 'logged_out') return '你已安全退出当前账号。'
  return ''
})

function clearFeedback() {
  formError.value = ''
  formInfo.value = ''
  Object.keys(fieldError).forEach((key) => { fieldError[key as keyof typeof fieldError] = '' })
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
  const destination = normalizedDestination().value
  fieldError.password = validatePassword(form.password, destination) ?? ''
  fieldError.confirm = form.confirm === form.password ? '' : '两次密码不一致'
  return !fieldError.password && !fieldError.confirm
}

function validateVerification(): string | null {
  const destination = validateContact()
  fieldError.code = validateCode(form.code) ?? ''
  if (!challenge.value || destination !== challengeDestination.value) {
    fieldError.code = '请先获取当前联系方式的验证码'
  }
  return destination && !fieldError.code ? destination : null
}

function startCountdown(seconds: number) {
  window.clearInterval(countdownTimer)
  countdown.value = Math.max(0, Math.round(seconds))
  countdownTimer = window.setInterval(() => {
    countdown.value = Math.max(0, countdown.value - 1)
    if (countdown.value === 0) window.clearInterval(countdownTimer)
  }, 1000)
}

async function loadCapabilities() {
  try {
    capabilities.value = await fetchVerificationCapabilities()
    if (!emailAvailable.value && smsAvailable.value) channel.value = 'SMS'
    if (!emailAvailable.value && !smsAvailable.value) {
      formError.value = '邮箱和短信验证通道暂时不可用，请稍后重试。'
    }
  } catch (error) {
    formError.value = errorMessage(error, '验证通道读取失败')
  }
}

async function requestCode() {
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

async function peekCode() {
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

async function onLogin() {
  clearFeedback()
  fieldError.identifier = form.identifier.trim() ? '' : '邮箱或手机号不能为空'
  fieldError.password = form.password ? '' : '密码不能为空'
  if (fieldError.identifier || fieldError.password) return
  pending.value = 'login'
  try {
    const account = await loginAccount(form.identifier.trim(), form.password, rememberMe.value)
    useSessionStore().remember(account)
    await router.replace(safeNextPath(props.next))
  } catch (error) {
    formError.value = errorMessage(error, '邮箱、手机号或密码不正确')
  } finally {
    pending.value = ''
  }
}

async function onRegister() {
  clearFeedback()
  const destination = validateVerification()
  const passwordValid = validatePasswordFields()
  fieldError.agreements = acceptedAgreements.value ? '' : '请先同意用户协议和隐私政策'
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
    useSessionStore().remember(account)
    await router.replace(props.next ? safeNextPath(props.next) : '/onboarding')
  } catch (error) {
    formError.value = errorMessage(error, '账号创建失败')
  } finally {
    pending.value = ''
  }
}

async function onResetVerify() {
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

async function onResetPassword() {
  clearFeedback()
  if (!validatePasswordFields() || !verificationToken.value) return
  const destination = normalizedDestination().value
  pending.value = 'reset-password'
  try {
    await confirmPasswordReset({
      channel: channel.value,
      destination,
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

async function switchMode(mode: AuthLandingMode, reason?: string) {
  const query: Record<string, string> = { auth: mode }
  if (props.next) query.next = props.next
  if (reason) query.reason = reason
  await router.replace({ name: 'home', query })
}

function selectChannel(next: VerificationChannel) {
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

function onDialogKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key !== 'Tab' || !dialogRef.value) return
  const focusable = Array.from(dialogRef.value.querySelectorAll<HTMLElement>(
    'button:not(:disabled), input:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])',
  ))
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function resetState() {
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

watch(() => props.mode, async () => {
  resetState()
  await nextTick()
  primaryInput.value?.focus()
})

onMounted(async () => {
  previousFocus.value = document.activeElement as HTMLElement | null
  await loadCapabilities()
  await nextTick()
  primaryInput.value?.focus()
})

onBeforeUnmount(() => {
  window.clearInterval(countdownTimer)
  previousFocus.value?.focus()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="landing-auth" appear>
      <div class="landing-auth" role="presentation" @click.self="emit('close')">
        <section
          ref="dialogRef"
          class="landing-auth__dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="landing-auth-title"
          @keydown="onDialogKeydown"
        >
          <button class="landing-auth__close" type="button" aria-label="关闭身份验证" @click="emit('close')"><X :size="21" /></button>

          <header class="landing-auth__header">
            <span class="landing-auth__brand"><img :src="brandLogo" alt="JobProof AI" width="124" height="27"></span>
            <span class="landing-auth__eyebrow"><ShieldCheck :size="14" />{{ mode === 'register' ? '创建账号' : mode === 'reset' ? '账号安全' : '安全登录' }}</span>
            <h1 id="landing-auth-title">{{ title }}</h1>
            <p>{{ subtitle }}</p>
          </header>

          <ol v-if="mode === 'reset'" class="landing-auth__steps" aria-label="找回密码进度">
            <li :class="{ active: resetStep === 'verify', done: resetStep !== 'verify' }"><span>{{ resetStep === 'verify' ? '1' : '✓' }}</span>验证身份</li>
            <li :class="{ active: resetStep === 'password', done: resetStep === 'complete' }"><span>{{ resetStep === 'complete' ? '✓' : '2' }}</span>设置新密码</li>
            <li :class="{ active: resetStep === 'complete' }"><span>3</span>完成</li>
          </ol>

          <p v-if="notice" class="landing-auth__notice" role="status">{{ notice }}</p>
          <p v-if="formInfo" class="landing-auth__notice is-success" role="status">{{ formInfo }}</p>
          <p v-if="formError" class="landing-auth__error" role="alert">{{ formError }}</p>

          <form v-if="mode === 'login'" novalidate autocomplete="on" @submit.prevent="onLogin">
            <label for="landing-login-identifier">邮箱或手机号</label>
            <div class="landing-auth__input" :class="{ 'has-error': fieldError.identifier }"><Mail :size="18" /><input id="landing-login-identifier" ref="primaryInput" v-model="form.identifier" name="username" type="text" placeholder="请输入邮箱或手机号" autocomplete="username" :disabled="!!pending"></div>
            <small v-if="fieldError.identifier">{{ fieldError.identifier }}</small>
            <label for="landing-login-password">密码</label>
            <div class="landing-auth__input" :class="{ 'has-error': fieldError.password }"><LockKeyhole :size="18" /><input id="landing-login-password" v-model="form.password" name="password" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" :disabled="!!pending"><button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" /><Eye v-else :size="18" /></button></div>
            <small v-if="fieldError.password">{{ fieldError.password }}</small>
            <div class="landing-auth__options"><label><input v-model="rememberMe" type="checkbox" :disabled="!!pending"> 保持登录</label><button type="button" @click="switchMode('reset')">忘记密码？</button></div>
            <button class="landing-auth__submit" type="submit" :disabled="!!pending">{{ pending === 'login' ? '正在登录…' : '登录' }}</button>
          </form>

          <form v-else-if="mode === 'register'" novalidate autocomplete="on" @submit.prevent="onRegister">
            <div class="landing-auth__segments" aria-label="注册方式">
              <button type="button" :class="{ active: channel === 'EMAIL' }" :disabled="!emailAvailable || !!pending" @click="selectChannel('EMAIL')"><Mail :size="16" />邮箱注册</button>
              <button type="button" :class="{ active: channel === 'SMS' }" :disabled="!smsAvailable || !!pending" @click="selectChannel('SMS')"><Phone :size="16" />手机注册</button>
            </div>
            <template v-if="capabilities?.registrationEnabled">
              <label for="landing-register-contact">{{ contactLabel }}</label>
              <div class="landing-auth__input" :class="{ 'has-error': fieldError.destination }"><component :is="channel === 'EMAIL' ? Mail : Phone" :size="18" /><input id="landing-register-contact" ref="primaryInput" v-model="form.destination" :name="channel === 'EMAIL' ? 'email' : 'tel'" :type="channel === 'EMAIL' ? 'email' : 'tel'" :inputmode="channel === 'EMAIL' ? 'email' : 'tel'" :placeholder="contactPlaceholder" :autocomplete="channel === 'EMAIL' ? 'email' : 'tel'" :disabled="!!pending"></div>
              <small v-if="fieldError.destination">{{ fieldError.destination }}</small>
              <label for="landing-register-code">{{ contactLabel }}验证码</label>
              <div class="landing-auth__input landing-auth__code" :class="{ 'has-error': fieldError.code }"><KeyRound :size="18" /><input id="landing-register-code" v-model="form.code" name="one-time-code" inputmode="numeric" maxlength="6" placeholder="请输入 6 位验证码" autocomplete="one-time-code" :disabled="!!pending"><button type="button" :disabled="countdown > 0 || !!pending || !selectedChannelAvailable" @click="requestCode">{{ pending === 'send-code' ? '发送中…' : sendLabel }}</button></div>
              <small v-if="fieldError.code">{{ fieldError.code }}</small>
              <button v-if="devMode && challenge" class="landing-auth__dev-code" type="button" :disabled="!!pending" @click="peekCode">{{ pending === 'peek-code' ? '读取中…' : '读取本地验证码' }}</button>
              <label for="landing-register-password">设置密码</label>
              <div class="landing-auth__input" :class="{ 'has-error': fieldError.password }"><LockKeyhole :size="18" /><input id="landing-register-password" v-model="form.password" name="new-password" :type="showPassword ? 'text' : 'password'" placeholder="8–72 位，建议包含字母、数字和符号" autocomplete="new-password" :disabled="!!pending"><button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" /><Eye v-else :size="18" /></button></div>
              <small v-if="fieldError.password">{{ fieldError.password }}</small>
              <div class="landing-auth__strength" :aria-label="`密码强度：${passwordStrengthText}`"><span v-for="n in 4" :key="n" :class="{ filled: n <= passwordStrength }" /><em>密码强度：{{ passwordStrengthText }}</em></div>
              <label for="landing-register-confirm">确认密码</label>
              <div class="landing-auth__input" :class="{ 'has-error': fieldError.confirm }"><LockKeyhole :size="18" /><input id="landing-register-confirm" v-model="form.confirm" name="confirm-password" type="password" placeholder="再次输入密码" autocomplete="new-password" :disabled="!!pending"></div>
              <small v-if="fieldError.confirm">{{ fieldError.confirm }}</small>
              <label class="landing-auth__agreement"><input v-model="acceptedAgreements" type="checkbox" :disabled="!!pending"><span>我已阅读并同意 <RouterLink to="/terms" target="_blank">《用户协议》</RouterLink> 和 <RouterLink to="/privacy" target="_blank">《隐私政策》</RouterLink></span></label>
              <small v-if="fieldError.agreements">{{ fieldError.agreements }}</small>
              <button class="landing-auth__submit has-top-space" type="submit" :disabled="!!pending || !selectedChannelAvailable">{{ pending === 'register' ? '正在创建…' : '创建账号' }}</button>
            </template>
            <p v-else class="landing-auth__review"><ShieldCheck :size="18" />注册条款正在等待业务复核，当前暂不开放正式注册。</p>
          </form>

          <form v-else-if="resetStep === 'verify'" novalidate autocomplete="on" @submit.prevent="onResetVerify">
            <div class="landing-auth__segments" aria-label="找回方式">
              <button type="button" :class="{ active: channel === 'EMAIL' }" :disabled="!emailAvailable || !!pending" @click="selectChannel('EMAIL')"><Mail :size="16" />邮箱找回</button>
              <button type="button" :class="{ active: channel === 'SMS' }" :disabled="!smsAvailable || !!pending" @click="selectChannel('SMS')"><Phone :size="16" />手机号找回</button>
            </div>
            <label for="landing-reset-contact">注册{{ contactLabel }}</label>
            <div class="landing-auth__input" :class="{ 'has-error': fieldError.destination }"><component :is="channel === 'EMAIL' ? Mail : Phone" :size="18" /><input id="landing-reset-contact" ref="primaryInput" v-model="form.destination" :type="channel === 'EMAIL' ? 'email' : 'tel'" :placeholder="contactPlaceholder" :autocomplete="channel === 'EMAIL' ? 'email' : 'tel'" :disabled="!!pending"></div>
            <small v-if="fieldError.destination">{{ fieldError.destination }}</small>
            <label for="landing-reset-code">{{ contactLabel }}验证码</label>
            <div class="landing-auth__input landing-auth__code" :class="{ 'has-error': fieldError.code }"><KeyRound :size="18" /><input id="landing-reset-code" v-model="form.code" inputmode="numeric" maxlength="6" placeholder="请输入 6 位验证码" autocomplete="one-time-code" :disabled="!!pending"><button type="button" :disabled="countdown > 0 || !!pending || !selectedChannelAvailable" @click="requestCode">{{ pending === 'send-code' ? '发送中…' : sendLabel }}</button></div>
            <small v-if="fieldError.code">{{ fieldError.code }}</small>
            <button v-if="devMode && challenge" class="landing-auth__dev-code" type="button" :disabled="!!pending" @click="peekCode">{{ pending === 'peek-code' ? '读取中…' : '读取本地验证码' }}</button>
            <p class="landing-auth__tip"><ShieldCheck :size="15" />验证码 {{ capabilities?.codeTtlMinutes ?? 10 }} 分钟内有效，请勿转发给他人</p>
            <button class="landing-auth__submit has-top-space" type="submit" :disabled="!!pending">{{ pending === 'verify-reset' ? '正在验证…' : '下一步' }}</button>
          </form>

          <form v-else-if="resetStep === 'password'" novalidate autocomplete="on" @submit.prevent="onResetPassword">
            <button class="landing-auth__back" type="button" :disabled="!!pending" @click="resetStep = 'verify'"><ArrowLeft :size="16" />重新验证身份</button>
            <label for="landing-reset-password">新密码</label>
            <div class="landing-auth__input" :class="{ 'has-error': fieldError.password }"><LockKeyhole :size="18" /><input id="landing-reset-password" ref="primaryInput" v-model="form.password" name="new-password" :type="showPassword ? 'text' : 'password'" placeholder="8–72 位，建议包含字母、数字和符号" autocomplete="new-password" :disabled="!!pending"><button type="button" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" /><Eye v-else :size="18" /></button></div>
            <small v-if="fieldError.password">{{ fieldError.password }}</small>
            <div class="landing-auth__strength"><span v-for="n in 4" :key="n" :class="{ filled: n <= passwordStrength }" /><em>密码强度：{{ passwordStrengthText }}</em></div>
            <label for="landing-reset-confirm">确认新密码</label>
            <div class="landing-auth__input" :class="{ 'has-error': fieldError.confirm }"><LockKeyhole :size="18" /><input id="landing-reset-confirm" v-model="form.confirm" type="password" placeholder="再次输入新密码" autocomplete="new-password" :disabled="!!pending"></div>
            <small v-if="fieldError.confirm">{{ fieldError.confirm }}</small>
            <button class="landing-auth__submit has-top-space" type="submit" :disabled="!!pending">{{ pending === 'reset-password' ? '正在重置…' : '重置密码' }}</button>
          </form>

          <div v-else class="landing-auth__complete">
            <span><Check :size="30" /></span>
            <p>新密码已生效，旧登录会话已经失效。</p>
            <button class="landing-auth__submit" type="button" @click="switchMode('login', 'password_reset')">返回登录</button>
          </div>

          <p v-if="mode === 'login'" class="landing-auth__switch">还没有账号？ <button type="button" @click="switchMode('register')">免费创建</button></p>
          <p v-else-if="mode === 'register'" class="landing-auth__switch">已有账号？ <button type="button" @click="switchMode('login')">返回登录</button></p>
          <p v-else-if="resetStep !== 'complete'" class="landing-auth__switch">想起密码了？ <button type="button" @click="switchMode('login')">返回登录</button></p>
          <p class="landing-auth__privacy"><ShieldCheck :size="14" />身份验证信息加密传输，不会发送给 AI 模型</p>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.landing-auth { position: fixed; inset: 0; z-index: 300; display: grid; place-items: center; padding: 24px; background: rgba(14, 31, 63, .52); backdrop-filter: blur(12px); }
.landing-auth__dialog { position: relative; width: min(570px, 100%); max-height: calc(100svh - 48px); overflow: auto; padding: 30px 38px 24px; border: 1px solid #dce6f4; border-radius: 8px; background: #fff; box-shadow: 0 30px 90px rgba(8, 28, 64, .3); scrollbar-width: thin; scrollbar-color: #c6d4e7 transparent; }
.landing-auth__close { position: absolute; top: 18px; right: 18px; width: 38px; height: 38px; display: grid; place-items: center; padding: 0; border: 0; border-radius: 8px; color: #52647f; background: #f2f6fc; transition: color 160ms ease, background 160ms ease, transform 160ms ease; }
.landing-auth__close:hover { color: #2457e8; background: #eaf1ff; transform: rotate(3deg); }
.landing-auth__header { display: grid; justify-items: start; padding-right: 44px; }
.landing-auth__brand { display: inline-flex; align-items: center; min-height: 30px; }
.landing-auth__brand img { width: 124px; height: 27px; object-fit: contain; }
.landing-auth__eyebrow { display: inline-flex; align-items: center; gap: 5px; margin-top: 20px; padding: 4px 8px; border-radius: 6px; color: #2457e8; background: #edf3ff; font-size: 12px; font-weight: 700; }
.landing-auth__header h1 { margin: 8px 0 0; color: #10234a; font-size: 30px; line-height: 1.22; letter-spacing: 0; }
.landing-auth__header p { margin: 5px 0 18px; color: #70809a; font-size: 14px; }
.landing-auth form { display: grid; }
.landing-auth form > label:not(.landing-auth__agreement) { margin: 9px 0 6px; color: #263955; font-size: 13px; font-weight: 700; }
.landing-auth form > small { margin-top: 4px; color: #c43228; font-size: 11px; }
.landing-auth__input { min-height: 52px; display: flex; align-items: center; gap: 10px; padding: 0 13px; border: 1px solid #d4dfed; border-radius: 8px; color: #8191a8; background: #fbfdff; transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease; }
.landing-auth__input:focus-within { border-color: #2864f0; color: #2457e8; background: #fff; box-shadow: 0 0 0 4px rgba(36, 87, 232, .1); }
.landing-auth__input.has-error { border-color: #d92d20; }
.landing-auth__input > svg { flex: 0 0 auto; }
.landing-auth__input input { width: 100%; min-width: 0; height: 50px; padding: 0; border: 0; outline: 0; color: #17233a; background: transparent; font-size: 14px; }
.landing-auth__input input::placeholder { color: #9aa8bb; }
.landing-auth__input button { min-width: 34px; height: 34px; display: grid; place-items: center; padding: 0 8px; border: 0; border-radius: 7px; color: #6f8098; background: transparent; white-space: nowrap; }
.landing-auth__code button { min-width: 104px; border-left: 1px solid #e3e9f1; border-radius: 0; color: #2457e8; font-size: 12px; font-weight: 700; }
.landing-auth__segments { display: grid; grid-template-columns: repeat(2, 1fr); gap: 3px; margin: 4px 0 5px; padding: 3px; border-radius: 8px; background: #eef2f8; }
.landing-auth__segments button { min-height: 42px; display: flex; align-items: center; justify-content: center; gap: 7px; border: 0; border-radius: 6px; color: #62738d; background: transparent; font-weight: 650; transition: color 160ms ease, background 160ms ease, box-shadow 160ms ease; }
.landing-auth__segments button.active { color: #2457e8; background: #fff; box-shadow: 0 2px 8px rgba(24, 51, 91, .08); }
.landing-auth__segments button:disabled { opacity: .42; }
.landing-auth__submit { width: 100%; height: 52px; border: 0; border-radius: 8px; color: #fff; background: #2864f0; box-shadow: 0 10px 24px rgba(36, 87, 232, .22); font-size: 15px; font-weight: 750; transition: transform 180ms ease, background 180ms ease, box-shadow 180ms ease; }
.landing-auth__submit.has-top-space { margin-top: 18px; }
.landing-auth__submit:hover:not(:disabled) { transform: translateY(-1px); background: #174dcc; box-shadow: 0 14px 30px rgba(36, 87, 232, .27); }
.landing-auth__submit:disabled, .landing-auth__input button:disabled { cursor: not-allowed; opacity: .55; }
.landing-auth__options { min-height: 46px; display: flex; align-items: center; justify-content: space-between; gap: 18px; color: #5e6f88; font-size: 12px; }
.landing-auth__options label, .landing-auth__agreement { display: flex; align-items: flex-start; gap: 8px; }
.landing-auth__options input, .landing-auth__agreement input { width: 16px; height: 16px; flex: 0 0 auto; margin: 0; accent-color: #2457e8; }
.landing-auth__options button, .landing-auth__switch button, .landing-auth__dev-code, .landing-auth__back { padding: 0; border: 0; color: #2457e8; background: transparent; font-weight: 700; }
.landing-auth__dev-code { justify-self: end; margin-top: 5px; font-size: 11px; }
.landing-auth__agreement { margin-top: 14px; color: #5e6f88; font-size: 12px; line-height: 1.5; }
.landing-auth__agreement a { color: #2457e8; font-weight: 650; }
.landing-auth__strength { display: grid; grid-template-columns: repeat(4, 42px) 1fr; align-items: center; gap: 5px; margin-top: 7px; }
.landing-auth__strength span { height: 5px; border-radius: 3px; background: #e3e9f2; }
.landing-auth__strength span.filled { background: #2d68ef; }
.landing-auth__strength em { margin-left: 6px; color: #718199; font-size: 11px; font-style: normal; }
.landing-auth__notice, .landing-auth__error { margin: 0 0 12px; padding: 9px 12px; border: 1px solid #cfe0ff; border-radius: 8px; color: #2457e8; background: #eef4ff; font-size: 12px; line-height: 1.5; }
.landing-auth__notice.is-success { border-color: #bce8dc; color: #087c61; background: #edfbf7; }
.landing-auth__error { border-color: #f5caca; color: #b42318; background: #fff0ef; }
.landing-auth__tip, .landing-auth__review { display: flex; align-items: center; gap: 7px; margin: 14px 0 0; padding: 10px 12px; border: 1px solid #cfe0ff; border-radius: 8px; color: #5371a8; background: #f2f7ff; font-size: 12px; }
.landing-auth__review { margin-bottom: 8px; }
.landing-auth__switch { margin: 16px 0 0; text-align: center; color: #6c7d95; font-size: 12px; }
.landing-auth__privacy { display: flex; align-items: center; justify-content: center; gap: 6px; margin: 16px 0 0; padding-top: 14px; border-top: 1px solid #e8edf4; color: #8a99ad; font-size: 10px; }
.landing-auth__privacy svg { color: #119776; }
.landing-auth__steps { display: grid; grid-template-columns: repeat(3, 1fr); gap: 22px; margin: 5px 0 20px; padding: 0; list-style: none; }
.landing-auth__steps li { position: relative; display: flex; align-items: center; gap: 7px; color: #94a2b6; font-size: 12px; }
.landing-auth__steps li:not(:last-child)::after { position: absolute; left: calc(100% - 5px); width: 22px; height: 1px; background: #d8e1ed; content: ''; }
.landing-auth__steps span { width: 26px; height: 26px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: #aab6c8; font-weight: 700; }
.landing-auth__steps li.active, .landing-auth__steps li.done { color: #2457e8; font-weight: 700; }
.landing-auth__steps li.active span, .landing-auth__steps li.done span { background: #2864f0; }
.landing-auth__back { justify-self: start; display: inline-flex; align-items: center; gap: 5px; margin-bottom: 6px; font-size: 12px; }
.landing-auth__complete { display: grid; justify-items: center; gap: 12px; padding: 12px 0 2px; text-align: center; }
.landing-auth__complete > span { width: 62px; height: 62px; display: grid; place-items: center; border-radius: 50%; color: #0b8f6d; background: #e9faf4; }
.landing-auth__complete p { margin: 0 0 8px; color: #62738d; font-size: 13px; }
.landing-auth-enter-active, .landing-auth-leave-active { transition: opacity 180ms ease; }
.landing-auth-enter-active .landing-auth__dialog, .landing-auth-leave-active .landing-auth__dialog { transition: transform 220ms ease, opacity 180ms ease; }
.landing-auth-enter-from, .landing-auth-leave-to { opacity: 0; }
.landing-auth-enter-from .landing-auth__dialog, .landing-auth-leave-to .landing-auth__dialog { opacity: 0; transform: translateY(14px) scale(.988); }
@media (max-width: 760px) {
  .landing-auth { align-items: end; padding: 0; }
  .landing-auth__dialog { width: 100%; max-height: calc(100svh - 14px); padding: 24px 20px max(22px, env(safe-area-inset-bottom)); border-width: 1px 0 0; border-radius: 8px 8px 0 0; }
  .landing-auth__header h1 { font-size: 25px; }
  .landing-auth__eyebrow { margin-top: 15px; }
  .landing-auth__strength { grid-template-columns: repeat(4, 1fr); }
  .landing-auth__strength em { grid-column: 1 / -1; margin: 1px 0 0; }
  .landing-auth__steps { gap: 10px; }
  .landing-auth__steps li { gap: 5px; font-size: 11px; }
  .landing-auth__steps li:not(:last-child)::after { display: none; }
}
@media (prefers-reduced-motion: reduce) { .landing-auth, .landing-auth__dialog, .landing-auth__input, .landing-auth__submit, .landing-auth__segments button { transition: none; } }
</style>
