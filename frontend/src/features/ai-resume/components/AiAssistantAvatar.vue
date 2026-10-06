<script setup lang="ts">
withDefaults(defineProps<{
  active?: boolean
}>(), {
  active: false,
})
</script>

<template>
  <span class="ai-avatar" :class="{ 'ai-avatar--active': active }" aria-hidden="true">
    <span class="ai-avatar__antenna" />
    <span class="ai-avatar__shell">
      <span class="ai-avatar__face">
        <span class="ai-avatar__eyes"><i /><i /></span>
        <span class="ai-avatar__mouth" />
      </span>
      <span class="ai-avatar__status" />
    </span>
  </span>
</template>

<style scoped>
.ai-avatar {
  width: 32px;
  height: 34px;
  position: relative;
  display: block;
  flex: none;
  margin-top: 16px;
  color: #2563eb;
}
.ai-avatar__antenna {
  width: 2px;
  height: 6px;
  position: absolute;
  top: 0;
  left: 15px;
  z-index: 1;
  background: #7da2dc;
  border-radius: 2px;
}
.ai-avatar__antenna::after {
  content: '';
  width: 5px;
  height: 5px;
  position: absolute;
  top: -3px;
  left: -1.5px;
  background: #4f7fc8;
  border: 1px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 2px #dce9fb;
}
.ai-avatar__shell {
  width: 30px;
  height: 27px;
  position: absolute;
  left: 1px;
  bottom: 0;
  display: grid;
  place-items: center;
  background: linear-gradient(180deg, #f8fbff 0%, #e9f2ff 100%);
  border: 1px solid #9bb9e5;
  border-radius: 8px;
  box-shadow: 0 5px 12px rgba(40, 79, 140, .14), inset 0 1px 0 #fff;
  animation: ai-avatar-idle 3.8s ease-in-out infinite;
  transform-origin: 50% 100%;
}
.ai-avatar__face {
  width: 21px;
  height: 15px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 3px;
  background: #d9e8fc;
  border: 1px solid #b8cff0;
  border-radius: 5px;
  transition: background-color .25s ease, box-shadow .25s ease;
}
.ai-avatar__eyes {
  display: flex;
  gap: 6px;
  animation: ai-avatar-blink 5.6s ease-in-out infinite;
  transform-origin: center;
}
.ai-avatar__eyes i {
  width: 3px;
  height: 4px;
  display: block;
  background: currentColor;
  border-radius: 3px;
}
.ai-avatar__mouth {
  width: 7px;
  height: 2px;
  border-bottom: 1.5px solid #5277ad;
  border-radius: 0 0 7px 7px;
}
.ai-avatar__status {
  width: 5px;
  height: 5px;
  position: absolute;
  right: -2px;
  bottom: 3px;
  background: #22a06b;
  border: 1.5px solid #fff;
  border-radius: 50%;
}
.ai-avatar--active .ai-avatar__shell {
  animation: ai-avatar-active 1.7s ease-in-out infinite;
}
.ai-avatar--active .ai-avatar__face {
  background: #cfe2ff;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, .07);
}
.ai-avatar--active .ai-avatar__status {
  animation: ai-avatar-pulse 1.4s ease-in-out infinite;
}
@keyframes ai-avatar-blink {
  0%, 43%, 47%, 100% { transform: scaleY(1); }
  45% { transform: scaleY(.08); }
}
@keyframes ai-avatar-idle {
  0%, 100% { transform: translateY(0) rotate(0); }
  50% { transform: translateY(-1.5px) rotate(.5deg); }
}
@keyframes ai-avatar-active {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}
@keyframes ai-avatar-pulse {
  0%, 100% { opacity: .65; transform: scale(.85); }
  50% { opacity: 1; transform: scale(1.15); }
}
@media (prefers-reduced-motion: reduce) {
  .ai-avatar__shell, .ai-avatar__eyes, .ai-avatar__status { animation: none; }
}
</style>
