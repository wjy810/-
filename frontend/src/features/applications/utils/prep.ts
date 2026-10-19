import type { JobApplication } from '../types'

export type InterviewPrep = { title: string; detail: string; query: Record<string, string> }

/**
 * Once a company has moved an application to a test or an interview, a mock interview for that company
 * is the most useful next thing. With a job match the interview draws on the match report; without one
 * it starts from the company and the position.
 */
export function interviewPrep(application: Pick<JobApplication, 'stage' | 'company' | 'positionTitle' | 'jobMatchId'>): InterviewPrep | null {
  if (application.stage !== 'ASSESSMENT' && application.stage !== 'INTERVIEW') return null
  const title = application.stage === 'INTERVIEW' ? '面试前，先模拟一场' : '笔试之后就是面试，提前练练'
  return application.jobMatchId
    ? { title, detail: '按这家公司的岗位匹配报告出题，优先问到你的短板。', query: { jobMatchId: application.jobMatchId } }
    : { title, detail: '按这家公司和岗位出题，结束后逐题给出反馈。', query: { company: application.company, position: application.positionTitle } }
}
