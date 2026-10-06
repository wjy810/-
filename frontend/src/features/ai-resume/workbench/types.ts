import type { AiResumeMessage } from '../types'

export type WorkbenchView = 'conversation' | 'edit' | 'design' | 'templates'
export type MobilePane = 'workspace' | 'preview'
export type DraftState = 'idle' | 'waiting' | 'saving' | 'saved' | 'error'
export type WorkbenchTool = 'branches' | 'history' | 'privacy'
export type DisplayMessage = AiResumeMessage & { transient?: boolean; phase?: 'sending' | 'loading' | 'streaming' }
export type StructuredItem = Record<string, unknown>

export const WORKBENCH_VIEWS: readonly WorkbenchView[] = ['conversation', 'edit', 'design', 'templates']
