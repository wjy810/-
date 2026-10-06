import { createRouter, createWebHistory } from 'vue-router'
import {
  buildAuthLandingQuery,
  buildLoginLandingQuery,
  buildProtectedLoginLandingLocation,
} from '@/features/identity/authLanding'
import { hydrateSession, session } from '@/features/identity/session'
import { resumeLandingPath } from '@/features/resume/utils/resumeLanding'

function signedInHome() {
  return session.account.value?.role === 'ADMIN'
    ? { name: 'template-admin' }
    : { name: 'career-library' }
}

async function resolveResumeHome() {
  if (session.account.value?.role === 'ADMIN') return { name: 'template-admin' }
  try {
    const { listResumes } = await import('@/features/resume/services/resumeApi')
    return { path: resumeLandingPath(await listResumes()), replace: true }
  } catch {
    return { name: 'resumes', replace: true }
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/features/landing/pages/LandingPage.vue'),
    },
    {
      path: '/login',
      name: 'login',
      redirect: (to) => session.signedIn.value
        ? signedInHome()
        : { name: 'home', query: buildLoginLandingQuery(to.query) },
    },
    {
      path: '/register',
      name: 'register',
      redirect: (to) => session.signedIn.value
        ? signedInHome()
        : { name: 'home', query: buildAuthLandingQuery('register', to.query) },
    },
    {
      path: '/reset',
      name: 'reset',
      redirect: (to) => session.signedIn.value
        ? signedInHome()
        : { name: 'home', query: buildAuthLandingQuery('reset', to.query) },
    },
    {
      path: '/terms',
      name: 'terms',
      component: () => import('@/features/identity/pages/PublicPolicyPage.vue'),
      props: { kind: 'terms' },
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('@/features/identity/pages/PublicPolicyPage.vue'),
      props: { kind: 'privacy' },
    },
    {
      path: '/onboarding',
      name: 'onboarding',
      meta: { requiresAuth: true },
      component: () => import('@/features/identity/pages/OnboardingPage.vue'),
    },
    {
      path: '/workspace',
      redirect: { name: 'career-library' },
    },
    {
      path: '/resume-home',
      name: 'resume-home',
      meta: { requiresAuth: true },
      beforeEnter: resolveResumeHome,
      component: () => import('@/features/resume/pages/ResumeHomeRedirectPage.vue'),
    },
    {
      path: '/career-library',
      name: 'career-library',
      meta: { requiresAuth: true },
      component: () => import('@/features/career-library/pages/CareerLibraryPage.vue'),
    },
    {
      path: '/resume-templates',
      name: 'resume-templates',
      component: () => import('@/features/resume/pages/ResumeTemplateCatalogPage.vue'),
    },
    {
      path: '/updates',
      name: 'updates',
      component: () => import('@/features/updates/pages/UpdateListPage.vue'),
    },
    {
      path: '/updates/:version',
      name: 'update-detail',
      component: () => import('@/features/updates/pages/UpdateDetailPage.vue'),
    },
    {
      path: '/template-assets/:catalogId',
      name: 'template-asset-detail',
      component: () => import('@/features/resume/pages/ResumeTemplateAssetDetailPage.vue'),
    },
    {
      path: '/resume-templates/:templateId',
      name: 'resume-template-detail',
      meta: { requiresAuth: true },
      component: () => import('@/features/resume/pages/ResumeTemplateDetailPage.vue'),
    },
    {
      path: '/admin/resume-templates',
      name: 'template-admin',
      meta: { requiresAuth: true, adminOnly: true },
      component: () => import('@/features/template-admin/pages/TemplateAdminPage.vue'),
    },
    {
      path: '/admin/ai-channels',
      name: 'admin-ai-channels',
      meta: { requiresAuth: true, adminOnly: true },
      component: () => import('@/features/ai/pages/AiSystemChannelsPage.vue'),
    },
    {
      path: '/admin/changelog',
      name: 'admin-changelog',
      meta: { requiresAuth: true, adminOnly: true },
      component: () => import('@/features/updates/pages/AdminChangelogListPage.vue'),
    },
    {
      path: '/admin/changelog/new',
      name: 'admin-changelog-new',
      meta: { requiresAuth: true, adminOnly: true },
      component: () => import('@/features/updates/pages/AdminChangelogEditorPage.vue'),
    },
    {
      path: '/admin/changelog/:id/edit',
      name: 'admin-changelog-edit',
      meta: { requiresAuth: true, adminOnly: true },
      component: () => import('@/features/updates/pages/AdminChangelogEditorPage.vue'),
    },
    {
      path: '/resumes',
      name: 'resumes',
      meta: { requiresAuth: true },
      component: () => import('@/features/resume/pages/ResumeListPage.vue'),
    },
    {
      path: '/mock-interviews',
      name: 'mock-interviews',
      meta: { requiresAuth: true },
      component: () => import('@/features/mock-interview/pages/MockInterviewHomePage.vue'),
    },
    {
      path: '/job-match',
      name: 'job-match-home',
      meta: { requiresAuth: true },
      component: () => import('@/features/job-match/pages/JobMatchHomePage.vue'),
    },
    {
      path: '/job-match/new',
      name: 'job-match-new',
      meta: { requiresAuth: true },
      component: () => import('@/features/job-match/pages/JobMatchCreatePage.vue'),
    },
    {
      path: '/job-match/:id/analyzing',
      name: 'job-match-analyzing',
      meta: { requiresAuth: true },
      component: () => import('@/features/job-match/pages/JobMatchAnalyzingPage.vue'),
    },
    {
      path: '/job-match/:id/clarifications',
      name: 'job-match-clarifications',
      meta: { requiresAuth: true },
      component: () => import('@/features/job-match/pages/JobMatchClarificationsPage.vue'),
    },
    {
      path: '/job-match/:id/report',
      name: 'job-match-report',
      meta: { requiresAuth: true },
      component: () => import('@/features/job-match/pages/JobMatchReportPage.vue'),
    },
    {
      path: '/job-match/:id/similar-jobs',
      name: 'job-match-similar',
      meta: { requiresAuth: true },
      component: () => import('@/features/job-match/pages/JobMatchSimilarJobsPage.vue'),
    },
    {
      path: '/job-match/history',
      name: 'job-match-history',
      meta: { requiresAuth: true },
      component: () => import('@/features/job-match/pages/JobMatchHistoryPage.vue'),
    },
    {
      path: '/career-planning',
      name: 'career-planning',
      meta: { requiresAuth: true },
      component: () => import('@/features/career-planning/pages/CareerCanvasOverviewPage.vue'),
    },
    {
      path: '/career-planning/new',
      name: 'career-planning-new',
      meta: { requiresAuth: true },
      component: () => import('@/features/career-planning/pages/CareerPlanningPage.vue'),
    },
    {
      path: '/career-planning/:sessionId',
      name: 'career-planning-session',
      meta: { requiresAuth: true },
      component: () => import('@/features/career-planning/pages/CareerPlanningPage.vue'),
    },
    {
      path: '/mock-interviews/new',
      name: 'mock-interview-create',
      meta: { requiresAuth: true },
      component: () => import('@/features/mock-interview/pages/MockInterviewCreatePage.vue'),
    },
    {
      path: '/mock-interviews/:sessionId/session',
      name: 'mock-interview-session',
      meta: { requiresAuth: true },
      component: () => import('@/features/mock-interview/pages/MockInterviewSessionPage.vue'),
    },
    {
      path: '/mock-interviews/:sessionId/report',
      name: 'mock-interview-report',
      meta: { requiresAuth: true },
      component: () => import('@/features/mock-interview/pages/MockInterviewReportPage.vue'),
    },
    {
      path: '/ai-resume/new',
      name: 'ai-resume-new',
      meta: { requiresAuth: true },
      component: () => import('@/features/ai-resume/pages/AiResumeNewPage.vue'),
    },
    {
      path: '/ai-resume/:conversationId',
      name: 'ai-resume-workbench',
      meta: { requiresAuth: true },
      component: () => import('@/features/ai-resume/pages/AiResumeWorkbenchPage.vue'),
    },
    {
      path: '/resumes/versions/:versionId',
      name: 'resume-version',
      meta: { requiresAuth: true },
      component: () => import('@/features/resume/pages/ResumeVersionRedirect.vue'),
    },
    {
      path: '/resumes/:id/manual',
      name: 'resume-editor-manual',
      meta: { requiresAuth: true },
      component: () => import('@/features/resume/pages/ResumeEditorPage.vue'),
    },
    {
      path: '/resumes/:id',
      name: 'resume-editor',
      meta: { requiresAuth: true },
      component: () => import('@/features/ai-resume/pages/AiResumeResumeRedirectPage.vue'),
    },
    {
      path: '/notifications',
      name: 'notifications',
      meta: { requiresAuth: true },
      component: () => import('@/features/notification/pages/NotificationListPage.vue'),
    },
    {
      path: '/account',
      meta: { requiresAuth: true },
      component: () => import('@/features/identity/pages/AccountSettingsLayoutPage.vue'),
      children: [
        {
          path: '',
          name: 'account',
          component: () => import('@/features/identity/pages/AccountPage.vue'),
        },
        {
          path: 'ai',
          name: 'account-ai',
          component: () => import('@/features/ai-resume/pages/AiResumeUsagePage.vue'),
        },
        {
          path: 'data-rights',
          name: 'account-data-rights',
          component: () => import('@/features/datarights/pages/DataRightsPage.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
  scrollBehavior(to) {
    if (to.name === 'resume-editor-manual' && to.hash === '#export') {
      // The export section is rendered only after the resume request completes.
      return { top: 0 }
    }
    if (to.hash) {
      return { el: to.hash, top: 72 }
    }
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  if (!session.ready.value) {
    await hydrateSession()
  }
  if (to.meta.requiresAuth && !session.signedIn.value) {
    return buildProtectedLoginLandingLocation(
      to.fullPath,
      typeof to.query.reason === 'string' ? to.query.reason : 'unauthenticated',
    )
  }
  if (to.meta.guestOnly && session.signedIn.value) {
    return signedInHome()
  }
  if (to.meta.adminOnly && session.account.value?.role !== 'ADMIN') {
    return signedInHome()
  }
  return true
})

type MotionViewTransition = {
  updateCallbackDone: Promise<void>
  finished: Promise<void>
}

type MotionDocument = Document & {
  startViewTransition?: (update: () => Promise<unknown>) => MotionViewTransition
}

let navigationTransitionActive = false

function withNavigationTransition<T>(navigate: () => Promise<T>): Promise<T> {
  if (
    typeof document === 'undefined'
    || navigationTransitionActive
    || window.matchMedia('(prefers-reduced-motion: reduce)').matches
  ) {
    return navigate()
  }

  const motionDocument = document as MotionDocument
  if (!motionDocument.startViewTransition) return navigate()

  let navigation: Promise<T> | undefined
  try {
    navigationTransitionActive = true
    const transition = motionDocument.startViewTransition(() => {
      navigation = navigate()
      return navigation.then(() => undefined)
    })
    void transition.finished.catch(() => undefined).finally(() => {
      navigationTransitionActive = false
    })
    return transition.updateCallbackDone.then(() => navigation as Promise<T>)
  } catch {
    navigationTransitionActive = false
    return navigate()
  }
}

const routerPush = router.push.bind(router)
const routerReplace = router.replace.bind(router)
router.push = ((to) => withNavigationTransition(() => routerPush(to))) as typeof router.push
router.replace = ((to) => withNavigationTransition(() => routerReplace(to))) as typeof router.replace

export default router
