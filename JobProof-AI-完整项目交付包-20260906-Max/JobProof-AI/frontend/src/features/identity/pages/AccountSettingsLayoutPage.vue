<script setup lang="ts">
import AppChrome from '@/shared/ui/AppChrome.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import SettingsNav from '@/shared/ui/SettingsNav.vue'
</script>

<template>
  <AppChrome>
    <main class="settings-page">
      <header class="settings-page__head">
        <span class="settings-page__mark" aria-hidden="true">
          <AppIcon name="settings" :size="21" />
        </span>
        <div>
          <p class="settings-page__eyebrow">账户设置</p>
          <h1>账号与数据</h1>
          <p>统一管理登录安全、AI 使用授权和个人数据权利。</p>
        </div>
      </header>

      <div class="settings-workspace">
        <aside class="settings-workspace__nav" aria-label="账户设置导航">
          <div class="settings-workspace__nav-head">
            <strong>设置中心</strong>
            <span>所有变更均与当前账号关联</span>
          </div>
          <SettingsNav />
        </aside>

        <section class="settings-workspace__content">
          <RouterView v-slot="{ Component, route }">
            <Transition name="settings-panel" mode="out-in">
              <component :is="Component" :key="route.path" />
            </Transition>
          </RouterView>
        </section>
      </div>
    </main>
  </AppChrome>
</template>

<style>
.settings-page {
  width: min(100%, 1320px);
  margin: 0 auto;
  padding: 30px 32px 56px;
}

.settings-page__head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}

.settings-page__mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid #d9e5fb;
  border-radius: 11px;
  background: #eef4ff;
  color: var(--primary);
}

.settings-page__eyebrow {
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
}

.settings-page__head h1 {
  margin-top: 1px;
  font-size: 24px;
  letter-spacing: 0;
}

.settings-page__head div > p:last-child {
  margin-top: 3px;
  color: var(--text-2);
  font-size: 13px;
}

.settings-workspace {
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.settings-workspace__nav {
  position: sticky;
  top: 84px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.045);
}

.settings-workspace__nav-head {
  display: grid;
  gap: 2px;
  padding: 6px 9px 13px;
  margin-bottom: 7px;
  border-bottom: 1px solid #edf0f5;
}

.settings-workspace__nav-head strong {
  font-size: 13px;
}

.settings-workspace__nav-head span {
  color: var(--text-3);
  font-size: 11px;
}

.settings-workspace__content {
  min-width: 0;
}

.settings-section {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.settings-section__head {
  min-height: 58px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 2px 2px 0;
}

.settings-section__title {
  display: flex;
  align-items: flex-start;
  gap: 11px;
}

.settings-section__icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 9px;
  background: var(--primary-soft);
  color: var(--primary);
}

.settings-section__head h2 {
  font-size: 19px;
  letter-spacing: 0;
}

.settings-section__head p {
  margin-top: 3px;
  color: var(--text-2);
  font-size: 12.5px;
}

.settings-section__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.settings-card {
  overflow: hidden;
  border: 1px solid #e0e6ef;
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.035);
}

.settings-card > .card__head {
  padding: 18px 20px 8px;
}

.settings-card > .card__body {
  padding: 12px 20px 20px;
}

.settings-panel-enter-active {
  transition: opacity 220ms ease, transform 260ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.settings-panel-leave-active {
  transition: opacity 110ms ease, transform 110ms ease;
}

.settings-panel-enter-from {
  opacity: 0;
  transform: translateY(7px);
}

.settings-panel-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

.settings-section .ack {
  position: relative;
  min-height: 42px;
  align-items: center;
  gap: 11px;
  margin: 10px 0;
  padding: 10px 12px;
  border: 1px solid #e2e7ef;
  border-radius: 9px;
  background: #fafbfd;
  transition: border-color 180ms ease, background-color 180ms ease, box-shadow 180ms ease;
}

.settings-section .ack:hover {
  border-color: #c9d7ee;
  background: #f7faff;
}

.settings-section .ack:has(input:focus-visible) {
  border-color: rgba(37, 99, 235, 0.6);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.settings-section .ack input[type='checkbox'] {
  width: 19px;
  height: 19px;
  margin: 0;
  display: grid;
  place-content: center;
  flex: 0 0 auto;
  appearance: none;
  border: 1.5px solid #b9c3d2;
  border-radius: 5px;
  background: #fff;
  cursor: pointer;
  transition: border-color 160ms ease, background-color 160ms ease, transform 140ms ease, box-shadow 160ms ease;
}

.settings-section .ack input[type='checkbox']::before {
  width: 9px;
  height: 5px;
  border: solid #fff;
  border-width: 0 0 2px 2px;
  content: '';
  opacity: 0;
  transform: translateY(-1px) rotate(-45deg) scale(0.7);
  transition: opacity 120ms ease, transform 160ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.settings-section .ack input[type='checkbox']:checked {
  border-color: var(--primary);
  background: var(--primary);
  box-shadow: 0 2px 7px rgba(37, 99, 235, 0.22);
}

.settings-section .ack input[type='checkbox']:checked::before {
  opacity: 1;
  transform: translateY(-1px) rotate(-45deg) scale(1);
}

.settings-section .ack input[type='checkbox']:active {
  transform: scale(0.9);
}

.settings-section .ack input[type='checkbox']:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.settings-section .ack span {
  line-height: 1.55;
}

@media (max-width: 940px) {
  .settings-page {
    padding: 24px 20px 44px;
  }

  .settings-workspace {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .settings-workspace__nav {
    position: static;
  }

  .settings-workspace__nav-head {
    display: none;
  }
}

@media (max-width: 620px) {
  .settings-page {
    padding: 18px 14px 36px;
  }

  .settings-page__head {
    align-items: flex-start;
    margin-bottom: 16px;
  }

  .settings-page__mark {
    width: 40px;
    height: 40px;
  }

  .settings-page__head h1 {
    font-size: 21px;
  }

  .settings-section__head {
    min-height: 0;
    align-items: stretch;
    flex-direction: column;
  }

  .settings-section__actions {
    width: 100%;
  }

  .settings-section__actions .btn {
    flex: 1;
  }

  .settings-card > .card__head {
    padding: 16px 16px 6px;
  }

  .settings-card > .card__body {
    padding: 10px 16px 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .settings-panel-enter-active,
  .settings-panel-leave-active,
  .settings-section .ack,
  .settings-section .ack input[type='checkbox'],
  .settings-section .ack input[type='checkbox']::before {
    transition: none !important;
  }
}
</style>
