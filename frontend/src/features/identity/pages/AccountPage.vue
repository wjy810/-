<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock } from 'lucide-vue-next'
import AppIcon from '@/shared/ui/AppIcon.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { PASSWORD_RULE_HINT, validatePassword } from '@/shared/lib/validation'
import { changePassword, fetchSessions, fetchVerificationCapabilities, revokeOtherSessions, revokeSession } from '../services/authApi'
import { useSessionStore } from '@/stores/session'
import ContactBindDialog from '../components/ContactBindDialog.vue'
import type { AccountView, SessionView, VerificationCapabilities, VerificationChannel } from '../types'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()
const form = reactive({ currentPassword: '', newPassword: '', confirm: '' })
const fieldError = reactive({ currentPassword: '', newPassword: '', confirm: '' })
const acknowledged = ref(false)
const pending = ref('')
const loading = ref(true)
const formError = ref('')
const pageError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'account-page-error')
useToastFeedback(notice, 'success', 'account-page-notice')
const sessions = ref<SessionView[]>([])
const sessionsFailed = ref(false)
const currentSession = computed(() => sessions.value.find((item) => item.current))
const otherSessions = computed(() => sessions.value.filter((item) => !item.current))
const account = computed(() => session.account)
const verified = computed(() => Boolean(account.value?.emailVerified || account.value?.phoneVerified))
const accountState = computed(() => account.value?.status === 'DELETION_PENDING'
  ? { text: '注销申请处理中', tone: 'warning' as const, badge: '处理中' }
  : { text: '正常使用中', tone: 'primary' as const, badge: '正常' })
const capabilities = ref<VerificationCapabilities | null>(null)
const binding = ref<VerificationChannel | null>(null)
const contacts = computed(() => [
  {
    channel: 'EMAIL' as const, label: '邮箱', value: account.value?.email ?? null, verified: Boolean(account.value?.emailVerified),
    available: capabilities.value?.emailEnabled ?? false,
    hint: '用来登录、找回密码，接收面试提醒、简历回执和账号安全提醒',
  },
  {
    channel: 'SMS' as const, label: '手机号', value: account.value?.phoneMasked ?? null, verified: Boolean(account.value?.phoneVerified),
    available: capabilities.value?.smsEnabled ?? false,
    hint: '用来登录和找回密码',
  },
])
const bindingCurrent = computed(() => contacts.value.find(item => item.channel === binding.value)?.value ?? null)

async function loadSessions(): Promise<void> {
  loading.value = true
  pageError.value = ''
  sessionsFailed.value = false
  try {
    sessions.value = await fetchSessions()
  } catch (error) {
    sessionsFailed.value = true
    pageError.value = errorMessage(error, '登录设备读取失败')
  } finally {
    loading.value = false
  }
}

async function loadCapabilities(): Promise<void> {
  try { capabilities.value = await fetchVerificationCapabilities() } catch { capabilities.value = null }
}

function onBound(updated: AccountView): void {
  const kind = binding.value === 'EMAIL' ? '邮箱' : '手机号'
  binding.value = null
  session.remember(updated)
  notice.value = `${kind}已绑定。原来的${kind}（如果有）会收到一条安全提醒。`
  if (route.query.bind) void router.replace({ query: { ...route.query, bind: undefined } })
}

function validate(): boolean {
  fieldError.currentPassword = form.currentPassword ? '' : '当前密码不能为空'
  fieldError.newPassword = validatePassword(form.newPassword, session.account?.email ?? undefined) ?? ''
  fieldError.confirm = form.confirm === form.newPassword ? '' : '两次密码不一致'
  if (!acknowledged.value) {
    formError.value = '请先确认：改密后全部会话立即失效。'
    return false
  }
  return !fieldError.currentPassword && !fieldError.newPassword && !fieldError.confirm
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  if (!validate()) {
    return
  }
  pending.value = 'password'
  try {
    await changePassword(form.currentPassword, form.newPassword)
    session.clear()
    await router.replace({ name: 'home', query: { auth: 'login', reason: 'password_changed' } })
  } catch (error) {
    formError.value = errorMessage(error, '改密未成功')
  } finally {
    pending.value = ''
  }
}

