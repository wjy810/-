<script setup lang="ts">
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiKbd from '@/shared/ui/UiKbd.vue'
import { useCommandPaletteStore } from '@/stores/commandPalette'

const palette = useCommandPaletteStore()

const SECTIONS: Array<{ title: string; items: Array<{ keys: string[]; label: string; then?: boolean }> }> = [
  {
    title: '全局',
    items: [
      { keys: ['mod', 'k'], label: '搜索或跳转' },
      { keys: ['?'], label: '显示快捷键' },
      { keys: ['g', 'd'], label: '前往工作台', then: true },
      { keys: ['g', 'r'], label: '前往我的简历', then: true },
      { keys: ['g', 'm'], label: '前往岗位匹配', then: true },
      { keys: ['g', 'p'], label: '前往职业规划', then: true },
      { keys: ['g', 'i'], label: '前往模拟面试', then: true },
      { keys: ['esc'], label: '关闭弹窗或抽屉' },
    ],
  },
  {
    title: 'AI 简历工作台',
    items: [
      { keys: ['mod', 'enter'], label: '确认当前卡片' },
      { keys: ['mod', 's'], label: '立即保存草稿' },
      { keys: ['mod', '/'], label: '聚焦对话输入框' },
      { keys: ['mod', 'e'], label: '导出 PDF' },
      { keys: ['alt', '1'], label: '切换到对话视图' },
      { keys: ['alt', '2'], label: '切换到编辑视图' },
      { keys: ['alt', '3'], label: '切换到设计视图' },
      { keys: ['alt', '4'], label: '切换到模板视图' },
    ],
  },
]
</script>

<template>
  <UiDialog v-model:open="palette.shortcutsOpen" title="键盘快捷键" description="熟练使用快捷键，效率翻倍。" width="lg">
    <div class="shortcuts">
      <section v-for="section in SECTIONS" :key="section.title" class="shortcuts__section">
        <h3>{{ section.title }}</h3>
        <ul>
          <li v-for="item in section.items" :key="item.label">
            <span>{{ item.label }}</span>
            <span class="shortcuts__keys">
              <template v-if="item.then">
                <UiKbd :keys="[item.keys[0]]" /><small>然后</small><UiKbd :keys="[item.keys[1]]" />
              </template>
              <UiKbd v-else :keys="item.keys" />
            </span>
          </li>
        </ul>
      </section>
    </div>
  </UiDialog>
</template>

<style scoped>
.shortcuts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6);
}

.shortcuts__section h3 {
  margin-bottom: var(--space-2);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 650;
  letter-spacing: 0.04em;
}

.shortcuts__section ul {
  list-style: none;
  display: grid;
}

.shortcuts__section li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 9px 0;
  border-bottom: 1px solid var(--border-subtle);
  font-size: var(--fs-sm);
}

.shortcuts__keys {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-tertiary);
  font-size: 11px;
}

@media (max-width: 640px) {
  .shortcuts {
    grid-template-columns: 1fr;
  }
}
</style>
