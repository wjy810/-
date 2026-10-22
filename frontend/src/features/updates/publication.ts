import { reactive } from 'vue'
import { api } from '@/shared/api/client'

export type PublicationChoice = { expectedVersion: number; sendNotification: boolean; notificationConfirmed: boolean }
type Preview = { versionNo: number; versionLabel: string; title: string; audience: string; recipientCount: number; sendNotification: boolean; showWhatsNew: boolean }
export const publicationState = reactive<{ current: { preview: Preview; scheduledAt?: string; resolve: (choice: PublicationChoice | null) => void } | null }>({ current: null })

export async function confirmPublication(id: string, scheduledAt?: string): Promise<PublicationChoice | null> {
  const preview = await api<Preview>(`/api/v1/admin/changelog/${encodeURIComponent(id)}/publication-preview`)
  publicationState.current?.resolve(null)
  return new Promise(resolve => { publicationState.current = { preview, scheduledAt, resolve } })
}

export function settlePublication(notify: boolean | null): void {
  const current = publicationState.current
  publicationState.current = null
  current?.resolve(notify === null ? null : { expectedVersion: current.preview.versionNo, sendNotification: notify, notificationConfirmed: notify })
}
