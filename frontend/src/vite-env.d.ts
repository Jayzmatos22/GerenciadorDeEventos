/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base da API. Vazio usa o proxy do Vite (ver vite.config.ts). */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
