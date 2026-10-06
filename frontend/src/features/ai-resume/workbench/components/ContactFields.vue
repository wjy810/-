<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown, ChevronUp, ImagePlus, Link2, Mail, MapPin, Phone, Plus, Trash2, UserRound } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import type { AiResumeCard } from '../../types'
import { useWorkbench } from '../useWorkbench'

const props = defineProps<{ card: AiResumeCard }>()

const wb = useWorkbench()
const conversation = wb.session.conversation
const photoInput = ref<HTMLInputElement | null>(null)

const payload = computed(() => wb.drafts.payloadOf(props.card))
const links = computed(() => wb.drafts.contactLinks(props.card))

function value(key: string): string {
  return String(payload.value[key] ?? '')
}

function update(key: string, next: string): void {
  wb.drafts.updateField(props.card, key, next)
}

function onPhotoChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) void wb.session.uploadPhoto(file)
}
</script>

<template>
  <div class="contact-fields">
    <div class="contact-fields__grid">
      <UiField v-slot="{ id }" label="姓名" required>
        <UiInput :id="id" :model-value="value('name')" :icon="UserRound" autocomplete="name" maxlength="50" placeholder="填写用于简历的姓名" data-autofocus @update:model-value="update('name', $event)" />
      </UiField>
      <UiField v-slot="{ id }" label="所在城市" optional>
        <UiInput :id="id" :model-value="value('location')" :icon="MapPin" autocomplete="address-level2" maxlength="60" placeholder="例如：杭州" @update:model-value="update('location', $event)" />
      </UiField>
      <UiField v-slot="{ id }" label="邮箱" hint="与手机号至少填一项">
        <UiInput :id="id" :model-value="value('email')" :icon="Mail" type="email" inputmode="email" autocomplete="email" maxlength="254" placeholder="name@example.com" @update:model-value="update('email', $event)" />
      </UiField>
      <UiField v-slot="{ id }" label="手机号" hint="与邮箱至少填一项">
        <UiInput :id="id" :model-value="value('phone')" :icon="Phone" type="tel" inputmode="tel" autocomplete="tel" maxlength="30" placeholder="例如：138 0000 0000" @update:model-value="update('phone', $event)" />
      </UiField>
    </div>

    <section class="contact-fields__links" aria-label="个人链接">
      <header>
        <div><strong>个人链接</strong><small>选填作品集、GitHub 或个人主页</small></div>
        <UiButton variant="ghost" size="sm" :icon="Plus" @click="wb.drafts.addContactLink(card)">添加链接</UiButton>
      </header>
      <p v-if="!links.length" class="contact-fields__empty">尚未添加个人链接</p>
      <div v-for="(link, index) in links" :key="index" class="contact-fields__link">
        <UiInput :model-value="link" :icon="Link2" type="url" inputmode="url" autocomplete="url" placeholder="https://" :aria-label="`链接 ${index + 1}`" @update:model-value="wb.drafts.updateContactLink(card, index, $event)" />
        <UiIconButton :icon="ChevronUp" size="sm" :label="`上移链接 ${index + 1}`" :disabled="index === 0" @click="wb.drafts.moveContactLink(card, index, index - 1)" />
        <UiIconButton :icon="ChevronDown" size="sm" :label="`下移链接 ${index + 1}`" :disabled="index === links.length - 1" @click="wb.drafts.moveContactLink(card, index, index + 1)" />
        <UiIconButton :icon="Trash2" size="sm" :label="`删除链接 ${index + 1}`" @click="wb.drafts.removeContactLink(card, index)" />
      </div>
    </section>

    <section v-if="conversation" class="contact-fields__photo" aria-label="简历照片">
      <img v-if="conversation.photo" :src="conversation.photo.contentUrl" alt="当前简历照片" />
      <span v-else class="contact-fields__photo-empty"><UserRound :size="20" aria-hidden="true" /></span>
      <div><strong>简历照片</strong><small>PNG / JPEG，最多 1MB；是否显示由模板设计中的“照片”决定</small></div>
      <input ref="photoInput" class="contact-fields__file" type="file" accept="image/png,image/jpeg" tabindex="-1" aria-hidden="true" @change="onPhotoChange" />
      <UiButton variant="secondary" size="sm" :icon="ImagePlus" :pending="wb.session.photoPending.value" @click="photoInput?.click()">
        {{ conversation.photo ? '更换' : '上传' }}
      </UiButton>
      <UiIconButton v-if="conversation.photo" :icon="Trash2" size="sm" label="移除照片" :disabled="wb.session.photoPending.value" @click="wb.session.removePhoto()" />
    </section>
  </div>
</template>

<style scoped>
.contact-fields {
  display: grid;
  gap: var(--space-4);
  container-type: inline-size;
}

.contact-fields__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.contact-fields__links,
.contact-fields__photo {
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.contact-fields__links {
  display: grid;
  gap: var(--space-2);
}

.contact-fields__links header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.contact-fields__links header div,
.contact-fields__photo div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.contact-fields strong {
  font-size: var(--fs-sm);
  font-weight: 650;
}

.contact-fields small,
.contact-fields__empty {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.contact-fields__link {
  display: grid;
  grid-template-columns: minmax(0, 1fr) repeat(3, auto);
  align-items: center;
  gap: 2px;
}

.contact-fields__photo {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--space-3);
}

.contact-fields__photo img,
.contact-fields__photo-empty {
  width: 48px;
  height: 60px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
  background: var(--surface-2);
}

.contact-fields__photo img {
  object-fit: cover;
}

.contact-fields__photo-empty {
  display: grid;
  place-items: center;
  color: var(--text-tertiary);
}

.contact-fields__file {
  display: none;
}

@container (max-width: 420px) {
  .contact-fields__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .contact-fields__photo {
    grid-template-columns: 48px minmax(0, 1fr) auto;
  }

  .contact-fields__photo div {
    grid-column: 2 / -1;
  }
}
</style>
