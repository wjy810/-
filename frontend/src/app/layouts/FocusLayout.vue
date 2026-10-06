<script setup lang="ts">
/** Full-screen layout for the AI resume workbench and other immersive editors (docs/03 §4.3). */
</script>

<template>
  <div class="focus-shell">
    <RouterView v-slot="{ Component, route }">
      <component :is="Component" :key="route.path" />
    </RouterView>
  </div>
</template>

<style scoped>
.focus-shell {
  min-height: 100vh;
  view-transition-name: page;
}
</style>
