<script setup lang="ts">
import AppIcon from '@/shared/ui/AppIcon.vue'
import type { IconName } from '@/shared/ui/icons'
import brandLogo from '@/assets/jobproof-ai-logo.png'

defineProps<{
  title: string
  sub: string
}>()

const props: Array<{ icon: IconName; title: string; sub: string }> = [
  { icon: 'shield', title: '沉淀职业资料', sub: '让经历与成果随时可用' },
  { icon: 'file-text', title: '用版本记录过程', sub: '清晰追踪成长与迭代' },
  { icon: 'trending-up', title: '辅助简历优化', sub: '让 AI 建议基于确认事实' },
]
</script>

<template>
  <div class="auth">
    <aside class="auth__brand">
      <div class="auth__aurora auth__aurora--one" aria-hidden="true" />
      <div class="auth__aurora auth__aurora--two" aria-hidden="true" />
      <div class="auth__grid" aria-hidden="true" />

      <div class="auth__logo">
        <img :src="brandLogo" alt="JobProof AI" width="124" height="27">
      </div>

      <div class="auth__story">
        <p class="auth__eyebrow"><span /> 可验证的职业成长档案</p>
        <h2>让每一次求职，<br /><em>都有证据可循。</em></h2>
        <p class="auth__story-sub">把经历、技能与职业文件沉淀为长期资料，让 AI 建议建立在确认事实之上。</p>
      </div>

      <ul class="auth__props">
        <li v-for="item in props" :key="item.title">
          <span class="auth__prop-icon"><AppIcon :name="item.icon" :size="19" /></span>
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.sub }}</p>
          </div>
        </li>
      </ul>

      <div class="auth__trust">
        <span><AppIcon name="shield" :size="14" /> 事实与 AI 推断分离</span>
        <span>结果可追溯</span>
        <span>数据可导出</span>
      </div>
    </aside>

    <main class="auth__main">
      <div class="auth__mobile-logo">
        <img :src="brandLogo" alt="JobProof AI" width="124" height="27">
      </div>
      <div class="auth__card">
        <div class="auth__heading">
          <span class="auth__heading-mark"><AppIcon name="shield" :size="18" /></span>
          <h1>{{ title }}</h1>
          <p class="auth__sub">{{ sub }}</p>
        </div>
        <slot />
        <div class="auth__consent">
          <p class="auth__consent-link">
            <AppIcon name="shield" :size="14" />
            <RouterLink class="text-link" to="/account/data-rights">隐私与数据说明</RouterLink>
          </p>
          <p class="auth__consent-text">登录即表示你已阅读并同意《隐私与数据说明》，我们将严格保护你的数据安全与隐私权利。</p>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(450px, 46%) 1fr;
  background: #f7f9fc;
}

