/**
 * Estado que o React Router carrega entre rotas. Tipado para que a leitura de
 * `useLocation().state` não caia em `any` — ele é `unknown` por natureza, já que qualquer
 * navegação pode ter colocado qualquer coisa ali.
 */
export interface EstadoDeRota {
  /** Rota de origem, guardada quando o login interrompe uma navegação. */
  de?: string
}
