import { api } from '@/shared/api/client'
import type { AiBilling, AiChannel, AiSystemCapability, AiSystemChannel, AiSystemChannelInput, AiUsage, ChannelInput, ChannelTestResult, PageResponse, PersonalSubscription, Wallet, WalletLedger } from '../types'

const ROOT = '/api/v1/ai'
const idempotencyKey = () => crypto.randomUUID()

function page(path: string, pageNumber: number, size = 10): string {
  return `${ROOT}${path}${path.includes('?') ? '&' : '?'}page=${pageNumber}&size=${size}`
}

export const fetchChannels = (scope: 'SYSTEM' | 'PERSONAL', pageNumber = 0) => api<PageResponse<AiChannel>>(page(`/channels?scope=${scope}`, pageNumber))
export const createPersonalChannel = (body: ChannelInput) => api<AiChannel>(`${ROOT}/channels/personal`, { method: 'POST', body: JSON.stringify({ name: body.name, providerCode: body.providerCode, baseUrl: body.baseUrl, apiKey: body.apiKey, protocol: body.protocol, idempotencyKey: idempotencyKey() }) })
export const updatePersonalChannel = (channel: AiChannel, body: ChannelInput) => api<AiChannel>(`${ROOT}/channels/personal/${encodeURIComponent(channel.id)}`, { method: 'PUT', body: JSON.stringify({ name: body.name, baseUrl: body.baseUrl, ...(body.apiKey ? { apiKey: body.apiKey } : {}), status: body.status, versionNo: channel.versionNo, idempotencyKey: idempotencyKey() }) })
export const testPersonalChannel = (id: string) => api<ChannelTestResult>(`${ROOT}/channels/personal/${encodeURIComponent(id)}/test`, { method: 'POST', body: JSON.stringify({ idempotencyKey: idempotencyKey() }) })
export const archivePersonalChannel = (channel: AiChannel) => api<AiChannel>(`${ROOT}/channels/personal/${encodeURIComponent(channel.id)}/archive`, { method: 'POST', body: JSON.stringify({ versionNo: channel.versionNo, idempotencyKey: idempotencyKey() }) })
export const fetchWallet = () => api<Wallet>(`${ROOT}/wallet`)
export const fetchLedger = (pageNumber = 0) => api<PageResponse<WalletLedger>>(page('/wallet/ledger', pageNumber))
export const fetchUsage = (pageNumber = 0) => api<PageResponse<AiUsage>>(page('/usage', pageNumber))
export const fetchBilling = (pageNumber = 0) => api<PageResponse<AiBilling>>(page('/billing', pageNumber))
export const fetchSubscription = () => api<PersonalSubscription>(`${ROOT}/subscriptions/personal-channel`)
export const subscribePersonal = (walletVersion: number) => api<PersonalSubscription>(`${ROOT}/subscriptions/personal-channel`, { method: 'POST', body: JSON.stringify({ autoRenew: true, versionNo: walletVersion, idempotencyKey: idempotencyKey() }) })
export const cancelSubscriptionRenewal = (subscription: PersonalSubscription) => api<PersonalSubscription>(`${ROOT}/subscriptions/personal-channel/cancel-renewal`, { method: 'POST', body: JSON.stringify({ versionNo: subscription.versionNo, idempotencyKey: idempotencyKey() }) })
export const listSystemChannels = () => api<AiSystemChannel[]>('/api/v1/admin/ai/channels')
export const createSystemChannel = (body: AiSystemChannelInput) => api<AiSystemChannel>('/api/v1/admin/ai/channels', { method: 'POST', body: JSON.stringify(body) })
export const testSystemChannel = (channelId: string, providerModel: string, platformModelCode: string) => api<AiSystemCapability>(`/api/v1/admin/ai/channels/${encodeURIComponent(channelId)}/capability-test`, { method: 'POST', body: JSON.stringify({ providerModel: providerModel || null, platformModelCode }) })
export const activateSystemChannel = (channel: AiSystemChannel) => api<AiSystemChannel>(`/api/v1/admin/ai/channels/${encodeURIComponent(channel.id)}/activate`, { method: 'POST', body: JSON.stringify({ expectedVersion: channel.versionNo }) })
export const disableSystemChannel = (channel: AiSystemChannel) => api<AiSystemChannel>(`/api/v1/admin/ai/channels/${encodeURIComponent(channel.id)}/disable`, { method: 'POST', body: JSON.stringify({ expectedVersion: channel.versionNo }) })
