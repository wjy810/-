<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Eye, ImagePlus, Plus, Save, Send, Trash2, UploadCloud } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage, isVersionConflict } from '@/shared/api/types'
import UpdateContent from '../components/UpdateContent.vue'
import { MODULE_LABELS, SECTION_LABELS, TYPE_LABELS, moduleLabel, sectionLabel, typeLabel } from '../labels'
import { archiveUpdate, cancelUpdateSchedule, createUpdate, fetchAdminUpdate, publishUpdate, reviseUpdate, saveUpdate, scheduleUpdate, uploadUpdateAsset, validateUpdate } from '../services/updatesApi'
import type { ReleaseDetail, ReleaseDraft, UpdateSection } from '../types'
import '../updates.css'

const route = useRoute(); const router = useRouter()
const id = computed(() => typeof route.params.id === 'string' ? route.params.id : '')
const isNew = computed(() => !id.value)
const loading = ref(!isNew.value); const loadError = ref<unknown>(null); const pending = ref(''); const error = ref(''); const notice = ref('')
useToastFeedback(error, 'error', 'admin-changelog-editor-error')
useToastFeedback(notice, 'success', 'admin-changelog-editor-notice')
const saveState = ref(isNew.value ? '尚未创建' : '正在读取'); const previewMode = ref<'desktop' | 'mobile'>('desktop')
const detail = ref<ReleaseDetail | null>(null); const scheduleAt = ref(''); let saveTimer = 0; let suppressAutosave = true
const revisionOpen = ref(false); const archiveOpen = ref(false); const revisionReason = ref(''); const uploadingSection = ref(-1)
const draft = ref<ReleaseDraft>({ versionLabel: '', title: '', summary: '', releaseType: 'FEATURE', audience: 'PUBLIC', modules: [], ctaLabel: '', ctaPath: '', showWhatsNew: true, sendNotification: true, expectedVersion: 0, sections: [{ sectionType: 'HIGHLIGHTS', title: '本次更新亮点', body: '', items: [''] }] })
const typeOptions = Object.keys(TYPE_LABELS).map(value => ({ value, label: typeLabel(value) }))
const audienceOptions = [{ value: 'PUBLIC', label: '所有访问者' }, { value: 'AUTHENTICATED', label: '仅登录用户' }]
const sectionOptions = Object.keys(SECTION_LABELS).map(value => ({ value, label: sectionLabel(value) }))
const sectionKeys = new WeakMap<UpdateSection, number>()
let nextSectionKey = 0

function sectionKey(section: UpdateSection): number {
  const existing = sectionKeys.get(section)
  if (existing !== undefined) return existing
  nextSectionKey += 1
  sectionKeys.set(section, nextSectionKey)
  return nextSectionKey
}

function hydrate(value: ReleaseDetail): void {
  detail.value = value; const r = value.release
  draft.value = { versionLabel: r.versionLabel, title: r.title, summary: r.summary, releaseType: r.releaseType, audience: r.audience, modules: [...r.modules], ctaLabel: r.ctaLabel || '', ctaPath: r.ctaPath || '', showWhatsNew: r.showWhatsNew, sendNotification: r.sendNotification, expectedVersion: r.versionNo, sections: value.sections.map(s => ({ ...s, items: [...s.items] })) }
  saveState.value = '已保存'; scheduleAt.value = r.scheduledAt ? localDateTime(r.scheduledAt) : ''
}

function localDateTime(value: string): string { const date = new Date(value); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16) }
function toggleModule(value: string): void { draft.value.modules = draft.value.modules.includes(value) ? draft.value.modules.filter(item => item !== value) : [...draft.value.modules, value] }
function addSection(): void { draft.value.sections.push({ sectionType: 'FEATURES', title: '新增功能', body: '', items: [''] }) }
function removeSection(index: number): void { draft.value.sections.splice(index, 1) }
function moveSection(index: number, delta: number): void { const target = index + delta; if (target < 0 || target >= draft.value.sections.length) return; const [item] = draft.value.sections.splice(index, 1); draft.value.sections.splice(target, 0, item) }
function addItem(section: UpdateSection): void { section.items.push('') }
function removeItem(section: UpdateSection, index: number): void { section.items.splice(index, 1) }
function assetUrl(assetId: string): string {
  const release = detail.value?.release
  if (!release || ['PUBLISHED', 'ARCHIVED'].includes(release.status)) return `/api/v1/updates/assets/${encodeURIComponent(assetId)}`
  return `/api/v1/admin/changelog/${encodeURIComponent(release.id)}/assets/${encodeURIComponent(assetId)}`
}
function removeImage(section: UpdateSection): void { section.imageAssetId = null; section.imageAlt = null }

