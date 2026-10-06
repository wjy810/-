import { readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const root = resolve('../.codex_tmp/completion-acceptance-20260906')
const sources = ['release-authenticated/results.json', 'release-resumes-fixed/results.json']
const scenes = new Map()
const supersededFailures = []
const failed = row => Boolean(row.failed || row.overflow > 1 || row.brokenImages?.length || row.errors?.length)
for (const source of sources) {
  const rows = JSON.parse(await readFile(resolve(root, source), 'utf8'))
  for (const row of rows) {
    const key = `${row.width}x${row.height}:${row.name}`
    const previous = scenes.get(key)
    if (previous && failed(previous)) supersededFailures.push(previous)
    scenes.set(key, { ...row, evidenceFile: source })
  }
}
const results = [...scenes.values()]
const summary = {
  generatedAt: new Date().toISOString(),
  scope: 'Authenticated local-browser acceptance using the release API on 18081 and source preview on 5174.',
  sources,
  uniqueScenes: results.length,
  coreScenes: results.filter(row => row.name !== 'resumes-populated-pagination').length,
  populatedResumePaginationScenes: results.filter(row => row.name === 'resumes-populated-pagination').length,
  viewportCounts: Object.fromEntries([...new Set(results.map(row => `${row.width}x${row.height}`))]
    .map(viewport => [viewport, results.filter(row => `${row.width}x${row.height}` === viewport).length])),
  failures: results.filter(failed),
  supersededFailures,
  limitations: [
    'Screenshots and DOM checks cover loaded pages and selected local form interactions, not paid external AI generation.',
    'Real recording devices, provider verification delivery, private file scanning and account deletion are outside this browser matrix.',
  ],
  results,
}
const output = resolve(root, 'final-authenticated-summary.json')
await writeFile(output, JSON.stringify(summary, null, 2))
console.log(JSON.stringify({ output, uniqueScenes: summary.uniqueScenes, coreScenes: summary.coreScenes, failures: summary.failures.length, supersededFailures: supersededFailures.length }, null, 2))
if (summary.failures.length) process.exitCode = 1
