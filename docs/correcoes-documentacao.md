# Pendências da documentação

As correções do corpo do texto (seção 6 restaurada, referências aos diagramas, Apêndice A) já
estão aplicadas em `Documentacao-Completa-com-Diagramas.docx`. Ficam a critério da equipe:

## Para preencher

- **Folha de rosto:** os campos "Área de concentração" e "Orientador(a)" estão em branco.

## Divergências com o código, não corrigidas

- **Seções 11.1, 14.1 e 5.2** afirmam branch `main` protegida, pipeline executado a cada envio e
  métricas sobre Pull Requests. O repositório tem uma única branch, e o pipeline nunca rodou: o
  `ci.yml` dispara em `main`, que não existe.
- **Seção 14.2:** "a implantação é acionada de forma manual" — Render e Vercel publicam
  automaticamente a cada envio.
- **RN-11:** "extensão mínima definida em configuração" — o mínimo de 8 caracteres é fixo no
  código (`RegistrarRequest`).
- **Seções 2.1 e 9.1** citam participantes esclarecendo dúvidas com a IA. Essa funcionalidade não
  existe: a IA atende apenas o cadastro de evento e o resumo de avaliações.
