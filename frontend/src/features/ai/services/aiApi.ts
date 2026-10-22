import { api, newRequestId, type ApiInit } from '@/shared/api/client'
import type { AiChannelPreference, AiSystemCapability, AiSystemChannel, AiSystemChannelInput } from '../types'

export const listSystemChannels = () => api<AiSystemChannel[]>('/api/v1/admin/ai/channels')
export const createSystemChannel = (body: AiSystemChannelInput) => api<AiSystemChannel>('/api/v1/admin/ai/channels', { method: 'POST', body: JSON.stringify(body) })
export const testSystemChannel = (channelId: string, providerModel: string) => api<AiSystemCapability>(`/api/v1/admin/ai/channels/${encodeURIComponent(channelId)}/capability-test`, { method: 'POST', body: JSON.stringify({ providerModel: providerModel.trim() || null }), timeoutMs: 150_000 })
export const activateSystemChannel = (channel: AiSystemChannel, providerModel: string) => api<AiSystemChannel>(`/api/v1/admin/ai/channels/${encodeURIComponent(channel.id)}/activate`, { method: 'POST', body: JSON.stringify({ expectedVersion: channel.versionNo, providerModel }) })
export const disableSystemChannel = (channel: AiSystemChannel) => api<AiSystemChannel>(`/api/v1/admin/ai/channels/${encodeURIComponent(channel.id)}/disable`, { method: 'POST', body: JSON.stringify({ expectedVersion: channel.versionNo }) })
export const updateSystemChannel = (channel: AiSystemChannel, body: AiSystemChannelInput) => api<AiSystemChannel>(`/api/v1/admin/ai/channels/${encodeURIComponent(channel.id)}`, { method: 'PUT', body: JSON.stringify({ ...body, expectedVersion: channel.versionNo }) })
const personalPath = (id: string) => `/api/v1/ai/channels/personal/${encodeURIComponent(id)}`
// Keep only command identities in memory; never put channel credentials in browser storage.
const pendingCommands = new Map<string, string>()
async function personalCommand<T>(path: string, body: Record<string, unknown>, method = 'POST', timeoutMs?: number): Promise<T> {
  const bytes = new TextEncoder().encode(JSON.stringify({ path, method, body }))
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  const fingerprint = Array.from(new Uint8Array(digest), n => n.toString(16).padStart(2, '0')).join('')
  const key = pendingCommands.get(fingerprint) ?? newRequestId()
  pendingCommands.set(fingerprint, key)
  try {
    const result = await api<T>(path, { method, body: JSON.stringify({ ...body, idempotencyKey: key }), timeoutMs } as ApiInit)
    pendingCommands.delete(fingerprint)
    return result
  } catch (error) {
    // A transport failure or incomplete body may hide a committed command; retain its key.
    throw error
  }
}
export const listPersonalChannels = async () => (await api<{ items: AiSystemChannel[] }>('/api/v1/ai/channels?scope=PERSONAL&size=100')).items
export const createPersonalChannel = (body: AiSystemChannelInput) => personalCommand<AiSystemChannel>('/api/v1/ai/channels/personal', { ...body })
export const updatePersonalChannel = (channel: AiSystemChannel, body: AiSystemChannelInput) => personalCommand<AiSystemChannel>(personalPath(channel.id), { ...body, versionNo: channel.versionNo }, 'PUT')
export const testPersonalChannel = (id: string, providerModel: string) => personalCommand<AiSystemCapability>(`${personalPath(id)}/test`, { providerModel: providerModel.trim() || null }, 'POST', 150_000)
export const activatePersonalChannel = (channel: AiSystemChannel, providerModel: string) => personalCommand<AiSystemChannel>(`${personalPath(channel.id)}/activate`, { versionNo: channel.versionNo, providerModel })
export const disablePersonalChannel = (channel: AiSystemChannel) => personalCommand<AiSystemChannel>(`${personalPath(channel.id)}/disable`, { versionNo: channel.versionNo })
export const archivePersonalChannel = (channel: AiSystemChannel) => personalCommand<AiSystemChannel>(`${personalPath(channel.id)}/archive`, { versionNo: channel.versionNo })
export const getChannelPreference = () => api<AiChannelPreference>('/api/v1/ai/channels/preference')
export const saveChannelPreference = (preference: AiChannelPreference, personalChannelId: string | null, confirmPlatform: boolean) => personalCommand<AiChannelPreference>('/api/v1/ai/channels/preference', { personalChannelId, confirmPlatform, versionNo: preference.versionNo }, 'PUT')
