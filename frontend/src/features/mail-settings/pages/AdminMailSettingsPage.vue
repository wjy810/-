<script setup lang="ts">
/**
 * 邮件设置 (docs/phase2/05): the SMTP account verification codes and notification emails go out through.
 * Saved here, it takes effect at once and overrides the environment; clearing it goes back to the
 * environment. The authorization code is write-only.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { CircleAlert, CircleCheck, Mail, RefreshCw, RotateCcw, Save, Send, ServerCog } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import UiSwitch from '@/shared/ui/UiSwitch.vue'
import { confirm } from '@/shared/ui/confirm'
import { toast } from '@/shared/ui/toast'
import { errorMessage, isApiClientError } from '@/shared/api/types'
import { formatRelative, formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import { useSessionStore } from '@/stores/session'
import { clearMailSettings, fetchMailSettings, saveMailSettings, sendTestMail } from '../services/mailSettingsApi'
import { FIELD_OF_REASON, MAIL_PRESETS, presetFor } from '../presets'
import type { MailSecurity, MailSettings, MailTestResult } from '../types'

type Field = 'host' | 'port' | 'security' | 'username' | 'password' | 'fromAddress' | 'fromName'

const session = useSessionStore()
const page = useLoadState(() => fetchMailSettings())
const data = computed<MailSettings | null>(() => page.data.value)
const form = reactive({ host: '', port: '465', security: 'SSL' as MailSecurity, username: '', password: '', fromAddress: '', fromName: 'JobProof AI', notifications: true })
const errors = ref<Partial<Record<Field, string>>>({})
const saving = ref(false)
const clearing = ref(false)
const testing = ref(false)
const testTo = ref(session.account?.email ?? '')
const testResult = ref<MailTestResult | null>(null)

const securityItems = [
  { value: 'SSL' as const, label: 'SSL' },
  { value: 'STARTTLS' as const, label: 'STARTTLS' },
  { value: 'NONE' as const, label: '不加密' },
]
const preset = computed(() => presetFor(form.host))
const saved = computed(() => data.value?.admin ?? null)
const lastTest = computed(() => testResult.value ?? data.value?.lastTest ?? null)
const STATUS: Record<string, { label: string; tone: 'success' | 'warning' | 'danger' | 'neutral' }> = {
  SENT: { label: '已发送', tone: 'success' },
  PENDING: { label: '待发送', tone: 'warning' },
  FAILED: { label: '失败', tone: 'danger' },
}

function fill(value: MailSettings | null): void {
  const admin = value?.admin
  Object.assign(form, admin
    ? { host: admin.host, port: String(admin.port), security: admin.security, username: admin.username, password: '',
        fromAddress: admin.fromAddress, fromName: admin.fromName, notifications: admin.notifications }
    : { host: 'smtp.qq.com', port: '465', security: 'SSL', username: '', password: '', fromAddress: '', fromName: 'JobProof AI',
        notifications: true })
  errors.value = {}
}

async function load(): Promise<void> {
  fill(await page.load() ?? null)
}

function applyPreset(key: string): void {
  const chosen = MAIL_PRESETS.find(item => item.key === key)
  if (!chosen) return
  Object.assign(form, { host: chosen.host, port: String(chosen.port), security: chosen.security })
  errors.value = {}
}

function onUsername(value: string): void {
  // Most providers send as the signed-in address; keep the sender in step until it is edited apart.
  if (!form.fromAddress || form.fromAddress === form.username) form.fromAddress = value
  form.username = value
}

async function save(): Promise<void> {
  if (saving.value) return
  errors.value = {}
  saving.value = true
  try {
    const result = await saveMailSettings({
      host: form.host.trim(),
      port: Number(form.port),
      security: form.security,
      username: form.username.trim(),
      password: form.password,
      fromAddress: form.fromAddress.trim(),
      fromName: form.fromName.trim(),
      notifications: form.notifications,
      expectedVersion: saved.value?.version ?? 0,
    })
    page.data.value = result
    fill(result)
    toast.success('邮件设置已保存，立即生效', { description: '建议发一封测试邮件确认能收到。' })
  } catch (reason) {
    const field = isApiClientError(reason) ? FIELD_OF_REASON[reason.reason] : undefined
    if (field) errors.value = { [field]: errorMessage(reason, '这一项不正确') }
    else toast.error(errorMessage(reason, '邮件设置保存失败'))
  } finally {
    saving.value = false
  }
}

async function clear(): Promise<void> {
  if (!(await confirm({
    title: '恢复为环境变量中的配置？',
    message: data.value?.environment.configured
      ? '后台保存的账号和授权码会被删除，之后的邮件改用服务器环境变量中的账号发送。'
      : '后台保存的账号和授权码会被删除。服务器环境变量里没有可用的邮件账号，删除后验证码和提醒邮件都会发不出去。',
    confirmText: '删除后台配置',
    tone: 'danger',
  }))) return
  clearing.value = true
  try {
    const result = await clearMailSettings()
    page.data.value = result
    testResult.value = null
    fill(result)
    toast.success('已恢复为环境变量中的配置')
  } catch (reason) {
    toast.error(errorMessage(reason, '恢复失败'))
  } finally {
    clearing.value = false
  }
}

async function test(): Promise<void> {
  if (testing.value) return
  testing.value = true
  try {
    testResult.value = await sendTestMail(testTo.value.trim())
  } catch (reason) {
    toast.error(errorMessage(reason, '测试邮件没有发出'))
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container page-container--wide mail-admin">
    <UiPageHeader title="邮件设置" description="注册验证码和提醒邮件都通过这里的账号发出。保存后立即生效，不需要重启服务。">
      <template #actions>
        <UiButton variant="secondary" :icon="RefreshCw" :pending="page.loading.value && page.loaded.value" @click="load">刷新</UiButton>
      </template>
    </UiPageHeader>

    <PageState :loading="page.loading.value" :error="page.error.value" :loaded="page.loaded.value" error-title="邮件设置读取失败" @retry="load">
      <template #skeleton>
        <UiSkeleton height="84px" radius="var(--radius-lg)" />
        <UiSkeleton height="420px" radius="var(--radius-lg)" />
      </template>

      <template v-if="data">
        <section class="mail-status" :class="`is-${saved && !saved.usable ? 'broken' : data.source.toLowerCase()}`" aria-live="polite">
          <span class="mail-status__icon"><component :is="data.source === 'NONE' || (saved && !saved.usable) ? CircleAlert : CircleCheck" :size="18" /></span>
          <div class="mail-status__body">
            <template v-if="saved && !saved.usable">
              <strong>保存的授权码无法解密</strong>
              <p>服务器的主密钥可能已更换。邮件暂停发送，重新填写授权码并保存即可恢复。</p>
            </template>
            <template v-else-if="data.source === 'ADMIN' && saved">
              <strong>正在使用后台配置</strong>
              <p>{{ saved.host }}:{{ saved.port }} · {{ saved.security }} · 发件人 {{ saved.fromName }} &lt;{{ saved.fromAddress }}&gt; · {{ formatRelative(saved.updatedAt) }}保存</p>
            </template>
            <template v-else-if="data.source === 'ENVIRONMENT'">
              <strong>正在使用服务器环境变量中的配置</strong>
              <p>{{ data.environment.host }} · 账号 {{ data.environment.username }}。在下面保存后，会改用后台配置。</p>
            </template>
            <template v-else>
              <strong>还没有可用的邮件账号</strong>
              <p>注册验证码和提醒邮件都发不出去。填写下面的 SMTP 账号并保存即可。</p>
            </template>
          </div>
          <UiBadge :tone="data.notifications ? 'success' : 'neutral'" size="sm" dot>提醒邮件{{ data.notifications ? '已开启' : '未开启' }}</UiBadge>
        </section>

        <p v-if="!data.canStoreSecrets" class="mail-warning">
          <CircleAlert :size="15" />服务器没有配置主密钥（JOBPROOF_AI_MASTER_KEY），暂时不能在后台保存授权码。配置后重启服务即可。
        </p>

        <div class="mail-grid">
          <section class="mail-card" aria-labelledby="mail-account-title">
            <header class="mail-card__head">
              <span class="mail-card__icon"><ServerCog :size="17" /></span>
              <div>
                <h2 id="mail-account-title">SMTP 账号</h2>
                <p>选一个常用邮箱自动填好服务器和端口，再填账号与授权码。</p>
              </div>
            </header>

            <div class="presets" role="group" aria-label="常用邮箱">
              <button
                v-for="item in MAIL_PRESETS"
                :key="item.key"
                type="button"
                class="presets__chip"
                :class="{ 'is-active': preset?.key === item.key }"
                :aria-pressed="preset?.key === item.key"
                @click="applyPreset(item.key)"
              >{{ item.label }}</button>
            </div>
            <p v-if="preset" class="mail-hint">{{ preset.hint }}</p>

            <form id="mail-settings-form" class="mail-form" novalidate @submit.prevent="save">
              <UiField v-slot="{ id }" label="SMTP 服务器" required :error="errors.host" class="mail-form__wide">
                <UiInput :id="id" v-model="form.host" placeholder="smtp.qq.com" autocomplete="off" spellcheck="false" />
              </UiField>
              <UiField v-slot="{ id }" label="端口" required :error="errors.port">
                <UiInput :id="id" v-model="form.port" type="number" min="1" max="65535" inputmode="numeric" />
              </UiField>
              <UiField label="加密方式" :error="errors.security" hint="465 端口选 SSL，587 端口选 STARTTLS">
                <UiSegmented v-model="form.security" :items="securityItems" aria-label="加密方式" size="sm" block />
              </UiField>
              <UiField v-slot="{ id }" label="账号" required :error="errors.username">
                <UiInput :id="id" :model-value="form.username" placeholder="notice@example.com" autocomplete="off" spellcheck="false" @update:model-value="onUsername" />
              </UiField>
              <UiField v-slot="{ id }" label="授权码" :required="!saved" :error="errors.password" :hint="saved ? '已保存，留空则不修改' : '邮箱后台生成的 SMTP 授权码，不是登录密码'">
                <UiInput :id="id" v-model="form.password" type="password" :placeholder="saved ? '••••••••（已保存）' : 'SMTP 授权码'" autocomplete="new-password" />
              </UiField>
              <UiField v-slot="{ id }" label="发件地址" required :error="errors.fromAddress">
                <UiInput :id="id" v-model="form.fromAddress" placeholder="notice@example.com" autocomplete="off" spellcheck="false" />
              </UiField>
              <UiField v-slot="{ id }" label="发件人名称" :error="errors.fromName">
                <UiInput :id="id" v-model="form.fromName" maxlength="60" placeholder="JobProof AI" />
              </UiField>
              <div class="mail-form__wide mail-switch">
                <div>
                  <strong>发送提醒邮件</strong>
                  <small>简历回执、面试与笔试提醒、会员到期提醒；用户还可以在自己的“设置 › 通知”里关掉。验证码不受这个开关影响。</small>
                </div>
                <UiSwitch v-model="form.notifications" label="发送提醒邮件" />
              </div>
            </form>
            <footer class="mail-card__foot">
              <UiButton v-if="saved" variant="ghost" :icon="RotateCcw" :pending="clearing" @click="clear">恢复为环境变量</UiButton>
              <UiButton type="submit" form="mail-settings-form" :icon="Save" :pending="saving" :disabled="!data.canStoreSecrets && Boolean(form.password)">保存并生效</UiButton>
            </footer>
          </section>

          <div class="mail-side">
            <section class="mail-card" aria-labelledby="mail-test-title">
              <header class="mail-card__head">
                <span class="mail-card__icon is-send"><Send :size="17" /></span>
                <div>
                  <h2 id="mail-test-title">发送测试邮件</h2>
                  <p>用当前生效的账号发一封，确认能收到。</p>
                </div>
              </header>
              <form class="mail-test" @submit.prevent="test">
                <UiInput v-model="testTo" type="email" placeholder="收件邮箱" aria-label="测试邮件收件人" />
                <UiButton type="submit" variant="secondary" :pending="testing" :disabled="data.source === 'NONE' || !testTo.trim()">发送</UiButton>
              </form>
              <p v-if="lastTest" class="mail-result" :class="lastTest.ok ? 'is-ok' : 'is-failed'" role="status">
                <component :is="lastTest.ok ? CircleCheck : CircleAlert" :size="15" />
                <span>{{ lastTest.message }}<small> · {{ formatWhen(lastTest.at) }}</small></span>
              </p>
            </section>

            <section class="mail-card" aria-labelledby="mail-delivery-title">
              <header class="mail-card__head">
                <span class="mail-card__icon is-mail"><Mail :size="17" /></span>
                <div>
                  <h2 id="mail-delivery-title">提醒邮件发送情况</h2>
                  <p>失败的邮件会自动重试，最多 5 次。</p>
                </div>
              </header>
              <dl class="delivery">
                <div><dt>近 24 小时已发送</dt><dd>{{ data.delivery.sent24h }}</dd></div>
                <div><dt>待发送 / 重试中</dt><dd>{{ data.delivery.pending }}</dd></div>
                <div :class="{ 'is-bad': data.delivery.failed7d > 0 }"><dt>近 7 天失败</dt><dd>{{ data.delivery.failed7d }}</dd></div>
              </dl>
              <ul v-if="data.delivery.recent.length" class="recent" aria-label="最近的提醒邮件">
                <li v-for="email in data.delivery.recent" :key="email.id">
                  <div class="recent__main">
                    <strong :title="email.subject">{{ email.subject.replace('【JobProof AI】', '') }}</strong>
                    <small>{{ email.to }} · {{ formatRelative(email.createdAt) }}<template v-if="email.attempts > 1"> · 尝试 {{ email.attempts }} 次</template></small>
                    <small v-if="email.lastError && email.status !== 'SENT'" class="recent__error">{{ email.lastError }}</small>
                  </div>
                  <UiBadge :tone="(STATUS[email.status] ?? STATUS.PENDING)!.tone" size="sm">{{ (STATUS[email.status] ?? { label: email.status }).label }}</UiBadge>
                </li>
              </ul>
              <p v-else class="mail-hint">还没有发出过提醒邮件。</p>
            </section>
          </div>
        </div>
      </template>
    </PageState>
  </div>
</template>

<style scoped>
.mail-admin {
  display: grid;
  gap: var(--space-5);
}

.mail-admin :deep(.page-state) {
  display: grid;
  gap: var(--space-4);
}

.mail-status {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
}

.mail-status.is-admin,
.mail-status.is-environment {
  border-color: color-mix(in srgb, var(--color-success) 35%, var(--border-subtle));
  background: linear-gradient(120deg, var(--color-success-soft), transparent 70%), var(--surface-1);
}

.mail-status.is-none,
.mail-status.is-broken {
  border-color: color-mix(in srgb, var(--color-warning) 45%, var(--border-subtle));
  background: linear-gradient(120deg, var(--color-warning-soft), transparent 70%), var(--surface-1);
}

.mail-status__icon {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--color-success-text);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.mail-status.is-none .mail-status__icon,
.mail-status.is-broken .mail-status__icon {
  color: var(--color-warning-text);
}

.mail-status__body {
  flex: 1;
  min-width: 0;
}

.mail-status__body strong {
  font-size: var(--fs-body);
  font-weight: 650;
}

.mail-status__body p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  overflow-wrap: anywhere;
}

.mail-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  color: var(--color-danger-text);
  background: var(--color-danger-soft);
  font-size: var(--fs-sm);
}

.mail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.mail-side {
  display: grid;
  gap: var(--space-5);
  min-width: 0;
}

.mail-card {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.mail-card__head {
  display: flex;
  gap: var(--space-3);
}

.mail-card__head h2 {
  font-size: var(--fs-h3);
  font-weight: 650;
}

.mail-card__head p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.mail-card__icon {
  width: 34px;
  height: 34px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
}

.mail-card__icon.is-send { color: var(--color-accent-text); background: var(--color-accent-soft); }
.mail-card__icon.is-mail { color: var(--color-success-text); background: var(--color-success-soft); }

.mail-card__foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.presets__chip {
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  background: var(--surface-1);
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out);
}

.presets__chip:hover {
  color: var(--text-primary);
  border-color: var(--border-strong, var(--border-default));
}

.presets__chip.is-active {
  color: var(--color-primary-text);
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
  font-weight: 600;
}

.presets__chip:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.mail-hint {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.mail-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
}

.mail-form__wide {
  grid-column: 1 / -1;
}

.mail-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

.mail-switch strong {
  display: block;
  font-size: var(--fs-body);
  font-weight: 600;
}

.mail-switch small {
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.mail-test {
  display: flex;
  gap: var(--space-2);
}

.mail-test > :first-child {
  flex: 1;
  min-width: 0;
}

.mail-result {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.mail-result svg {
  flex: none;
  margin-top: 3px;
}

.mail-result.is-ok {
  color: var(--color-success-text);
  background: var(--color-success-soft);
}

.mail-result.is-failed {
  color: var(--color-danger-text);
  background: var(--color-danger-soft);
}

.mail-result small {
  opacity: 0.8;
}

.delivery {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
  margin: 0;
}

.delivery > div {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

.delivery dt {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.delivery dd {
  margin: 2px 0 0;
  font-size: 20px;
  font-weight: 650;
}

.delivery > div.is-bad dd {
  color: var(--color-danger-text);
}

.recent {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.recent li {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 0;
  border-bottom: 1px solid var(--border-subtle);
}

.recent li:last-child {
  border-bottom: 0;
}

.recent__main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 1px;
}

.recent__main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.recent__main small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.recent__main small.recent__error {
  color: var(--color-danger-text);
}

@media (max-width: 1100px) {
  .mail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .mail-form {
    grid-template-columns: 1fr;
  }

  .mail-status {
    flex-wrap: wrap;
  }

  /* The text keeps the full width; the reminder badge drops below it, lined up with the text. */
  .mail-status__body {
    flex-basis: calc(100% - 48px);
  }

  .mail-status > :last-child {
    margin-left: 48px;
  }

  .delivery dd {
    font-size: 17px;
  }
}
</style>
