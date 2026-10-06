import { api } from '@/shared/api/client'
import type { AiSystemCapability, AiSystemChannel, AiSystemChannelInput } from '../types'

export const listSystemChannels = () => api<AiSystemChannel[]>('/api/v1/admin/ai/channels')
export const createSystemChannel = (body: AiSystemChannelInput) => api<AiSystemChannel>('/api/v1/admin/ai/channels', { method: 'POST', body: JSON.stringify(body) })
export const testSystemChannel = (channelId: string, providerModel: string, platformModelCode: string) => api<AiSystemCapability>(`/api/v1/admin/ai/channels/${encodeURIComponent(channelId)}/capability-test`, { method: 'POST', body: JSON.stringify({ providerModel: providerModel || null, platformModelCode }) })
export const activateSystemChannel = (channel: AiSystemChannel) => api<AiSystemChannel>(`/api/v1/admin/ai/channels/${encodeURIComponent(channel.id)}/activate`, { method: 'POST', body: JSON.stringify({ expectedVersion: channel.versionNo }) })
export const disableSystemChannel = (channel: AiSystemChannel) => api<AiSystemChannel>(`/api/v1/admin/ai/channels/${encodeURIComponent(channel.id)}/disable`, { method: 'POST', body: JSON.stringify({ expectedVersion: channel.versionNo }) })
