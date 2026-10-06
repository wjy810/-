<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { errorMessage, isNotFound } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import SettingsNav from '@/shared/ui/SettingsNav.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import ChannelEditor from '../components/ChannelEditor.vue'
import { archivePersonalChannel, cancelSubscriptionRenewal, createPersonalChannel, fetchBilling, fetchChannels, fetchLedger, fetchSubscription, fetchUsage, fetchWallet, subscribePersonal, testPersonalChannel, updatePersonalChannel } from '../services/aiApi'
import type { AiBilling, AiChannel, AiUsage, ChannelInput, PageResponse, PersonalSubscription, Wallet, WalletLedger } from '../types'

type DataTab = 'ledger' | 'usage' | 'billing'
const systemChannels = ref<AiChannel[]>([])
const personalChannels = ref<AiChannel[]>([])
const wallet = ref<Wallet | null>(null)
const subscription = ref<PersonalSubscription | null>(null)
const ledger = ref<PageResponse<WalletLedger> | null>(null)
const usage = ref<PageResponse<AiUsage> | null>(null)
const billing = ref<PageResponse<AiBilling> | null>(null)
const activeTab = ref<DataTab>('ledger')
const loading = ref(true)
const tabLoading = ref(false)
const pageError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'ai-settings-page-error')
useToastFeedback(notice, 'success', 'ai-settings-page-notice')
const pending = ref('')
const editorOpen = ref(false)
const editing = ref<AiChannel | null>(null)
const editorError = ref('')
const confirmArchive = ref<AiChannel | null>(null)
const personalEnabled = computed(() => subscription.value?.status === 'ACTIVE' && new Date(subscription.value.currentPeriodEnd).getTime() > Date.now())

const statusText: Record<string, string> = { ACTIVE: '启用', DISABLED: '停用', DRAFT: '草稿', ARCHIVED: '已归档', HEALTHY: '健康', DEGRADED: '降级', UNHEALTHY: '异常', UNKNOWN: '未检测', CANCEL_AT_PERIOD_END: '周期末停止续费', EXPIRED: '已过期', PAST_DUE: '待补款', SUCCEEDED: '成功', FAILED: '失败', CANCELLED: '已取消', PENDING: '处理中', RUNNING: '运行中', SETTLED: '已结算', PAYMENT_REQUIRED: '待付款', VOID: '已作废', PARTIALLY_REFUNDED: '部分退款', REFUNDED: '已退款' }
function label(value?: string | null): string { return value ? statusText[value] ?? value : '—' }
function tone(value?: string | null): 'green' | 'orange' | 'red' | 'gray' | 'blue' { if (['ACTIVE', 'HEALTHY', 'SUCCEEDED', 'SETTLED'].includes(value ?? '')) return 'green'; if (['FAILED', 'UNHEALTHY', 'PAST_DUE', 'PAYMENT_REQUIRED'].includes(value ?? '')) return 'red'; if (['DEGRADED', 'CANCEL_AT_PERIOD_END', 'PENDING', 'RUNNING'].includes(value ?? '')) return 'orange'; if (value === 'DISABLED') return 'gray'; return 'blue' }
function money(value: string | number | null | undefined, currency = 'USD'): string { const number = Number(value ?? 0); return `${currency} ${Number.isFinite(number) ? number.toFixed(4) : value}` }

async function loadOverview(): Promise<void> {
  loading.value = true
  pageError.value = ''
  const results = await Promise.allSettled([fetchChannels('SYSTEM'), fetchChannels('PERSONAL'), fetchWallet(), fetchSubscription()])
  if (results[0].status === 'fulfilled') systemChannels.value = results[0].value.items
  if (results[1].status === 'fulfilled') personalChannels.value = results[1].value.items
  if (results[2].status === 'fulfilled') wallet.value = results[2].value
  if (results[3].status === 'fulfilled') subscription.value = results[3].value
  else if (!isNotFound(results[3].reason)) pageError.value = errorMessage(results[3].reason, '订阅状态读取失败')
  const failed = results.slice(0, 3).find((result) => result.status === 'rejected')
  if (failed?.status === 'rejected') pageError.value = errorMessage(failed.reason, 'AI 配置读取失败')
  loading.value = false
}

