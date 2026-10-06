import { createApp } from 'vue'
import App from './App.vue'
import router from './app/router'
import { safeNextPath } from './features/identity/safeNext'
import { bindSessionExpiry } from './features/identity/session'
import './style.css'

bindSessionExpiry(() => {
  const next = safeNextPath(router.currentRoute.value.fullPath)
  void router.replace({
    name: 'home',
    query: next === '/career-library'
      ? { auth: 'login', reason: 'session_expired' }
      : { auth: 'login', reason: 'session_expired', next },
  })
})

createApp(App).use(router).mount('#app')
