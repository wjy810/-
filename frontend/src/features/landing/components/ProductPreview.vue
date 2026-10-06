<script setup lang="ts">
import { Check, Download, FileCheck2, Send } from 'lucide-vue-next'
// The homepage preview uses the compact JP mark (the same 32px asset as the
// public favicon), rather than the wide wordmark which collapses in this slot.
const brandIcon = '/favicon.png'
</script>

<template>
  <figure id="product-preview" class="product-scene" aria-label="JobProof AI 产品界面预览">
    <div class="product-window product-window--chat">
      <div class="product-window__bar">
        <span class="product-ai-mark"><img :src="brandIcon" alt="" width="28" height="28" /></span>
        <strong>AI 简历助手</strong>
        <i aria-hidden="true"></i>
        <small>在线</small>
      </div>
      <div class="product-chat">
        <div class="product-chat__user">我想准备产品经理岗位，帮我梳理一份真实、清晰的简历。</div>
        <div class="product-chat__answer">好的，我会先确认事实，再生成可以逐项采纳的简历内容。</div>
        <div class="product-check"><Check :size="15" /><span>提取教育与项目经历</span></div>
        <div class="product-check"><Check :size="15" /><span>整理岗位相关经历</span></div>
        <div class="product-check"><Check :size="15" /><span>生成可核验的表达</span></div>
      </div>
      <div class="product-composer"><span>继续补充一段项目经历…</span><Send :size="17" /></div>
    </div>

    <div class="product-window product-window--resume">
      <div class="product-resume__head"><span>张一帆</span><small>产品经理</small></div>
      <p>北京 · 138-0000-8888 · zhangyifan@example.com</p>
      <h3>个人简介</h3>
      <p>具备用户研究、需求分析与项目协作经验，能够从业务目标出发推进方案落地。</p>
      <h3>项目经历</h3>
      <div class="product-resume__row"><strong>校园招聘管理平台</strong><time>2025.03 - 2025.06</time></div>
      <ul>
        <li>梳理目标用户流程，输出核心需求与迭代优先级。</li>
        <li>协调设计与研发完成首版验证，并基于反馈持续优化。</li>
      </ul>
      <h3>技能</h3>
      <div class="product-resume__tags"><span>需求分析</span><span>用户研究</span><span>数据分析</span></div>
      <div class="product-resume__actions"><span><FileCheck2 :size="14" />真实 A4 预览</span><strong><Download :size="14" />导出 PDF</strong></div>
    </div>

    <div class="product-float product-float--proof">
      <FileCheck2 :size="21" />
      <span><small>事实校验</small><strong>已通过</strong></span>
    </div>
  </figure>
</template>
