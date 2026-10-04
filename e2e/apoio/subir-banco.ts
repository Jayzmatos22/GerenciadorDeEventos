import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const RAIZ_DO_PROJETO = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')

/**
 * Garante o PostgreSQL de pé antes de a pilha subir. O `--wait` espera o healthcheck do
 * `docker-compose.yml`, então o backend não tenta conectar num banco que ainda está iniciando.
 *
 * Em ambiente que já fornece o banco (um serviço do CI, por exemplo), defina
 * `E2E_SEM_DOCKER=1` e esta etapa sai do caminho.
 */
export default function subirBanco(): void {
  if (process.env.E2E_SEM_DOCKER === '1') {
    console.log('[e2e] E2E_SEM_DOCKER=1: assumindo um PostgreSQL já disponível.')
    return
  }

  console.log('[e2e] subindo o PostgreSQL com docker compose…')
  execFileSync('docker', ['compose', 'up', '-d', '--wait'], {
    cwd: RAIZ_DO_PROJETO,
    stdio: 'inherit',
  })
}
