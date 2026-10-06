import { api } from './client'

export type FeatureKey = 'aiWorkbench' | 'jobMatch' | 'careerPlanning' | 'aiAssistant'

export type SiteOperator = {
  name: string
  icpRecord: string
  publicSecurityRecord: string
  contactEmail: string
  contactPhone: string
  address: string
  feedbackUrl: string
}

export type SiteCapabilities = {
  features: Record<FeatureKey, boolean>
  registrationOpen: boolean
  policies: { published: boolean; termsVersion: string; privacyVersion: string }
  operator: SiteOperator
}

export type PolicyBlock = { type: 'paragraph' | 'item'; text: string }
export type PolicyDocument = {
  kind: 'terms' | 'privacy'
  title: string
  version: string
  /** False: the built-in reference text; the operator has not published an official one. */
  published: boolean
  sections: Array<{ heading: string | null; blocks: PolicyBlock[] }>
}

export const fetchSiteCapabilities = () => api<SiteCapabilities>('/api/v1/capabilities')
export const fetchPolicyDocument = (kind: 'terms' | 'privacy') => api<PolicyDocument>(`/api/v1/policies/${kind}`)
