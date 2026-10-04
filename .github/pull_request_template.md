## O que muda

<!-- Uma ou duas frases: o que o PR faz e por quê. Se corrige um defeito, descreva o sintoma. -->

## Regras de negócio tocadas

<!-- Liste os RN-xx afetados, ou escreva "nenhuma". Toda regra tocada precisa de teste citando
     o identificador no nome, como manda a seção 11 da especificação. -->

## Como verificar

<!-- O caminho para alguém reproduzir, não só "rodei os testes". -->

- [ ] `./mvnw verify`
- [ ] `npm run lint --prefix frontend` e `npm run typecheck --prefix frontend`
- [ ] `npm test --prefix e2e`

## Decisões em aberto

<!-- Se assumiu algo que a especificação não define, registre aqui e deixe o comentário
     `// DECISÃO:` no código. Se nada ficou em aberto, escreva "nenhuma". -->
