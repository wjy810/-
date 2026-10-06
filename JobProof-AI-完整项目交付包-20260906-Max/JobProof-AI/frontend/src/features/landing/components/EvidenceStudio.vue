<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Check, FileText, Pause, Play, Sun, CloudSun } from 'lucide-vue-next'
import type { EvidenceScene, SceneLight, SceneQuality } from './evidenceScene'

const host = ref<HTMLElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const ready = ref(false)
const staticOnly = ref(false)
const paused = ref(false)
const reduced = ref(false)
const quality = ref<SceneQuality>('standard')
const light = ref<SceneLight>('daylight')
const controls = computed(() => ready.value && !reduced.value && !staticOnly.value)
let scene: EvidenceScene | null = null
let resizeObserver: ResizeObserver | null = null
let intersection: IntersectionObserver | null = null
let media: MediaQueryList | null = null
let frame = 0
let disposed = false
let visible = true
let pendingLoad = false
let lastInteraction = 0
let lastFrame = 0
let elapsed = 0
let fixedTime: number | null = null
let px = 0
let py = 0
let targetX = 0
let targetY = 0
let slowFrames = 0
let frames = 0

function stop() { cancelAnimationFrame(frame); frame = 0 }
function draw(now: number) {
  frame = 0
  if (!scene || disposed || !visible || document.hidden) return
  const delta = lastFrame ? now - lastFrame : 0
  if (quality.value !== 'standard' && delta > 0 && delta < 32) { frame = requestAnimationFrame(draw); return }
  lastFrame = now
  if (fixedTime == null && !paused.value && !reduced.value) elapsed += Math.min(delta, 50) / 1000
  px += (targetX - px) * 0.09
  py += (targetY - py) * 0.09
  const started = performance.now()
  try { scene.render(fixedTime ?? Math.max(0.01, elapsed), px, py) }
  catch { staticOnly.value = true; release(); return }
  if (performance.now() - started > 35 && ++slowFrames > 30 && quality.value !== 'standard') quality.value = 'standard'
  frames++
  if (host.value && import.meta.env.DEV) {
    host.value.dataset.frames = String(frames)
    host.value.dataset.sceneTime = String(fixedTime ?? elapsed)
    host.value.dataset.postprocessing = quality.value === 'cinematic' ? 'depth-of-field-selective-highlight' : 'none'
  }
  if (fixedTime != null || paused.value || reduced.value) return
  if (quality.value !== 'standard' || now - lastInteraction < 3000) frame = requestAnimationFrame(draw)
}
function wake() {
  if (disposed || !scene || document.hidden || !visible) return
  lastInteraction = performance.now()
  lastFrame = 0
  if (!frame) frame = requestAnimationFrame(draw)
}
function pointer(event: PointerEvent) {
  if (!controls.value || paused.value || fixedTime != null || !host.value) return
  const box = host.value.getBoundingClientRect()
  targetX = Math.max(-1, Math.min(1, (event.clientX - box.left) / box.width * 2 - 1))
  targetY = Math.max(-1, Math.min(1, (event.clientY - box.top) / box.height * 2 - 1))
  wake()
}
function leave() { targetX = 0; targetY = 0; wake() }
async function loadScene() {
  if (disposed || pendingLoad || scene || !canvas.value || !host.value || staticOnly.value || reduced.value || innerWidth < 921) return
  pendingLoad = true
  try {
    const { createEvidenceScene } = await import('./evidenceScene')
    if (disposed || staticOnly.value || reduced.value || innerWidth < 921 || !canvas.value || !host.value) return
    scene = createEvidenceScene(canvas.value, quality.value)
    scene.setLight(light.value)
    scene.resize(host.value.clientWidth, host.value.clientHeight - 56)
    elapsed = fixedTime ?? (reduced.value ? 2 : 0)
    ready.value = true
    wake()
  } catch { staticOnly.value = true; ready.value = false }
  finally { pendingLoad = false }
}
function release() { stop(); scene?.dispose(); scene = null; ready.value = false }
function onResize() {
  if (!host.value) return
  if (innerWidth < 921 || reduced.value) release()
  else if (scene) { scene.resize(host.value.clientWidth, host.value.clientHeight - 56); wake() }
  else void loadScene()
}
function onMotion() { reduced.value = media?.matches ?? false; onResize() }
function onVisibility() { if (document.hidden) stop(); else wake() }
function onContextLoss(event: Event) {
  event.preventDefault()
  if (disposed) return
  staticOnly.value = true; release()
}
watch(quality, value => { scene?.setQuality(value); wake() })
watch(light, value => { scene?.setLight(value); wake() })
watch(paused, () => { stop(); wake() })
onMounted(() => {
  media = matchMedia('(prefers-reduced-motion: reduce)')
  reduced.value = media.matches
  if (import.meta.env.DEV) {
    const query = new URLSearchParams(location.search)
    staticOnly.value = query.get('studio') === 'static'
    if (query.get('studio') === 'still') fixedTime = 2.4
    if (query.get('studio') === 'still' && query.has('time')) {
      const value = Number(query.get('time'))
      if (Number.isFinite(value)) fixedTime = Math.max(0, Math.min(value, 3600))
    }
    if (['standard', 'high', 'cinematic'].includes(query.get('quality') || '')) quality.value = query.get('quality') as SceneQuality
    if (query.get('light') === 'cloud') light.value = 'cloud'
  }
  media.addEventListener('change', onMotion)
  document.addEventListener('visibilitychange', onVisibility)
  canvas.value?.addEventListener('webglcontextlost', onContextLoss)
  resizeObserver = new ResizeObserver(onResize)
  if (host.value) {
    resizeObserver.observe(host.value)
    intersection = new IntersectionObserver(entries => { visible = !!entries[0]?.isIntersecting; if (visible) { void loadScene(); wake() } else stop() })
    intersection.observe(host.value)
  }
})
onBeforeUnmount(() => {
  disposed = true
  media?.removeEventListener('change', onMotion)
  document.removeEventListener('visibilitychange', onVisibility)
  canvas.value?.removeEventListener('webglcontextlost', onContextLoss)
  resizeObserver?.disconnect(); intersection?.disconnect(); release()
})
</script>

