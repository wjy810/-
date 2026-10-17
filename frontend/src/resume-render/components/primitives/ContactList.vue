<script setup lang="ts">
import { computed } from 'vue'
import { breakableChunks } from '../../model/breaks'
import type { RenderHeader } from '../../model/types'
import { CONTACT_ICONS } from './icons'

const props = withDefaults(defineProps<{ header: RenderHeader; icons?: boolean; separator?: string; withLocation?: boolean }>(), {
  icons: true, separator: '·', withLocation: true,
})

const items = computed(() => {
  const list: Array<{ key: string; icon: string; chunks: string[]; href?: string }> = []
  if (props.header.phone) list.push({ key: 'phone', icon: 'phone', chunks: [props.header.phone], href: `tel:${props.header.phone.replace(/[^\d+]/g, '')}` })
  if (props.header.email) list.push({ key: 'email', icon: 'email', chunks: breakableChunks(props.header.email), href: `mailto:${props.header.email}` })
  if (props.withLocation && props.header.location) list.push({ key: 'location', icon: 'location', chunks: [props.header.location] })
  props.header.links.forEach((link, index) => list.push({ key: `link${index}`, icon: link.kind === 'github' || link.kind === 'linkedin' ? link.kind : 'web', chunks: breakableChunks(link.label), href: link.href }))
  return list
})
</script>

<template>
  <ul class="rr-contact" :class="{ 'rr-contact--icons': icons }">
    <li v-for="(item, index) in items" :key="item.key" class="rr-contact__item" :data-kind="item.key.replace(/\d+$/, '')">
      <svg v-if="icons" class="rr-contact__icon" viewBox="0 0 24 24" aria-hidden="true"><path :d="CONTACT_ICONS[item.icon]" /></svg>
      <!-- <wbr> marks where a narrow column may wrap an address; rows keep each item on one line. -->
      <component :is="item.href ? 'a' : 'span'" :href="item.href"><template v-for="(chunk, i) in item.chunks" :key="i"><wbr v-if="i" />{{ chunk }}</template></component>
      <span v-if="index < items.length - 1" class="rr-contact__sep" aria-hidden="true">{{ separator }}</span>
    </li>
  </ul>
</template>