async function loadTab(tab = activeTab.value, page = 0): Promise<void> {
  activeTab.value = tab
  tabLoading.value = true
  pageError.value = ''
  try {
    if (tab === 'ledger') ledger.value = await fetchLedger(page)
    if (tab === 'usage') usage.value = await fetchUsage(page)
    if (tab === 'billing') billing.value = await fetchBilling(page)
  } catch (error) { pageError.value = errorMessage(error, '记录读取失败') } finally { tabLoading.value = false }
}

function openCreate(): void { editing.value = null; editorError.value = ''; editorOpen.value = true }
function openEdit(channel: AiChannel): void { editing.value = channel; editorError.value = ''; editorOpen.value = true }
async function saveChannel(input: ChannelInput): Promise<void> {
  pending.value = 'editor'; editorError.value = ''
  try { editing.value ? await updatePersonalChannel(editing.value, input) : await createPersonalChannel(input); editorOpen.value = false; notice.value = editing.value ? '个人通道已更新。' : '个人通道已添加，密钥已安全写入。'; await loadOverview() }
  catch (error) { editorError.value = errorMessage(error, '通道保存失败；若数据已更新，请刷新后重试。') } finally { pending.value = '' }
}
async function toggleChannel(channel: AiChannel): Promise<void> {
  pending.value = `toggle-${channel.id}`; notice.value = ''
  try { await updatePersonalChannel(channel, { name: channel.name, providerCode: channel.providerCode, baseUrl: channel.baseUrl, apiKey: '', protocol: channel.protocol, status: channel.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }); notice.value = channel.status === 'ACTIVE' ? '个人通道已停用。' : '个人通道已启用。'; await loadOverview() }
  catch (error) { pageError.value = errorMessage(error, '状态更新失败；如发生版本冲突，请刷新后重试。') } finally { pending.value = '' }
}
async function testChannel(channel: AiChannel): Promise<void> {
  pending.value = `test-${channel.id}`; notice.value = ''
  try { const result = await testPersonalChannel(channel.id); notice.value = result.status === 'HEALTHY' ? `检测通过，延迟 ${result.latencyMs ?? '—'} ms。` : `检测完成：${label(result.status)}${result.message ? `，${result.message}` : ''}`; await loadOverview() }
  catch (error) { pageError.value = errorMessage(error, '通道检测失败') } finally { pending.value = '' }
}
async function archiveConfirmed(): Promise<void> {
  const channel = confirmArchive.value; if (!channel) return
  pending.value = `archive-${channel.id}`
  try { await archivePersonalChannel(channel); confirmArchive.value = null; notice.value = '个人通道已归档，原密钥不再生效。'; await loadOverview() }
  catch (error) { pageError.value = errorMessage(error, '归档失败；如发生版本冲突，请刷新后重试。') } finally { pending.value = '' }
}
async function subscribe(): Promise<void> {
  if (!wallet.value) return
  pending.value = 'subscribe'
  try { subscription.value = await subscribePersonal(wallet.value.versionNo); notice.value = '个人通道订阅已开通。'; await loadOverview(); await loadTab() }
  catch (error) { pageError.value = errorMessage(error, '开通失败，请检查钱包余额或刷新最新版本。') } finally { pending.value = '' }
}
async function cancelRenewal(): Promise<void> {
  if (!subscription.value) return
  pending.value = 'cancel-renewal'
  try { subscription.value = await cancelSubscriptionRenewal(subscription.value); notice.value = '已关闭自动续费；当前周期仍可继续使用。' }
  catch (error) { pageError.value = errorMessage(error, '关闭自动续费失败；如发生版本冲突，请刷新后重试。') } finally { pending.value = '' }
}
function pageFor(tab: DataTab): PageResponse<unknown> | null { return tab === 'ledger' ? ledger.value : tab === 'usage' ? usage.value : billing.value }
function turnPage(delta: number): void { const current = pageFor(activeTab.value); if (current) void loadTab(activeTab.value, current.page + delta) }

