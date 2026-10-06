import type { FeatureKey } from '@/shared/api/capabilities'

export {}

declare module 'vue-router' {
  interface RouteMeta {
    /** Full-screen editor (FocusLayout): no app chrome, its own toolbars. */
    focus?: boolean
    requiresAuth?: boolean
    guestOnly?: boolean
    adminOnly?: boolean
    /** Shown in the topbar and document title. */
    title?: string
    /** Lets the page use the full content width. */
    wide?: boolean
    /** Deployment feature switch; when off the layout shows the "not available" page instead. */
    feature?: FeatureKey
  }
}
