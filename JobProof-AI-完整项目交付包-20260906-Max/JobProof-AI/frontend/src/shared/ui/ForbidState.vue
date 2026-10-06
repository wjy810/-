<script setup lang="ts">
import { computed } from 'vue'
import { isApiClientError } from '@/shared/api/types'

const props = defineProps<{ error: unknown }>()

const reason = computed(() => (isApiClientError(props.error) ? props.error.reason : 'FORBIDDEN'))
const message = computed(() =>
  isApiClientError(props.error) ? props.error.message : '没有权限执行此操作。',
)
</script>

<template>
  <section class="forbid" role="alert">
    <p class="kicker">403</p>
    <h1>无权访问</h1>
    <p class="lede">{{ message }}</p>
    <p class="fine">
      <code>{{ reason }}</code>
      · 运营默认不能看求职者原文；也不能改他人对象。
    </p>
  </section>
</template>
