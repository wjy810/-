#!/usr/bin/env node
// Writes the built-in template manifests (src/resume-render/templates/manifests.ts) to the backend
// resources, where BuiltInTemplateSynchronizer reads them (docs/phase2/03 §5.1), and the sample
// resumes (src/resume-render/samples.ts) to the backend test resources for the renderer contract test.
// `--check` fails instead of writing when a committed file is out of date.
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { build } from 'rolldown'

const root = fileURLToPath(new URL('..', import.meta.url))
const targets = {
  manifests: join(root, '../backend/src/main/resources/templates/builtin-manifests.json'),
  samples: join(root, '../backend/src/test/resources/templates/render-samples.json'),
}
const dir = mkdtempSync(join(tmpdir(), 'jp-manifests-'))
try {
  await build({
    input: {
      manifests: join(root, 'src/resume-render/templates/manifests.ts'),
      samples: join(root, 'src/resume-render/samples.ts'),
    },
    output: { dir, format: 'esm', entryFileNames: '[name].mjs' },
    logLevel: 'silent',
  })
  const { MANIFESTS } = await import(pathToFileURL(join(dir, 'manifests.mjs')).href)
  const { SAMPLES } = await import(pathToFileURL(join(dir, 'samples.mjs')).href)
  const outputs = {
    manifests: `${JSON.stringify({ schema: 'resume-template-manifests-v1', templates: MANIFESTS }, null, 2)}\n`,
    samples: `${JSON.stringify(SAMPLES, null, 2)}\n`,
  }
  const check = process.argv.includes('--check')
  let stale = false
  for (const [key, json] of Object.entries(outputs)) {
    const target = targets[key]
    if (check) {
      let current = ''
      try { current = readFileSync(target, 'utf8') } catch { /* missing counts as stale */ }
      if (current !== json) {
        console.error(`${target} is out of date; run \`npm run export:manifests\`.`)
        stale = true
      }
    } else {
      mkdirSync(dirname(target), { recursive: true })
      writeFileSync(target, json)
      console.log(`Wrote ${target}`)
    }
  }
  if (stale) process.exit(1)
  if (check) console.log(`Template manifests (${MANIFESTS.length}) and render samples are up to date.`)
} finally {
  rmSync(dir, { recursive: true, force: true })
}
