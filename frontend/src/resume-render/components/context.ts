import type { InjectionKey } from 'vue'
import type { Block } from '../layout/blocks'
import type { RenderDocument } from '../model/types'
import type { ResumeDesignV2 } from '../theme/design'
import type { ResolvedTheme } from '../theme/tokens'
import type { TemplateManifest } from '../templates/manifest'

export interface PageContext {
  blocks: (region: string) => Block[]
  probe: boolean
}
export const PAGE_CONTEXT: InjectionKey<PageContext> = Symbol('resume-render-page')

/** Props every template Layout receives. */
export interface LayoutProps {
  document: RenderDocument
  design: ResumeDesignV2
  manifest: TemplateManifest
  theme: ResolvedTheme
  page: { index: number; total: number; continuation: boolean }
}
