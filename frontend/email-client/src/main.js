import App from './App.svelte'

const bootstrap = window.__EMAIL_CLIENT_BOOTSTRAP__ || {}

const app = new App({
  target: document.getElementById('email-client-root'),
  props: { bootstrap }
})

export default app
