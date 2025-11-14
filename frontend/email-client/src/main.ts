import App from './App.svelte'

type EmailMessage = {
  id: string
  contextId: string | null
  senderName: string | null
  senderEmail: string | null
  recipientName: string | null
  recipientEmail: string | null
  subject: string
  emailBodyRaw: string
  emailBodyTransformedText: string
  emailBodyTransformedMarkdown: string | null
  emailBodyHtml: string | null
  llmSummary: string | null
  receivedTimestampIso: string | null
  receivedTimestampDisplay: string | null
  labels: string[]
  companyLogoUrl: string | null
  avatarUrl: string | null
  starred: boolean
  read: boolean
  preview: string
  contextForAi: string | null
}

type AiFunctionVariantSummary = {
  key: string
  label: string
  defaultInstruction: string | null
  defaultArgs: Record<string, string>
}

type AiFunctionSummary = {
  key: string
  label: string
  description: string
  category: string
  defaultInstruction: string
  subjectRequired: boolean
  contextRequired: boolean
  outputFormat: string
  primary: boolean
  scopes: string[]
  defaultArgs: Record<string, string>
  variants: AiFunctionVariantSummary[]
}

type AiFunctionCatalogDto = {
  categories: Array<{
    category: string
    label: string
    functionKeys: string[]
  }>
  functionsByKey: Record<string, AiFunctionSummary>
  primaryCommands: string[]
}

export type EmailClientBootstrap = {
  uiNonce: string | null
  messages: EmailMessage[]
  folderCounts: Record<string, number>
  effectiveFolders: Record<string, string>
  aiFunctions: AiFunctionCatalogDto | null
}

declare global {
  interface Window {
    __EMAIL_CLIENT_BOOTSTRAP__?: EmailClientBootstrap
  }
}

const defaultBootstrap: EmailClientBootstrap = {
  uiNonce: null,
  messages: [],
  folderCounts: {},
  effectiveFolders: {},
  aiFunctions: null
}

const bootstrap = window.__EMAIL_CLIENT_BOOTSTRAP__ ?? defaultBootstrap

const target = document.getElementById('email-client-root')

if (!target) {
  throw new Error('Email client root element is missing from the DOM')
}

const app = new App({
  target,
  props: { bootstrap }
})

export default app

export type {
  EmailMessage,
  AiFunctionCatalogDto,
  AiFunctionSummary,
  AiFunctionVariantSummary
}
