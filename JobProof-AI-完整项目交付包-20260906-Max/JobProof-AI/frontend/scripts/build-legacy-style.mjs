import { mkdir, readdir, readFile, stat, writeFile } from 'node:fs/promises'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

// The deployed release is the visual baseline requested by the product owner.
// This utility produces a single, cacheable stylesheet from that release while
// translating Vue scope ids to the current source ids. It deliberately does
// not touch templates or runtime behaviour.
const frontendRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const projectRoot = dirname(frontendRoot)
const legacyDir = join(projectRoot, 'deploy/web/assets')
const currentDir = join(projectRoot, 'frontend/dist/assets')
const outputFile = join(projectRoot, 'frontend/public/legacy-release.css')

const isCss = (name) => name.endsWith('.css')
// Vite's eight-character content hash may itself contain a hyphen (for
// example `o7K1-l70`), so split by its fixed length rather than by `-`.
const prefixOf = (name) => name.replace(/\.css$/, '').slice(0, -8).replace(/-+$/, '')
const unique = (items) => [...new Set(items)]

async function filesWithMtime(dir) {
  const entries = await readdir(dir)
  const files = []
  for (const name of entries.filter(isCss)) {
    const file = join(dir, name)
    const info = await stat(file)
    files.push({ name, file, mtime: info.mtimeMs })
  }
  return files
}

function scopeIds(css) {
  return unique([...css.matchAll(/data-v-([a-z0-9]+)/g)].map((match) => match[1]))
}

function classesForScope(css, id) {
  const classes = new Set()
  const rulePattern = /([^{}]+)\{/g
  for (const match of css.matchAll(rulePattern)) {
    if (!match[1].includes(`data-v-${id}`)) continue
    for (const classMatch of match[1].matchAll(/\.([A-Za-z_][A-Za-z0-9_-]*)/g)) {
      classes.add(classMatch[1])
    }
  }
  return classes
}

function score(left, right) {
  let overlap = 0
  for (const value of left) if (right.has(value)) overlap += 1
  return overlap
}

function buildScopeMap(oldCss, currentCss) {
  const oldIds = scopeIds(oldCss)
  const currentIds = scopeIds(currentCss)
  const currentClasses = new Map(currentIds.map((id) => [id, classesForScope(currentCss, id)]))
  const used = new Set()
  const map = new Map()

  for (const oldId of oldIds) {
    const oldClasses = classesForScope(oldCss, oldId)
    const candidates = currentIds
      .filter((id) => !used.has(id))
      .map((id) => ({ id, points: score(oldClasses, currentClasses.get(id)) }))
      .sort((a, b) => b.points - a.points)
    const best = candidates[0]
    if (best && best.points > 0) {
      map.set(oldId, best.id)
      used.add(best.id)
    }
  }
  return map
}

function translateScopes(css, scopeMap) {
  let translated = css
  const originalIds = scopeIds(css)
  for (const [oldId, currentId] of scopeMap) {
    translated = translated.replaceAll(`data-v-${oldId}`, `data-v-${currentId}`)
  }
  // A scope that no longer exists belongs to a removed/merged component. Keep
  // its declarations useful as a visual fallback by dropping the old marker.
  for (const oldId of originalIds) {
    if (!scopeMap.has(oldId)) translated = translated.replaceAll(`[data-v-${oldId}]`, '')
  }
  return translated
}

const releaseCutoffStart = new Date('2026-08-29T09:05:00+08:00').getTime()
const releaseCutoffEnd = new Date('2026-08-29T09:06:00+08:00').getTime()
const legacyFiles = (await filesWithMtime(legacyDir))
  .filter((file) => file.mtime >= releaseCutoffStart && file.mtime <= releaseCutoffEnd)
  .sort((a, b) => b.mtime - a.mtime)
  .filter((file, index, all) => index === all.findIndex((candidate) => prefixOf(candidate.name) === prefixOf(file.name)))
  .sort((a, b) => a.name.localeCompare(b.name))
const currentFiles = await filesWithMtime(currentDir)
const currentByPrefix = new Map(currentFiles.map((file) => [prefixOf(file.name), file]))

const chunks = []
const mappingReport = []
for (const legacy of legacyFiles) {
  const oldCss = await readFile(legacy.file, 'utf8')
  const current = currentByPrefix.get(prefixOf(legacy.name))
  const currentCss = current ? await readFile(current.file, 'utf8') : ''
  const scopeMap = current ? buildScopeMap(oldCss, currentCss) : new Map()
  mappingReport.push(`${basename(legacy.file)} -> ${current ? basename(current.file) : 'no-current-pair'} (${scopeMap.size} scopes)`)
  chunks.push(`/* ${legacy.name} */\n${translateScopes(oldCss, scopeMap)}`)
}

await mkdir(dirname(outputFile), { recursive: true })
await writeFile(outputFile, `${chunks.join('\n')}\n`, 'utf8')
console.log(`Wrote ${outputFile} (${(chunks.join('\n').length / 1024).toFixed(1)} KiB)`)
console.log(mappingReport.join('\n'))
