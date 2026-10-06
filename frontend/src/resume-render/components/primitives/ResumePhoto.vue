<script setup lang="ts">
defineProps<{ src: string; shape: 'CIRCLE' | 'ROUNDED' | 'SQUARE' }>()
</script>

<template>
  <img class="rr-photo" :data-shape="shape.toLowerCase()" :src="src" alt="" />
</template>
