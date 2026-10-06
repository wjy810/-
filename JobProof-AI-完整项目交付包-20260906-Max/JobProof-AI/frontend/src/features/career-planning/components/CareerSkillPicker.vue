<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, Plus, Search, Tags, X } from 'lucide-vue-next'
import AppModal from '@/shared/ui/AppModal.vue'
import {
  careerSkillCatalog, careerSkillCategories, filterCareerSkillOptions,
  normalizeCareerSkillName, removeCareerSkill, upsertCareerSkill,
} from '../utils/skillPicker'
import type { CareerSkillDraft, CareerSkillStatus } from '../utils/skillPicker'

const props = defineProps<{ modelValue: CareerSkillDraft[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: CareerSkillDraft[]] }>()

const open = ref(false)
const query = ref('')
const category = ref<string>('全部')
const customName = ref('')
const customStatus = ref<CareerSkillStatus>('PRACTICED')
const draft = ref<CareerSkillDraft[]>([])

const filteredOptions = computed(() => filterCareerSkillOptions(careerSkillCatalog, query.value, category.value))

function beginSelection(): void {
  draft.value = props.modelValue.map(item => ({ ...item }))
  query.value = ''
  category.value = '全部'
  customName.value = ''
  customStatus.value = 'PRACTICED'
  open.value = true
}

function close(): void {
  open.value = false
}

function selected(name: string): CareerSkillDraft | undefined {
  const normalized = normalizeCareerSkillName(name)
  return draft.value.find(item => normalizeCareerSkillName(item.name) === normalized)
}

function choose(name: string, status: CareerSkillStatus, custom = false): void {
  draft.value = upsertCareerSkill(draft.value, { name, status, custom })
}

function remove(name: string): void {
  draft.value = removeCareerSkill(draft.value, name)
}

function removeCommitted(name: string): void {
  emit('update:modelValue', removeCareerSkill(props.modelValue, name))
}

function addCustom(): void {
  const name = customName.value.trim()
  if (!name) return
  choose(name, customStatus.value, true)
  customName.value = ''
}

function commit(): void {
  emit('update:modelValue', draft.value.map(item => ({ ...item })))
  close()
}

function statusLabel(status: CareerSkillStatus): string {
  return status === 'LEARNING' ? '正在学习' : '已实践'
}
</script>

