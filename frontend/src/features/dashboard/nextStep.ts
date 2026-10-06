/**
 * "What should I do next?" rules for the dashboard (docs/01 DASH-01 / DASH-02).
 * Pure functions so the product rules are unit-tested and easy to tune.
 */
import type { WorkspaceOverview } from './types'

export type NextStepKind = 'create-resume' | 'continue-resume' | 'first-match' | 'practice-interview' | 'plan-career' | 'continue-work'

export type NextStep = {
  kind: NextStepKind
  title: string
  description: string
  cta: string
  to: string
}

export function nextStep(overview: WorkspaceOverview): NextStep {
  const resumes = overview.resumes
  const recentResume = resumes?.recent[0]
  if (resumes && resumes.total === 0) {
    return {
      kind: 'create-resume',
      title: '用 AI 写出你的第一份简历',
      description: 'AI 一次只问一个问题，把你的经历整理成每一句都站得住的简历，右侧实时排版成 A4。',
      cta: '开始写简历',
      to: '/ai-resume/new',
    }
  }
  if (recentResume && recentResume.confirmedModules < 3) {
    return {
      kind: 'continue-resume',
      title: `继续完善「${recentResume.title}」`,
      description: `已确认 ${recentResume.confirmedModules} / ${recentResume.totalModules || 11} 个模块，再补几项就能导出 PDF。`,
      cta: '继续编辑',
      to: `/resumes/${encodeURIComponent(recentResume.id)}`,
    }
  }
  const matches = overview.jobMatches
  if (matches && matches.total === 0) {
    return {
      kind: 'first-match',
      title: '用一个真实 JD 测测匹配度',
      description: '粘贴岗位描述，逐条对照你的简历证据，看清优势和需要补强的地方。',
      cta: '新建岗位匹配',
      to: '/job-match/new',
    }
  }
  const interviews = overview.mockInterviews
  if (interviews && interviews.total === 0) {
    const match = matches?.recent[0]
    return {
      kind: 'practice-interview',
      title: match ? `针对「${match.title}」模拟一次面试` : '来一场模拟面试',
      description: '结合简历和目标岗位出题，文字或语音作答，每题都有具体反馈。',
      cta: '开始模拟面试',
      to: '/mock-interviews/new',
    }
  }
  if (overview.careerCanvases && overview.careerCanvases.total === 0) {
    return {
      kind: 'plan-career',
      title: '规划你的职业方向',
      description: '基于真实经历生成能力画布，把目标拆成可执行、可验证的学习路径。',
      cta: '新建能力画布',
      to: '/career-planning/new',
    }
  }
  if (interviews?.resumable) {
    return {
      kind: 'continue-work',
      title: `继续面试「${interviews.resumable.title}」`,
      description: `已回答 ${interviews.resumable.answeredCount} / ${interviews.resumable.questionCount} 题。`,
      cta: '继续面试',
      to: `/mock-interviews/${encodeURIComponent(interviews.resumable.id)}/session`,
    }
  }
  return {
    kind: 'continue-work',
    title: recentResume ? `继续打磨「${recentResume.title}」` : '继续你的求职准备',
    description: '针对新的目标岗位开一个分支版本，或者导出最新的 PDF。',
    cta: recentResume ? '打开简历' : '查看简历',
    to: recentResume ? `/resumes/${encodeURIComponent(recentResume.id)}` : '/resumes',
  }
}

export type ReadinessItem = { key: string; label: string; done: boolean; progress: number; to: string }

export function readiness(overview: WorkspaceOverview): { score: number; items: ReadinessItem[] } {
  const profile = Math.max(0, Math.min(100, overview.profile?.completeness ?? 0))
  const items: ReadinessItem[] = [
    { key: 'profile', label: '完善求职资料', done: profile >= 80, progress: profile / 100, to: '/career-library' },
    { key: 'resume', label: '一份可导出的简历', done: (overview.resumes?.exportable ?? 0) > 0, progress: Math.min(1, (overview.resumes?.recent[0]?.confirmedModules ?? 0) / 5), to: '/resumes' },
    { key: 'match', label: '完成一次岗位匹配', done: (overview.jobMatches?.completed ?? 0) > 0, progress: (overview.jobMatches?.total ?? 0) > 0 ? 0.5 : 0, to: '/job-match' },
    { key: 'interview', label: '练过一次面试', done: (overview.mockInterviews?.completed ?? 0) > 0, progress: (overview.mockInterviews?.total ?? 0) > 0 ? 0.5 : 0, to: '/mock-interviews' },
  ]
  const score = Math.round(items.reduce((sum, item) => sum + (item.done ? 1 : item.progress), 0) / items.length * 100)
  return { score, items }
}

export function isNewUser(overview: WorkspaceOverview): boolean {
  return (overview.resumes?.total ?? 0) === 0 && (overview.jobMatches?.total ?? 0) === 0 && (overview.mockInterviews?.total ?? 0) === 0
}
