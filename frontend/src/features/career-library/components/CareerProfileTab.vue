<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowRight, Camera, CheckCircle2, Info, Link2, LoaderCircle, LockKeyhole, MapPin,
  ShieldCheck, Target, Trash2, UserRound,
} from 'lucide-vue-next'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import JobTaxonomyPicker from '@/features/ai-resume/components/JobTaxonomyPicker.vue'
import { listJobTaxonomy } from '@/features/ai-resume/services/aiResumeApi'
import { errorMessage } from '@/shared/api/types'
import { pollTask } from '@/shared/lib/pollTask'
import type { JobTaxonomyNode, JobTaxonomySelection } from '@/features/ai-resume/types'
import type { CareerOverview, CareerProfile } from '../types'
import { commitCareerAvatar, deleteCareerAvatar, uploadCareerAvatar } from '../services/careerLibraryApi'

const props = defineProps<{
  profile: CareerProfile
  overview: CareerOverview
  pending?: boolean
}>()
const emit = defineEmits<{
  save: [value: { basics: Record<string, unknown>; intentions: Record<string, unknown>; preferences: Record<string, unknown>; summary: string }]
  profileUpdated: [value: CareerProfile]
  notice: [message: string]
  error: [message: string]
}>()

const form = reactive({
  name: '', email: '', phone: '', location: '', links: '', targetJob: '', jobCategory: '',
  taxonomyNodeId: '', taxonomyCategoryId: '', taxonomyGroupId: '', targetCity: '', workMode: '', summary: '',
})
const privacyOpen = ref(false)
const categoryNodes = ref<JobTaxonomyNode[]>([])
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarPending = ref(false)
const avatarReady = ref(Boolean(props.profile.avatarFileId))
const avatarNonce = ref(Date.now())
let baseline = ''

const workModes = [
  { value: '', label: '请选择办公方式' },
  { value: 'ONSITE', label: '现场办公' },
  { value: 'HYBRID', label: '混合办公' },
  { value: 'REMOTE', label: '远程办公' },
]
const cityOptions = [
  { value: '', label: '请选择目标城市' },
  { value: '北京', label: '北京' }, { value: '上海', label: '上海' }, { value: '广州', label: '广州' },
  { value: '深圳', label: '深圳' }, { value: '杭州', label: '杭州' }, { value: '成都', label: '成都' },
  { value: '武汉', label: '武汉' }, { value: '南京', label: '南京' }, { value: '其他', label: '其他' },
]

const initial = computed(() => form.name.trim().charAt(0) || '求')
const completeness = computed(() => props.overview.profileCompleteness || props.profile.completeness || 0)
const missing = computed(() => props.overview.missingItems || props.profile.missingItems || [])
const dirty = computed(() => baseline !== JSON.stringify(form))

function read(source: Record<string, unknown>, key: string): string {
  const value = source[key]
  return typeof value === 'string' ? value : ''
}

function hydrate(profile: CareerProfile): void {
  Object.assign(form, {
    name: read(profile.basics, 'name'), email: read(profile.basics, 'email'),
    phone: read(profile.basics, 'phone'), location: read(profile.basics, 'location'),
    links: Array.isArray(profile.basics.links) ? profile.basics.links.join('\n') : read(profile.basics, 'links'),
    targetJob: read(profile.intentions, 'targetJob'), jobCategory: read(profile.intentions, 'jobCategory'),
    taxonomyNodeId: read(profile.intentions, 'taxonomyNodeId'),
    taxonomyCategoryId: read(profile.intentions, 'taxonomyCategoryId'),
    taxonomyGroupId: read(profile.intentions, 'taxonomyGroupId'),
    targetCity: read(profile.preferences, 'targetCity'), workMode: read(profile.preferences, 'workMode'),
    summary: profile.summary || '',
  })
  baseline = JSON.stringify(form)
}

async function chooseJob(selection: JobTaxonomySelection): Promise<void> {
  form.targetJob = selection.job.displayName
  form.taxonomyNodeId = selection.job.id
  form.taxonomyCategoryId = selection.categoryId
  form.taxonomyGroupId = selection.groupId
  if (!categoryNodes.value.length) {
    try { categoryNodes.value = (await listJobTaxonomy()).filter((node) => node.level === 'CATEGORY') }
    catch { categoryNodes.value = [] }
  }
  form.jobCategory = categoryNodes.value.find((node) => node.id === selection.categoryId)?.displayName
    || selection.job.catalogOccupationCode || '通用其他'
}

