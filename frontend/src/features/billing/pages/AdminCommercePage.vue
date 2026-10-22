<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ExternalLink, Settings2, Search } from 'lucide-vue-next'
import UiCard from '@/shared/ui/UiCard.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
import UiTabs from '@/shared/ui/UiTabs.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import { errorMessage } from '@/shared/api/types'
import { toast } from '@/shared/ui/toast'
import * as api from '../services/commerceApi'
const prices=ref<api.Price[]>([])
const shop=ref<api.Shop>({enabled:false,url:'',name:'链动小铺',description:'前往外部小铺购买余额兑换码，付款后返回本站钱包兑换充值。',version:0})
const error=ref(''),busy=ref(false),loading=ref(false),loaded=ref(false),tab=ref('subscriptions'),search=ref('')
const editing=ref<api.Price|null>(null),editError=ref('')
const tabs=[{value:'subscriptions',label:'订阅价格'},{value:'functions',label:'AI 功能价格'}]
const isSubscription=(p:api.Price)=>p.code.startsWith('PRO_')||p.code==='PERSONAL_CHANNEL'
const visible=computed(()=>prices.value.filter(p=>(tab.value==='subscriptions'?isSubscription(p):!isSubscription(p))&&p.name.includes(search.value.trim())))
async function load(){loading.value=true;try{const c=await api.catalog();prices.value=c.prices;shop.value=c.shop;error.value='';loaded.value=true}catch(e){error.value=errorMessage(e,'加载失败')}finally{loading.value=false}}
function edit(p:api.Price){editing.value={...p};editError.value=''}
async function save(){
  if(!editing.value||busy.value)return
  busy.value=true
  try{const p=editing.value;const updated=await api.savePrice({...p,amount:p.amount==null||String(p.amount)===''?null:Number(p.amount),days:Number(p.days)});prices.value=prices.value.map(item=>item.code===updated.code?updated:item);editing.value=null;toast.success('价格已保存')}
  catch(e){editError.value=errorMessage(e,'保存失败')}finally{busy.value=false}
}
async function saveLink(){if(busy.value)return;busy.value=true;try{shop.value=await api.saveShop(shop.value);toast.success('购码入口已保存');error.value=''}catch(e){error.value=errorMessage(e,'保存失败')}finally{busy.value=false}}
onMounted(load)
</script>
<template>
  <div class="page-container page-container--wide commerce-admin">
    <UiPageHeader title="商业设置" description="管理购码入口、订阅与 AI 功能价格。已确认任务与已购买订阅保留原价格。">
      <template #actions><UiButton variant="secondary" :pending="loading" @click="load">刷新设置</UiButton></template>
    </UiPageHeader>
    <p v-if="error" role="alert">{{error}}</p>
    <p v-if="loading && !loaded" role="status">正在加载商业设置…</p>
    <template v-if="loaded">
      <UiCard>
        <div class="section-heading"><div><h2>购码入口</h2><p>用户前往外部小铺购买，返回本站兑换人民币余额。</p></div><ExternalLink :size="22" aria-hidden="true"/></div>
        <form class="shop-form" @submit.prevent="saveLink">
          <UiField id="shop-name" label="展示名称"><UiInput id="shop-name" v-model="shop.name" maxlength="80" required/></UiField>
          <UiField id="shop-url" label="小铺购买链接"><UiInput id="shop-url" v-model="shop.url" type="url" placeholder="https://" /></UiField>
          <UiField id="shop-description" class="shop-form__wide" label="入口说明"><UiTextarea id="shop-description" v-model="shop.description" maxlength="500" required/></UiField>
          <label class="commerce-toggle"><input v-model="shop.enabled" type="checkbox"/> 在钱包、会员及余额不足提示中展示</label>
          <UiButton type="submit" :pending="busy">保存购码入口</UiButton>
        </form>
      </UiCard>
      <UiCard class="price-catalog">
        <div class="section-heading"><div><h2>价格目录</h2><p>AI 功能优先使用月度额度；额度不足时，用户确认价格后才扣余额。</p></div><Settings2 :size="22" aria-hidden="true"/></div>
        <UiTabs v-model="tab" :items="tabs" aria-label="价格分类"/>
        <UiInput v-model="search" aria-label="搜索功能或商品" placeholder="搜索功能或商品"/>
        <UiEmptyState v-if="!visible.length" :icon="Search" title="没有匹配的价格项目" description="试试其他关键词。"/>
        <div v-else class="price-list"><article v-for="p in visible" :key="p.code" class="price-row">
          <div><h3>{{p.name}}</h3><p>{{isSubscription(p)?'余额购买，手动续费':'每次成功业务操作计费，内部重试不另收费'}}</p></div>
          <div class="price-row__value"><strong>{{p.amount==null?'价格待配置':'¥'+p.amount}}</strong><span>{{isSubscription(p)?'/ '+p.days+' 天':'/ 次'}}</span><small>{{p.enabled && p.amount!=null?'已开放':'未开放'}}</small></div>
          <UiButton variant="secondary" size="sm" @click="edit(p)">编辑价格</UiButton>
        </article></div>
      </UiCard>
    </template>
    <UiDrawer :open="!!editing" title="编辑价格" description="保存后，新报价使用新价格；已有订单和已确认任务不受影响。" @close="!busy && (editing=null)">
      <form v-if="editing" id="price-editor" class="price-editor" @submit.prevent="save">
        <UiField id="price-name" label="显示名称"><UiInput id="price-name" v-model="editing.name" maxlength="120" required/></UiField>
        <UiField id="price-amount" label="人民币价格" hint="留空表示尚未配置，不允许付费使用。"><UiInput id="price-amount" v-model="editing.amount" type="number" min="0" :step="isSubscription(editing)?'0.01':'0.00000001'" placeholder="未配置"/></UiField>
        <UiField v-if="isSubscription(editing)" id="price-days" label="每期天数"><UiInput id="price-days" v-model="editing.days" type="number" min="1" max="3650" :disabled="editing.code==='PERSONAL_CHANNEL'"/></UiField>
        <label class="commerce-toggle"><input v-model="editing.enabled" type="checkbox"/> 开放购买 / 余额调用</label>
        <p v-if="editError" role="alert">{{editError}}</p>
        <div class="price-editor__actions"><UiButton variant="secondary" :disabled="busy" @click="editing=null">取消</UiButton><UiButton type="submit" :pending="busy">保存价格</UiButton></div>
      </form>
    </UiDrawer>
  </div>
