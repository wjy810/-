import { jobProofIcons, type JobProofIconId } from './jobProofIconCatalog'

export const jobProofIconIds = {
  careerLibrary: 'nav-career-library',
  resumeWorkbench: 'nav-resume-workbench',
  jobMatching: 'nav-job-matching',
  careerPlanning: 'nav-career-planning',
  mockInterview: 'nav-mock-interview',
  templateCenter: 'nav-template-center',
  notificationCenter: 'nav-notification-center',
  abilityCanvas: 'planning-ability-canvas',
  newCanvas: 'planning-new-canvas',
  learningPlan: 'planning-learning-plan',
  abilityValidation: 'planning-pending-validation',
  versionHistory: 'planning-canvas-versions',
} as const satisfies Record<string, JobProofIconId>

export type JobProofIconName = JobProofIconId

export const jobProofIconNames: ReadonlySet<string> = new Set(Object.keys(jobProofIcons))
