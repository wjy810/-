<script setup lang="ts">
/**
 * 绑定或更换邮箱、手机号: send a code to the new address or number, enter it with the current password. The old
 * address gets a security notice about the change, so a change nobody asked for does not go unnoticed.
 */
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Lock, ShieldCheck } from 'lucide-vue-next'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import { errorMessage } from '@/shared/api/types'
import { normalizeEmail, normalizePhone, validateCode } from '@/shared/lib/validation'
import { bindContact, confirmContactVerification, fetchDevMailbox, requestContactVerification } from '../services/authApi'
import type { AccountView, VerificationChallenge, VerificationChannel } from '../types'

const props = defineProps<{ open: boolean; channel: VerificationChannel; current?: string | null }>()
const emit = defineEmits<{ close: []; bound: [account: AccountView] }>()

const devMode = import.meta.env.DEV
const form = reactive({ destination: '', code: '', password: '' })
const errors = reactive({ destination: '', code: '', password: '' })
const challenge = ref<VerificationChallenge | null>(null)
const challengeFor = ref('')
const countdown = ref(0)
const pending = ref('')
const failure = ref('')
const info = ref('')
let timer = 0

const kind = computed(() => (props.channel === 'EMAIL' ? '邮箱' : '手机号'))
const title = computed(() => `${props.current ? '更换' : '绑定'}${kind.value}`)

function normalized(): { value: string; error: string | null } {
  return props.channel === 'EMAIL' ? normalizeEmail(form.destination) : normalizePhone(form.destination)
}

function reset(): void {
  Object.assign(form, { destination: '', code: '', password: '' })
  Object.assign(errors, { destination: '', code: '', password: '' })
  challenge.value = null
  challengeFor.value = ''
  failure.value = ''
  info.value = ''
  window.clearInterval(timer)
  countdown.value = 0
}

async function sendCode(): Promise<void> {
  failure.value = ''
  const target = normalized()
  errors.destination = target.error ?? ''
  if (target.error || countdown.value > 0) return
  pending.value = 'send'
  try {
    challenge.value = await requestContactVerification(props.channel, target.value, 'CHANGE_CONTACT')
    challengeFor.value = target.value
    info.value = `验证码已发送至 ${challenge.value.destinationMasked}`
    countdown.value = challenge.value.resendAfterSeconds
    window.clearInterval(timer)
    timer = window.setInterval(() => {
      countdown.value = Math.max(0, countdown.value - 1)
      if (!countdown.value) window.clearInterval(timer)
    }, 1000)
  } catch (reason) {
    failure.value = errorMessage(reason, '验证码发送失败')
  } finally {
    pending.value = ''
  }
}

async function peekCode(): Promise<void> {
  if (!challengeFor.value) return
  try { form.code = (await fetchDevMailbox(challengeFor.value)).code } catch { failure.value = '本地开发验证码不可用' }
}

async function submit(): Promise<void> {
  failure.value = ''
  const target = normalized()
  errors.destination = target.error ?? ''
  errors.code = validateCode(form.code) ?? ''
  if (!errors.code && (!challenge.value || challengeFor.value !== target.value)) errors.code = '请先获取这个联系方式的验证码'
  errors.password = form.password ? '' : '请输入当前密码'
  if (errors.destination || errors.code || errors.password || !challenge.value) return
  pending.value = 'bind'
  try {
    const confirmed = await confirmContactVerification(challenge.value.challengeId, target.value, form.code.trim())
    const account = await bindContact({
      channel: props.channel, destination: target.value, verificationToken: confirmed.verificationToken,
      currentPassword: form.password,
    })
    emit('bound', account)
  } catch (reason) {
    failure.value = errorMessage(reason, `${title.value}失败`)
  } finally {
    pending.value = ''
  }
}

watch(() => props.open, open => { if (open) reset() }, { immediate: true })
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <UiDialog
    :open="open"
    :title="title"
    :description="current ? `当前${kind}：${current}。更换后用新的${kind}登录和接收提醒，原${kind}会收到一条安全提醒。` : `绑定后可以用${kind}登录，${channel === 'EMAIL' ? '面试提醒、简历回执和账号安全提醒也能发到邮箱' : '也能用它找回密码'}。`"
    width="md"
    :dismissible="!pending"
    @close="emit('close')"
  >
    <form id="contact-bind-form" class="bind-form" novalidate @submit.prevent="submit">
      <UiBanner v-if="failure" tone="danger">{{ failure }}</UiBanner>
      <UiField v-slot="{ id }" :label="`新的${kind}`" required :error="errors.destination || null">
        <div class="bind-form__row">
          <UiInput
            :id="id"
            v-model="form.destination"
            :type="channel === 'EMAIL' ? 'email' : 'tel'"
            :inputmode="channel === 'EMAIL' ? 'email' : 'tel'"
            :autocomplete="channel === 'EMAIL' ? 'email' : 'tel'"
            :placeholder="channel === 'EMAIL' ? 'name@example.com' : '例如：138 0000 0000'"
            :disabled="Boolean(pending)"
          />
          <UiButton variant="secondary" :pending="pending === 'send'" :disabled="countdown > 0 || pending === 'bind'" data-testid="send-code" @click="sendCode">
            {{ countdown > 0 ? `${countdown} 秒后重发` : challenge ? '重新发送' : '发送验证码' }}
          </UiButton>
        </div>
      </UiField>
      <p v-if="info" class="bind-form__info" role="status">{{ info }}</p>
      <UiField v-slot="{ id }" label="验证码" required :error="errors.code || null">
        <UiInput :id="id" v-model="form.code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6 位数字" :disabled="pending === 'bind'" />
      </UiField>
      <button v-if="devMode && challenge" type="button" class="bind-form__dev" @click="peekCode">读取本地验证码</button>
      <UiField v-slot="{ id }" label="当前密码" required :error="errors.password || null" hint="确认是你本人在操作">
        <UiInput :id="id" v-model="form.password" :icon="Lock" type="password" autocomplete="current-password" :disabled="pending === 'bind'" />
      </UiField>
      <p class="bind-form__tip"><ShieldCheck :size="14" />验证码只用于这次{{ title }}，请勿转发给他人。</p>
    </form>
    <template #footer>
      <UiButton variant="secondary" :disabled="Boolean(pending)" @click="emit('close')">取消</UiButton>
      <UiButton type="submit" form="contact-bind-form" :pending="pending === 'bind'">确认{{ title }}</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.bind-form {
  display: grid;
  gap: var(--space-4);
}

.bind-form__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
}

.bind-form__info,
.bind-form__tip {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: var(--fs-xs);
}

.bind-form__dev {
  justify-self: start;
  padding: 0;
  border: 0;
  color: var(--color-primary-text);
  background: none;
  font-size: var(--fs-xs);
  cursor: pointer;
}
</style>
