<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { COMPARE_KEYS, glossVersionStatus, prettyValue, versionOrdinals } from '../labels'
import { compareResumeVersions } from '../services/resumeApi'
import type { ResumeCompareView, ResumeVersionView } from '../types'

const props = defineProps<{
  open: boolean
  versions: ResumeVersionView[]
}>()

const emit = defineEmits<{
  close: []
  forbidden: [error: unknown]
}>()

const leftId = ref('')
const rightId = ref('')
const compared = ref<ResumeCompareView | null>(null)
const comparing = ref(false)
const compareError = ref('')

type DiffLine = { type: 'same' | 'add' | 'del'; text: string }
type FieldDiff = { key: string; label: string; differs: boolean; lines: DiffLine[] }

const ordinals = computed(() => versionOrdinals(props.versions))
const sorted = computed(() => [...props.versions].sort((a, b) => (ordinals.value.get(b.id) ?? 0) - (ordinals.value.get(a.id) ?? 0)))
const sides = computed(() => (compared.value ? [compared.value.left, compared.value.right] : []))

function optionLabel(item: ResumeVersionView): string {
  return `版本 ${ordinals.value.get(item.id) ?? '—'} · ${glossVersionStatus(item.status)} · ${formatWhen(item.frozenAt || item.createdAt)}`
}

function snapshotText(version: ResumeVersionView | undefined, key: string): string {
  const value = version?.snapshot?.[key]
  if (key === 'keyOutcomes' && Array.isArray(value)) {
    // One line per outcome with its evidence state; never the raw ids.
    const lines = value.map((item) => {
      const outcome = (item ?? {}) as { text?: unknown; evidenceId?: unknown; waiveNoEvidence?: unknown }
      const state = outcome.evidenceId ? '已关联证据' : outcome.waiveNoEvidence ? '确认暂无证据' : '未处理'
      return `${String(outcome.text ?? '').trim()}（${state}）`
    })
    return lines.length ? lines.join('\n') : '—'
  }
  return prettyValue(value)
}

function diffLines(before: string, after: string): DiffLine[] {
  const a = before.split('\n')
  const b = after.split('\n')
  const m = a.length
  const n = b.length
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array<number>(n + 1).fill(0))
  for (let i = m - 1; i >= 0; i -= 1) {
    for (let j = n - 1; j >= 0; j -= 1) {
      const up = dp[i + 1]?.[j] ?? 0
      const side = dp[i]?.[j + 1] ?? 0
      dp[i][j] = a[i] === b[j] ? (dp[i + 1]?.[j + 1] ?? 0) + 1 : Math.max(up, side)
    }
  }
  const out: DiffLine[] = []
  let i = 0
  let j = 0
  while (i < m && j < n) {
    if (a[i] === b[j]) {
      out.push({ type: 'same', text: a[i] ?? '' })
      i += 1
      j += 1
    } else if ((dp[i + 1]?.[j] ?? 0) >= (dp[i]?.[j + 1] ?? 0)) {
      out.push({ type: 'del', text: a[i] ?? '' })
      i += 1
    } else {
      out.push({ type: 'add', text: b[j] ?? '' })
      j += 1
    }
  }
  while (i < m) {
    out.push({ type: 'del', text: a[i] ?? '' })
    i += 1
  }
  while (j < n) {
    out.push({ type: 'add', text: b[j] ?? '' })
    j += 1
  }
  return out
}

const fieldDiffs = computed<FieldDiff[]>(() => {
  if (!compared.value) {
    return []
  }
  return COMPARE_KEYS.map((field) => {
    const left = snapshotText(compared.value?.left, field.key)
    const right = snapshotText(compared.value?.right, field.key)
    return { key: field.key, label: field.label, differs: left !== right, lines: diffLines(left, right) }
  })
})

const summary = computed(() => {
  let added = 0
  let removed = 0
  let evidence = 0
  for (const field of fieldDiffs.value) {
    for (const line of field.lines) {
      if (line.type === 'same') {
        continue
      }
      if (field.key === 'keyOutcomes') {
        evidence += 1
      } else if (line.type === 'add') {
        added += 1
      } else {
        removed += 1
      }
    }
  }
  return { added, removed, evidence }
})

function sideLines(field: FieldDiff, isLeft: boolean): DiffLine[] {
  return field.lines.filter(
    (line) => line.type === 'same' || (isLeft ? line.type === 'del' : line.type === 'add'),
  )
}

function initDefaults(): void {
  const list = sorted.value
  leftId.value = list[1]?.id ?? list[0]?.id ?? ''
  rightId.value = list[0]?.id ?? ''
}

async function runCompare(): Promise<void> {
  if (!leftId.value || !rightId.value || leftId.value === rightId.value) {
    return
  }
  comparing.value = true
  compareError.value = ''
  try {
    compared.value = await compareResumeVersions(leftId.value, rightId.value)
  } catch (error) {
    compared.value = null
    if (isForbidden(error)) {
      emit('forbidden', error)
      return
    }
    compareError.value = errorMessage(error, '版本对比失败')
  } finally {
    comparing.value = false
  }
}

