<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { PASSWORD_RULE_HINT, validatePassword } from '@/shared/lib/validation'
import { changePassword, fetchSessions, revokeOtherSessions, revokeSession } from '../services/authApi'
import { useSessionStore } from '@/stores/session'
import type { SessionView } from '../types'

const router = useRouter()
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
const currentSession = computed(() => sessions.value.find((item) => item.current))
const otherSessions = computed(() => sessions.value.filter((item) => !item.current))

async function loadSessions(): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    sessions.value = await fetchSessions()
  } catch (error) {
    pageError.value = errorMessage(error, '登录设备读取失败')
  } finally {
    loading.value = false
  }
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

onMounted(() => void loadSessions())
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
        <AppTag tone="green">已验证</AppTag>
      </article>
      <article>
        <span><AppIcon name="shield" :size="17" /></span>
        <div><small>账号状态</small><strong>安全保护中</strong></div>
        <AppTag tone="blue">正常</AppTag>
      </article>
      <article>
        <span><AppIcon name="grid" :size="17" /></span>
        <div><small>有效会话</small><strong>{{ loading ? '读取中' : `${sessions.length} 个` }}</strong></div>
        <small>{{ otherSessions.length ? `${otherSessions.length} 个其他设备` : '仅当前设备' }}</small>
      </article>
    </section>

    <div class="security-columns">
      <div class="security-primary">
        <section class="settings-card">
          <header class="card__head">
            <div>
              <h3 class="card__title">登录信息</h3>
              <p class="card__sub">当前账号可使用已验证的邮箱或手机号登录</p>
            </div>
          </header>
          <div class="card__body">
            <div class="info-row">
              <span class="info-row__icon"><AppIcon name="mail" :size="17" /></span>
              <div>
                <p class="info-row__label">登录标识</p>
                <p class="info-row__value">{{ session.account?.displayIdentifier || '—' }}</p>
              </div>
              <AppTag tone="green">已验证</AppTag>
            </div>
            <p class="credential-note"><AppIcon name="info" :size="14" />当前版本暂不支持绑定或更换联系方式，也不提供第三方账号登录。</p>
          </div>
        </section>

        <section class="settings-card">
          <header class="card__head">
            <div>
              <h3 class="card__title">更新密码</h3>
              <p class="card__sub">建议使用未在其他网站重复使用的强密码</p>
            </div>
          </header>
          <div class="card__body">
            <AppBanner tone="warn">更新成功后，所有设备都会退出登录，需要使用新密码重新进入。</AppBanner>
            <AppBanner v-if="formError" tone="bad">{{ formError }}</AppBanner>
            <form class="security-password-form" novalidate @submit.prevent="onSubmit">
              <AppField id="cur-pass" label="当前密码" :error="fieldError.currentPassword">
                <div class="input-icon"><AppIcon name="lock" :size="16" /><input id="cur-pass" v-model="form.currentPassword" type="password" autocomplete="current-password" :disabled="pending !== ''" /></div>
              </AppField>
              <AppField id="new-pass" label="新密码" :error="fieldError.newPassword" :hint="PASSWORD_RULE_HINT">
                <div class="input-icon"><AppIcon name="lock" :size="16" /><input id="new-pass" v-model="form.newPassword" type="password" autocomplete="new-password" :disabled="pending !== ''" /></div>
              </AppField>
              <AppField id="new-pass-2" label="确认新密码" :error="fieldError.confirm">
                <div class="input-icon"><AppIcon name="lock" :size="16" /><input id="new-pass-2" v-model="form.confirm" type="password" autocomplete="new-password" :disabled="pending !== ''" /></div>
              </AppField>
              <label class="ack">
                <input v-model="acknowledged" type="checkbox" :disabled="pending !== ''" />
                <span>我已了解更新密码后，当前账号的全部会话会立即失效。</span>
              </label>
              <AppButton type="submit" :pending="pending === 'password'" :disabled="!acknowledged || pending !== ''">
                {{ pending === 'password' ? '正在更新…' : acknowledged ? '更新密码并退出全部设备' : '请先确认影响' }}
              </AppButton>
            </form>
          </div>
        </section>
      </div>

      <section class="settings-card device-card">
        <header class="card__head">
          <div><h3 class="card__title">登录设备</h3><p class="card__sub">发现陌生会话时，请立即退出并更新密码</p></div>
          <AppButton variant="ghost" :pending="pending === 'others'" :disabled="!otherSessions.length || pending !== ''" @click="revokeOthers">退出其他设备</AppButton>
        </header>
        <div class="card__body">
          <div v-if="loading" aria-busy="true"><div class="bone" /><div class="bone bone--short" /></div>
          <div v-else-if="currentSession" class="session-row is-current">
            <span class="session-row__icon"><AppIcon name="shield" :size="17" /></span>
            <div><strong>当前浏览器会话</strong><small>最近活动 {{ formatWhen(currentSession.lastSeenAt) }}</small></div>
            <AppTag tone="green">当前设备</AppTag>
          </div>
          <p v-else class="empty">未读取到当前会话。</p>
          <div v-for="item in otherSessions" :key="item.id" class="session-row">
            <span class="session-row__icon"><AppIcon name="grid" :size="17" /></span>
            <div><strong>其他登录会话</strong><small>最近活动 {{ formatWhen(item.lastSeenAt) }}</small></div>
            <AppButton variant="ghost" :pending="pending === item.id" :disabled="pending !== ''" @click="revokeOne(item.id)">退出</AppButton>
          </div>
          <div v-if="!loading && !otherSessions.length" class="device-empty"><AppIcon name="check-circle" :size="18" /><span><strong>没有其他有效设备</strong><small>当前账号仅在这个浏览器保持登录。</small></span></div>
        </div>
      </section>
    </div>

    <section class="settings-card security-record">
      <header class="card__head"><div><h3 class="card__title">安全记录</h3><p class="card__sub">当前仍有效的登录会话活动</p></div></header>
      <div class="card__body table-wrap"><table><thead><tr><th>事件类型</th><th>设备</th><th>首次登录</th><th>最近活动</th></tr></thead><tbody><tr v-for="item in sessions" :key="item.id"><td><span class="event-label"><AppIcon name="lock" :size="13" />登录</span></td><td>{{ item.current ? '当前设备' : '其他设备' }}</td><td>{{ formatWhen(item.createdAt) }}</td><td>{{ formatWhen(item.lastSeenAt) }}</td></tr></tbody></table><p v-if="!loading && !sessions.length" class="empty">暂无安全记录。</p></div>
    </section>
  </div>
</template>

<style scoped>
.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
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

.security-password-form {
  display: grid;
  gap: 2px;
  margin-top: 14px;
}

.security-password-form .input-icon {
  min-height: 46px;
  border-radius: 10px;
}

.security-password-form .input-icon input {
  height: 44px;
}

.device-card .card__head {
  align-items: center;
}

.session-row {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 0;
  border-bottom: 1px solid var(--border);
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
  background: var(--success-soft);
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
  color: var(--text);
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
  border-bottom: 1px solid var(--border);
  text-align: left;
  font-size: 12.5px;
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