async function revokeOne(id: string): Promise<void> {
  pending.value = id
  notice.value = ''
  try {
    await revokeSession(id)
    notice.value = '已退出该登录设备。'
    await loadSessions()
  } catch (error) {
    pageError.value = errorMessage(error, '退出设备失败')
  } finally {
    pending.value = ''
  }
}

async function revokeOthers(): Promise<void> {
  pending.value = 'others'
  notice.value = ''
  try {
    const result = await revokeOtherSessions()
    notice.value = result.revoked ? `已退出 ${result.revoked} 个其他登录设备。` : '当前没有其他有效登录设备。'
    await loadSessions()
  } catch (error) {
    pageError.value = errorMessage(error, '退出其他设备失败')
  } finally {
    pending.value = ''
  }
}

onMounted(async () => {
  void loadSessions()
  await loadCapabilities()
  // "去绑定邮箱" on the notification settings lands here with ?bind=email.
  const wanted = route.query.bind === 'email' ? 'EMAIL' : route.query.bind === 'phone' ? 'SMS' : null
  if (wanted && contacts.value.find(item => item.channel === wanted)?.available) binding.value = wanted
})
</script>

<template>
  <div class="settings-section account-security">
    <header class="settings-section__head">
      <div class="settings-section__title">
        <span class="settings-section__icon"><AppIcon name="lock" :size="18" /></span>
        <div>
          <h2>账号与安全</h2>
          <p>查看登录凭证、更新密码并管理当前账号的登录设备。</p>
        </div>
      </div>
    </header>

    <section class="security-summary" aria-label="账号安全概览">
      <article>
        <span><AppIcon name="mail" :size="17" /></span>
        <div><small>主登录标识</small><strong>{{ session.account?.displayIdentifier || '—' }}</strong></div>
        <UiBadge :tone="verified ? 'success' : 'warning'">{{ verified ? '已验证' : '未验证' }}</UiBadge>
      </article>
      <article>
        <span><AppIcon name="shield" :size="17" /></span>
        <div><small>账号状态</small><strong>{{ accountState.text }}</strong></div>
        <UiBadge :tone="accountState.tone">{{ accountState.badge }}</UiBadge>
      </article>
      <article>
        <span><AppIcon name="grid" :size="17" /></span>
        <div><small>有效会话</small><strong>{{ loading ? '读取中' : sessionsFailed ? '读取失败' : `${sessions.length} 个` }}</strong></div>
        <small>{{ sessionsFailed ? '稍后刷新再看' : otherSessions.length ? `${otherSessions.length} 个其他设备` : '仅当前设备' }}</small>
      </article>
    </section>

    <div class="security-columns">
      <div class="security-primary">
        <section class="settings-card">
          <header class="security-card__head">
            <div>
              <h3 class="security-card__title">登录信息与联系方式</h3>
              <p class="security-card__sub">已验证的邮箱和手机号都可以用来登录和找回密码</p>
            </div>
          </header>
          <div class="security-card__body">
            <div v-for="item in contacts" :key="item.channel" class="info-row" :data-testid="`contact-${item.channel.toLowerCase()}`">
              <span class="info-row__icon"><AppIcon :name="item.channel === 'EMAIL' ? 'mail' : 'phone'" :size="17" /></span>
              <div class="info-row__text">
                <p class="info-row__label">{{ item.label }}</p>
                <p class="info-row__value">{{ item.value || '未绑定' }}</p>
                <small>{{ item.hint }}</small>
              </div>
              <UiBadge :tone="item.value ? (item.verified ? 'success' : 'warning') : 'neutral'">{{ item.value ? (item.verified ? '已验证' : '未验证') : '未绑定' }}</UiBadge>
              <UiButton v-if="item.available" size="sm" variant="secondary" :data-testid="`bind-${item.channel.toLowerCase()}`" @click="binding = item.channel">
                {{ item.value ? '更换' : '绑定' }}
              </UiButton>
            </div>
            <p v-if="capabilities && !capabilities.emailEnabled && !capabilities.smsEnabled" class="credential-note"><AppIcon name="info" :size="14" />验证码通道暂未开通，暂时不能绑定或更换联系方式。</p>
            <p class="credential-note"><AppIcon name="info" :size="14" />更换时需要新联系方式的验证码和当前密码；原来的邮箱会收到一条安全提醒。暂不提供第三方账号登录。</p>
          </div>
        </section>

        <section class="settings-card">
          <header class="security-card__head">
            <div>
              <h3 class="security-card__title">更新密码</h3>
              <p class="security-card__sub">建议使用未在其他网站重复使用的强密码</p>
            </div>
          </header>
          <div class="security-card__body">
            <div class="security-banners">
              <UiBanner tone="warning">更新成功后，所有设备都会退出登录，需要使用新密码重新进入。</UiBanner>
              <UiBanner v-if="formError" tone="danger">{{ formError }}</UiBanner>
            </div>
            <form class="security-password-form" novalidate @submit.prevent="onSubmit">
              <UiField id="cur-pass" v-slot="{ id, describedBy, invalid }" label="当前密码" :error="fieldError.currentPassword">
                <UiInput :id="id" v-model="form.currentPassword" :icon="Lock" size="lg" type="password" autocomplete="current-password" :disabled="pending !== ''" :invalid="invalid" :aria-describedby="describedBy" />
              </UiField>
              <UiField id="new-pass" v-slot="{ id, describedBy, invalid }" label="新密码" :error="fieldError.newPassword" :hint="PASSWORD_RULE_HINT">
                <UiInput :id="id" v-model="form.newPassword" :icon="Lock" size="lg" type="password" autocomplete="new-password" :disabled="pending !== ''" :invalid="invalid" :aria-describedby="describedBy" />
              </UiField>
              <UiField id="new-pass-2" v-slot="{ id, describedBy, invalid }" label="确认新密码" :error="fieldError.confirm">
                <UiInput :id="id" v-model="form.confirm" :icon="Lock" size="lg" type="password" autocomplete="new-password" :disabled="pending !== ''" :invalid="invalid" :aria-describedby="describedBy" />
              </UiField>
              <UiCheckbox v-model="acknowledged" :disabled="pending !== ''" label="我已了解更新密码后，当前账号的全部会话会立即失效。" />
              <UiButton type="submit" :pending="pending === 'password'" :disabled="!acknowledged || (pending !== '' && pending !== 'password')">
                {{ pending === 'password' ? '正在更新…' : acknowledged ? '更新密码并退出全部设备' : '请先确认影响' }}
              </UiButton>
            </form>
          </div>
        </section>
      </div>

      <section class="settings-card device-card">
        <header class="security-card__head">
          <div><h3 class="security-card__title">登录设备</h3><p class="security-card__sub">发现陌生会话时，请立即退出并更新密码</p></div>
          <UiButton variant="secondary" :pending="pending === 'others'" :disabled="!otherSessions.length || (pending !== '' && pending !== 'others')" @click="revokeOthers">退出其他设备</UiButton>
        </header>
        <div class="security-card__body">
          <div v-if="loading" aria-busy="true"><UiSkeleton :lines="2" /></div>
          <div v-else-if="currentSession" class="session-row is-current">
            <span class="session-row__icon"><AppIcon name="shield" :size="17" /></span>
            <div><strong>{{ currentSession.clientLabel || '当前浏览器会话' }}</strong><small>最近活动 {{ formatWhen(currentSession.lastSeenAt) }}</small></div>
            <UiBadge tone="success">当前设备</UiBadge>
          </div>
          <UiEmptyState v-else size="sm" illustration="empty-search" :title="sessionsFailed ? '登录设备读取失败，请稍后刷新。' : '未读取到当前会话。'" />
          <div v-for="item in otherSessions" :key="item.id" class="session-row">
            <span class="session-row__icon"><AppIcon name="grid" :size="17" /></span>
            <div><strong>{{ item.clientLabel || '其他登录会话' }}</strong><small>登录于 {{ formatWhen(item.createdAt) }} · 最近活动 {{ formatWhen(item.lastSeenAt) }}</small></div>
            <UiButton variant="secondary" :pending="pending === item.id" :disabled="pending !== '' && pending !== item.id" @click="revokeOne(item.id)">退出</UiButton>
          </div>
          <div v-if="!loading && !sessionsFailed && !otherSessions.length" class="device-empty"><AppIcon name="check-circle" :size="18" /><span><strong>没有其他有效设备</strong><small>当前账号仅在这个浏览器保持登录。</small></span></div>
        </div>
      </section>
    </div>

    <section class="settings-card security-record">
      <header class="security-card__head"><div><h3 class="security-card__title">安全记录</h3><p class="security-card__sub">当前仍有效的登录会话。改密码、新设备登录、更换联系方式会发到通知中心的“账号安全”</p></div></header>
      <div class="security-card__body table-wrap"><table><thead><tr><th>事件类型</th><th>设备</th><th>首次登录</th><th>最近活动</th></tr></thead><tbody><tr v-for="item in sessions" :key="item.id"><td><span class="event-label"><AppIcon name="lock" :size="13" />登录</span></td><td>{{ item.clientLabel || (item.current ? '当前设备' : '其他设备') }}{{ item.current && item.clientLabel ? '（当前）' : '' }}</td><td>{{ formatWhen(item.createdAt) }}</td><td>{{ formatWhen(item.lastSeenAt) }}</td></tr></tbody></table><UiEmptyState v-if="!loading && !sessions.length" size="sm" illustration="empty-history" title="暂无安全记录。" /></div>
    </section>

    <ContactBindDialog :open="Boolean(binding)" :channel="binding ?? 'EMAIL'" :current="bindingCurrent" @close="binding = null" @bound="onBound" />
  </div>
