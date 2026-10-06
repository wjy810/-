#!/usr/bin/env node
// Runs every pure-logic test (`*.test.ts`, excluding `*.component.test.ts`) with the
// Node test runner, so new tests are picked up without editing package.json.
import { spawnSync } from 'node:child_process'
import { readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const files = []
function walk(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) walk(path)
    else if (entry.endsWith('.test.ts') && !entry.endsWith('.component.test.ts')) files.push(relative(root, path))
  }
}
walk(join(root, 'src'))
files.sort()
const result = spawnSync(process.execPath, ['--test', ...files], { cwd: root, stdio: 'inherit' })
process.exit(result.status ?? 1)