async function onImageSelected(index: number, event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = ''; notice.value = ''
  if (!detail.value && !(await save(false))) return
  const releaseId = detail.value?.release.id
  if (!releaseId) { error.value = '请先创建草稿，再上传更新图片'; return }
  uploadingSection.value = index
  try {
    const asset = await uploadUpdateAsset(releaseId, file)
    const section = draft.value.sections[index]
    if (!section) return
    section.imageAssetId = asset.id
    section.imageAlt = section.imageAlt || file.name.replace(/\.[^.]+$/, '')
    notice.value = '图片已上传并加入当前区块'
    if (detail.value?.release.status === 'DRAFT') await save(true)
  } catch (cause) { error.value = errorMessage(cause, '图片上传失败') }
  finally { uploadingSection.value = -1 }
}

// A failed load shows an error with retry, never a blank form that could be saved over the real version.
async function load(): Promise<void> { if (isNew.value) return; loading.value = true; try { hydrate(await fetchAdminUpdate(id.value)); loadError.value = null } catch (reason) { loadError.value = reason } finally { loading.value = false; setTimeout(() => { suppressAutosave = false }, 0) } }

async function save(auto = false): Promise<boolean> {
  if (isNew.value) {
    if (auto) return false
    pending.value = 'create'; error.value = ''
    try { const created = await createUpdate(draft.value); hydrate(created); await router.replace(`/admin/changelog/${created.release.id}/edit`); suppressAutosave = false; notice.value = '草稿已创建'; return true }
    catch (reason) { error.value = errorMessage(reason, '创建草稿失败'); return false }
    finally { pending.value = '' }
  }
  if (detail.value?.release.status !== 'DRAFT') return true
  if (!auto) pending.value = 'save'; saveState.value = '正在保存'; error.value = ''
  try { const saved = await saveUpdate(id.value, draft.value); suppressAutosave = true; hydrate(saved); setTimeout(() => { suppressAutosave = false }, 0); saveState.value = `已自动保存 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`; return true }
  catch (reason) { saveState.value = isVersionConflict(reason) ? '保存冲突，请重新载入' : '保存失败'; error.value = errorMessage(reason, '保存失败'); return false }
  finally { pending.value = '' }
}

async function onPublish(): Promise<void> { if (!(await save(false)) || !id.value) return; pending.value = 'publish'; try { const check = await validateUpdate(id.value); if (!check.valid) { error.value = check.issues.join('；'); return } hydrate(await publishUpdate(id.value, draft.value.expectedVersion)); notice.value = '版本已正式发布' } catch (reason) { error.value = errorMessage(reason, '发布失败') } finally { pending.value = '' } }
async function onSchedule(): Promise<void> { if (!(await save(false)) || !id.value || !scheduleAt.value) { error.value = '请选择定时发布时间'; return } pending.value = 'schedule'; try { hydrate(await scheduleUpdate(id.value, draft.value.expectedVersion, new Date(scheduleAt.value).toISOString())); notice.value = '已安排定时发布' } catch (reason) { error.value = errorMessage(reason, '定时发布失败') } finally { pending.value = '' } }
async function onCancelSchedule(): Promise<void> { pending.value = 'cancel'; try { hydrate(await cancelUpdateSchedule(id.value, draft.value.expectedVersion)); notice.value = '已取消定时发布' } catch (reason) { error.value = errorMessage(reason) } finally { pending.value = '' } }
async function onRevise(): Promise<void> { const reason = revisionReason.value.trim(); if (reason.length < 4) { error.value = '修订原因至少填写 4 个字'; return } pending.value = 'revise'; try { hydrate(await reviseUpdate(id.value, draft.value, reason)); revisionOpen.value = false; revisionReason.value = ''; notice.value = '修订已发布并保留旧版本快照' } catch (cause) { error.value = errorMessage(cause, '修订失败') } finally { pending.value = '' } }
async function onArchive(): Promise<void> { pending.value = 'archive'; try { hydrate(await archiveUpdate(id.value, draft.value.expectedVersion)); archiveOpen.value = false; notice.value = '版本已归档' } catch (cause) { error.value = errorMessage(cause, '归档失败') } finally { pending.value = '' } }

