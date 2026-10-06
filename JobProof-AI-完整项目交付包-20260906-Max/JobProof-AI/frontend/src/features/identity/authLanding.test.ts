import assert from 'node:assert/strict'
import test from 'node:test'
import {
  authLandingMode,
  buildAuthLandingQuery,
  buildLoginLandingQuery,
  buildProtectedLoginLandingLocation,
} from './authLanding.ts'
import { safeNextPath } from './safeNext.ts'

test('login defaults to the career library while preserving an explicit protected destination', () => {
  assert.equal(safeNextPath(undefined), '/career-library')
  assert.equal(safeNextPath('/mock-interviews'), '/mock-interviews')
  assert.equal(safeNextPath('https://example.com'), '/career-library')
})

test('legacy login routes become a homepage login overlay query', () => {
  assert.deepEqual(
    buildLoginLandingQuery({ reason: 'session_expired', next: '/resumes', ignored: 'value' }),
    { auth: 'login', reason: 'session_expired', next: '/resumes' },
  )
})

test('invalid or repeated query values are not forwarded to the homepage', () => {
  assert.deepEqual(buildLoginLandingQuery({ reason: ['logged_out'], next: 42 }), { auth: 'login' })
  assert.equal(authLandingMode('login'), 'login')
  assert.equal(authLandingMode('register'), 'register')
  assert.equal(authLandingMode('reset'), 'reset')
  assert.equal(authLandingMode('unknown'), null)
  assert.deepEqual(buildAuthLandingQuery('register', { next: '/career-library' }), {
    auth: 'register',
    next: '/career-library',
  })
  assert.deepEqual(buildAuthLandingQuery('reset', { next: '/resumes' }), {
    auth: 'reset',
    next: '/resumes',
  })
})

test('plain homepage stays public and protected features open the homepage login dialog', () => {
  assert.equal(authLandingMode(undefined), null)
  assert.deepEqual(
    buildProtectedLoginLandingLocation('/career-library'),
    {
      name: 'home',
      query: {
        auth: 'login',
        next: '/career-library',
        reason: 'unauthenticated',
      },
    },
  )
})