onMounted(async () => { await Promise.all([loadOverview(), loadTab('ledger')]) })
</script>

<template>
  <div class="page ai-page">
    <header class="page-head"><div><h1 class="page-head__title">AI 配置</h1><p class="page-head__sub">管理系统与个人 AI 通道、订阅权益和用量账单。</p></div><AppButton variant="ghost" :disabled="loading || !!pending" @click="loadOverview"><AppIcon name="refresh" :size="15" />刷新</AppButton></header>
    <div class="settings-layout"><aside class="card settings-layout__nav"><SettingsNav /></aside><div class="settings-layout__content">
      <div v-if="loading" class="grid-stats" aria-busy="true"><div v-for="item in 3" :key="item" class="stat"><div class="bone" /><div class="bone bone--short" /></div></div>
      <div v-else class="grid-stats"><section class="stat wallet-stat"><span class="stat__icon"><AppIcon name="briefcase" /></span><span class="stat__label">钱包可用余额</span><strong class="stat__value">{{ money(wallet?.availableBalance, wallet?.currency) }}</strong><span class="stat__sub">冻结 {{ money(wallet?.heldBalance, wallet?.currency) }}</span></section><section class="stat"><span class="stat__icon stat__icon--purple"><AppIcon name="sparkles" /></span><span class="stat__label">个人通道订阅</span><strong class="stat__value stat__value--text">{{ subscription ? label(subscription.status) : '未开通' }}</strong><span class="stat__sub">{{ subscription ? `${money(subscription.price, subscription.currency)} / 周期` : '开通后可调用个人通道' }}</span></section><section class="stat"><span class="stat__icon stat__icon--green"><AppIcon name="check-circle" /></span><span class="stat__label">可用通道</span><strong class="stat__value">{{ systemChannels.filter((item) => item.status === 'ACTIVE').length + personalChannels.filter((item) => item.status === 'ACTIVE').length }}</strong><span class="stat__sub">系统 {{ systemChannels.length }} · 个人 {{ personalChannels.length }}</span></section></div>

      <section class="card subscription-card"><header class="card__head"><div><h2 class="card__title">个人通道订阅</h2><p class="card__sub">个人供应商费用由你自行承担；平台仅收取订阅费。</p></div><AppTag :tone="tone(subscription?.status)">{{ subscription ? label(subscription.status) : '未开通' }}</AppTag></header><div class="card__body subscription-card__body"><div v-if="subscription" class="subscription-meta"><span><small>当前周期</small>{{ formatWhen(subscription.currentPeriodStart) }} — {{ formatWhen(subscription.currentPeriodEnd) }}</span><span><small>自动续费</small>{{ subscription.autoRenew ? '已开启' : '已关闭' }}</span></div><p v-else class="fine">月付价格由服务端返回。开通与续费从钱包余额扣款；当前周期中途不退款。</p><AppButton v-if="!subscription || ['EXPIRED','PAST_DUE'].includes(subscription.status)" :pending="pending === 'subscribe'" :disabled="!wallet" @click="subscribe">开通个人通道</AppButton><AppButton v-else-if="subscription.autoRenew" variant="ghost" :pending="pending === 'cancel-renewal'" @click="cancelRenewal">关闭自动续费</AppButton><span v-else class="fine">续费已关闭，到期后个人通道将不可调用。</span></div></section>

      <section class="card"><header class="card__head"><div><h2 class="card__title">通道池</h2><p class="card__sub">系统通道只读；个人密钥写入后仅展示安全掩码。</p></div><AppButton :disabled="!!pending" @click="openCreate"><AppIcon name="plus" :size="15" />添加个人通道</AppButton></header><div class="card__body channel-sections"><section><div class="section-title"><h3>系统通道</h3><span>{{ systemChannels.length }} 个</span></div><AppEmpty v-if="!loading && !systemChannels.length" text="暂无可用系统通道" hint="平台启用通道后会在此显示。" /><div v-else class="channel-grid"><article v-for="channel in systemChannels" :key="channel.id" class="channel"><div class="channel__top"><span class="channel__mark"><AppIcon name="sparkles" /></span><div><strong>{{ channel.name }}</strong><small>{{ channel.providerCode }} · {{ channel.protocol }}</small></div><AppTag :tone="tone(channel.status)">{{ label(channel.status) }}</AppTag></div><dl><div><dt>Base URL</dt><dd :title="channel.baseUrl">{{ channel.baseUrl }}</dd></div><div><dt>健康状态</dt><dd><AppTag :tone="tone(channel.healthStatus)">{{ label(channel.healthStatus) }}</AppTag></dd></div></dl></article></div></section><section><div class="section-title"><h3>个人通道</h3><span>{{ personalChannels.length }} 个</span></div><AppBanner v-if="!personalEnabled" tone="warn">个人通道可配置和检测，但只有订阅处于有效期内时才能调用。</AppBanner><AppEmpty v-if="!loading && !personalChannels.length" text="还没有个人通道" hint="添加后可单独启停、检测和归档。"><AppButton @click="openCreate">添加第一个通道</AppButton></AppEmpty><div v-else class="channel-grid"><article v-for="channel in personalChannels" :key="channel.id" class="channel"><div class="channel__top"><span class="channel__mark channel__mark--personal"><AppIcon name="settings" /></span><div><strong>{{ channel.name }}</strong><small>{{ channel.providerCode }} · {{ channel.protocol }}</small></div><AppTag :tone="tone(channel.status)">{{ label(channel.status) }}</AppTag></div><dl><div><dt>密钥</dt><dd class="secret">{{ channel.apiKeyMasked || '••••' }}</dd></div><div><dt>健康状态</dt><dd><AppTag :tone="tone(channel.healthStatus)">{{ label(channel.healthStatus) }}</AppTag></dd></div></dl><div class="channel__actions"><AppButton variant="ghost" :disabled="!!pending" @click="openEdit(channel)"><AppIcon name="edit" :size="14" />编辑</AppButton><AppButton variant="ghost" :pending="pending === `toggle-${channel.id}`" :disabled="!!pending || channel.status === 'DRAFT'" @click="toggleChannel(channel)">{{ channel.status === 'ACTIVE' ? '停用' : '启用' }}</AppButton><AppButton variant="ghost" :pending="pending === `test-${channel.id}`" :disabled="!!pending" @click="testChannel(channel)"><AppIcon name="refresh" :size="14" />检测</AppButton><AppButton variant="text" :disabled="!!pending" @click="confirmArchive = channel"><AppIcon name="archive" :size="14" />归档</AppButton></div></article></div></section></div></section>

      <section class="card records"><header class="card__head"><div><h2 class="card__title">资金与使用记录</h2><p class="card__sub">账单金额以服务端结算结果为准，前端不自行计算。</p></div></header><div class="record-tabs" role="tablist"><button v-for="tab in ([['ledger','钱包流水'],['usage','AI 用量'],['billing','账单']] as const)" :key="tab[0]" :class="{ active: activeTab === tab[0] }" role="tab" :aria-selected="activeTab === tab[0]" @click="loadTab(tab[0])">{{ tab[1] }}</button></div><div class="record-table" :aria-busy="tabLoading"><div v-if="tabLoading" class="records-loading"><div class="bone" /><div class="bone" /></div><table v-else-if="activeTab === 'ledger' && ledger?.items.length" class="tbl"><thead><tr><th>类型</th><th>金额</th><th>余额</th><th>关联</th><th>时间</th></tr></thead><tbody><tr v-for="item in ledger.items" :key="item.id"><td>{{ label(item.type) }}</td><td :class="Number(item.amount) >= 0 ? 'amount--in' : 'amount--out'">{{ money(item.amount) }}</td><td>{{ money(item.balanceAfter) }}</td><td>{{ item.referenceType || '—' }}</td><td>{{ formatWhen(item.createdAt) }}</td></tr></tbody></table><table v-else-if="activeTab === 'usage' && usage?.items.length" class="tbl"><thead><tr><th>状态</th><th>模型</th><th>输入 / 缓存 / 输出</th><th>成功数</th><th>开始时间</th></tr></thead><tbody><tr v-for="item in usage.items" :key="item.id"><td><AppTag :tone="tone(item.status)">{{ label(item.status) }}</AppTag></td><td><code>{{ item.modelId }}</code></td><td>{{ item.inputTokens ?? 0 }} / {{ item.cachedInputTokens ?? 0 }} / {{ item.outputTokens ?? 0 }}</td><td>{{ item.successfulUnits ?? '—' }}</td><td>{{ formatWhen(item.startedAt) }}</td></tr></tbody></table><table v-else-if="activeTab === 'billing' && billing?.items.length" class="tbl"><thead><tr><th>状态</th><th>预估</th><th>实结</th><th>已退款</th><th>结算时间</th></tr></thead><tbody><tr v-for="item in billing.items" :key="item.id"><td><AppTag :tone="tone(item.status)">{{ label(item.status) }}</AppTag></td><td>{{ money(item.estimatedAmount, item.currency) }}</td><td>{{ money(item.actualAmount, item.currency) }}</td><td>{{ money(item.refundedAmount, item.currency) }}</td><td>{{ formatWhen(item.settledAt) }}</td></tr></tbody></table><AppEmpty v-else text="暂无记录" hint="实际调用或资金变动后会出现在这里。" /></div><footer v-if="pageFor(activeTab)" class="pager"><span>第 {{ pageFor(activeTab)!.page + 1 }} / {{ Math.max(pageFor(activeTab)!.totalPages, 1) }} 页，共 {{ pageFor(activeTab)!.totalElements }} 条</span><div><AppButton variant="ghost" :disabled="tabLoading || pageFor(activeTab)!.page <= 0" @click="turnPage(-1)">上一页</AppButton><AppButton variant="ghost" :disabled="tabLoading || pageFor(activeTab)!.page + 1 >= pageFor(activeTab)!.totalPages" @click="turnPage(1)">下一页</AppButton></div></footer></section>
    </div></div>
    <ChannelEditor :open="editorOpen" :channel="editing" :pending="pending === 'editor'" :error="editorError" @close="editorOpen = false" @submit="saveChannel" />
    <div v-if="confirmArchive" class="confirm-mask" @click.self="confirmArchive = null"><section class="confirm card" role="alertdialog" aria-modal="true"><span class="confirm__icon"><AppIcon name="archive" /></span><h2>归档「{{ confirmArchive.name }}」？</h2><p>归档后通道不可恢复，已保存的密钥将失效；历史用量和账单保留。</p><div><AppButton variant="ghost" :disabled="!!pending" @click="confirmArchive = null">取消</AppButton><AppButton variant="danger" :pending="pending === `archive-${confirmArchive.id}`" @click="archiveConfirmed">确认归档</AppButton></div></section></div>
  </div>
