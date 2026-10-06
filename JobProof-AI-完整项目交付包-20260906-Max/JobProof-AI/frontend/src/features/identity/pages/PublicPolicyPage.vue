<script setup lang="ts">
import { ArrowLeft, FileText, ShieldCheck } from 'lucide-vue-next'
import brandLogo from '@/assets/jobproof-ai-logo.png'

const props = defineProps<{ kind: 'terms' | 'privacy' }>()
const isTerms = props.kind === 'terms'
</script>

<template>
  <div class="policy-page">
    <header class="policy-topbar">
      <RouterLink class="policy-brand" to="/"><img :src="brandLogo" alt="JobProof AI" width="124" height="27"></RouterLink>
      <RouterLink class="policy-back" to="/"><ArrowLeft :size="16" />返回首页</RouterLink>
    </header>
    <main>
      <article>
        <header>
          <span><FileText v-if="isTerms" :size="17" /><ShieldCheck v-else :size="17" />{{ isTerms ? '服务规则' : '信息保护' }}</span>
          <h1>{{ isTerms ? 'JobProof AI 用户协议' : 'JobProof AI 隐私政策' }}</h1>
          <p>版本：2026-08-26-v1 · 生效状态：开发环境待业务与法律复核</p>
        </header>

        <aside><ShieldCheck :size="18" />本页面当前用于产品开发和验收。在完成业务、法律及供应商条款复核前，不作为正式对外版本。</aside>

        <template v-if="isTerms">
          <section><h2>1. 服务范围</h2><p>JobProof AI 提供简历创建、求职资料管理、岗位信息整理、模板预览与文件导出等求职准备工具。平台不代表用户向第三方自动投递，也不承诺面试或录用结果。</p></section>
          <section><h2>2. 账号与身份验证</h2><p>用户可使用已验证邮箱或中国大陆手机号创建账号并使用密码登录。验证码仅用于确认联系方式归属，不用于验证码登录。用户应妥善保管密码和验证信息。</p></section>
          <section><h2>3. 用户内容与真实性</h2><p>用户应确保录入的教育、经历、项目、技能和成果信息真实合法。AI 生成内容属于待确认建议，用户在采纳、导出或使用前应自行核对。</p></section>
          <section><h2>4. 合理使用</h2><p>不得利用服务发送垃圾信息、绕过安全门禁、批量盗取模板或侵犯他人隐私和知识产权。对明显异常或高风险请求，平台可暂时限制相关功能。</p></section>
          <section><h2>5. 服务变更与终止</h2><p>开发阶段功能可能调整。重要变更将通过站内方式说明；账号删除与数据导出按产品内的数据权利流程处理。</p></section>
        </template>
        <template v-else>
          <section><h2>1. 收集的信息</h2><p>为创建账号，我们处理邮箱或手机号、密码哈希、验证时间、协议版本和会话记录。使用简历功能时，还会处理用户主动提交的求职资料与文件。</p></section>
          <section><h2>2. 使用目的</h2><p>相关信息用于身份验证、账号安全、简历与求职资料服务、问题排查和必要审计。登录凭据、原始密码和验证码不会发送给 AI 模型。</p></section>
          <section><h2>3. 验证服务供应商</h2><p>邮件验证码可通过 QQ SMTP 发送，短信验证码可通过阿里云号码认证服务发送与核验。供应商只接收完成验证所需的最少联系信息和请求参数。</p></section>
          <section><h2>4. 保存与安全</h2><p>密码使用不可逆哈希保存，验证码不以明文长期保存，验证令牌一次性消费。日志仅记录脱敏联系方式、供应商请求号和安全错误码。</p></section>
          <section><h2>5. 用户权利</h2><p>用户可在账号功能中查看会话、修改密码、申请数据导出或删除账号。联系方式绑定与更换不属于当前版本，后续上线时将另行提供确认流程。</p></section>
        </template>
      </article>
    </main>
  </div>
</template>

<style scoped>
.policy-page { min-height: 100vh; color: #17233a; background: #f5f8fc; }
.policy-topbar { position: sticky; top: 0; z-index: 10; height: 64px; display: flex; align-items: center; justify-content: space-between; padding: 0 max(22px, calc((100vw - 980px) / 2)); border-bottom: 1px solid #dfe7f2; background: rgba(255, 255, 255, .94); backdrop-filter: blur(12px); }
.policy-brand, .policy-back { display: inline-flex; align-items: center; gap: 9px; color: #10234a; }
.policy-brand img { width: 124px; height: 27px; object-fit: contain; }
.policy-back { color: #52647f; font-size: 13px; }
.policy-page main { width: min(900px, calc(100% - 32px)); margin: 0 auto; padding: 48px 0 72px; }
.policy-page article { padding: 44px 56px; border: 1px solid #dfe7f2; border-radius: 8px; background: #fff; box-shadow: 0 18px 50px rgba(25, 48, 82, .07); }
.policy-page article > header span { display: inline-flex; align-items: center; gap: 6px; padding: 5px 9px; border-radius: 6px; color: #2457e8; background: #edf3ff; font-size: 12px; font-weight: 700; }
.policy-page h1 { margin: 12px 0 5px; color: #10234a; font-size: 32px; letter-spacing: 0; }
.policy-page article > header p { margin: 0; color: #7a899e; font-size: 12px; }
.policy-page aside { display: flex; align-items: flex-start; gap: 8px; margin: 28px 0 32px; padding: 14px 16px; border: 1px solid #f0d7a2; border-radius: 8px; color: #7b5910; background: #fff9eb; font-size: 13px; line-height: 1.7; }
.policy-page aside svg { flex: 0 0 auto; margin-top: 3px; }
.policy-page section { padding: 21px 0; border-top: 1px solid #e8edf4; }
.policy-page section h2 { margin: 0 0 8px; color: #1f3150; font-size: 17px; letter-spacing: 0; }
.policy-page section p { margin: 0; color: #53647c; font-size: 14px; line-height: 1.85; }
@media (max-width: 640px) {
  .policy-topbar { height: 58px; padding: 0 16px; }
  .policy-page main { width: 100%; padding: 0; }
  .policy-page article { padding: 30px 20px 48px; border-width: 0; border-radius: 0; box-shadow: none; }
  .policy-page h1 { font-size: 26px; }
}
</style>
