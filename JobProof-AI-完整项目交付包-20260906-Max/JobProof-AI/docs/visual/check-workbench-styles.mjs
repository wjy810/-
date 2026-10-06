import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

// Read-only syntax and scope guard for the daylight workbench refinement.
const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(path.join(root, 'frontend/package.json'))
const { parse, compileStyle, compileTemplate } = require('@vue/compiler-sfc')
const postcss = require('postcss')
const files = [
  'frontend/src/style.css',
  'frontend/src/shared/ui/AppChrome.vue',
  'frontend/src/features/ai-resume/workbench-studio.css',
  'frontend/src/features/career-library/career-library.css',
  'frontend/src/features/career-library/pages/CareerLibraryPage.vue',
  'frontend/src/features/career-library/components/CareerProfileTab.vue',
  'frontend/src/features/career-planning/career-planning.css',
  'frontend/src/features/career-planning/pages/CareerCanvasOverviewPage.vue',
  'frontend/src/features/career-planning/components/CareerPlanningWorkbench.vue',
  'frontend/src/features/career-planning/components/CareerAbilityCanvas.vue',
  ...['CareerLearningPlan', 'CareerValidationWorkspace', 'CareerVersionHistory', 'CareerSkillPicker', 'CareerPlanSetupDialog', 'CareerProposalReview'].map(name => `frontend/src/features/career-planning/components/${name}.vue`),
  'frontend/src/features/job-match/job-match.css',
  'frontend/src/features/mock-interview/mock-interview.css',
  'frontend/src/features/mock-interview/pages/MockInterviewHomePage.vue',
  'frontend/src/features/notification/pages/NotificationListPage.vue',
  'frontend/src/features/updates/updates.css',
  'frontend/src/features/resume/pages/ResumeTemplateCatalogPage.vue',
]
const failures = []
for (const relative of files) {
  const filename = path.join(root, relative)
  const source = fs.readFileSync(filename, 'utf8')
  try {
    if (relative.endsWith('.css')) {
      postcss.parse(source, { from: filename })
    } else {
      const parsed = parse(source, { filename })
      failures.push(...parsed.errors.map(error => `${relative}: ${String(error)}`))
      if (parsed.descriptor.template) {
        const template = compileTemplate({
          source: parsed.descriptor.template.content,
          filename, id: 'visual-syntax-check',
          compilerOptions: { expressionPlugins: ['typescript'] },
        })
        failures.push(...template.errors.map(error => `${relative}: ${String(error)}`))
      }
      parsed.descriptor.styles.forEach(style => {
        const compiled = compileStyle({ source: style.content, filename, id: 'data-v-visual-check', scoped: style.scoped })
        failures.push(...compiled.errors.map(error => `${relative}: ${String(error)}`))
      })
    }
  } catch (error) { failures.push(`${relative}: ${error.message}`) }
}
const chrome = postcss.parse(fs.readFileSync(path.join(root, 'frontend/src/features/ai-resume/workbench-studio.css'), 'utf8'))
chrome.walkRules(rule => {
  if (/resume-sheet|resume-pages/.test(rule.selector)) failures.push(`Printable resume selector in chrome: ${rule.selector}`)
  if (!rule.selector.split(',').every(selector => selector.trim().startsWith('.ai-workbench '))) failures.push(`Unscoped chrome selector: ${rule.selector}`)
})
if (failures.length) {
  console.error(failures.join('\n'))
  process.exitCode = 1
} else console.log(`PASS: ${files.length} files parsed; AI workbench stylesheet is page-scoped and excludes printable resume selectors.`)
