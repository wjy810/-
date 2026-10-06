<script setup lang="ts">
import AppIcon from '@/shared/ui/AppIcon.vue'
</script>

<template>
  <span class="user-avatar" aria-hidden="true">
    <AppIcon name="user" :size="16" />
  </span>
</template>

<style scoped>
.user-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  flex: none;
  margin-top: 16px;
  color: #fff;
  background: #315fba;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #b8c9e6, 0 5px 12px rgba(31, 72, 143, .16);
}
</style>
