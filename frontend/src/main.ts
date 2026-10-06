import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './app/App.vue'
import router from './app/router'
import { queryClient } from './app/queryClient'
import { safeNextPath } from './features/identity/safeNext'
import { setUnauthenticatedHandler } from './shared/api/client'
import { useSessionStore } from './stores/session'
import { usePreferencesStore } from './stores/preferences'
import './design/index.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(VueQueryPlugin, { queryClient })
app.use(router)

// Apply theme/motion preferences immediately (index.html already set the first-paint theme).
usePreferencesStore(pinia)

// Any 401 means the server session ended: drop identity, keep the user's place, ask them to sign in again.
const session = useSessionStore(pinia)
setUnauthenticatedHandler(() => {
  if (!session.account) return
  session.clear()
  const next = safeNextPath(router.currentRoute.value.fullPath)
  void router.replace({
    name: 'home',
    query: next === '/dashboard' ? { auth: 'login', reason: 'session_expired' } : { auth: 'login', reason: 'session_expired', next },
  })
})

app.mount('#app')