.auth__brand {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  min-height: 100vh;
  padding: clamp(42px, 6vw, 76px) clamp(42px, 6vw, 82px);
  display: flex;
  flex-direction: column;
  color: #fff;
  background: linear-gradient(145deg, #07142f 0%, #0b2047 48%, #102d62 100%);
}

.auth__grid {
  position: absolute;
  inset: 0;
  z-index: -3;
  opacity: .12;
  background-image: linear-gradient(rgba(255,255,255,.14) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.14) 1px, transparent 1px);
  background-size: 52px 52px;
  mask-image: linear-gradient(to bottom right, #000, transparent 76%);
}

.auth__aurora { position:absolute; z-index:-2; border-radius:50%; filter:blur(2px); pointer-events:none; }
.auth__aurora--one { width:520px; height:520px; left:-250px; bottom:-210px; background:radial-gradient(circle, rgba(44,124,255,.58), rgba(44,124,255,0) 68%); }
.auth__aurora--two { width:440px; height:440px; right:-220px; top:12%; background:radial-gradient(circle, rgba(62,208,191,.18), rgba(62,208,191,0) 68%); }

.auth__logo { width:fit-content; padding:8px 10px; display:flex; align-items:center; border-radius:8px; background:#fff; box-shadow:0 10px 26px rgba(0,0,0,.16); }
.auth__logo img, .auth__mobile-logo img { width:124px; height:27px; object-fit:contain; }
.auth__mark {
  width:48px; height:48px; border-radius:15px; display:grid; place-items:center;
  color:#fff; background:linear-gradient(145deg,#3b82f6,#1d5eea);
  box-shadow:0 14px 30px rgba(0,65,190,.42), inset 0 1px 0 rgba(255,255,255,.25);
}

.auth__story { margin:auto 0 38px; max-width:560px; }
.auth__eyebrow { display:flex; align-items:center; gap:9px; margin-bottom:20px; font-size:12px; font-weight:700; letter-spacing:.16em; color:#a9c9ff; }
.auth__eyebrow span { width:28px; height:2px; border-radius:9px; background:#4f91ff; }
.auth__story h2 { font-size:clamp(38px,4.4vw,60px); line-height:1.14; letter-spacing:-.045em; font-weight:750; }
.auth__story h2 em { color:#79aaff; font-style:normal; }
.auth__story-sub { max-width:470px; margin-top:24px; font-size:15px; line-height:1.8; color:#a9bcda; }

.auth__props { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; list-style:none; }
.auth__props li { min-height:132px; padding:18px; border:1px solid rgba(255,255,255,.1); border-radius:16px; background:rgba(255,255,255,.055); backdrop-filter:blur(12px); transition:transform .2s ease,border-color .2s ease,background .2s ease; }
.auth__props li:hover { transform:translateY(-3px); border-color:rgba(126,171,255,.35); background:rgba(255,255,255,.085); }
.auth__prop-icon { width:38px; height:38px; margin-bottom:14px; border-radius:11px; display:grid; place-items:center; color:#9bc0ff; background:rgba(60,128,255,.15); border:1px solid rgba(115,164,255,.2); }
.auth__props strong { font-size:13px; }
.auth__props p { margin-top:6px; font-size:11px; line-height:1.6; color:#91a6ca; }
.auth__trust { display:flex; flex-wrap:wrap; align-items:center; gap:18px; margin-top:28px; font-size:11px; color:#7f98c1; }
.auth__trust span { display:flex; align-items:center; gap:5px; }
.auth__trust span:not(:first-child)::before { content:'·'; margin-right:12px; color:#40618f; }

.auth__main { position:relative; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:54px 32px; background:radial-gradient(circle at 70% 15%,rgba(41,110,245,.07),transparent 28%),#f8fafc; }
.auth__main::before { content:''; position:absolute; width:300px; height:300px; right:5%; bottom:5%; border-radius:50%; background:radial-gradient(circle,rgba(37,99,235,.05),transparent 70%); pointer-events:none; }
.auth__mobile-logo { display:none; }
.auth__card { position:relative; width:min(480px,100%); padding:44px 46px 38px; border:1px solid rgba(214,222,235,.9); border-radius:24px; background:rgba(255,255,255,.94); box-shadow:0 24px 70px rgba(29,55,94,.12),0 2px 8px rgba(29,55,94,.04); }
.auth__heading { margin-bottom:30px; text-align:center; }
.auth__heading-mark { width:42px; height:42px; margin:0 auto 16px; border-radius:13px; display:grid; place-items:center; color:#2563eb; background:#edf4ff; border:1px solid #dce9ff; }
.auth__card h1 { font-size:28px; line-height:1.25; letter-spacing:-.025em; color:#111c32; }
.auth__sub { margin-top:9px; font-size:14px; color:#64748b; }
.auth__consent { margin-top:26px; padding-top:22px; text-align:center; border-top:1px solid #edf0f5; }
.auth__consent-link { display:flex; align-items:center; justify-content:center; gap:6px; font-size:13px; color:#16805c; }
.auth__consent-text { max-width:360px; margin:8px auto 0; font-size:11px; line-height:1.7; color:#94a0b3; }

@media (max-width:1050px) { .auth { grid-template-columns:minmax(390px,42%) 1fr; } .auth__brand { padding:48px 40px; } .auth__story h2 { font-size:42px; } .auth__props { grid-template-columns:1fr; } .auth__props li { min-height:auto; display:grid; grid-template-columns:38px 1fr; column-gap:13px; padding:14px; } .auth__prop-icon { grid-row:1/3; margin:0; } .auth__props p { margin-top:3px; } }
@media (max-width:820px) { .auth { display:block; } .auth__brand { display:none; } .auth__main { min-height:100vh; justify-content:flex-start; padding:38px 20px 44px; } .auth__mobile-logo { display:flex; align-items:center; width:min(480px,100%); margin-bottom:28px; color:#14213a; } }
@media (max-width:520px) { .auth__main { padding:24px 16px 32px; } .auth__mobile-logo { margin-bottom:20px; } .auth__card { padding:32px 24px 28px; border-radius:20px; } .auth__card h1 { font-size:25px; } }
@media (prefers-reduced-motion:reduce) { .auth__props li { transition:none; } }
</style>
