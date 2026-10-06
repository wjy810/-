// Dependency-free whitespace/conflict and syntax gate for maintained operational scripts.
import { readdir, readFile } from 'node:fs/promises'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
let failures = 0
let checked = 0
async function visit(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (['__pycache__', 'data', 'web', 'backups', 'letsencrypt', 'acme-webroot', 'acme-logs'].includes(entry.name)) continue
    const path = join(directory, entry.name)
    if (entry.isDirectory()) { await visit(path); continue }
    if (!/\.(sh|py|mjs|yml|conf)$/.test(path)) continue
    const content = await readFile(path, 'utf8')
    checked++
    if (/^(?:<<<<<<< |=======|>>>>>>> )/m.test(content) || /[\t ]+\r?$/m.test(content)) {
      console.error(`${relative(root, path)}: conflict marker or trailing whitespace`)
      failures++
    }
    if (path.endsWith('.mjs')) {
      const result = spawnSync(process.execPath, ['--check', path], { stdio: 'inherit' })
      if (result.status !== 0) failures++
    }
  }
}
for (const path of ['scripts', 'deploy', '.github/workflows']) await visit(join(root, path))
console.log(`Operational lint: ${checked} files; failures=${failures}`)
process.exitCode = failures ? 1 : 0
