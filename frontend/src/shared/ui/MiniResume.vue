<script setup lang="ts">
/**
 * Stylised A4 miniature used for marketing and thumbnails (not a real render).
 * Variants mirror the smart-template families: banner, sidebar, classic, minimal, split.
 */
withDefaults(defineProps<{ variant?: 'banner' | 'sidebar' | 'classic' | 'minimal' | 'split'; accent?: string; name?: string; role?: string }>(), {
  variant: 'banner',
  accent: '#4a44d9',
  name: '林晓',
  role: '后端开发实习生',
})
</script>

<template>
  <div class="mini-resume" :class="`mini-resume--${variant}`" :style="{ '--accent': accent }" aria-hidden="true">
    <header class="mr-head">
      <span v-if="variant !== 'minimal' && variant !== 'classic'" class="mr-photo" />
      <span class="mr-head__text">
        <strong>{{ name }}</strong>
        <small>{{ role }}</small>
      </span>
    </header>
    <div class="mr-body">
      <aside v-if="variant === 'sidebar' || variant === 'split'" class="mr-side">
        <span class="mr-title" />
        <span class="mr-line" style="width: 90%" />
        <span class="mr-line" style="width: 70%" />
        <span class="mr-title" />
        <span class="mr-chip-row"><i /><i /><i /></span>
        <span class="mr-title" />
        <span class="mr-line" style="width: 80%" />
      </aside>
      <div class="mr-main">
        <template v-for="block in 3" :key="block">
          <span class="mr-title" />
          <span class="mr-meta"><i /><i /></span>
          <span class="mr-line" style="width: 96%" />
          <span class="mr-line" style="width: 88%" />
          <span v-if="block !== 3" class="mr-line" style="width: 72%" />
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mini-resume {
  width: 100%;
  aspect-ratio: 210 / 297;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 1px 1px rgba(30, 28, 25, 0.05), 0 14px 32px -14px rgba(30, 28, 25, 0.28);
  color: #1e1c19;
  font-family: var(--font-sans);
}

.mr-head {
  display: flex;
  align-items: center;
  gap: 8%;
  padding: 9% 9% 6%;
}

.mr-head__text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.mr-head strong {
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.04em;
  line-height: 1.2;
}

.mr-head small {
  color: #7e786d;
  font-size: 8px;
}

.mr-photo {
  width: 22%;
  aspect-ratio: 1;
  flex-shrink: 0;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 20%, #fff);
  box-shadow: inset 0 -8px 0 color-mix(in srgb, var(--accent) 35%, #fff);
}

.mr-body {
  flex: 1;
  display: flex;
  gap: 6%;
  padding: 0 9% 9%;
}

.mr-side {
  width: 34%;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.mr-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.mr-title {
  width: 42%;
  height: 5px;
  margin-top: 6px;
  border-radius: 3px;
  background: var(--accent);
}

.mr-line {
  height: 3px;
  border-radius: 2px;
  background: #ebe7e0;
}

.mr-meta {
  display: flex;
  justify-content: space-between;
}

.mr-meta i {
  width: 34%;
  height: 4px;
  border-radius: 2px;
  background: #d9d4ca;
}

.mr-meta i:last-child {
  width: 18%;
}

.mr-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.mr-chip-row i {
  width: 28%;
  height: 6px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--accent) 18%, #fff);
}

/* banner: solid colour header */
.mini-resume--banner .mr-head {
  margin-bottom: 7%;
  background: var(--accent);
  color: #fff;
}

.mini-resume--banner .mr-head small {
  color: rgba(255, 255, 255, 0.8);
}

.mini-resume--banner .mr-photo {
  background: rgba(255, 255, 255, 0.3);
  box-shadow: inset 0 -8px 0 rgba(255, 255, 255, 0.4);
}

/* sidebar: tinted left column */
.mini-resume--sidebar .mr-body {
  padding-left: 0;
}

.mini-resume--sidebar .mr-side {
  padding: 4% 6% 0 9%;
  background: color-mix(in srgb, var(--accent) 8%, #fff);
}

.mini-resume--sidebar .mr-head {
  border-bottom: 2px solid var(--accent);
  margin-bottom: 5%;
}

/* classic: centred serif-like header with rules */
.mini-resume--classic .mr-head {
  justify-content: center;
  text-align: center;
  margin: 0 9% 6%;
  border-bottom: 1px solid #1e1c19;
}

.mini-resume--classic .mr-head strong {
  font-family: 'Songti SC', 'Noto Serif SC', serif;
  font-size: 14px;
}

.mini-resume--classic .mr-title {
  width: 100%;
  height: 1.5px;
  background: color-mix(in srgb, var(--accent) 70%, #1e1c19);
}

/* minimal: left accent bar */
.mini-resume--minimal .mr-head {
  border-left: 4px solid var(--accent);
  margin: 9% 0 5% 9%;
  padding: 1% 0 1% 6%;
}

.mini-resume--minimal .mr-title {
  width: 28%;
  height: 4px;
  background: #1e1c19;
}

/* split: two-tone header + sidebar */
.mini-resume--split .mr-head {
  margin-bottom: 6%;
  background: linear-gradient(90deg, var(--accent) 38%, color-mix(in srgb, var(--accent) 12%, #fff) 38%);
}

.mini-resume--split .mr-photo {
  background: rgba(255, 255, 255, 0.35);
  box-shadow: inset 0 -8px 0 rgba(255, 255, 255, 0.45);
}
</style>
