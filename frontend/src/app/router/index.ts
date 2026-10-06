import { createRouter, createWebHistory } from 'vue-router'
import { buildProtectedLoginLandingLocation } from '@/features/identity/authLanding'
import { useSessionStore } from '@/stores/session'
import { routes, signedInHome } from './routes'

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.name === 'resume-editor-manual' && to.hash === '#export') {
      // The export section is rendered only after the resume request completes.
      return { top: 0 }
    }
    if (to.hash) return { el: to.hash, top: 72, behavior: 'smooth' }
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const session = useSessionStore()
  if (!session.ready) {
    await session.hydrate()
  }
  if (to.meta.requiresAuth && !session.signedIn) {
    return buildProtectedLoginLandingLocation(to.fullPath, typeof to.query.reason === 'string' ? to.query.reason : 'unauthenticated')
  }
  if (to.meta.guestOnly && session.signedIn) {
    return signedInHome()
  }
  if (to.meta.adminOnly && !session.isAdmin) {
    return signedInHome()
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title && to.name !== 'home' ? `${title} · JobProof AI` : 'JobProof AI · 求职证据官'
})

// Smooth page cross-fades via the View Transitions API (falls back silently).
type MotionViewTransition = { updateCallbackDone: Promise<void>; finished: Promise<void> }
type MotionDocument = Document & { startViewTransition?: (update: () => Promise<unknown>) => MotionViewTransition }

let transitionActive = false

function withNavigationTransition<T>(navigate: () => Promise<T>): Promise<T> {
  const doc = typeof document === 'undefined' ? null : (document as MotionDocument)
  if (
    !doc?.startViewTransition
    || transitionActive
    || document.documentElement.dataset.motion === 'reduce'
    || window.matchMedia('(prefers-reduced-motion: reduce)').matches
  ) {
    return navigate()
  }
  let navigation: Promise<T> | undefined
  try {
    transitionActive = true
    const transition = doc.startViewTransition(() => {
      navigation = navigate()
      return navigation.then(() => undefined)
    })
    void transition.finished.catch(() => undefined).finally(() => {
      transitionActive = false
    })
    return transition.updateCallbackDone.then(() => navigation as Promise<T>)
  } catch {
    transitionActive = false
    return navigate()
  }
}

const routerPush = router.push.bind(router)
const routerReplace = router.replace.bind(router)
router.push = (to => withNavigationTransition(() => routerPush(to))) as typeof router.push
router.replace = (to => withNavigationTransition(() => routerReplace(to))) as typeof router.replace

export default router