watch(draft, () => { if (suppressAutosave || isNew.value || detail.value?.release.status !== 'DRAFT') return; saveState.value = '等待自动保存'; window.clearTimeout(saveTimer); saveTimer = window.setTimeout(() => void save(true), 700) }, { deep: true })
onMounted(load); onUnmounted(() => window.clearTimeout(saveTimer))
</script>

<template>
  <section class="page admin-editor"><header class="page-head"><div><h1 class="page-head__title">{{ isNew ? '新建更新版本' : `编辑版本 ${draft.versionLabel}` }}</h1><p class="page-head__sub">结构化编写、实时预览并经过发布检查后触达用户。</p></div><div class="page-head__actions"><span class="save-state" :class="{ 'is-error': saveState.includes('失败') || saveState.includes('冲突') }">{{ saveState }}</span><RouterLink class="btn btn--ghost" to="/updates"><Eye :size="16" />公开页</RouterLink></div></header>
    <div v-if="loading" class="empty-state">正在读取版本…</div><UiErrorState v-else-if="loadError" :error="loadError" title="版本读取失败" @retry="load" /><div v-else class="admin-editor__grid"><form class="admin-editor__form" @submit.prevent="save(false)"><div class="editor-grid"><label class="editor-field"><span>版本号</span><input v-model.trim="draft.versionLabel" placeholder="v1.0.0" :disabled="detail?.release.status==='PUBLISHED'" /></label><label class="editor-field"><span>发布类型</span><AppSelect v-model="draft.releaseType" :options="typeOptions" aria-label="发布类型" /></label><label class="editor-field wide"><span>标题</span><input v-model.trim="draft.title" maxlength="255" placeholder="概括这次更新" /></label><label class="editor-field wide"><span>摘要</span><textarea v-model.trim="draft.summary" maxlength="500" placeholder="10-500 字，说明更新对用户的价值" /></label><label class="editor-field"><span>受众</span><AppSelect v-model="draft.audience" :options="audienceOptions" aria-label="发布受众" /></label><div class="editor-field"><span>触达方式</span><label class="editor-module"><input v-model="draft.showWhatsNew" type="checkbox" />首次登录亮点弹窗</label><label class="editor-module"><input v-model="draft.sendNotification" type="checkbox" />发送站内通知</label></div><div class="editor-field wide"><span>产品模块</span><div class="editor-modules"><label v-for="(label, value) in MODULE_LABELS" :key="value" class="editor-module"><input type="checkbox" :checked="draft.modules.includes(value)" @change="toggleModule(value)" />{{ label }}</label></div></div><label class="editor-field"><span>体验按钮文字</span><input v-model.trim="draft.ctaLabel" placeholder="立即体验" /></label><label class="editor-field"><span>体验地址</span><input v-model.trim="draft.ctaPath" placeholder="/resumes 或 https://…" /></label></div>
      <section class="editor-sections">
        <div class="section-title"><h2>更新内容</h2><button class="btn btn--ghost btn--sm" type="button" @click="addSection"><Plus :size="15" />添加区块</button></div>
        <TransitionGroup name="editor-section" tag="div" class="editor-section-list">
        <article v-for="(section, index) in draft.sections" :key="sectionKey(section)" class="editor-section">
          <header class="editor-section__head">
            <AppSelect v-model="section.sectionType" :options="sectionOptions" aria-label="区块类型" />
            <div><button class="icon-btn" type="button" title="上移" :disabled="index===0" @click="moveSection(index,-1)"><ArrowUp :size="15" /></button><button class="icon-btn" type="button" title="下移" :disabled="index===draft.sections.length-1" @click="moveSection(index,1)"><ArrowDown :size="15" /></button><button class="icon-btn" type="button" title="删除区块" @click="removeSection(index)"><Trash2 :size="15" /></button></div>
          </header>
          <div class="editor-section__fields">
            <label class="editor-field"><span>区块标题</span><input v-model.trim="section.title" maxlength="120" /></label>
            <label class="editor-field"><span>说明段落</span><textarea v-model.trim="section.body" maxlength="4000" /></label>
            <div class="editor-field"><span>更新要点</span><div class="editor-items"><div v-for="(_, itemIndex) in section.items" :key="itemIndex" class="editor-item"><input v-model.trim="section.items[itemIndex]" maxlength="500" placeholder="一条明确、可验证的变化" /><button class="icon-btn" type="button" title="删除要点" @click="removeItem(section,itemIndex)"><Trash2 :size="14" /></button></div><button class="btn btn--ghost btn--sm" type="button" @click="addItem(section)"><Plus :size="14" />添加要点</button></div></div>
            <div class="editor-field">
              <span>区块图片（可选）</span>
              <div v-if="section.imageAssetId" class="editor-image">
                <img :src="assetUrl(section.imageAssetId)" :alt="section.imageAlt || section.title" />
                <label><span>图片替代文本</span><input v-model.trim="section.imageAlt" maxlength="255" placeholder="简洁说明图片中的关键信息" /></label>
                <button class="btn btn--ghost btn--sm" type="button" @click="removeImage(section)"><Trash2 :size="14" />移除引用</button>
              </div>
              <label v-else class="editor-upload" :class="{ 'is-busy': uploadingSection===index }">
                <UploadCloud :size="18" /><span>{{ uploadingSection===index ? '正在上传…' : '上传 PNG、JPEG 或 WebP（不超过 5 MiB）' }}</span>
                <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploadingSection>=0" @change="onImageSelected(index,$event)" />
              </label>
            </div>
          </div>
        </article>
        </TransitionGroup>
      </section>
      <div class="editor-grid" style="margin-top:18px"><label class="editor-field wide"><span>定时发布时间</span><AppDatePicker v-model="scheduleAt" mode="datetime" label="定时发布时间" /></label></div>
      <footer class="editor-actions"><button class="btn btn--ghost" type="submit" :disabled="!!pending"><Save :size="16" />{{ isNew ? '创建草稿' : '保存草稿' }}</button><button v-if="detail?.release.status==='DRAFT'" class="btn btn--ghost" type="button" :disabled="!!pending" @click="onSchedule">定时发布</button><button v-if="detail?.release.status==='SCHEDULED'" class="btn btn--ghost" type="button" :disabled="!!pending" @click="onCancelSchedule">取消定时</button><button v-if="isNew || detail?.release.status==='DRAFT'" class="btn btn--primary" type="button" :disabled="!!pending" @click="onPublish"><Send :size="16" />立即发布</button><button v-if="detail?.release.status==='PUBLISHED'" class="btn btn--primary" type="button" :disabled="!!pending" @click="revisionOpen=true"><ImagePlus :size="16" />发布修订</button><button v-if="detail?.release.status==='PUBLISHED'" class="btn btn--danger" type="button" :disabled="!!pending" @click="archiveOpen=true">归档版本</button></footer></form>
      <aside class="admin-editor__preview"><div class="preview-toolbar"><strong>实时预览</strong><div class="preview-switch" :class="{ 'is-mobile': previewMode==='mobile' }"><button type="button" :class="{ 'is-active': previewMode==='desktop' }" @click="previewMode='desktop'">Desktop</button><button type="button" :class="{ 'is-active': previewMode==='mobile' }" @click="previewMode='mobile'">Mobile</button></div></div><div class="editor-preview-canvas" :class="{ 'is-mobile': previewMode==='mobile' }"><article class="preview-release"><span class="update-pill">{{ draft.versionLabel || 'v0.0.0' }}</span><h1>{{ draft.title || '版本标题' }}</h1><div class="preview-release__meta"><span class="update-pill">{{ typeLabel(draft.releaseType) }}</span><span v-for="tag in draft.modules" :key="tag" class="update-pill">{{ moduleLabel(tag) }}</span></div><p>{{ draft.summary || '在这里预览版本摘要和更新内容。' }}</p><UpdateContent :sections="draft.sections" :asset-url="assetUrl" compact /></article></div></aside></div>
  </section>
  <AppModal :open="revisionOpen" title="发布版本修订" :width="520" @close="revisionOpen=false">
    <div class="editor-dialog"><p>已发布内容不会原地覆盖。本次修改将形成新的不可变修订，并保留旧修订用于审计。</p><label class="editor-field"><span>修订原因</span><textarea v-model.trim="revisionReason" maxlength="500" placeholder="说明为什么需要修订，至少 4 个字" /></label></div>
    <template #footer><button class="btn btn--ghost" type="button" @click="revisionOpen=false">取消</button><button class="btn btn--primary" type="button" :disabled="pending==='revise'" @click="onRevise">{{ pending==='revise' ? '正在发布…' : '确认发布修订' }}</button></template>
  </AppModal>
  <AppModal :open="archiveOpen" title="归档更新版本" :width="500" @close="archiveOpen=false">
    <div class="editor-dialog"><p>归档后，该版本将退出公开列表、最新版本区域、通知和亮点弹窗。原详情地址仍可访问，并明确显示“已归档”。</p></div>
    <template #footer><button class="btn btn--ghost" type="button" @click="archiveOpen=false">取消</button><button class="btn btn--danger" type="button" :disabled="pending==='archive'" @click="onArchive">{{ pending==='archive' ? '正在归档…' : '确认归档' }}</button></template>
  </AppModal>