watch(
  () => [props.open, leftId.value, rightId.value] as const,
  ([open]) => {
    if (!open) {
      return
    }
    if (!leftId.value || !rightId.value) {
      initDefaults()
      return
    }
    if (leftId.value === rightId.value) {
      compared.value = null
      return
    }
    void runCompare()
  },
  { immediate: true },
)
</script>

<template>
  <AppModal :open="open" title="简历版本对比" :width="1080" @close="emit('close')">
    <div v-if="sorted.length < 2" class="compare">
      <AppEmpty text="至少需要两个版本才能对比" hint="再冻结一个版本后就可以对比差异。" icon="copy" />
    </div>
    <div v-else class="compare">
      <p class="muted">对比两个版本的简历差异：绿色为新增，红色为删除，橙色为证据链接变化。</p>

      <div class="compare__bar">
        <label class="compare__pick">
          <span class="field__label">旧版本</span>
          <AppSelect v-model="leftId" ariaLabel="旧版本" :disabled="comparing">
            <option v-for="item in sorted" :key="`l-${item.id}`" :value="item.id">
              {{ optionLabel(item) }}
            </option>
          </AppSelect>
        </label>
        <span class="compare__vs">VS</span>
        <label class="compare__pick">
          <span class="field__label">新版本</span>
          <AppSelect v-model="rightId" ariaLabel="新版本" :disabled="comparing">
            <option v-for="item in sorted" :key="`r-${item.id}`" :value="item.id">
              {{ optionLabel(item) }}
            </option>
          </AppSelect>
        </label>
      </div>

      <AppBanner v-if="compareError" tone="bad">{{ compareError }}</AppBanner>
      <AppBanner v-if="leftId && rightId && leftId === rightId" tone="warn">
        请选择两个不同的版本再对比。
      </AppBanner>

      <div v-if="comparing" class="bones" aria-busy="true">
        <div class="bone" />
        <div class="bone bone--short" />
      </div>

      <template v-else-if="compared">
        <div class="summary">
          <span class="summary__item summary__item--add">
            <AppIcon name="plus" :size="14" />
            新增 {{ summary.added }} 处
          </span>
          <span class="summary__item summary__item--del">
            <AppIcon name="x" :size="14" />
            删除 {{ summary.removed }} 处
          </span>
          <span class="summary__item summary__item--ev">
            <AppIcon name="link" :size="14" />
            证据变化 {{ summary.evidence }} 处
          </span>
        </div>

        <div class="panes">
          <article v-for="(side, sideIndex) in sides" :key="side.id" class="pane">
            <header class="pane__head">
              <strong>{{ optionLabel(side) }}</strong>
              <AppTag :tone="side.immutable ? 'orange' : 'gray'">
                {{ side.immutable ? '已冻结' : '待确认' }}
              </AppTag>
            </header>
            <section
              v-for="field in fieldDiffs"
              :key="field.key"
              class="pane__field"
              :class="{ 'is-diff': field.differs }"
            >
              <div class="pane__field-head">
                <span class="pane__label">{{ field.label }}</span>
                <AppTag v-if="field.differs" :tone="field.key === 'keyOutcomes' ? 'orange' : 'blue'">
                  {{ field.key === 'keyOutcomes' ? '证据变化' : '有变更' }}
                </AppTag>
              </div>
              <div class="pane__lines">
                <p
                  v-for="(line, lineIndex) in sideLines(field, sideIndex === 0)"
                  :key="lineIndex"
                  class="pane__line"
                  :class="`pane__line--${line.type}`"
                >
                  {{ line.text || ' ' }}
                </p>
              </div>
            </section>
          </article>
        </div>

        <p class="fine">
          暂不支持直接恢复到旧版本；如需沿用旧内容，请在工作台中手动修改。
        </p>
      </template>
    </div>

    <template #footer>
      <AppButton variant="ghost" @click="emit('close')">关闭</AppButton>
    </template>
  </AppModal>
</template>

<style scoped>
.compare {
  display: grid;
  gap: 14px;
}

.compare__bar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.compare__pick {
  flex: 1;
  display: grid;
  gap: 6px;
}

.compare__vs {
  align-self: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}

.bones {
  display: grid;
  gap: 10px;
}

.summary {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.summary__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
}

.summary__item--add {
  background: var(--success-soft);
  color: var(--color-success);
}

.summary__item--del {
  background: var(--danger-soft);
  color: var(--color-danger);
}

.summary__item--ev {
  background: var(--warning-soft);
  color: var(--color-warning);
}

.panes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-items: start;
}

.pane {
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  background: var(--surface);
  overflow: hidden;
}

.pane__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  background: var(--surface-2);
  font-size: 13px;
}

.pane__field {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-subtle);
}

.pane__field:last-child {
  border-bottom: none;
}

.pane__field.is-diff {
  border-left: 3px solid var(--color-warning);
}

.pane__field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.pane__label {
  font-size: 13px;
  font-weight: 600;
}

.pane__lines {
  display: grid;
  gap: 2px;
}

.pane__line {
  font-size: 12.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 1px 6px;
  border-radius: 4px;
}

.pane__line--add {
  background: var(--success-soft);
  color: var(--color-success-text);
}

.pane__line--del {
  background: var(--danger-soft);
  color: var(--color-danger-text);
  text-decoration: line-through;
}

@media (max-width: 900px) {
  .panes {
    grid-template-columns: 1fr;
  }
}
</style>