<template>
  <figure id="product-preview" ref="host" class="evidence-studio product-scene" :data-renderer="ready ? 'webgl' : 'static'" :data-quality="quality" :data-paused="paused" aria-label="简历纸张、项目证据与技能路径示意" @pointermove="pointer" @pointerleave="leave">
    <div class="studio-daylight" aria-hidden="true"></div>
    <div class="studio-surface" :class="{ 'is-rendered': ready }">
      <svg class="studio-path" viewBox="0 0 640 520" aria-hidden="true"><path d="M56 146 C115 54 359 48 510 92 S615 322 550 420" /><circle cx="56" cy="146" r="5" /><circle cx="387" cy="72" r="5" /><circle cx="550" cy="420" r="5" /></svg>
      <article class="studio-evidence studio-evidence--project"><span class="studio-index">01 / 项目证据</span><h3>经历，有据可依</h3><p>整理项目中的思考、行动<br>与每一项可验证的成果。</p><div class="studio-confirm"><Check :size="13" /> 由你确认，再写入简历</div></article>
      <article class="studio-paper"><header><span class="studio-monogram">J / P</span><small>个人简历 · 示例</small></header><h3>让真实经历<br>被看见。</h3><p>产品设计 / 用户体验</p><div v-for="title in ['个人简介', '项目经历', '教育背景', '专业技能']" :key="title" class="studio-paper__section"><strong>{{ title }}</strong><i></i><i></i><i></i></div><footer>JOBPROOF AI <span>01</span></footer></article>
      <article class="studio-evidence studio-evidence--skills"><span class="studio-index">02 / 能力连接</span><h3>能力，清晰呈现</h3><div class="studio-skill-tags"><span>用户研究</span><span>需求分析</span><span>数据表达</span></div></article>
    </div>
    <canvas ref="canvas" class="studio-canvas" :class="{ 'is-ready': ready }" aria-hidden="true"></canvas>
    <figcaption><span><FileText :size="15" /> 从真实经历，到一份好简历</span><small>{{ ready ? '纸张与证据 · 场景示意' : '静态预览 · 示例内容' }}</small></figcaption>
    <div v-if="controls" class="studio-controls" aria-label="画面设置">
      <div class="studio-quality" aria-label="画面质量"><button v-for="item in ([['standard', '轻量'], ['high', '细腻'], ['cinematic', '电影']] as const)" :key="item[0]" type="button" :aria-pressed="quality === item[0]" @click="quality = item[0]">{{ item[1] }}</button></div>
      <button type="button" :aria-label="light === 'daylight' ? '切换薄云光照' : '切换日光'" @click="light = light === 'daylight' ? 'cloud' : 'daylight'"><Sun v-if="light === 'daylight'" :size="15" /><CloudSun v-else :size="15" /></button>
      <button type="button" :aria-label="paused ? '继续场景动态' : '暂停场景动态'" :aria-pressed="paused" @click="paused = !paused"><Play v-if="paused" :size="14" /><Pause v-else :size="14" /></button>
    </div>
  </figure>
</template>