</template>

<style scoped>
.editor-upload { position: relative; min-height: 82px; display: flex; align-items: center; justify-content: center; gap: 9px; padding: 14px; border: 1px dashed var(--color-primary-border); border-radius: 10px; background: var(--surface-2); color: var(--color-primary); cursor: pointer; transition: border-color .2s ease, background-color .2s ease, transform .2s ease; }
.editor-upload:hover { border-color: var(--color-primary); background: var(--surface-2); transform: translateY(-1px); }
.editor-upload.is-busy { cursor: wait; opacity: .72; }
.editor-upload input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.editor-image { display: grid; gap: 10px; }
.editor-image > img { width: 100%; max-height: 260px; object-fit: contain; border: 1px solid var(--border-subtle); border-radius: 9px; background: var(--surface-1); }
.editor-image > label { display: grid; gap: 6px; }
.editor-image > .btn { justify-self: start; }
.editor-dialog { display: grid; gap: 16px; }
.editor-dialog > p { margin: 0; color: var(--text-secondary); line-height: 1.7; }
.admin-editor__grid { animation: editor-workspace-in var(--motion-base) var(--motion-ease-out) both; }
.editor-section-list { position: relative; display: grid; gap: 12px; }
.editor-section-enter-active,
.editor-section-leave-active,
.editor-section-move {
  transition: opacity var(--motion-base) ease, transform var(--motion-base) var(--motion-ease);
}
.editor-section-enter-from,
.editor-section-leave-to { opacity: 0; transform: translateY(7px) scale(.995); }
.preview-switch { position: relative; isolation: isolate; }
.preview-switch::before {
  position: absolute;
  z-index: 0;
  inset: 3px auto 3px 3px;
  width: calc((100% - 6px) / 2);
  border-radius: 7px;
  background: var(--surface-1);
  box-shadow: 0 2px 8px rgba(25,49,89,.08);
  content: '';
  transition: transform var(--motion-slow) var(--motion-ease);
}
.preview-switch.is-mobile::before { transform: translateX(100%); }
.preview-switch button { position: relative; z-index: 1; transition: color var(--motion-base) ease, transform var(--motion-fast) var(--motion-ease); }
.preview-switch button.is-active { background: transparent; box-shadow: none; }
.preview-switch button:active { transform: scale(.96); }
@keyframes editor-workspace-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
@media (max-width: 700px) {
  .page-head__actions > .btn,
  .editor-sections .btn,
  .editor-actions .btn,
  .editor-section .icon-btn,
  .preview-switch button,
  .editor-module,
  .admin-editor:deep(.app-select__trigger),
  .admin-editor :deep(.app-date-picker__trigger) {
    min-height: 44px;
  }
  .editor-section .icon-btn {
    min-width: 44px;
  }
  .editor-actions .btn {
    flex: 1 1 auto;
  }
}
@media (prefers-reduced-motion: reduce) {
  .editor-upload,
  .editor-section-enter-active,
  .editor-section-leave-active,
  .editor-section-move,
  .preview-switch::before,
  .preview-switch button { transition: none; }
  .admin-editor__grid { animation: none; }
  .preview-switch button:active { transform: none; }
}
</style>
