<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Receipt, Search } from 'lucide-vue-next'
import { api } from '@/shared/api/client'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import UiCard from '@/shared/ui/UiCard.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiPagination from '@/shared/ui/UiPagination.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
import { history, type History } from '../../services/commerceApi'
const props = defineProps<{ admin?: boolean }>()
const kind = ref('ledger'), accountId = ref(''), status = ref(''), from = ref(''), to = ref(''), page = ref(0)
const data = ref<History>(), error = ref(''), busy = ref(false), selected = ref<Record<string, unknown> | null>(null)
const attempts = ref<Record<string, unknown>[]>([]), detailError = ref(''), detailBusy = ref(false)
const kinds = computed(() => [
  {value:'ledger',label:'人民币流水'}, {value:'bills',label:'AI 消费明细'}, {value:'orders',label:'订阅订单'},
  ...(props.admin ? [{value:'wallets',label:'钱包账户'}, {value:'subscriptions',label:'个人通道订阅'}, {value:'pro-subscriptions',label:'PRO 会员'}] : []),
])
const labels: Record<string,string> = {
  id:'记录编号',account_id:'账号',ledger_type:'流水类型',amount:'金额',balance_after:'变动后余额',reference_id:'关联编号',
  created_at:'创建时间',currency:'币种',status:'状态',actual_amount:'实际金额',estimated_amount:'冻结金额',refunded_amount:'退还金额',
  product_code:'商品',days:'天数',expires_at:'到期时间',available_balance:'可用余额',held_balance:'冻结余额',
  current_period_end:'订阅到期',price:'价格',updated_at:'更新时间',auto_renew:'自动续费',operation_name:'功能',
  payment_source:'支付来源',model_code:'实际模型',successful_units:'成功次数',input_tokens:'输入 Token',output_tokens:'输出 Token',
  plan:'会员类型',failure_code:'失败原因',channel_id:'通道编号',
}
const words: Record<string,string> = {
  CREDIT:'充值到账',DEBIT:'余额支出',HOLD:'预占冻结',SETTLE:'消费结算',RELEASE:'解除冻结',SETTLED:'已结算',RELEASED:'已释放',
  ACTIVE:'生效中',EXPIRED:'已到期',CANCELLED:'已取消',INACTIVE:'未订阅',QUOTA:'月度额度',WALLET:'人民币余额',PERSONAL:'个人通道',
  SUCCEEDED:'成功',FAILED:'失败',RUNNING:'处理中',PRO_MONTH:'PRO 30 天',PRO_QUARTER:'PRO 90 天',PRO_YEAR:'PRO 365 天',PERSONAL_CHANNEL:'个人通道 30 天',
}
const statuses = computed(() => [{value:'',label:'全部状态'}, ...(
  kind.value === 'ledger' ? ['CREDIT','DEBIT','HOLD','SETTLE','RELEASE'] : kind.value === 'bills' ? ['SETTLED','RELEASED','HELD']
    : kind.value === 'orders' ? ['PRO_MONTH','PRO_QUARTER','PRO_YEAR','PERSONAL_CHANNEL']
      : kind.value === 'wallets' ? ['CNY','USD'] : ['ACTIVE','EXPIRED','INACTIVE','CANCELLED']
).map(value => ({value,label:words[value] ?? value}))])
const columnSets: Record<string,string[]> = {
  ledger:['created_at','ledger_type','amount','balance_after'],
  bills:['created_at','operation_name','payment_source','model_code','successful_units','actual_amount','status'],
  orders:['created_at','product_code','amount','expires_at'],wallets:['account_id','currency','available_balance','held_balance'],
  subscriptions:['account_id','status','current_period_end','price'],'pro-subscriptions':['account_id','plan','status','expires_at'],
}
const columns = computed(() => columnSets[kind.value] ?? [])
function format(key: string, value: unknown, row: Record<string, unknown> = {}): string {
  if (value === null || value === undefined || value === '') return '—'
  if (key.endsWith('_at') || key === 'current_period_end') return formatWhen(String(value))
  if (key === 'auto_renew') return value ? '开启' : '手动续费'
  if (['amount','balance_after','actual_amount','estimated_amount','refunded_amount','price','available_balance','held_balance'].includes(key))
    return (row.currency === 'USD' ? '$' : '¥') + Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:8})
  return words[String(value)] ?? String(value)
}
async function load(reset = false) {
  if (reset) page.value = 0
  busy.value = true
  try {
    const q = new URLSearchParams({kind:kind.value,page:String(page.value)})
    if(accountId.value)q.set('accountId',accountId.value.trim())
    if(status.value)q.set('status',status.value)
    if(from.value)q.set('from',new Date(from.value).toISOString())
    if(to.value)q.set('to',new Date(to.value).toISOString())
    data.value = await history(q,props.admin); error.value = ''
  } catch(e) { error.value = errorMessage(e,'账单查询失败') }
  finally { busy.value = false }
}
async function detail(row: Record<string, unknown>) {
  selected.value=row; attempts.value=[]; detailError.value=''
  if(kind.value!=='bills' || !row.usage_id)return
  detailBusy.value=true
  try { attempts.value=await api('/api/v1/ai/charges/usage/'+encodeURIComponent(String(row.usage_id))+'/attempts') }
  catch(e){ detailError.value=errorMessage(e,'调用记录加载失败') }
  finally { detailBusy.value=false }
}
async function changeKind(value: string|number) {kind.value=String(value);status.value='';await load(true)}
async function changePage(value: number) {page.value=value;await load()}
onMounted(()=>load())
</script>
<template>
  <UiCard class="billing-history">
    <div class="billing-heading"><div><h2>交易与账单记录</h2><p>每次到账、消费与退还，都有迹可查。</p></div><Receipt :size="22" aria-hidden="true"/></div>
    <form class="billing-filters" @submit.prevent="load(true)">
      <UiField id="billing-kind" label="记录类型"><UiSelect id="billing-kind" :model-value="kind" :options="kinds" @update:model-value="changeKind"/></UiField>
      <UiField v-if="admin" id="billing-account" label="账号"><UiInput id="billing-account" v-model="accountId" placeholder="账号 ID，留空查看全部"/></UiField>
      <UiField id="billing-status" label="状态或类型"><UiSelect id="billing-status" v-model="status" :options="statuses"/></UiField>
      <UiField id="billing-from" label="开始时间"><AppDatePicker id="billing-from" v-model="from" mode="datetime" label="开始时间"/></UiField>
      <UiField id="billing-to" label="截止时间（不含）"><AppDatePicker id="billing-to" v-model="to" mode="datetime" label="截止时间（不含）"/></UiField>
      <UiButton type="submit" :icon="Search" :pending="busy">查询</UiButton>
    </form>
    <p v-if="error" role="alert">{{error}} <UiButton variant="ghost" @click="load()">重试</UiButton></p>
    <p v-else-if="busy && !data" role="status">正在加载账单…</p>
    <UiEmptyState v-else-if="!data?.content.length" :icon="Receipt" title="暂无记录" description="完成兑换或使用 AI 后，可在这里查看相应明细。"/>
    <div v-else class="billing-table" :aria-busy="busy"><table><thead><tr><th v-for="col in columns" :key="col">{{labels[col]}}</th><th>详情</th></tr></thead>
      <tbody><tr v-for="row in data.content" :key="String(row.id)"><td v-for="col in columns" :key="col">{{format(col,row[col],row)}}</td><td><UiButton size="sm" variant="ghost" @click="detail(row)">查看</UiButton></td></tr></tbody></table></div>
    <UiPagination v-if="data && data.totalElements>0" :page="page" :page-count="Math.max(1,Math.ceil(data.totalElements/data.size))" :disabled="busy" @update:page="changePage">共 {{data.totalElements}} 条</UiPagination>
    <UiDrawer :open="!!selected" title="账单详情" @close="selected=null">
      <dl v-if="selected" class="billing-detail"><template v-for="(label,key) in labels" :key="key"><template v-if="selected[key]!==undefined"><dt>{{label}}</dt><dd>{{format(key,selected[key],selected)}}</dd></template></template></dl>
      <p v-if="detailBusy" role="status">正在加载调用记录…</p><p v-if="detailError" role="alert">{{detailError}}</p>
      <div v-if="attempts.length" class="billing-attempts"><h3>模型调用记录</h3><p>重试和内部调用包含在本次费用中，不重复计费。</p><UiCard v-for="attempt in attempts" :key="String(attempt.id)"><strong>{{attempt.model_code || '模型未返回'}}</strong><p>{{format('status',attempt.status)}} · {{formatWhen(String(attempt.created_at))}}</p><small>输入 {{attempt.input_tokens}} / 输出 {{attempt.output_tokens}} Token</small></UiCard></div>
    </UiDrawer>
  </UiCard>
</template>
<style scoped>
.billing-history{display:grid;gap:var(--space-5)}
.billing-heading{display:flex;align-items:center;justify-content:space-between;gap:var(--space-4)}
.billing-heading p,.billing-attempts p{color:var(--text-secondary);font-size:var(--fs-sm);margin-top:var(--space-2)}
.billing-filters{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));align-items:end;gap:var(--space-4)}
.billing-table{overflow-x:auto}.billing-table table{border-collapse:collapse;width:100%;font-size:var(--fs-sm)}
.billing-table th,.billing-table td{padding:var(--space-3);border-bottom:1px solid var(--border-subtle);text-align:left;white-space:nowrap}
.billing-table th{font-weight:500;color:var(--text-secondary)}.billing-table td{font-variant-numeric:tabular-nums}
.billing-detail{display:grid;grid-template-columns:minmax(80px,1fr) 2fr;gap:var(--space-3);font-size:var(--fs-sm)}
.billing-detail dt{color:var(--text-secondary)}.billing-detail dd{margin:0;overflow-wrap:anywhere}
.billing-attempts{display:grid;gap:var(--space-3);margin-top:var(--space-6)}
@media(max-width:800px){.billing-filters{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:480px){.billing-filters{grid-template-columns:1fr}}
</style>