<template>
  <div class="career-skill-picker">
    <div v-if="modelValue.length" class="career-skill-picker__selected" aria-label="已选择技能">
      <span v-for="skill in modelValue" :key="skill.name" :class="`is-${skill.status.toLowerCase()}`">
        <strong>{{ skill.name }}</strong><small>{{ statusLabel(skill.status) }}</small>
        <button type="button" :aria-label="`移除 ${skill.name}`" @click="removeCommitted(skill.name)"><X :size="13" /></button>
      </span>
    </div>
    <button class="career-skill-picker__trigger" type="button" @click="beginSelection">
      <Tags :size="18" />
      <span><strong>{{ modelValue.length ? `已选择 ${modelValue.length} 项技能` : '选择专业技能' }}</strong><small>支持搜索、多选和自定义技能，并区分是否已经实践</small></span>
      <Plus :size="17" />
    </button>

    <AppModal :open="open" title="选择专业技能" :width="780" mobile-sheet @close="close">
      <div class="career-skill-dialog">
        <p class="career-skill-dialog__notice"><Tags :size="18" />只选择真实掌握或正在学习的技能，学习中的内容不会被 AI 当作已掌握事实。</p>
        <label class="career-skill-dialog__search">
          <Search :size="18" /><input v-model.trim="query" autofocus placeholder="搜索技能或分类" aria-label="搜索技能">
        </label>
        <nav class="career-skill-dialog__categories" aria-label="技能分类">
          <button v-for="item in careerSkillCategories" :key="item" type="button" :class="{ active: category === item }" @click="category = item">{{ item }}</button>
        </nav>

        <section v-if="draft.length" class="career-skill-dialog__draft">
          <header><strong>已选择</strong><small>{{ draft.length }} 项</small></header>
          <div><button v-for="skill in draft" :key="skill.name" type="button" @click="remove(skill.name)"><span>{{ skill.name }}</span><small>{{ statusLabel(skill.status) }}</small><X :size="13" /></button></div>
        </section>

        <section class="career-skill-dialog__options">
          <article v-for="option in filteredOptions" :key="option.name" :class="{ selected: selected(option.name) }">
            <div><strong>{{ option.name }}</strong><small>{{ option.category }}</small></div>
            <div class="career-skill-status" :aria-label="`${option.name}掌握状态`">
              <button type="button" :class="{ active: selected(option.name)?.status === 'PRACTICED' }" @click="choose(option.name, 'PRACTICED')"><Check v-if="selected(option.name)?.status === 'PRACTICED'" :size="13" />已实践</button>
              <button type="button" :class="{ active: selected(option.name)?.status === 'LEARNING' }" @click="choose(option.name, 'LEARNING')"><Check v-if="selected(option.name)?.status === 'LEARNING'" :size="13" />学习中</button>
            </div>
          </article>
          <p v-if="!filteredOptions.length" class="career-skill-dialog__empty">没有匹配的预设技能，可以在下方自定义添加。</p>
        </section>

        <section class="career-skill-dialog__custom">
          <div><strong>自定义技能</strong><small>未收录的行业、工具或专业能力可以自行添加</small></div>
          <div class="career-skill-dialog__custom-row">
            <input v-model.trim="customName" maxlength="40" placeholder="输入技能名称" aria-label="自定义技能名称" @keydown.enter.prevent="addCustom">
            <div class="career-skill-status" aria-label="自定义技能掌握状态">
              <button type="button" :class="{ active: customStatus === 'PRACTICED' }" @click="customStatus = 'PRACTICED'">已实践</button>
              <button type="button" :class="{ active: customStatus === 'LEARNING' }" @click="customStatus = 'LEARNING'">学习中</button>
            </div>
            <button class="career-skill-dialog__add" type="button" :disabled="!customName.trim()" @click="addCustom"><Plus :size="16" />添加</button>
          </div>
        </section>
      </div>
      <template #footer>
        <button class="cp-secondary" type="button" @click="close">取消</button>
        <button class="cp-primary" type="button" :disabled="!draft.length" @click="commit"><Check :size="16" />确认选择 {{ draft.length }} 项</button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.career-skill-picker{display:grid;gap:10px}.career-skill-picker__selected{display:flex;flex-wrap:wrap;gap:8px}.career-skill-picker__selected>span{min-height:34px;display:flex;align-items:center;gap:7px;padding:0 7px 0 10px;border:1px solid #bfd4f5;border-radius:7px;color:#205ba8;background:#f2f7ff}.career-skill-picker__selected>span.is-learning{color:#a85b08;border-color:#f0cf9f;background:#fff8ec}.career-skill-picker__selected strong{font-size:12px}.career-skill-picker__selected small{font-size:12px;opacity:.78}.career-skill-picker__selected button{width:24px;height:24px;display:grid;place-items:center;border:0;border-radius:5px;color:inherit;background:transparent}.career-skill-picker__selected button:hover{background:#fff}.career-skill-picker__trigger{width:100%;min-height:64px;padding:10px 13px;display:flex;align-items:center;gap:11px;border:1px solid #cdd9e9;border-radius:9px;text-align:left;color:#2d527e;background:#fff;transition:border-color .18s ease,box-shadow .18s ease,background-color .18s ease}.career-skill-picker__trigger:hover{border-color:#79a8eb;background:#f9fbff;box-shadow:0 0 0 3px #2563eb12}.career-skill-picker__trigger>svg:first-child{color:var(--primary)}.career-skill-picker__trigger>svg:last-child{margin-left:auto}.career-skill-picker__trigger>span{display:grid;gap:3px}.career-skill-picker__trigger strong{font-size:13px}.career-skill-picker__trigger small{color:#7788a0;font-size:12px}.career-skill-dialog{display:grid;gap:15px}.career-skill-dialog__notice{display:flex;align-items:flex-start;gap:9px;padding:11px 12px;border:1px solid #cfe0f8;border-radius:8px;color:#466381;background:#f6f9ff;font-size:12px;line-height:1.55}.career-skill-dialog__notice svg{flex:0 0 auto;color:var(--primary)}.career-skill-dialog__search{height:44px;display:flex;align-items:center;gap:9px;padding:0 12px;border:1px solid #cedaea;border-radius:9px;color:#7d8ca1}.career-skill-dialog__search:focus-within{border-color:#5c91e7;box-shadow:0 0 0 3px #2563eb14}.career-skill-dialog__search input{min-width:0;flex:1;border:0;outline:0}.career-skill-dialog__categories{display:flex;gap:7px;overflow-x:auto;scrollbar-width:none}.career-skill-dialog__categories::-webkit-scrollbar{display:none}.career-skill-dialog__categories button{min-height:34px;padding:0 13px;flex:0 0 auto;border:1px solid #d9e2ee;border-radius:17px;color:#61728a;background:#fff}.career-skill-dialog__categories button.active{color:var(--primary);border-color:#8db6f3;background:#edf4ff}.career-skill-dialog__draft{padding:11px;border:1px solid #dce6f2;border-radius:9px;background:#f9fbfe}.career-skill-dialog__draft header{display:flex;justify-content:space-between;color:#365274;font-size:12px}.career-skill-dialog__draft header small{color:#7788a0}.career-skill-dialog__draft>div{margin-top:9px;display:flex;flex-wrap:wrap;gap:7px}.career-skill-dialog__draft button{min-height:31px;padding:0 8px 0 10px;display:flex;align-items:center;gap:6px;border:1px solid #c8d9f1;border-radius:6px;color:#275eaa;background:#fff}.career-skill-dialog__draft button small{color:#7188a7;font-size:12px}.career-skill-dialog__options{display:grid;grid-template-columns:1fr 1fr;gap:8px;max-height:300px;padding-right:2px;overflow:auto;overscroll-behavior:contain}.career-skill-dialog__options article{min-height:66px;padding:9px 10px;display:flex;align-items:center;justify-content:space-between;gap:8px;border:1px solid #e0e7f1;border-radius:8px;background:#fff}.career-skill-dialog__options article.selected{border-color:#94b9ee;background:#f7faff}.career-skill-dialog__options article>div:first-child{display:grid;gap:3px;min-width:0}.career-skill-dialog__options article strong{font-size:12px}.career-skill-dialog__options article small{color:#8390a4;font-size:12px}.career-skill-status{display:flex;gap:3px;padding:3px;border-radius:7px;background:#f1f4f8}.career-skill-status button{min-height:28px;padding:0 7px;display:flex;align-items:center;gap:3px;border:0;border-radius:5px;color:#718096;background:transparent;font-size:12px}.career-skill-status button.active{color:var(--primary);background:#fff;box-shadow:0 1px 5px #2f52721c}.career-skill-dialog__empty{grid-column:1/-1;padding:24px;color:#7b899f;text-align:center}.career-skill-dialog__custom{padding:12px;border-top:1px solid #e4eaf2;background:#fbfcfe}.career-skill-dialog__custom>div:first-child{display:grid;gap:3px}.career-skill-dialog__custom strong{font-size:12px}.career-skill-dialog__custom small{color:#7d8ca2;font-size:12px}.career-skill-dialog__custom-row{margin-top:10px;display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:8px}.career-skill-dialog__custom-row>input{height:38px;padding:0 10px;border:1px solid #d3deeb;border-radius:7px;outline:0}.career-skill-dialog__custom-row>input:focus{border-color:#5c91e7;box-shadow:0 0 0 3px #2563eb12}.career-skill-dialog__add{min-height:38px;padding:0 12px;display:flex;align-items:center;gap:5px;border:0;border-radius:7px;color:#fff;background:var(--primary)}.career-skill-dialog__add:disabled{opacity:.5}
@media(max-width:700px){.career-skill-picker__trigger{min-height:68px}.career-skill-dialog__notice{font-size:12px}.career-skill-dialog__options{grid-template-columns:1fr;max-height:none}.career-skill-dialog__options article{min-height:70px}.career-skill-dialog__categories button{min-height:44px;border-radius:8px}.career-skill-status button{min-height:44px;padding-inline:9px}.career-skill-dialog__custom-row{grid-template-columns:1fr}.career-skill-dialog__custom-row>.career-skill-status{width:max-content}.career-skill-dialog__add{min-height:44px;justify-content:center}}
@media(prefers-reduced-motion:reduce){.career-skill-picker__trigger{transition:none}}
</style>
