import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const routerSource = readFileSync(new URL('./router.ts', import.meta.url), 'utf8')

test('seeker authentication and legacy workspace entry default to the career library', () => {
  assert.match(routerSource, /function signedInHome\(\)[\s\S]*?: \{ name: 'career-library' \}/)
  assert.match(routerSource, /path: '\/workspace',[\s\S]*?redirect: \{ name: 'career-library' \}/)
})

test('retired matching and job-data routes stay outside the application router', () => {
  assert.doesNotMatch(routerSource, /path:\s*['"]\/matching(?:\/|['"])/)
  assert.doesNotMatch(routerSource, /features\/matching/)
  assert.doesNotMatch(routerSource, /path:\s*['"]\/jobs(?:\/|['"])/)
})

test('job match routes expose the complete resumable workflow', () => {
  for (const path of [
    '/job-match',
    '/job-match/new',
    '/job-match/:id/analyzing',
    '/job-match/:id/clarifications',
    '/job-match/:id/report',
    '/job-match/:id/similar-jobs',
    '/job-match/history',
  ]) {
    assert.match(routerSource, new RegExp(`path:\\s*['"]${path.replaceAll('/', '\\/')}['"]`))
  }
  assert.match(routerSource, /features\/job-match/)
})

test('career planning exposes a resumable authenticated workspace', () => {
  for (const path of ['/career-planning', '/career-planning/new', '/career-planning/:sessionId']) {
    assert.match(routerSource, new RegExp(`path:\\s*['"]${path.replaceAll('/', '\\/')}['"]`))
  }
  assert.match(routerSource, /features\/career-planning/)
})
