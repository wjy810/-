<script setup lang="ts">
defineProps<{
  title?: string
  sub?: string
}>()
</script>

<template>
  <section class="card">
    <header v-if="title || sub || $slots.actions" class="card__head">
      <div>
        <h3 v-if="title" class="card__title">{{ title }}</h3>
        <p v-if="sub" class="card__sub">{{ sub }}</p>
      </div>
      <div v-if="$slots.actions" class="card__actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="card__body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="card__foot">
      <slot name="footer" />
    </footer>
  </section>
</template>

<style scoped>
.card__foot {
  padding: 0 20px 16px;
}
</style>
