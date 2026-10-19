<script setup lang="ts">
/**
 * What this match will hand to the model, read back from the server's frozen authorisation: the
 * confirmed JD, the chosen resume revision, the authorised library items — and the fields that are
 * stripped before anything leaves.
 */
import { computed, ref, shallowRef, watch } from 'vue'
import { BriefcaseBusiness, EyeOff, FileText, FolderOpen, ShieldCheck } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { fetchRedactionPreview } from '../services/jobMatchApi'
import type { RedactionPreview } from '../types'

const props = defineProps<{
  open: boolean
  matchId: string
  jobTitle?: string | null
  requirementCount: number
  resumeTitle?: string | null
}>()
const emit = defineEmits<{ close: [] }>()

const FIELD_LABELS: Record<string, string> = {
  name: '姓名',
  phone: '手机号',
  email: '邮箱',
  wechat: '微信',
  qq: 'QQ',
  links: '个人链接',
  personalLinks: '个人链接',
  website: '个人主页',
  url: '个人主页',
  github: 'GitHub',
  linkedin: '领英',
  birthDate: '出生日期',
  photoFileId: '照片',
  fullAddress: '详细地址',
  identityNumber: '身份证号',
  age: '年龄',
  gender: '性别',
  maritalStatus: '婚姻状况',
  ethnicity: '民族',
  photoRef: '照片',
  photo: '照片',
  avatar: '照片',
}

const preview = shallowRef<RedactionPreview | null>(null)
const loading = ref(false)
const error = ref<unknown>(null)

type Source = { key: string; type: string; title: string; relevance: number | null }

const sources = computed<Source[]>(() => (preview.value?.includedSources ?? []).map((item, index) => {
  const type = String(item.source_type ?? item.sourceType ?? '')
  const relevance = Number(item.relevance)
  return {
    key: `${type}:${String(item.source_id ?? item.sourceId ?? index)}`,
    type: type === 'CAREER_FILE' ? '资料文件' : '经历记录',
    title: String(item.title ?? '').trim() || '未命名资料',
    relevance: Number.isFinite(relevance) ? relevance : null,
  }
}))
/** Photo, photoRef and avatar are one thing to the user. */
const hiddenFields = computed(() => [...new Set((preview.value?.excludedFields ?? []).map(field => FIELD_LABELS[field] ?? field))])

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    preview.value = await fetchRedactionPreview(props.matchId)
  } catch (reason) {
    error.value = reason
  } finally {
    loading.value = false
  }
}

watch(() => props.open, open => { if (open) void load() }, { immediate: true })
</script>

<template>
  <UiDialog :open="open" title="发送给 AI 的内容" description="开始分析后，模型只会读到下面这些内容。" width="md" @close="emit('close')">
    <div class="jm-rp">
      <div v-if="loading" class="jm-rp__loading"><UiSkeleton v-for="n in 3" :key="n" height="52px" radius="var(--radius-md)" /></div>
      <UiErrorState v-else-if="error" compact title="读取发送范围失败" :error="error" @retry="load" />
      <template v-else-if="preview">
        <section>
          <h4>会发送</h4>
          <ul class="jm-rp__list">
            <li>
              <span class="jm-rp__icon"><BriefcaseBusiness :size="16" /></span>
              <p><strong>{{ jobTitle || '目标岗位' }}</strong><small>已确认的岗位 JD · {{ requirementCount }} 条要求</small></p>
            </li>
            <li>
              <span class="jm-rp__icon"><FileText :size="16" /></span>
              <p><strong>{{ resumeTitle || '投递简历' }}</strong><small>本次冻结的简历正式修订，下方字段已移除</small></p>
            </li>
            <li v-for="item in sources" :key="item.key">
              <span class="jm-rp__icon is-library"><FolderOpen :size="16" /></span>
              <p><strong>{{ item.title }}</strong><small>{{ item.type }}<template v-if="item.relevance !== null"> · 相关度 {{ item.relevance }}</template></small></p>
            </li>
          </ul>
          <p v-if="!sources.length" class="jm-rp__note">本次没有授权资料库内容，只分析 JD 和简历。</p>
        </section>
        <section>
          <h4>不会发送</h4>
          <div class="jm-rp__chips">
            <span v-for="field in hiddenFields" :key="field"><EyeOff :size="12" />{{ field }}</span>
          </div>
        </section>
        <p class="jm-rp__notice"><ShieldCheck :size="16" />{{ preview.notice }}。授权只作用于这一次匹配。</p>
      </template>
    </div>
    <template #footer>
      <UiButton variant="secondary" @click="emit('close')">知道了</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.jm-rp {
  display: grid;
  gap: var(--space-5);
}

.jm-rp__loading {
  display: grid;
  gap: var(--space-2);
}

.jm-rp h4 {
  margin-bottom: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  font-weight: 650;
  letter-spacing: 0.04em;
}

.jm-rp__list {
  list-style: none;
  display: grid;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.jm-rp__list li {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 12px;
}

.jm-rp__list li + li {
  border-top: 1px solid var(--border-subtle);
}

.jm-rp__list p {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.jm-rp__list strong {
  overflow: hidden;
  font-size: var(--fs-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.jm-rp__list small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.jm-rp__icon {
  width: 32px;
  height: 32px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  color: var(--color-primary-text);
  background: var(--color-primary-soft);
}

.jm-rp__icon.is-library {
  color: var(--color-success-text);
  background: var(--color-success-soft);
}

.jm-rp__note {
  margin-top: var(--space-2);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.jm-rp__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.jm-rp__chips span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  text-decoration: line-through;
  text-decoration-color: color-mix(in srgb, var(--text-tertiary) 60%, transparent);
}

.jm-rp__notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  color: var(--color-success-text);
  background: var(--color-success-soft);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.jm-rp__notice svg {
  flex: none;
  margin-top: 1px;
}
</style>
