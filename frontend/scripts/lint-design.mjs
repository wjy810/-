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

/**
 * Hex literals outside <style> (scripts, templates, inline styles, .ts) with their ceilings. These
 * render colours that must not follow the theme: brand art, user-picked swatches, the browser's
 * theme-color meta. Resume templates and the token files define colours and are exempt.
 */
const SCRIPT_HEX_ALLOWED = {
  // The logo's fixed gradient.
  'src/shared/ui/BrandMark.vue': 11,
  // Deterministic avatar background palette.
  'src/shared/ui/UiAvatar.vue': 10,
  // A miniature of a white resume page.
  'src/features/dashboard/components/ResumeMiniCard.vue': 5,
  // Default custom accent offered in the colour picker.
  'src/features/ai-resume/workbench/components/DesignView.vue': 1,
  // <meta name="theme-color"> for light and dark.
  'src/stores/preferences.ts': 2,
}
const SCRIPT_HEX_EXEMPT = /^src\/(design|resume-render)\//

/** Files allowed to import reka-ui directly, and why. */
const REKA_ALLOWED = {
  'src/app/App.vue': 'app-wide TooltipProvider',
  'src/features/identity/components/AuthDialog.vue': 'two-pane dialog layout beyond UiDialog',
  'src/features/command/CommandPalette.vue': 'palette dialog with its own listbox',
}

/**
 * Classes the retired global stylesheet (legacy.css, removed in X-6) used to style. A template that still
 * uses one without styling it itself renders unstyled; use the Ui* component instead (docs/phase2/00 X-6).
 */
const RETIRED_CLASSES = new Set([
  'page', 'page-head', 'page-head__title', 'page-head__sub', 'page-head__actions', 'btn', 'btn--primary', 'btn--ghost',
  'btn--danger', 'btn--text', 'btn--sm', 'btn--wax', 'btn--ink', 'btn-row', 'btn-block', 'icon-btn', 'card', 'card__head',
  'card__title', 'card__sub', 'card__body', 'card__actions', 'grid-2', 'grid-3', 'grid-stats', 'stat', 'stat__icon',
  'stat__label', 'stat__value', 'stat__sub', 'stat__foot', 'bone', 'bone--short', 'pager', 'pager__page', 'tbl', 'input',
  'textarea', 'select', 'field', 'field__label', 'field__hint', 'field__error', 'banner', 'tag', 'empty', 'kicker', 'lede',
  'ack', 'input-icon', 'progress', 'divider', 'list-row', 'list-rows', 'avatar', 'settings-layout',
])
// Custom properties that libraries set at runtime, and optional knobs read with a fallback.
const EXTERNAL_VARIABLE = /^reka-/

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

/** Everything a .vue file renders or computes outside <style>; the whole file for .ts. */
function nonStyleText(path, source) {
  if (path.endsWith('.ts')) return source
  if (!path.endsWith('.vue')) return ''
  return source.replace(/<style[^>]*>[\s\S]*?<\/style>/g, '')
}

function countHex(css) {
  return css.split('\n').filter(line => !IGNORED_LINE.test(line))
    .reduce((total, line) => total + (line.match(HEX)?.length ?? 0), 0)
}

/** Class names a template applies: static class="…" plus quoted names inside :class bindings. */
function templateClasses(source) {
  const template = source.replace(/<style[^>]*>[\s\S]*?<\/style>/g, '').replace(/<script[^>]*>[\s\S]*?<\/script>/g, '')
  const names = new Set()
  for (const match of template.matchAll(/\sclass="([^"]*)"/g)) match[1].split(/\s+/).filter(Boolean).forEach(name => names.add(name))
  for (const match of template.matchAll(/:class="([^"]*)"/g)) {
    for (const quoted of match[1].matchAll(/'([^']*)'/g)) quoted[1].split(/\s+/).filter(Boolean).forEach(name => names.add(name))
    for (const key of match[1].matchAll(/([a-zA-Z][\w-]*)\s*:/g)) names.add(key[1])
  }
  return names
}

function classesDefinedIn(css) {
  return new Set([...css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/\.([a-zA-Z][\w-]*)/g)].map(match => match[1]))
}

const problems = []
const shrunk = []
const sources = new Map()
for await (const path of files(src)) sources.set(relative(root, path).split(sep).join('/'), await readFile(path, 'utf8'))

// Global stylesheets (design tokens and feature .css files) apply everywhere; scoped <style> only to its own file.
const globalClasses = new Set()
const definedVariables = new Set()
for (const [rel, source] of sources) {
  if (rel.endsWith('.css')) classesDefinedIn(source).forEach(name => globalClasses.add(name))
  for (const match of source.matchAll(/(?:^|[\s;{('"`])--([\w-]+)\s*:/g)) definedVariables.add(match[1])
  for (const match of source.matchAll(/['"`]--([\w-]+)['"`]/g)) definedVariables.add(match[1])
  for (const match of source.matchAll(/@property\s+--([\w-]+)/g)) definedVariables.add(match[1])
}

for (const [rel, source] of sources) {
  if (rel.endsWith('.vue')) {
    const local = classesDefinedIn(styleText(rel, source))
    const retired = [...templateClasses(source)].filter(name => RETIRED_CLASSES.has(name) && !local.has(name) && !globalClasses.has(name))
    if (retired.length) problems.push(`${rel}: uses retired global class${retired.length > 1 ? 'es' : ''} ${retired.map(name => `.${name}`).join(', ')}; use the shared Ui* component or style it locally`)
  }
  // A var() without a fallback that nothing defines silently resolves to nothing (e.g. a mistyped token).
  const missing = new Set()
  for (const match of source.matchAll(/var\(\s*--([\w-]+)\s*\)/g)) {
    if (!definedVariables.has(match[1]) && !EXTERNAL_VARIABLE.test(match[1])) missing.add(match[1])
  }
  if (missing.size) problems.push(`${rel}: undefined CSS variable${missing.size > 1 ? 's' : ''} ${[...missing].map(name => `--${name}`).join(', ')}; use a token from src/design/tokens.css`)
}

for (const [rel, source] of sources) {
  const path = join(root, rel)
  if (!rel.startsWith('src/design/')) {
    const count = countHex(styleText(path, source))
    const ceiling = HEX_ALLOWED[rel] ?? 0
    if (count > ceiling) {
      problems.push(`${rel}: ${count} hard-coded colour${count > 1 ? 's' : ''} (allowed ${ceiling}); use tokens from src/design/tokens.css`)
    } else if (count < ceiling) {
      shrunk.push(`${rel}: ${count} < ${ceiling}`)
    }
  }

  if (!SCRIPT_HEX_EXEMPT.test(rel)) {
    const count = countHex(nonStyleText(path, source))
    const ceiling = SCRIPT_HEX_ALLOWED[rel] ?? 0
    if (count > ceiling) {
      problems.push(`${rel}: ${count} hard-coded colour${count > 1 ? 's' : ''} in script/template (allowed ${ceiling}); bind a token, e.g. style="color: var(--…)"`)
    } else if (count < ceiling) {
      shrunk.push(`${rel} (script/template): ${count} < ${ceiling}`)
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
