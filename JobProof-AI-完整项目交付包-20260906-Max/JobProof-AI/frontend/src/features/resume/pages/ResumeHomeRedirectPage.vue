<template>
  <main class="resume-home-redirect" aria-busy="true">
    <span class="spinner" aria-hidden="true" />
    <p>正在打开最近使用的简历</p>
  </main>
</template>

<style scoped>
.resume-home-redirect{min-height:100vh;display:grid;place-content:center;justify-items:center;gap:12px;color:var(--text-2);background:var(--bg)}
.spinner{width:28px;height:28px;border:3px solid var(--border);border-top-color:var(--primary);border-radius:50%;animation:spin .8s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.spinner{animation:none}}
</style>
