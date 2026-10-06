import { mkdirSync } from 'node:fs'
import { dirname } from 'node:path'
import { request, type FullConfig } from '@playwright/test'
import { SEEKER_STATE } from './fixtures'

/**
 * Sign the dev seeker in once per run and share the cookie jar. The backend rate-limits
 * every login attempt per account (8 / 5 min by default), so per-test logins across five
 * viewport projects would lock the account out halfway through the suite.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use.baseURL
  const context = await request.newContext({ baseURL })
  const response = await context.post('/api/v1/auth/login', { data: { identifier: 'seeker', password: 'seeker123' } })
  if (!response.ok()) {
    throw new Error(`Dev seeker login failed (${response.status()}): ${await response.text()}`)
  }
  mkdirSync(dirname(SEEKER_STATE), { recursive: true })
  await context.storageState({ path: SEEKER_STATE })
  await context.dispose()
}