<style scoped>
.evidence-studio { position: relative; isolation: isolate; width: 100%; height: 560px; margin: 0; min-width: 0; }
.studio-daylight { position: absolute; inset: 1% -3% 9%; z-index: -1; background: radial-gradient(ellipse at 20% 5%, #fff5daaa, transparent 68%), linear-gradient(133deg, #eff2ed00 15%, #e1eeed77 64%, #edf3f133); border-radius: 45% 8% 30% 10%; }
.studio-surface { position: absolute; inset: 0 0 56px; transition: opacity 200ms; }
.studio-surface.is-rendered { opacity: 0; visibility: hidden; pointer-events: none; }
.studio-canvas { position: absolute; inset: 0 0 56px; width: 100%; height: calc(100% - 56px); opacity: 0; pointer-events: none; }
.studio-canvas.is-ready { opacity: 1; }
.studio-path { position: absolute; inset: 0; width: 100%; height: 100%; fill: #2563eb; overflow: visible; }
.studio-path path { fill: none; stroke: #94b8b9; stroke-width: 1.25; stroke-dasharray: 5 5; }
.studio-paper { position: absolute; left: 37%; top: 7%; width: 43%; height: 80%; padding: 25px 22px 14px; transform: rotate(-5deg); background: #fffefb; border: 1px solid #e4e6dd; box-shadow: 9px 17px 20px -14px #1f3d3f44, 25px 32px 50px -27px #18332f33; color: #243e49; border-radius: 2px; }
.studio-paper header { display: flex; justify-content: space-between; align-items: center; font-size: 10px; }
.studio-paper small { font-size: 10px; color: #617174; }
.studio-monogram { color: #2563eb; font-weight: 700; letter-spacing: 3px; }
.studio-paper h3 { margin: 18px 0 6px; font-family: 'Noto Serif SC', 'Songti SC', 'SimSun', serif; font-size: 26px; line-height: 1.3; font-weight: 600; }
.studio-paper > p { font-size: 10px; color: #607477; padding-bottom: 13px; border-bottom: 2px solid #2563eb; }
.studio-paper__section { margin-top: 14px; display: grid; gap: 6px; }
.studio-paper__section strong { font-size: 10px; }
.studio-paper__section i { height: 3px; background: #d8e0df; border-radius: 1px; }
.studio-paper__section i:last-child { width: 65%; }
.studio-paper footer { display: flex; justify-content: space-between; margin-top: 18px; font-size: 8px; color: #758884; letter-spacing: 1px; }
.studio-evidence { position: absolute; z-index: 2; padding: 21px 22px; border: 1px solid #dfe6dd; border-radius: 8px; background: #fffefa; box-shadow: 10px 16px 24px -19px #21433d66, 20px 24px 38px -27px #273e3244; }
.studio-evidence--project { left: 1%; top: 31%; width: 43%; transform: rotate(2deg); }
.studio-evidence--skills { right: 0; bottom: 8%; width: 47%; transform: rotate(2deg); }
.studio-index { color: #397e7a; font-size: 11px; font-weight: 650; letter-spacing: 1px; }
.studio-evidence h3 { margin: 9px 0 7px; font-size: 17px; line-height: 1.4; }
.studio-evidence p { font-size: 12px; line-height: 1.7; color: #56696c; }
.studio-confirm { display: flex; gap: 5px; align-items: center; margin-top: 14px; padding-top: 10px; border-top: 1px solid #e5eae5; color: #457366; font-size: 10px; }
.studio-skill-tags { display: flex; gap: 5px; margin-top: 13px; flex-wrap: wrap; }
.studio-skill-tags span { padding: 4px 6px; font-size: 10px; color: #466778; background: #edf3f3; border-radius: 3px; }
figcaption { position: absolute; inset: auto 0 0; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; padding: 13px 2px; border-top: 1px solid #dbe5e3; color: #46605e; font-size: 12px; }
figcaption > span { display: flex; gap: 7px; align-items: center; }
figcaption small { color: #6e7a78; font-size: 12px; }
.studio-controls { position: absolute; display: flex; gap: 4px; align-items: center; right: 0; top: 0; }
.studio-controls button { display: grid; place-items: center; min-width: 32px; height: 34px; padding: 0 8px; border: 1px solid transparent; background: #fff9; color: #536766; border-radius: 6px; font-size: 12px; transition: background 150ms, color 150ms; }
.studio-controls button:hover { background: #fff; color: #2563eb; }
.studio-controls button:focus-visible { outline: 2px solid #2563eb; outline-offset: 3px; }
.studio-quality { display: flex; padding: 2px; border: 1px solid #dce5df; border-radius: 8px; background: #f5f7f2; }
.studio-quality button[aria-pressed='true'] { color: #2563eb; background: #fff; box-shadow: 0 1px 4px #233c4310; }
@media (max-width: 920px) { .evidence-studio { width: min(620px, 100%); height: 520px; justify-self: center; } }
@media (max-width: 640px) {
  .evidence-studio { height: 400px; }
  .studio-paper { left: 35%; top: 6%; width: 54%; height: 87%; padding: 15px 14px; }
  .studio-paper h3 { font-size: 22px; margin-top: 12px; }
  .studio-paper__section { margin-top: 10px; gap: 5px; }
  .studio-paper__section:last-of-type { display: none; }
  .studio-evidence { padding: 14px 12px; }
  .studio-evidence--project { left: 0; top: 28%; width: 47%; }
  .studio-evidence--skills { right: 0; bottom: 2%; width: 59%; }
  .studio-evidence h3 { font-size: 15px; }
  .studio-evidence p { font-size: 11px; }
  .studio-index { font-size: 10px; }
  .studio-confirm { font-size: 9px; margin-top: 8px; padding-top: 8px; }
  figcaption { font-size: 12px; }
}
@media (prefers-reduced-motion: reduce) { .studio-surface, .studio-controls button { transition: none; } }
</style>
