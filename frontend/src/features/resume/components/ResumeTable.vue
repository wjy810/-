<script setup lang="ts">
/** Resumes and their versions as a table; versions sit indented under their resume in "全部". */
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import UiBadge from '@/shared/ui/UiBadge.vue'
import { formatWhen } from '@/shared/lib/datetime'
import { coverageSub, rowAction, rowLink, ROW_BADGE_TONE, type ResumeRow } from '../utils/resumeRows'

defineProps<{
  rows: ResumeRow[]
  /** Indent versions under their resume (the "全部" tab). */
  nested?: boolean
  /** Evidence coverage only exists for resumes with key outcomes; without any, the column is noise. */
  showCoverage?: boolean
  busy?: boolean
}>()
const emit = defineEmits<{
  rename: [row: ResumeRow]
  copy: [row: ResumeRow]
  archive: [row: ResumeRow]
  restore: [row: ResumeRow]
  delete: [row: ResumeRow]
}>()

</script>

<template>
  <div class="table-scroll">
    <table class="data-table">
      <thead>
        <tr>
          <th>简历名称</th>
          <th>状态</th>
          <th v-if="showCoverage">证据覆盖度</th>
          <th>更新时间</th>
          <th class="ops-col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.key" :class="{ 'is-child': nested && row.kind === 'VERSION' }" data-testid="resume-row">
          <td data-label="简历名称">
            <div class="rname">
              <span class="rtype" :class="`rtype--${row.rowTone}`">
                <JobProofIcon :name="row.rowIcon" :size="16" />
              </span>
              <div class="rname__body">
                <div class="rname__line">
                  <span class="rname__title" :title="row.title">{{ row.title }}</span>
                  <UiBadge :tone="ROW_BADGE_TONE[row.rowTone]">{{ row.rowType }}<template v-if="row.ordinal"> · 第 {{ row.ordinal }} 版</template></UiBadge>
                </div>
                <p v-if="row.lockNote" class="rname__lock">
                  <AppIcon name="lock" :size="12" />
                  内容不可修改
                </p>
              </div>
            </div>
          </td>
          <td data-label="状态">
            <div class="rcell">
              <span class="rcell__line">
                <span class="dot" :class="`dot--${row.statusTone}`" />
                {{ row.statusLabel }}
              </span>
              <span class="fine">{{ row.statusSub }}</span>
            </div>
          </td>
          <td v-if="showCoverage" data-label="证据覆盖度">
            <div v-if="row.coverage !== null" class="rcell">
              <span class="rcell__line">
                <span class="dot" :class="row.coverage >= 60 ? 'dot--green' : 'dot--orange'" />
                {{ row.coverage }}%
              </span>
              <span class="fine">{{ coverageSub(row.coverage) }}</span>
            </div>
            <span v-else class="muted">—</span>
          </td>
          <td class="muted" data-label="更新时间">{{ formatWhen(row.updatedAt) }}</td>
          <td data-label="操作">
            <div class="ops">
              <RouterLink class="text-link" :to="rowLink(row)">{{ rowAction(row) }}</RouterLink>
              <template v-if="row.kind === 'MASTER'">
                <button v-if="!row.archived" class="text-link" type="button" :disabled="busy" data-testid="rename-resume" @click="emit('rename', row)">重命名</button>
                <button class="text-link" type="button" :disabled="busy" @click="emit('copy', row)">复制</button>
                <button v-if="!row.archived" class="text-link text-link--danger" type="button" :disabled="busy" @click="emit('archive', row)">归档</button>
                <button v-else class="text-link" type="button" :disabled="busy" @click="emit('restore', row)">恢复</button>
                <button class="text-link text-link--danger" type="button" :disabled="busy" @click="emit('delete', row)">永久删除</button>
              </template>
              <button v-else-if="row.rowType === '冻结版本'" class="text-link text-link--danger" type="button" :disabled="busy" @click="emit('delete', row)">永久删除</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.data-table tr.is-child td:first-child {
  padding-left: 38px;
  position: relative;
}

.data-table tr.is-child td:first-child::before {
  content: '';
  position: absolute;
  left: 22px;
  top: 0;
  bottom: 50%;
  width: 10px;
  border-left: 1.5px solid var(--border-default);
  border-bottom: 1.5px solid var(--border-default);
  border-bottom-left-radius: 6px;
}

.table-scroll {
  overflow-x: auto;
}

.rname {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 220px;
}

.rtype {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.rtype--blue {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.rtype--green {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.rtype--orange {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.rname__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.rname__line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.rname__title {
  font-weight: 500;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rname__lock {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-danger);
}

.rcell {
  display: grid;
  gap: 2px;
  justify-items: start;
}

.rcell__line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot--green {
  background: var(--color-success);
}

.dot--orange {
  background: var(--color-warning);
}

.dot--red {
  background: var(--color-danger);
}

.dot--gray {
  background: var(--text-tertiary);
}

.dot--blue {
  background: var(--color-primary);
}

.bound-text {
  color: var(--color-primary);
  font-size: 13px;
}

.ops {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.ops-col {
  width: 1%;
}

.text-link--danger {
  color: var(--color-danger);
}

.text-link--danger:hover {
  color: var(--color-danger);
}

.text-link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 560px) {
  .table-scroll {
    min-width: 0;
    max-width: 100%;
  }

}

@media (max-width: 560px) {
  .table-scroll {
    overflow: visible;
    padding: 10px;
  }

  .data-table,
  .data-table tbody {
    width: 100%;
    min-width: 0;
    display: grid;
    gap: 10px;
  }

  .data-table thead {
    display: none;
  }

  .data-table tbody tr {
    min-width: 0;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    padding: 14px;
    border: 1px solid var(--border-default);
    border-radius: var(--radius-md);
    background: var(--surface-1);
  }

  .data-table td {
    min-width: 0;
    display: grid;
    align-content: start;
    gap: 4px;
    padding: 0;
    border: 0;
    overflow-wrap: anywhere;
  }

  .data-table td::before {
    content: attr(data-label);
    color: var(--text-tertiary);
    font-size: 12px;
    line-height: 1.4;
  }

  .data-table td:first-child,
  .data-table td:last-child {
    grid-column: 1 / -1;
  }

  .data-table td:first-child::before,
  .data-table td:last-child::before {
    display: none;
  }

  .rname {
    min-width: 0;
  }

  .rname__title {
    max-width: 100%;
    white-space: normal;
    overflow-wrap: anywhere;
  }

  .ops {
    width: 100%;
    flex-wrap: wrap;
    gap: 8px 14px;
    padding-top: 2px;
    white-space: normal;
  }

}
</style>
