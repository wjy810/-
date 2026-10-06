import { api } from '@/shared/api/client'
import type {
  AccountView,
  DevMailbox,
  SessionView,
  VerificationCapabilities,
  VerificationChallenge,
  VerificationChannel,
  VerificationConfirmation,
  VerificationPurpose,
} from '../types'

type RegisterAccountInput = {
  channel: VerificationChannel
  destination: string
  verificationToken: string
  password: string
  acceptedTerms: boolean
  acceptedPrivacy: boolean
}

export function registerAccount(input: RegisterAccountInput): Promise<AccountView>
/** @deprecated Unreachable compatibility page only; production rejects unverified registration. */
export function registerAccount(email: string, password: string): Promise<AccountView>
export function registerAccount(input: RegisterAccountInput | string, legacyPassword?: string): Promise<AccountView> {
  return api<AccountView>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(typeof input === 'string' ? { email: input, password: legacyPassword } : input),
  })
}

export function loginAccount(loginIdentifier: string, password: string, rememberMe = true): Promise<AccountView> {
  return api<AccountView>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ identifier: loginIdentifier, password, rememberMe }),
  })
}

export function logoutAccount(): Promise<null> {
  return api<null>('/api/v1/auth/logout', { method: 'POST' })
}

export function changePassword(currentPassword: string, newPassword: string): Promise<null> {
  return api<null>('/api/v1/auth/password/change', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

type ConfirmPasswordResetInput = {
  channel: VerificationChannel
  destination: string
  verificationToken: string
  newPassword: string
}

export function confirmPasswordReset(input: ConfirmPasswordResetInput): Promise<null>
/** @deprecated Unreachable compatibility page only; production rejects direct code reset. */
export function confirmPasswordReset(email: string, code: string, newPassword: string): Promise<null>
export function confirmPasswordReset(
  input: ConfirmPasswordResetInput | string,
  legacyCode?: string,
  legacyPassword?: string,
): Promise<null> {
  return api<null>('/api/v1/auth/password/reset/confirm', {
    method: 'POST',
    body: JSON.stringify(typeof input === 'string'
      ? { email: input, code: legacyCode, newPassword: legacyPassword }
      : input),
  })
}

/** @deprecated Unreachable compatibility page only; production rejects this endpoint. */
export function requestPasswordReset(email: string): Promise<null> {
  return api<null>('/api/v1/auth/password/reset/request', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function fetchMe(): Promise<AccountView> {
  return api<AccountView>('/api/v1/me')
}

export function fetchSessions(): Promise<SessionView[]> {
  return api<SessionView[]>('/api/v1/auth/sessions')
}

export function revokeSession(id: string): Promise<null> {
  return api<null>(`/api/v1/auth/sessions/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function revokeOtherSessions(): Promise<{ revoked: number }> {
  return api<{ revoked: number }>('/api/v1/auth/sessions/revoke-others', { method: 'POST' })
}

export function fetchDevMailbox(email: string): Promise<DevMailbox> {
  return api<DevMailbox>(`/internal/dev/mailbox/${encodeURIComponent(email)}`)
}

export function fetchVerificationCapabilities(): Promise<VerificationCapabilities> {
  return api<VerificationCapabilities>('/api/v1/auth/verifications/capabilities')
}

export function requestContactVerification(
  channel: VerificationChannel,
  destination: string,
  purpose: VerificationPurpose,
): Promise<VerificationChallenge> {
  return api<VerificationChallenge>('/api/v1/auth/verifications/request', {
    method: 'POST',
    body: JSON.stringify({ channel, destination, purpose }),
  })
}

export function confirmContactVerification(
  challengeId: string,
  destination: string,
  code: string,
): Promise<VerificationConfirmation> {
  return api<VerificationConfirmation>('/api/v1/auth/verifications/confirm', {
    method: 'POST',
    body: JSON.stringify({ challengeId, destination, code }),
  })
}
