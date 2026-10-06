import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import test from 'node:test'

test('all Vue select controls use the shared AppSelect component', () => {
  const sourceRoot = join(import.meta.dirname, '..', '..')
  const offenders: string[] = []

  function scan(directory: string): void {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name)
      if (entry.isDirectory()) scan(path)
      else if (entry.name.endsWith('.vue') && /<select(?:\s|>)/i.test(readFileSync(path, 'utf8'))) offenders.push(path)
    }
  }

  scan(sourceRoot)
  assert.deepEqual(offenders, [])
})
