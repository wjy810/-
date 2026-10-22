import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
const api=vi.hoisted(()=>({catalog:vi.fn(),wallet:vi.fn(),subscription:vi.fn(),purchase:vi.fn(),redeem:vi.fn()}))
vi.mock('../services/commerceApi',()=>api)
const redeem=api.redeem
vi.mock('@/shared/ui/confirm',()=>({confirm:vi.fn().mockResolvedValue(true)}))
vi.mock('@/shared/ui/toast',()=>({toast:{success:vi.fn(),error:vi.fn()}}))
vi.mock('./components/BillingHistory.vue',()=>({default:{template:'<div>交易记录</div>'}}))
vi.mock('vue-router',()=>({RouterLink:{props:['to'],template:'<a :href="to"><slot/></a>'}}))
import WalletPage from './WalletPage.vue'
const p={code:'PRO_MONTH',name:'PRO 月卡',amount:19,currency:'CNY',days:30,enabled:true,version:2}
beforeEach(()=>{vi.clearAllMocks();api.catalog.mockResolvedValue({prices:[p,{...p,code:'PERSONAL_CHANNEL',name:'个人通道',enabled:false,amount:null}],shop:{enabled:false,url:'',name:'链动小铺',description:'外部购买后返回兑换',version:0}});api.wallet.mockResolvedValue({currency:'CNY',availableBalance:0,heldBalance:0});api.subscription.mockResolvedValue({status:'INACTIVE',currentPeriodEnd:null,autoRenew:false});api.purchase.mockResolvedValue({});redeem.mockResolvedValue({kind:'BALANCE',amount:50,balance:50,previouslyRedeemed:false})})
function render(){return mount(WalletPage,{global:{stubs:{UiPageHeader:true,RouterLink:true}}})}
it('shows zero wallet and disabled unconfigured subscription without a shop link',async()=>{const w=render();await flushPromises();expect(w.text()).toContain('可用余额 ¥0.00');expect(w.text()).toContain('尚未订阅');expect(w.text()).toContain('价格待配置');expect(w.find('a[target="_blank"]').exists()).toBe(false);expect(w.findAll('.products button')[1]?.attributes('disabled')).toBeDefined()})
it('confirms the displayed version and preserves request id on network retry',async()=>{api.purchase.mockRejectedValueOnce(new Error('network'));const w=render();await flushPromises();await w.get('.products button').trigger('click');await flushPromises();await w.get('.products button').trigger('click');await flushPromises();expect(api.purchase).toHaveBeenCalledTimes(2);expect(api.purchase.mock.calls[0]?.[0]).toEqual(p);expect(api.purchase.mock.calls[0]?.[1]).toBe(api.purchase.mock.calls[1]?.[1])})
it('redeems then reloads balances and displays an enabled shop link',async()=>{api.catalog.mockResolvedValue({prices:[p],shop:{enabled:true,url:'https://example.com/store',name:'自定义小铺',description:'外部购买后返回兑换',version:1}});const w=render();await flushPromises();await w.get('input').setValue('JP-AAAA-BBBB-CCCC-DDDD');await w.get('form').trigger('submit');await flushPromises();expect(redeem).toHaveBeenCalledWith('JP-AAAA-BBBB-CCCC-DDDD');expect(api.wallet).toHaveBeenCalledTimes(2);expect(w.get('a[target="_blank"]').attributes('href')).toBe('https://example.com/store');expect(w.get('a[target="_blank"]').text()).toBe('自定义小铺');expect(w.text()).toContain('外部购买后返回兑换')})
