<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Wallet, LockKeyhole, CheckCircle2, ArrowUpRight, Crown, Cpu } from 'lucide-vue-next'
import UiCard from '@/shared/ui/UiCard.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiStatTile from '@/shared/ui/UiStatTile.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import { toast } from '@/shared/ui/toast'
import { confirm } from '@/shared/ui/confirm'
import { formatWhen } from '@/shared/lib/datetime'
import { errorMessage } from '@/shared/api/types'
import * as commerce from '../services/commerceApi'
import BillingHistory from './components/BillingHistory.vue'
const props=defineProps<{personalOnly?:boolean}>()
const data=ref<Awaited<ReturnType<typeof commerce.catalog>>>(),balance=ref<commerce.Wallet>(),sub=ref<commerce.Subscription>()
const code=ref(''),busy=ref(false),loading=ref(false),loaded=ref(false),error=ref(''),reload=ref(0)
const receipt=ref<commerce.RedemptionReceipt|null>(null)
const products=computed(()=>data.value?.prices.filter(p=>props.personalOnly?p.code==='PERSONAL_CHANNEL':p.code.startsWith('PRO_')||p.code==='PERSONAL_CHANNEL')??[])
const requestIds=new Map<string,string>()
const money=(value:number|null|undefined)=>Number(value??0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:8})
const subLabel=computed(()=>sub.value?.status==='ACTIVE'?'订阅生效中':sub.value?.status==='EXPIRED'?'订阅已到期':'尚未订阅')
async function load(){
  loading.value=true
  try{const [c,w,s]=await Promise.all([commerce.catalog(),commerce.wallet(),commerce.subscription()]);data.value=c;balance.value=w;sub.value=s;error.value='';loaded.value=true}
  catch(e){error.value=errorMessage(e,'钱包加载失败')}
  finally{loading.value=false}
}
async function redeem(){
  if(busy.value||!code.value.trim())return
  busy.value=true;receipt.value=null;error.value=''
  try{receipt.value=await commerce.redeem(code.value);code.value='';await load();reload.value++;toast.success(receipt.value.previouslyRedeemed?'兑换记录已确认':'兑换成功')}
  catch(e){error.value=errorMessage(e,'兑换失败')}
  finally{busy.value=false}
}
async function buy(p:commerce.Price){
  if(busy.value)return
  if(!await confirm({title:'购买'+p.name,message:'将从人民币余额扣除 ¥'+money(p.amount)+'，有效期 '+p.days+' 天。已有有效订阅将在原到期日后顺延，不会自动续费。',confirmText:'确认支付'}))return
  busy.value=true
  const key=p.code+':'+p.version,id=requestIds.get(key)??crypto.randomUUID();requestIds.set(key,id)
  try{await commerce.purchase(p,id);requestIds.delete(key);await load();reload.value++;toast.success('购买成功')}
  catch(e){await load();error.value=errorMessage(e,'购买失败')}
  finally{busy.value=false}
}
onMounted(load)
</script>
<template>
  <div class="page-container commerce-page">
    <UiPageHeader :title="personalOnly?'个人通道订阅':'钱包与账单'" :description="personalOnly?'使用自己的 API Key，仅支付通道订阅费，调用不扣平台额度或余额。':'兑换人民币余额，用于订阅与经你确认的 AI 按次消费。'">
      <template #actions><UiButton variant="secondary" :pending="loading" @click="load">刷新</UiButton></template>
    </UiPageHeader>
    <p v-if="error" role="alert" class="wallet-error">{{error}} <UiButton variant="ghost" @click="load">重试</UiButton></p>
    <p v-if="loading && !loaded" role="status">正在加载钱包…</p>
    <template v-if="loaded">
      <UiCard class="wallet-card">
        <div class="wallet-card__top"><div><p class="wallet-caption">人民币账户 · CNY</p><h2>可用余额 ¥{{money(balance?.availableBalance)}}</h2><p>月度额度优先，余额消费由你逐次确认。</p></div><Wallet :size="30" :stroke-width="1.5" aria-hidden="true"/></div>
        <UiStatTile label="冻结余额" :value="'¥'+money(balance?.heldBalance)" hint="任务处理中暂时预占；失败部分自动释放。" :icon="LockKeyhole"/>
        <form class="redeem-form" @submit.prevent="redeem">
          <UiField id="wallet-code" label="兑换码充值"><UiInput id="wallet-code" v-model="code" autocomplete="off" aria-label="充值兑换码" placeholder="JP-XXXX-XXXX-XXXX-XXXX" :disabled="busy"/></UiField>
          <UiButton type="submit" :pending="busy" :disabled="!code.trim()">兑换充值</UiButton>
        </form>
        <div v-if="receipt" class="redeem-receipt" role="status" aria-live="polite"><CheckCircle2 :size="21" aria-hidden="true"/><div>
          <strong v-if="receipt.kind==='BALANCE'">{{receipt.previouslyRedeemed?'该兑换码已到账，未重复充值':'成功到账'}} ¥{{money(receipt.amount)}}</strong>
          <strong v-else>旧会员兑换码已按原权益兑换</strong><p>可用余额 ¥{{money(receipt.balance)}}</p>
        </div></div>
        <div class="shop-entry" v-if="data?.shop.enabled && data.shop.url"><a :href="data.shop.url" target="_blank" rel="noopener noreferrer">{{data.shop.name}}<ArrowUpRight :size="15" aria-hidden="true"/></a><p>{{data.shop.description}}</p></div>
        <p v-else class="wallet-caption">购码入口暂未开放。</p>
      </UiCard>
      <div class="section-intro"><h2>{{personalOnly?'开通或续费':'余额购买订阅'}}</h2><p>手动续费，到期提醒。有效期内续费从原到期日顺延。</p></div>
      <div class="products"><UiCard v-for="p in products" :key="p.code" class="product-card"><component :is="p.code==='PERSONAL_CHANNEL'?Cpu:Crown" :size="24" aria-hidden="true"/>
        <h3>{{p.name}}</h3><p class="product-price">{{p.amount==null?'价格待配置':'¥'+money(p.amount)}}<span> / {{p.days}} 天</span></p>
        <p>{{p.code==='PERSONAL_CHANNEL'?'最多 5 个个人通道，自备 API Key；上游费用由自己的 Key 承担。':'增加月度 AI 额度与专属简历链接数量。'}}</p>
        <UiButton :disabled="!p.enabled||p.amount==null||busy" @click="buy(p)">{{p.enabled?'余额购买 / 续期':'暂未开放'}}</UiButton>
      </UiCard></div>
      <UiCard class="personal-status"><div><h2>个人通道订阅 · {{subLabel}}</h2><p v-if="sub?.currentPeriodEnd">有效期至 {{formatWhen(sub.currentPeriodEnd)}} · 不自动续费</p><p v-else>开通后可保存并使用自己的模型通道。</p></div><UiButton variant="secondary" to="/account/ai-channels">管理个人通道</UiButton></UiCard>
      <BillingHistory :key="reload"/>
    </template>
  </div>