</template>

<style scoped>
.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.info-row__text {
  flex: 1;
  min-width: 0;
}

.info-row__text small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.info-row__icon {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.info-row__label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.info-row__value {
  margin-top: 2px;
  font-size: 14px;
  font-weight: 600;
}

.security-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.security-summary article {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 14px 16px;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: var(--surface-1);
}

.security-summary article > span:first-child {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 9px;
  background: var(--surface-2);
  color: var(--color-primary);
}

.security-summary article > div {
  min-width: 0;
  display: grid;
  flex: 1;
}

.security-summary small {
  color: var(--text-tertiary);
  font-size: 12px;
}

.security-summary strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.security-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.14fr) minmax(320px, 0.86fr);
  gap: 18px;
  align-items: start;
}

.security-primary {
  display: grid;
  gap: 18px;
}

.credential-note {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-tertiary);
  font-size: 12.5px;
}

.security-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-5) var(--space-1);
}

.security-card__title {
  font-size: var(--fs-h3);
  font-weight: 600;
  line-height: var(--lh-h3);
}

.security-card__sub {
  margin-top: 2px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.security-card__body {
  padding: var(--space-3) var(--space-5) var(--space-5);
}

.security-banners {
  display: grid;
  gap: var(--space-2);
}

.security-password-form {
  display: grid;
  gap: var(--space-4);
  margin-top: 14px;
}

.device-card .security-card__head {
  flex-wrap: wrap;
  align-items: center;
}

.session-row {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 0;
  border-bottom: 1px solid var(--border-default);
}

.session-row.is-current {
  margin: 0 -6px;
  padding-inline: 8px;
  border-radius: 9px;
  background: var(--surface-2);
}

.session-row__icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  background: var(--surface-2);
  color: var(--text-secondary);
}

.session-row.is-current .session-row__icon {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.session-row > div {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.session-row small,
.device-empty small {
  color: var(--text-tertiary);
  font-size: 12.5px;
}

.device-empty {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 14px 2px 2px;
  color: var(--color-success);
}

.device-empty span {
  display: grid;
  color: var(--text-primary);
}

.device-empty strong {
  font-size: 12.5px;
}

.table-wrap {
  overflow-x: auto;
}

.table-wrap table {
  width: 100%;
  border-collapse: collapse;
}

.table-wrap th,
.table-wrap td {
  padding: 12px;
  border-bottom: 1px solid var(--border-default);
  text-align: left;
  font-size: 12.5px;
  /* Keep "登录" and the timestamps on one line; a narrow screen scrolls the table sideways instead. */
  white-space: nowrap;
}

.table-wrap th {
  color: var(--text-tertiary);
  font-weight: 500;
}

.event-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 1120px) {
  .security-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .security-summary {
    grid-template-columns: 1fr;
  }
}
</style>