function submit(): void {
  emit('save', {
    basics: {
      name: form.name, email: form.email, phone: form.phone, location: form.location,
      links: form.links.split('\n').map((value) => value.trim()).filter(Boolean),
    },
    intentions: {
      targetJob: form.targetJob, jobCategory: form.jobCategory, taxonomyNodeId: form.taxonomyNodeId,
      taxonomyCategoryId: form.taxonomyCategoryId, taxonomyGroupId: form.taxonomyGroupId,
    },
    preferences: { targetCity: form.targetCity, workMode: form.workMode },
    summary: form.summary,
  })
}

function focusField(fieldId: string): void {
  const element = document.getElementById(fieldId)
  element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  window.setTimeout(() => element?.focus(), 220)
}

function beforeUnload(event: BeforeUnloadEvent): void {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

function chooseAvatar(): void {
  if (!avatarPending.value) avatarInput.value?.click()
}

async function onAvatarInput(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    emit('error', '头像仅支持 PNG、JPEG 和 WebP 图片。')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    emit('error', '头像文件不能超过 10 MB。')
    return
  }
  avatarPending.value = true
  avatarReady.value = false
  try {
    const upload = await uploadCareerAvatar(file)
    emit('notice', '头像已上传，正在进行安全检查…')
    const task = await pollTask(upload.task.id, () => {}, undefined, 900, 180_000)
    if (task.status !== 'SUCCEEDED') {
      throw new Error('头像没有通过安全检查或处理失败，请换一张图片重试')
    }
    const updated = await commitCareerAvatar(upload.file.id)
    emit('profileUpdated', updated)
    avatarNonce.value = Date.now()
    avatarReady.value = true
    emit('notice', '头像已更新。')
  } catch (reason) {
    emit('error', errorMessage(reason, '头像上传失败，原头像保持不变'))
  } finally { avatarPending.value = false }
}

async function removeAvatar(): Promise<void> {
  if (!props.profile.avatarFileId || avatarPending.value) return
  avatarPending.value = true
  try {
    const updated = await deleteCareerAvatar()
    avatarReady.value = false
    emit('profileUpdated', updated)
    emit('notice', '头像已删除。')
  } catch (reason) { emit('error', errorMessage(reason, '头像删除失败')) }
  finally { avatarPending.value = false }
}

