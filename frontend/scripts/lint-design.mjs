// Design-system guard (docs/04): colours come from tokens and Reka primitives are wrapped by shared/ui.
// Run by `npm test` and therefore by CI. Allowlisted counts are ceilings: they may only go down.
import { readdir, readFile } from 'node:fs/promises'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const src = join(root, 'src')

/** Files that must render fixed colours, with the most hex literals they may contain. */
const HEX_ALLOWED = {
  'src/features/dashboard/components/ResumeMiniCard.vue': 6,
  // Theme and accent swatches show the literal colours being chosen.
  'src/features/identity/pages/AppearancePage.vue': 14,
  // Brand illustrations on the public pages.
  'src/features/landing/pages/LandingPage.vue': 8,
  'src/features/identity/components/AuthDialog.vue': 10,
}

/** Files allowed to import reka-ui directly, and why. */
const REKA_ALLOWED = {
  'src/app/App.vue': 'app-wide TooltipProvider',
  'src/features/identity/components/AuthDialog.vue': 'two-pane dialog layout beyond UiDialog',
  'src/features/command/CommandPalette.vue': 'palette dialog with its own listbox',
}

const HEX = /#[0-9a-fA-F]{3,8}\b/g
// Masks use opaque black as an alpha channel, not as a colour.
const IGNORED_LINE = /^\s*(-webkit-)?mask(-image)?\s*:/

async function* files(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) yield* files(path)
    else if (/\.(vue|css|ts)$/.test(entry.name) && !/\.test\.ts$/.test(entry.name)) yield path
  }
}

function styleText(path, source) {
  if (path.endsWith('.css')) return source
  if (!path.endsWith('.vue')) return ''
  return [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map(match => match[1]).join('\n')
}

function countHex(css) {
  return css.split('\n').filter(line => !IGNORED_LINE.test(line))
    .reduce((total, line) => total + (line.match(HEX)?.length ?? 0), 0)
}

const problems = []
const shrunk = []
for await (const path of files(src)) {
  const rel = relative(root, path).split(sep).join('/')
  const source = await readFile(path, 'utf8')

  if (!rel.startsWith('src/design/')) {
    const count = countHex(styleText(path, source))
    const ceiling = HEX_ALLOWED[rel] ?? 0
    if (count > ceiling) {
      problems.push(`${rel}: ${count} hard-coded colour${count > 1 ? 's' : ''} (allowed ${ceiling}); use tokens from src/design/tokens.css`)
    } else if (count < ceiling) {
      shrunk.push(`${rel}: ${count} < ${ceiling}`)
    }
  }

  if (/from\s+['"]reka-ui['"]/.test(source) && !rel.startsWith('src/shared/ui/') && !REKA_ALLOWED[rel]) {
    problems.push(`${rel}: imports reka-ui directly; use or extend a component in src/shared/ui`)
  }
}

for (const line of problems) console.error(line)
if (shrunk.length) console.log(`Lower these ceilings in scripts/lint-design.mjs:\n  ${shrunk.join('\n  ')}`)
console.log(`Design lint: ${problems.length} problem${problems.length === 1 ? '' : 's'}`)
process.exitCode = problems.length ? 1 : 0