</template>
<style scoped>
.commerce-page{display:grid;gap:var(--space-5)}
.wallet-card{display:grid;gap:var(--space-5)}.wallet-card__top{display:flex;justify-content:space-between;gap:var(--space-4)}
.wallet-card__top h2{font-family:var(--font-sans);font-size:var(--fs-h2);font-variant-numeric:tabular-nums;margin:var(--space-2) 0}
.wallet-card__top p,.section-intro p,.personal-status p{color:var(--text-secondary);font-size:var(--fs-sm);line-height:1.7}
.wallet-caption{font-size:var(--fs-xs);color:var(--text-tertiary)}
.redeem-form{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:end;gap:var(--space-3)}
.redeem-receipt{display:flex;gap:var(--space-3);padding:var(--space-4);border:1px solid var(--border-subtle);border-radius:var(--radius-md);background:var(--surface-2)}
.redeem-receipt p{font-size:var(--fs-sm);margin-top:var(--space-1);color:var(--text-secondary)}
.shop-entry a{display:inline-flex;align-items:center;gap:var(--space-1);color:var(--color-primary-text);text-decoration:underline;text-underline-offset:3px}
.shop-entry p{color:var(--text-secondary);font-size:var(--fs-sm);margin-top:var(--space-2);line-height:1.7}
.products{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:var(--space-4)}
.product-card{display:flex;flex-direction:column;align-items:flex-start;gap:var(--space-3)}
.product-card h3{font-size:var(--fs-body)}.product-card>p{color:var(--text-secondary);font-size:var(--fs-sm);line-height:1.7}
.product-card .product-price{font-size:var(--fs-h3);font-weight:600;color:var(--text-primary)}.product-price span{font-size:var(--fs-xs);font-weight:400;color:var(--text-tertiary)}
.product-card>button{margin-top:auto;width:100%}.personal-status{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:var(--space-4)}
.wallet-error{padding:var(--space-3);background:var(--surface-2);border-radius:var(--radius-md)}
@media(max-width:500px){.redeem-form{grid-template-columns:1fr}.wallet-card__top h2{font-size:var(--fs-h3)}.products{grid-template-columns:1fr}}
</style>
