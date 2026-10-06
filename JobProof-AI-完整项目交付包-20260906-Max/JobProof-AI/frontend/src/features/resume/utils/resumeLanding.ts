import type { ResumeMasterSummary } from '../types'

export function recentActiveResume(resumes: readonly ResumeMasterSummary[]): ResumeMasterSummary | null {
  return [...resumes]
    .filter((resume) => resume.status !== 'ARCHIVED')
    .sort((left, right) => {
      const byUpdatedAt = (right.updatedAt ?? '').localeCompare(left.updatedAt ?? '')
      if (byUpdatedAt !== 0) return byUpdatedAt
      return right.id.localeCompare(left.id)
    })[0] ?? null
}

export function resumeLandingPath(resumes: readonly ResumeMasterSummary[]): string {
  const recent = recentActiveResume(resumes)
  return recent ? `/resumes/${encodeURIComponent(recent.id)}` : '/ai-resume/new'
}
