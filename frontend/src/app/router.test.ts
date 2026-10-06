import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const routesSource = readFileSync(new URL('./router/routes.ts', import.meta.url), 'utf8')

test('seekers land on the dashboard and the legacy workspace entry redirects there', () => {
  assert.match(routesSource, /function signedInHome\(\)[\s\S]*?\{ name: 'dashboard' \}/)
  assert.match(routesSource, /path: 'workspace', redirect: \{ name: 'dashboard' \}/)
})

test('pages are nested under persistent layouts instead of wrapping their own chrome', () => {
  assert.match(routesSource, /layouts\/AppLayout\.vue/)
  assert.match(routesSource, /layouts\/FocusLayout\.vue/)
})

test('retired matching and job-data routes stay outside the application router', () => {
  assert.doesNotMatch(routesSource, /path:\s*['"]\/?matching(?:\/|['"])/)
  assert.doesNotMatch(routesSource, /features\/matching/)
  assert.doesNotMatch(routesSource, /path:\s*['"]\/?jobs(?:\/|['"])/)
})

test('job match routes expose the complete resumable workflow', () => {
  for (const path of ['job-match', 'job-match/new', 'job-match/:id/analyzing', 'job-match/:id/clarifications', 'job-match/:id/report', 'job-match/:id/similar-jobs', 'job-match/history']) {
    assert.match(routesSource, new RegExp(`path:\\s*['"]${path.replaceAll('/', '\\/')}['"]`))
  }
  assert.match(routesSource, /features\/job-match/)
})

test('career planning exposes a resumable authenticated workspace', () => {
  for (const path of ['career-planning', 'career-planning/new', 'career-planning/:sessionId']) {
    assert.match(routesSource, new RegExp(`path:\\s*['"]${path.replaceAll('/', '\\/')}['"]`))
  }
  assert.match(routesSource, /features\/career-planning/)
})
