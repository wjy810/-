export type AuthLandingMode = 'login' | 'register' | 'reset'

export type AuthLandingQuery = {
  auth: AuthLandingMode
  next?: string
  reason?: string
}

export type AuthLandingLocation = {
  name: 'home'
  query: AuthLandingQuery
}

export function buildAuthLandingQuery(
  mode: AuthLandingMode,
  query: Record<string, unknown> = {},
): AuthLandingQuery {
  const result: AuthLandingQuery = { auth: mode }
  if (typeof query.next === 'string' && query.next.trim()) {
    result.next = query.next
  }
  if (typeof query.reason === 'string' && query.reason.trim()) {
    result.reason = query.reason
  }
  return result
}

export function buildLoginLandingQuery(query: Record<string, unknown> = {}): AuthLandingQuery {
  return buildAuthLandingQuery('login', query)
}

export function authLandingMode(value: unknown): AuthLandingMode | null {
  return value === 'login' || value === 'register' || value === 'reset' ? value : null
}

export function buildProtectedLoginLandingLocation(
  fullPath: string,
  reason = 'unauthenticated',
): AuthLandingLocation {
  return {
    name: 'home',
    query: {
      auth: 'login',
      next: fullPath,
      reason,
    },
  }
}
