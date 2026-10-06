export type AccountView = {
  id: string
  email?: string | null
  phoneMasked?: string | null
  displayIdentifier: string
  primaryChannel?: VerificationChannel | null
  status: string
  role: string
  createdAt?: string
}

export type DevMailbox = {
  to: string
  purpose: string
  code: string
}

export type SessionView = {
  id: string
  current: boolean
  createdAt: string
  lastSeenAt: string
  expiresAt: string
}

export type VerificationChannel = 'EMAIL' | 'SMS'
export type VerificationPurpose = 'REGISTER' | 'LOGIN_RECOVERY' | 'CHANGE_CONTACT'

export type VerificationCapabilities = {
  emailEnabled: boolean
  smsEnabled: boolean
  codeLength: number
  codeTtlMinutes: number
  resendCooldownSeconds: number
  registrationEnabled: boolean
  termsVersion: string
  privacyVersion: string
}

export type VerificationChallenge = {
  challengeId: string
  channel: VerificationChannel
  destinationMasked: string
  expiresAt: string
  resendAfterSeconds: number
}

export type VerificationConfirmation = {
  verificationToken: string
  channel: VerificationChannel
  purpose: VerificationPurpose
  destinationMasked: string
  verificationTokenExpiresAt: string
}
