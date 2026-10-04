import { defineConfig, devices } from '@playwright/test'

// Node, navegador e backend precisam concordar no fuso, senão as datas que o teste calcula
// são interpretadas de outro jeito pela página e o cenário deixa de fazer sentido. O sistema
// opera em horário de Brasília (seção 4 da especificação).
const FUSO = 'America/Sao_Paulo'
process.env.TZ = FUSO

const URL_FRONTEND = process.env.E2E_URL_FRONTEND ?? 'http://localhost:5173'
const URL_BACKEND = process.env.E2E_URL_BACKEND ?? 'http://localhost:8080'

/** No CI nada é reaproveitado: cada execução sobe a pilha do zero. */
const noCi = Boolean(process.env.CI)

export default defineConfig({
  testDir: './testes',

  // Os testes semeiam os próprios dados com e-mails únicos, então rodam em paralelo sem
  // colidir. A exceção é a fila de espera, que serializa dentro do próprio arquivo.
  fullyParallel: true,
  forbidOnly: noCi,
  retries: noCi ? 1 : 0,
  workers: noCi ? 2 : undefined,
  reporter: noCi ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL: URL_FRONTEND,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'pt-BR',
    timezoneId: FUSO,
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer: [
    {
      // IA_HABILITADA=false de propósito: o E2E cobre o caminho de indisponibilidade da RN-10,
      // que é o único lado da IA verificável sem um modelo no ar.
      // O script sobe o banco antes do backend: o Playwright inicia o webServer antes do
      // globalSetup, então deixar o banco por conta dele deixava o backend sem PostgreSQL.
      command: './e2e/apoio/subir-backend.sh',
      cwd: '..',
      url: `${URL_BACKEND}/actuator/health`,
      reuseExistingServer: !noCi,
      timeout: 240_000,
      stdout: 'ignore',
      stderr: 'pipe',
      env: {
        TZ: FUSO,
        JWT_SECRET: 'segredo-de-e2e-hs256-com-no-minimo-32-bytes-para-o-hmac',
        IA_HABILITADA: 'false',
        UPLOAD_DIR: 'target/uploads-e2e',
      },
    },
    {
      command: 'npm run dev',
      cwd: '../frontend',
      url: URL_FRONTEND,
      reuseExistingServer: !noCi,
      timeout: 120_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
})