</template>
<style scoped>
.commerce-admin,.price-catalog{display:grid;gap:var(--space-5)}
.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:var(--space-4);margin-bottom:var(--space-4)}
.section-heading p,.price-row p{color:var(--text-secondary);font-size:var(--fs-sm);line-height:1.7;margin-top:var(--space-2)}
.shop-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--space-4);align-items:end}
.shop-form__wide{grid-column:1/-1}.commerce-toggle{display:flex;align-items:center;gap:var(--space-2);font-size:var(--fs-sm);min-height:40px}
.commerce-toggle input{accent-color:var(--color-primary);width:16px;height:16px}
.price-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:var(--space-5);align-items:center;padding:var(--space-5) 0;border-bottom:1px solid var(--border-subtle)}
.price-row:last-child{border-bottom:0}.price-row h3{font-size:var(--fs-body);font-weight:600}
.price-row__value{text-align:right;font-variant-numeric:tabular-nums}.price-row__value span{margin-left:var(--space-1);font-size:var(--fs-sm);color:var(--text-secondary)}
.price-row__value small{display:block;color:var(--text-tertiary);margin-top:var(--space-1)}
.price-editor{display:grid;gap:var(--space-5)}.price-editor__actions{display:flex;justify-content:flex-end;gap:var(--space-3)}
@media(max-width:600px){.shop-form{grid-template-columns:1fr}.price-row{grid-template-columns:1fr auto;gap:var(--space-3)}.price-row>div:first-child{grid-column:1/-1}.price-row__value{text-align:left}}
</style>