</template>

<style scoped>
.ai-page{max-width:1500px;margin:0 auto}.stat__value--text{font-size:22px}.subscription-card{background:linear-gradient(135deg,var(--surface) 0%,var(--color-primary-soft) 140%)}.subscription-card__body{display:flex;align-items:center;justify-content:space-between;gap:20px}.subscription-meta{display:flex;gap:36px}.subscription-meta span{display:grid;gap:4px;font-size:13px}.subscription-meta small,.section-title span{color:var(--text-tertiary);font-size:12px}.channel-sections{display:grid;gap:26px}.section-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.section-title h3{font-size:14px}.channel-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.channel{border:1px solid var(--border);border-radius:12px;padding:16px;display:grid;gap:15px;background:var(--surface);transition:border-color .16s,box-shadow .16s}.channel:hover{border-color:var(--border-strong);box-shadow:var(--shadow-s)}.channel__top{display:flex;align-items:center;gap:11px;min-width:0}.channel__top>div{display:grid;gap:3px;flex:1;min-width:0}.channel__top strong,.channel__top small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.channel__top small{font-size:12px;color:var(--text-tertiary)}.channel__mark{width:36px;height:36px;border-radius:10px;background:var(--color-primary-soft);color:var(--color-primary);display:grid;place-items:center;flex:none}.channel__mark--personal{background:var(--purple-soft);color:var(--purple)}.channel dl{display:grid;grid-template-columns:1fr 1fr;gap:12px}.channel dl div{min-width:0}.channel dt{color:var(--text-tertiary);font-size:12px;margin-bottom:3px}.channel dd{font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.secret{font-family:ui-monospace,SFMono-Regular,monospace;letter-spacing:.04em}.channel__actions{display:flex;flex-wrap:wrap;gap:6px;padding-top:12px;border-top:1px solid var(--border)}.record-tabs{display:flex;gap:4px;padding:8px 20px 0;border-bottom:1px solid var(--border)}.record-tabs button{border:0;background:transparent;color:var(--text-secondary);padding:10px 14px;border-bottom:2px solid transparent}.record-tabs button.active{color:var(--color-primary);border-color:var(--color-primary);font-weight:600}.record-table{overflow-x:auto;min-height:170px;padding:4px 8px 8px}.records-loading{display:grid;gap:12px;padding:20px}.pager{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 20px;border-top:1px solid var(--border);font-size:12px;color:var(--text-tertiary)}.pager>div{display:flex;gap:8px}.amount--in{color:var(--color-success)}.amount--out{color:var(--color-danger)}.confirm-mask{position:fixed;inset:0;z-index:90;background:color-mix(in srgb, var(--text-primary) 45%, transparent);display:grid;place-items:center;padding:20px}.confirm{width:min(440px,100%);padding:28px;text-align:center;display:grid;gap:14px}.confirm__icon{margin:auto;width:48px;height:48px;border-radius:50%;display:grid;place-items:center;background:var(--danger-soft);color:var(--color-danger)}.confirm h2{font-size:17px}.confirm p{color:var(--text-secondary);font-size:13px}.confirm>div{display:flex;justify-content:center;gap:10px}@media(max-width:1120px){.channel-grid{grid-template-columns:1fr}}@media(max-width:700px){.ai-page{padding:18px 14px 40px}.subscription-card__body,.pager{align-items:stretch;flex-direction:column}.subscription-meta{display:grid;gap:12px}.channel dl{grid-template-columns:1fr}.channel__actions .btn{flex:1}.record-tabs{overflow-x:auto}.record-table{padding:0}.tbl th,.tbl td{white-space:nowrap}.pager>div .btn{flex:1}.pager>div{display:flex}}
.record-tabs { position: relative; isolation: isolate; display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 0; }
.record-tabs::after { position: absolute; left: 20px; bottom: -1px; width: calc((100% - 40px) / 3); height: 3px; border-radius: 3px 3px 0 0; background: var(--color-primary); box-shadow: 0 -2px 7px color-mix(in srgb, var(--color-primary) 18%, transparent); content: ''; pointer-events: none; transform: translateX(0); transition: transform var(--motion-slow) var(--motion-ease); }
.record-tabs:has(button:nth-child(2).active)::after { transform: translateX(100%); }
.record-tabs:has(button:nth-child(3).active)::after { transform: translateX(200%); }
.record-tabs button { transition: color var(--motion-base) ease, background-color var(--motion-fast) ease; }
.record-tabs button:hover { color: var(--color-primary); background: var(--surface-2); }
.record-tabs button.active { border-color: transparent; background: var(--surface-1); }
.record-table > table, .record-table > .records-loading, .record-table > .empty { animation: record-content-in var(--motion-base) var(--motion-ease-out) both; }
@keyframes record-content-in { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
@media(prefers-reduced-motion:reduce){.record-tabs::after,.record-tabs button{transition:none}.record-table>table,.record-table>.records-loading,.record-table>.empty{animation:none}}
</style>
