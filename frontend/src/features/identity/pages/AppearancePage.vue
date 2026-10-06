<script setup lang="ts">
import { computed } from 'vue'
import { Check, Monitor, Moon, Sun } from 'lucide-vue-next'
import UiSwitch from '@/shared/ui/UiSwitch.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import { usePreferencesStore, type MotionPreference, type ThemePreference } from '@/stores/preferences'

const preferences = usePreferencesStore()

const THEMES: Array<{ value: ThemePreference; label: string; icon: typeof Sun; hint: string }> = [
  { value: 'light', label: '浅色', icon: Sun, hint: '温润纸白，适合白天' },
  { value: 'dark', label: '深色', icon: Moon, hint: '墨夜配色，夜间护眼' },
  { value: 'system', label: '跟随系统', icon: Monitor, hint: '自动匹配设备设置' },
]

const motionItems: Array<{ value: MotionPreference; label: string }> = [
  { value: 'system', label: '跟随系统' },
  { value: 'full', label: '完整动效' },
  { value: 'reduce', label: '减少动效' },
]

const motion = computed({
  get: () => preferences.motion,
  set: (value: MotionPreference) => {
    preferences.motion = value
  },
})

const collapsed = computed({
  get: () => preferences.sidebarCollapsed,
  set: (value: boolean) => {
    preferences.sidebarCollapsed = value
  },
})
</script>

<template>
  <section class="appearance">
    <article class="card setting-card">
      <header class="setting-card__head">
        <h2>主题</h2>
        <p>选择界面配色。切换会立即生效，并保存在这台设备上。</p>
      </header>
      <div class="themes" role="radiogroup" aria-label="主题">
        <button
          v-for="item in THEMES"
          :key="item.value"
          type="button"
          role="radio"
          class="theme"
          :class="[`theme--${item.value}`, { 'is-active': preferences.theme === item.value }]"
          :aria-checked="preferences.theme === item.value"
          @click="preferences.setTheme(item.value)"
        >
          <span class="theme__preview" aria-hidden="true">
            <span class="theme__side" />
            <span class="theme__body">
              <span class="theme__bar" />
              <span class="theme__card" />
              <span class="theme__card theme__card--short" />
            </span>
          </span>
          <span class="theme__label">
            <component :is="item.icon" :size="15" />
            <strong>{{ item.label }}</strong>
            <Check v-if="preferences.theme === item.value" :size="15" class="theme__check" />
          </span>
          <small>{{ item.hint }}</small>
        </button>
      </div>
    </article>

    <article class="card setting-card">
      <header class="setting-card__head">
        <h2>动效</h2>
        <p>减少动效会关闭位移、缩放和循环动画，只保留必要的淡入淡出。</p>
      </header>
      <UiSegmented v-model="motion" :items="motionItems" aria-label="动效偏好" />
    </article>

    <article class="card setting-card setting-card--row">
      <header class="setting-card__head">
        <h2>默认收起侧边栏</h2>
        <p>为编辑内容留出更多空间；随时可在侧边栏顶部展开。</p>
      </header>
      <UiSwitch v-model="collapsed" label="默认收起侧边栏" />
    </article>
  </section>
</template>

<style scoped>
.appearance {
  display: grid;
  gap: var(--space-5);
}

.setting-card {
  padding: var(--space-6);
  display: grid;
  gap: var(--space-5);
}

.setting-card--row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.setting-card__head h2 {
  font-size: var(--fs-h3);
}

.setting-card__head p {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.themes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

.theme {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  text-align: left;
  transition: border-color var(--dur-fast), box-shadow var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out);
}

.theme:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.theme.is-active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.theme__preview {
  height: 88px;
  display: flex;
  overflow: hidden;
  border-radius: var(--radius-md);
  background: #f9f7f3;
  box-shadow: inset 0 0 0 1px rgba(30, 28, 25, 0.08);
}

.theme__side {
  width: 26%;
  background: #f2efe9;
}

.theme__body {
  flex: 1;
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 10px;
}

.theme__bar {
  width: 50%;
  height: 7px;
  border-radius: 4px;
  background: #4a44d9;
}

.theme__card {
  height: 18px;
  border-radius: 5px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(30, 28, 25, 0.08);
}

.theme__card--short {
  width: 70%;
}

.theme--dark .theme__preview {
  background: #121118;
}

.theme--dark .theme__side {
  background: #1a1922;
}

.theme--dark .theme__bar {
  background: #817def;
}

.theme--dark .theme__card {
  background: #22212d;
}

.theme--system .theme__preview {
  background: linear-gradient(105deg, #f9f7f3 50%, #121118 50%);
}

.theme--system .theme__side {
  background: linear-gradient(105deg, #f2efe9 50%, #1a1922 50%);
}

.theme--system .theme__card {
  background: linear-gradient(105deg, #fff 50%, #22212d 50%);
}

.theme__label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 2px;
  font-size: var(--fs-body);
}

.theme__check {
  margin-left: auto;
  color: var(--color-primary);
}

.theme small {
  padding: 0 2px 2px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

@media (max-width: 720px) {
  .themes {
    grid-template-columns: 1fr;
  }
}
</style>
