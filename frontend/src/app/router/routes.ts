/**
 * Route table (docs/03 §4.3). Pages are lazy-loaded and nested under persistent layouts:
 * AppLayout (sidebar + topbar), FocusLayout (full-screen editors) or standalone.
 */
import type { RouteRecordRaw } from 'vue-router'
import { buildAuthLandingQuery, buildLoginLandingQuery } from '@/features/identity/authLanding'
import { resumeLandingPath } from '@/features/resume/utils/resumeLanding'
import { useSessionStore } from '@/stores/session'

export function signedInHome() {
  return useSessionStore().isAdmin ? { name: 'template-admin' } : { name: 'dashboard' }
}

async function resolveResumeHome() {
  if (useSessionStore().isAdmin) return { name: 'template-admin' }
  try {
    const { listResumes } = await import('@/features/resume/services/resumeApi')
    return { path: resumeLandingPath(await listResumes()), replace: true }
  } catch {
    return { name: 'resumes', replace: true }
  }
}

const authed = { requiresAuth: true }

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    meta: { title: 'JobProof AI · 求职证据官' },
    component: () => import('@/features/landing/pages/LandingPage.vue'),
  },
  {
    path: '/login',
    name: 'login',
    redirect: to => (useSessionStore().signedIn ? signedInHome() : { name: 'home', query: buildLoginLandingQuery(to.query) }),
  },
  {
    path: '/register',
    name: 'register',
    redirect: to => (useSessionStore().signedIn ? signedInHome() : { name: 'home', query: buildAuthLandingQuery('register', to.query) }),
  },
  {
    path: '/reset',
    name: 'reset',
    redirect: to => (useSessionStore().signedIn ? signedInHome() : { name: 'home', query: buildAuthLandingQuery('reset', to.query) }),
  },
  {
    path: '/terms',
    name: 'terms',
    meta: { title: '用户协议' },
    component: () => import('@/features/identity/pages/PublicPolicyPage.vue'),
    props: { kind: 'terms' },
  },
  {
    path: '/privacy',
    name: 'privacy',
    meta: { title: '隐私政策' },
    component: () => import('@/features/identity/pages/PublicPolicyPage.vue'),
    props: { kind: 'privacy' },
  },

  // ---------- Full-screen editors ----------
  {
    path: '/',
    component: () => import('@/app/layouts/FocusLayout.vue'),
    children: [
      {
        path: 'ai-resume/new',
        name: 'ai-resume-new',
        meta: { ...authed, title: 'AI 创建简历' },
        component: () => import('@/features/ai-resume/pages/AiResumeNewPage.vue'),
      },
      {
        path: 'ai-resume/:conversationId',
        name: 'ai-resume-workbench',
        meta: { ...authed, title: 'AI 简历工作台' },
        component: () => import('@/features/ai-resume/pages/AiResumeWorkbenchPage.vue'),
      },
    ],
  },

  // ---------- Application shell ----------
  {
    path: '/',
    component: () => import('@/app/layouts/AppLayout.vue'),
    children: [
      { path: 'workspace', redirect: { name: 'dashboard' } },
      {
        path: 'dashboard',
        name: 'dashboard',
        meta: { ...authed, title: '工作台' },
        component: () => import('@/features/dashboard/pages/DashboardPage.vue'),
      },
      {
        path: 'onboarding',
        name: 'onboarding',
        meta: { ...authed, title: '求职方向' },
        component: () => import('@/features/identity/pages/OnboardingPage.vue'),
      },
      {
        path: 'resume-home',
        name: 'resume-home',
        meta: authed,
        beforeEnter: resolveResumeHome,
        component: () => import('@/features/resume/pages/ResumeHomeRedirectPage.vue'),
      },
      {
        path: 'career-library',
        name: 'career-library',
        meta: { ...authed, title: '资料库' },
        component: () => import('@/features/career-library/pages/CareerLibraryPage.vue'),
      },
      {
        path: 'resume-templates',
        name: 'resume-templates',
        meta: { title: '模板中心' },
        component: () => import('@/features/resume/pages/ResumeTemplateCatalogPage.vue'),
      },
      {
        path: 'resume-templates/:templateId',
        name: 'resume-template-detail',
        meta: { ...authed, title: '模板详情' },
        component: () => import('@/features/resume/pages/ResumeTemplateDetailPage.vue'),
      },
      {
        path: 'template-assets/:catalogId',
        name: 'template-asset-detail',
        meta: { title: 'Word 模板' },
        component: () => import('@/features/resume/pages/ResumeTemplateAssetDetailPage.vue'),
      },
      {
        path: 'updates',
        name: 'updates',
        meta: { title: '更新日志' },
        component: () => import('@/features/updates/pages/UpdateListPage.vue'),
      },
      {
        path: 'updates/:version',
        name: 'update-detail',
        meta: { title: '版本详情' },
        component: () => import('@/features/updates/pages/UpdateDetailPage.vue'),
      },
      {
        path: 'resumes',
        name: 'resumes',
        meta: { ...authed, title: '我的简历' },
        component: () => import('@/features/resume/pages/ResumeListPage.vue'),
      },
      {
        path: 'resumes/versions/:versionId',
        name: 'resume-version',
        meta: { ...authed, title: '简历版本' },
        component: () => import('@/features/resume/pages/ResumeVersionRedirect.vue'),
      },
      {
        path: 'resumes/:id/manual',
        name: 'resume-editor-manual',
        meta: { ...authed, title: '版本与导出' },
        component: () => import('@/features/resume/pages/ResumeEditorPage.vue'),
      },
      {
        path: 'resumes/:id',
        name: 'resume-editor',
        meta: authed,
        component: () => import('@/features/ai-resume/pages/AiResumeResumeRedirectPage.vue'),
      },
      {
        path: 'job-match',
        name: 'job-match-home',
        meta: { ...authed, title: '岗位匹配' },
        component: () => import('@/features/job-match/pages/JobMatchHomePage.vue'),
      },
      {
        path: 'job-match/new',
        name: 'job-match-new',
        meta: { ...authed, title: '新建匹配' },
        component: () => import('@/features/job-match/pages/JobMatchCreatePage.vue'),
      },
      {
        path: 'job-match/history',
        name: 'job-match-history',
        meta: { ...authed, title: '匹配历史' },
        component: () => import('@/features/job-match/pages/JobMatchHistoryPage.vue'),
      },
      {
        path: 'job-match/:id/analyzing',
        name: 'job-match-analyzing',
        meta: { ...authed, title: '分析中' },
        component: () => import('@/features/job-match/pages/JobMatchAnalyzingPage.vue'),
      },
      {
        path: 'job-match/:id/clarifications',
        name: 'job-match-clarifications',
        meta: { ...authed, title: '补充确认' },
        component: () => import('@/features/job-match/pages/JobMatchClarificationsPage.vue'),
      },
      {
        path: 'job-match/:id/report',
        name: 'job-match-report',
        meta: { ...authed, title: '匹配报告' },
        component: () => import('@/features/job-match/pages/JobMatchReportPage.vue'),
      },
      {
        path: 'job-match/:id/similar-jobs',
        name: 'job-match-similar',
        meta: { ...authed, title: '相似方向' },
        component: () => import('@/features/job-match/pages/JobMatchSimilarJobsPage.vue'),
      },
      {
        path: 'career-planning',
        name: 'career-planning',
        meta: { ...authed, title: '职业规划' },
        component: () => import('@/features/career-planning/pages/CareerCanvasOverviewPage.vue'),
      },
      {
        path: 'career-planning/new',
        name: 'career-planning-new',
        meta: { ...authed, title: '新建能力画布', wide: true },
        component: () => import('@/features/career-planning/pages/CareerPlanningPage.vue'),
      },
      {
        path: 'career-planning/:sessionId',
        name: 'career-planning-session',
        meta: { ...authed, title: '能力画布', wide: true },
        component: () => import('@/features/career-planning/pages/CareerPlanningPage.vue'),
      },
      {
        path: 'mock-interviews',
        name: 'mock-interviews',
        meta: { ...authed, title: '模拟面试' },
        component: () => import('@/features/mock-interview/pages/MockInterviewHomePage.vue'),
      },
      {
        path: 'mock-interviews/new',
        name: 'mock-interview-create',
        meta: { ...authed, title: '新建面试' },
        component: () => import('@/features/mock-interview/pages/MockInterviewCreatePage.vue'),
      },
      {
        path: 'mock-interviews/:sessionId/session',
        name: 'mock-interview-session',
        meta: { ...authed, title: '面试中' },
        component: () => import('@/features/mock-interview/pages/MockInterviewSessionPage.vue'),
      },
      {
        path: 'mock-interviews/:sessionId/report',
        name: 'mock-interview-report',
        meta: { ...authed, title: '面试报告' },
        component: () => import('@/features/mock-interview/pages/MockInterviewReportPage.vue'),
      },
      {
        path: 'notifications',
        name: 'notifications',
        meta: { ...authed, title: '通知' },
        component: () => import('@/features/notification/pages/NotificationListPage.vue'),
      },
      {
        path: 'account',
        meta: authed,
        component: () => import('@/features/identity/pages/AccountSettingsLayoutPage.vue'),
        children: [
          { path: '', name: 'account', meta: { title: '账号与安全' }, component: () => import('@/features/identity/pages/AccountPage.vue') },
          { path: 'appearance', name: 'account-appearance', meta: { title: '外观' }, component: () => import('@/features/identity/pages/AppearancePage.vue') },
          { path: 'ai', name: 'account-ai', meta: { title: 'AI 用量' }, component: () => import('@/features/ai-resume/pages/AiResumeUsagePage.vue') },
          { path: 'data-rights', name: 'account-data-rights', meta: { title: '数据与隐私' }, component: () => import('@/features/datarights/pages/DataRightsPage.vue') },
        ],
      },
      {
        path: 'admin/resume-templates',
        name: 'template-admin',
        meta: { ...authed, adminOnly: true, title: '模板运营' },
        component: () => import('@/features/template-admin/pages/TemplateAdminPage.vue'),
      },
      {
        path: 'admin/ai-channels',
        name: 'admin-ai-channels',
        meta: { ...authed, adminOnly: true, title: 'AI 通道' },
        component: () => import('@/features/ai/pages/AiSystemChannelsPage.vue'),
      },
      {
        path: 'admin/changelog',
        name: 'admin-changelog',
        meta: { ...authed, adminOnly: true, title: '更新发布' },
        component: () => import('@/features/updates/pages/AdminChangelogListPage.vue'),
      },
      {
        path: 'admin/changelog/new',
        name: 'admin-changelog-new',
        meta: { ...authed, adminOnly: true, title: '新建版本' },
        component: () => import('@/features/updates/pages/AdminChangelogEditorPage.vue'),
      },
      {
        path: 'admin/changelog/:id/edit',
        name: 'admin-changelog-edit',
        meta: { ...authed, adminOnly: true, title: '编辑版本' },
        component: () => import('@/features/updates/pages/AdminChangelogEditorPage.vue'),
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        meta: { title: '页面不存在' },
        component: () => import('@/app/pages/NotFoundPage.vue'),
      },
    ],
  },
]