watch(() => props.profile, hydrate, { immediate: true, deep: true })
watch(() => props.profile.avatarFileId, (value) => { avatarReady.value = Boolean(value); avatarNonce.value = Date.now() })
onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload)
  try { categoryNodes.value = (await listJobTaxonomy()).filter((node) => node.level === 'CATEGORY') }
  catch { categoryNodes.value = [] }
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <div class="career-two-column profile-tab">
    <main class="career-main-column">
      <form id="career-profile-form" class="career-stack" @submit.prevent="submit">
        <section class="career-card profile-master-card">
          <header class="career-card-title">
            <div><span class="title-icon blue"><UserRound :size="19" /></span><h2>职业主档</h2><span class="soft-chip">职业画像概览</span></div>
          </header>

          <div class="profile-master-row">
            <div class="profile-avatar-wrap">
              <input ref="avatarInput" class="sr-only" type="file" accept="image/png,image/jpeg,image/webp" @change="onAvatarInput" />
              <button class="profile-avatar" type="button" :title="profile.avatarFileId ? '更换头像' : '上传头像'" :disabled="avatarPending" @click="chooseAvatar">
                <img v-if="profile.avatarFileId && avatarReady" :src="`/api/v1/career-library/profile/avatar/content?v=${avatarNonce}`" alt="职业主档头像" @error="avatarReady = false" />
                <LoaderCircle v-else-if="avatarPending" class="avatar-loader" :size="25" />
                <span v-else>{{ initial }}</span><i><Camera :size="15" /></i>
              </button>
              <button v-if="profile.avatarFileId" class="avatar-remove" type="button" title="删除头像" :disabled="avatarPending" @click="removeAvatar"><Trash2 :size="13" /></button>
            </div>
            <div class="target-summary-strip">
              <Target :size="22" />
              <span><small>目标角色</small><strong>{{ form.targetJob || '尚未设置' }}</strong></span>
              <span><small>行业</small><strong>{{ form.jobCategory || '尚未设置' }}</strong></span>
              <span><small>办公方式</small><strong>{{ workModes.find((item) => item.value === form.workMode)?.label || '尚未设置' }}</strong></span>
            </div>
          </div>

          <div class="profile-form-grid">
            <label><span>姓名</span><input id="profile-name" v-model.trim="form.name" class="career-input" autocomplete="name" placeholder="填写姓名" /></label>
            <label><span>邮箱</span><input id="profile-email" v-model.trim="form.email" class="career-input" type="email" autocomplete="email" placeholder="name@example.com" /></label>
            <label><span>手机号</span><input id="profile-phone" v-model.trim="form.phone" class="career-input" autocomplete="tel" placeholder="填写手机号" /></label>
            <label><span>所在地点</span><span class="career-input-with-icon"><input id="profile-location" v-model.trim="form.location" class="career-input" placeholder="城市" /><MapPin :size="16" /></span></label>
            <label class="span-2"><span>个人链接 <small>作品集、GitHub 或个人主页，每行一个</small></span><span class="career-input-with-icon"><textarea id="profile-links" v-model="form.links" class="career-input link-input" rows="2" placeholder="https://github.com/username" /><Link2 :size="16" /></span></label>
          </div>
        </section>

        <section class="career-card intention-card">
          <header class="career-card-title">
            <div><span class="title-icon cyan"><Target :size="19" /></span><h2>求职意向</h2></div>
          </header>
          <div class="profile-form-grid intention-grid">
            <label class="taxonomy-field"><span>目标岗位</span><JobTaxonomyPicker :selected-name="form.targetJob" :selected-node-id="form.taxonomyNodeId" @select="chooseJob" /></label>
            <label><span>岗位分类</span><input id="profile-job-category" v-model.trim="form.jobCategory" class="career-input" readonly placeholder="选择岗位后自动填写" /></label>
            <label><span>目标城市</span><AppSelect id="profile-target-city" v-model="form.targetCity" :options="cityOptions" aria-label="目标城市" searchable /></label>
            <label><span>办公方式</span><AppSelect v-model="form.workMode" :options="workModes" aria-label="办公方式" /></label>
            <label class="span-2 summary-field"><span>职业简介 <small>{{ form.summary.length }}/300</small></span><textarea id="profile-summary" v-model="form.summary" class="career-input" rows="3" maxlength="300" placeholder="用 3-5 句话概括专业背景、核心技能与价值亮点，突出你能为目标岗位带来的贡献。" /></label>
          </div>
        </section>
      </form>
    </main>

    <aside class="career-aside-column">
      <section class="career-card aside-card completion-card">
        <h3>资料完整度</h3>
        <div class="completion-row">
          <div class="career-ring" :style="{ '--ring-value': `${completeness * 3.6}deg` }"><strong>{{ completeness }}%</strong></div>
          <div><strong>继续完善资料，<br />让 AI 更懂你</strong><small>上次更新：{{ new Date(profile.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</small></div>
        </div>
        <p class="profile-status" :class="{ 'is-dirty': dirty }" role="status">{{ pending ? '正在保存资料…' : dirty ? '有未保存的修改，完成后请保存。' : '资料已保存；仅在你授权后用于 AI 建议。' }}</p>
        <div class="profile-missing-head"><h4>待完善资料</h4><span>{{ missing.length }} 项</span></div>
        <div v-if="missing.length" class="missing-list">
          <button v-for="item in missing" :key="item.key" type="button" @click="focusField(item.fieldId)">
            <Info :size="17" /><span><strong>{{ item.label }}</strong><small>补充后可提高 AI 简历建议的准确度</small></span><ArrowRight :size="16" />
          </button>
        </div>
        <p v-else class="all-complete"><CheckCircle2 :size="18" />核心资料已经完整</p>
      </section>

      <button class="privacy-entry" type="button" @click="privacyOpen = true"><ShieldCheck :size="17" />隐私说明<ArrowRight :size="16" /></button>
    </aside>

    <AppModal :open="privacyOpen" title="资料库隐私说明" :width="520" @close="privacyOpen = false">
      <div class="privacy-dialog">
        <LockKeyhole :size="26" />
        <div><strong>默认仅你可见</strong><p>管理员不能查看文件正文。AI 仅在具体会话中获得你的明确授权后读取已确认的结构化资料，原始文件和预览图片不会发送给模型。</p></div>
      </div>
      <template #footer>
        <UiButton variant="ghost" @click="privacyOpen = false">关闭</UiButton>
        <UiButton variant="secondary" to="/account/data-rights">导出或删除我的数据</UiButton>
      </template>
    </AppModal>
  </div>
</template>
