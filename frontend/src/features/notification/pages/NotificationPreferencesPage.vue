<script setup lang="ts">
/** 通知偏好 (docs/phase2/03): per kind of notice, show it in the notification center and/or email it. */
import { computed, onMounted } from 'vue'
import { Bell, Info, Mail, RefreshCw, ShieldCheck } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import UiSwitch from '@/shared/ui/UiSwitch.vue'
import { toast } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import { fetchNotificationPreferences, saveNotificationPreferences } from '../services/notificationApi'
import { PREFERENCE_COPY } from '../labels'
import type { NotificationPreferenceItem } from '../types'

const page = useLoadState(() => fetchNotificationPreferences())
const prefs = computed(() => page.data.value)
/** Email can be switched on only with a sender on this site and a verified address on the account. */
const emailReady = computed(() => Boolean(prefs.value?.emailAvailable && prefs.value?.emailAddress))

async function toggle(item: NotificationPreferenceItem, channel: 'inApp' | 'email', value: boolean): Promise<void> {
  const current = prefs.value
  if (!current) return
  const next = { ...item, [channel]: value }
  const previous = current
  // Show the change at once; the server's answer replaces it (or puts it back).
  page.data.value = { ...current, items: current.items.map(entry => (entry.type === item.type ? next : entry)) }
  try {
    page.data.value = await saveNotificationPreferences([next])
    toast.success('通知设置已保存', { dedupeKey: 'notification-preferences' })
  } catch (reason) {
    page.data.value = previous
    toast.error(errorMessage(reason, '通知设置保存失败'))
  }
}

onMounted(page.load)
</script>

<template>
  <div class="settings-section notification-prefs">
    <header class="settings-section__head">
      <div class="settings-section__title">
        <span class="settings-section__icon"><Bell :size="18" /></span>
        <div>
          <h2>通知</h2>
          <p>选择哪些消息出现在通知中心、哪些同时发到你的邮箱。</p>
        </div>
      </div>
      <div class="settings-section__actions">
        <UiButton variant="secondary" :icon="RefreshCw" :pending="page.loading.value && page.loaded.value" @click="page.load">刷新</UiButton>
      </div>
    </header>

    <PageState :loading="page.loading.value" :error="page.error.value" :loaded="page.loaded.value" error-title="通知设置读取失败" compact @retry="page.load">
      <template #skeleton>
        <UiSkeleton height="64px" radius="var(--radius-lg)" />
        <UiSkeleton height="260px" radius="var(--radius-lg)" />
      </template>

      <template v-if="prefs">
        <section class="settings-card mailbox" :class="{ 'is-ready': emailReady }" aria-label="接收邮箱">
          <span class="mailbox__icon"><Mail :size="17" /></span>
          <div class="mailbox__body">
            <template v-if="!prefs.emailAvailable">
              <strong>本站暂未开通邮件提醒</strong>
              <p>消息会照常出现在通知中心。开通后可以在这里打开邮件提醒。</p>
            </template>
            <template v-else-if="!prefs.emailAddress">
              <strong>还没有可接收提醒的邮箱</strong>
              <p>在“账号与安全”里绑定并验证邮箱后，就能把面试提醒、简历回执发到邮箱。</p>
            </template>
            <template v-else>
              <strong>提醒邮件发往 {{ prefs.emailAddress }}</strong>
              <p>只发你在下面打开的几类消息；每条消息最多发一封，邮件底部可以随时回到这里关闭。</p>
            </template>
          </div>
          <UiButton v-if="prefs.emailAvailable && !prefs.emailAddress" size="sm" variant="secondary" to="/account?bind=email" data-testid="bind-email">去绑定邮箱</UiButton>
        </section>

        <section class="settings-card prefs" aria-labelledby="prefs-title">
          <h3 id="prefs-title" class="sr-only">通知类型</h3>
          <div class="prefs__head" aria-hidden="true">
            <span>消息类型</span>
            <span>通知中心</span>
            <span>邮件</span>
          </div>
          <ul class="prefs__list">
            <li v-for="item in prefs.items" :key="item.type" class="prefs__row">
              <div class="prefs__copy">
                <strong>{{ PREFERENCE_COPY[item.type]?.title ?? item.type }}</strong>
                <small>{{ PREFERENCE_COPY[item.type]?.detail }}</small>
              </div>
              <div class="prefs__switch">
                <span class="prefs__channel" aria-hidden="true">通知中心</span>
                <UiSwitch
                  :model-value="item.inApp"
                  :label="`${PREFERENCE_COPY[item.type]?.title ?? item.type}：在通知中心显示`"
                  @update:model-value="toggle(item, 'inApp', $event)"
                />
              </div>
              <div class="prefs__switch">
                <span class="prefs__channel" aria-hidden="true">邮件</span>
                <UiSwitch
                  :model-value="emailReady && item.email"
                  :disabled="!emailReady"
                  :label="`${PREFERENCE_COPY[item.type]?.title ?? item.type}：发送邮件`"
                  @update:model-value="toggle(item, 'email', $event)"
                />
              </div>
            </li>
          </ul>
          <p class="prefs__locked"><ShieldCheck :size="14" />账号安全通知（改密码、新设备登录、更换联系方式等）不能关闭：会一直显示在通知中心，有已验证的邮箱时也会发邮件。数据导出与删除、AI 任务结果的通知会一直显示在通知中心。</p>
        </section>

        <p class="prefs__hint"><Info :size="14" />关掉“通知中心”后，这类消息不会再出现在铃铛里；已经收到的消息不受影响。</p>
      </template>
    </PageState>
  </div>
</template>

<style scoped>
.notification-prefs :deep(.page-state) {
  display: grid;
  gap: var(--space-4);
}

.mailbox {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
}

.mailbox__icon {
  width: 34px;
  height: 34px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  background: var(--surface-3);
}

.mailbox.is-ready .mailbox__icon {
  color: var(--color-success-text);
  background: var(--color-success-soft);
}

.mailbox__body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}

.mailbox__body strong {
  font-size: var(--fs-body);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.mailbox__body p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.prefs {
  padding: var(--space-2) var(--space-5) var(--space-4);
}

.prefs__head,
.prefs__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 96px 72px;
  align-items: center;
  gap: var(--space-3);
}

.prefs__head {
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 600;
}

.prefs__head span:not(:first-child),
.prefs__switch {
  justify-self: center;
}

.prefs__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.prefs__row {
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--border-subtle);
}

.prefs__copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.prefs__copy strong {
  font-size: var(--fs-body);
  font-weight: 600;
}

.prefs__copy small {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.prefs__switch {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.prefs__channel {
  display: none;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.prefs__locked,
.prefs__hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.prefs__locked {
  padding-top: var(--space-4);
}

.prefs__locked svg,
.prefs__hint svg {
  flex: none;
  margin-top: 2px;
}

/* Narrow screens: each kind becomes a small card with its two switches labelled. */
@media (max-width: 560px) {
  .mailbox {
    flex-wrap: wrap;
    padding: var(--space-4);
  }

  .prefs {
    padding: var(--space-1) var(--space-4) var(--space-4);
  }

  .prefs__head {
    display: none;
  }

  .prefs__row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-3);
  }

  .prefs__copy {
    grid-column: 1 / -1;
  }

  .prefs__switch {
    justify-self: start;
  }

  .prefs__channel {
    display: inline;
  }
}
</style>
