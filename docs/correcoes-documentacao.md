# Correções a aplicar no arquivo-fonte da documentação

O PDF `Documentacao-Completa-com-Diagramas.pdf` já está com o Apêndice A novo (páginas 42 a 52).
Os ajustes abaixo são do **corpo do texto** e precisam ser feitos no arquivo de onde o PDF foi
exportado (Word ou Google Docs), porque um PDF não deixa reescrever parágrafo. Depois de
corrigir, exporte de novo e acrescente ao final as páginas de `apendice-a-diagramas.pdf`.

Os números de página citados são os do PDF atual.

---

## 1. Restaurar o trecho perdido da seção 6 — páginas 14 e 15 (urgente)

Na versão atual, a página 14 termina em *"a ordenação na fila de espera e eventuais."* e fica em
branco. Sumiram o fim desse parágrafo, mais dois parágrafos, o título 6.2, o parágrafo de abertura
e as seis linhas da tabela — na página 15 sobrou só o cabeçalho e o fragmento *"dependências com os
serviços externos"*.

**Apague** desde *"eventuais."* até o fim do parágrafo que começa com *"Os dois diagramas de
interação empregam barras de ativação"* (inclusive a tabela quebrada) e **cole no lugar**:

> eventuais solicitações de cancelamento, alternando esses estados a partir do enumerado
> StatusInscricao, cujos valores efetivamente gravados são CONFIRMADA, EM_ESPERA e CANCELADA. O
> valor AUSENTE existe na enumeração, mas nenhum fluxo da versão atual o grava: a ausência
> corresponde, hoje, à falta de registro de presença.
>
> A etapa de validação da participação e o fechamento do evento contam com as entidades Presenca,
> Certificado, Avaliacao e ResumoAvaliacao, todas vinculadas diretamente à Inscricao ou ao Evento.
> A entidade Presenca registra o check-in efetuado pelo organizador durante a realização do evento.
> Com base nesse registro, a entidade Certificado viabiliza a emissão do documento digital,
> atrelando a ele um código único de autenticidade para fins de verificação pública. Essa
> dependência torna explícita, no próprio modelo, a regra de que apenas participantes com presença
> registrada podem receber certificado.
>
> Após o encerramento das atividades, os participantes enviam notas e pareceres registrados na
> entidade Avaliacao, cujos dados são posteriormente processados pela inteligência artificial para
> sintetizar os feedbacks consolidados na entidade ResumoAvaliacao. Por fim, a persistência do fluxo
> de cadastro inteligente é sustentada pela entidade InteracaoIA, que armazena o texto em linguagem
> natural submetido pelo usuário, o JSON estruturado devolvido pela saída estruturada do modelo e a
> data da interação, mantendo o histórico necessário para auditoria e para a reconstituição do
> cadastro em caso de falha.

**Título** (estilo Título 2): **6.2 Diagramas de Modelagem**

> A modelagem do sistema foi documentada por meio de seis tipos de diagrama da Linguagem de
> Modelagem Unificada, distribuídos em onze figuras reproduzidas no Apêndice A. Os diagramas mais
> extensos foram divididos para preservar a legibilidade, e todos foram conferidos contra o
> código-fonte: apenas atributos, operações e fluxos efetivamente implementados estão representados.

**Tabela** (3 colunas, mesmo estilo das demais):

| Diagrama | Perspectiva | O que documenta |
| --- | --- | --- |
| Casos de Uso (A.1a e A.1b) | Funcional | Atores e funcionalidades, separados entre visitante e participante, de um lado, e organizador, de outro, com as relações de inclusão e extensão |
| Classes (A.2a e A.2b) | Estrutural | Entidades com atributos, operações e multiplicidades: o núcleo de usuários, eventos e inscrições, e os registros de participação e de inteligência artificial |
| Estados (A.3a e A.3b) | Comportamental | Ciclos de vida da inscrição e do evento |
| Atividades (A.4) | Comportamental | Cadastro assistido por IA em raias de responsabilidade, com o desvio de contingência |
| Interação (A.5, A.6a e A.6b) | Comportamental | Cadastro por IA, emissão do certificado e download do PDF, com barras de ativação e fragmentos combinados |
| Componentes (A.7) | Arquitetural | Módulos do backend e suas dependências com os serviços externos |

> Os diagramas de interação empregam barras de ativação sobre as linhas de vida, delimitando o
> período em que cada objeto permanece em execução, e fragmentos combinados para representar
> repetição (loop) e alternativas (alt). A emissão e o download do certificado aparecem em
> diagramas distintos porque assim ocorrem no código: a emissão apenas confirma o registro de
> presença e grava o código de autenticidade (A.6a), enquanto o arquivo PDF é montado pela
> biblioteca iText somente quando o participante o solicita, em memória e sem gravação em disco
> (A.6b).

---

## 2. Apresentação do Apêndice A — página 39

**Troque** o parágrafo *"Reproduzem-se a seguir, em páginas próprias e em orientação paisagem, os
seis diagramas…"* por:

> Reproduzem-se a seguir, em páginas próprias e em orientação paisagem, os onze diagramas citados
> na seção 6.2: casos de uso (A.1a e A.1b), classes (A.2a e A.2b), estados (A.3a e A.3b),
> atividades (A.4), interação (A.5, A.6a e A.6b) e componentes (A.7). Cada página traz, abaixo da
> figura, uma legenda que relaciona o diagrama aos requisitos e regras correspondentes.

---

## 3. Tabela de problemas comuns — seção 16, página 37, linha "Certificado emitido sem presença registrada"

O PDF não é mais gerado na emissão, então a linha precisa acompanhar.

- **Causa provável:** Verificação de presença ausente ou posicionada após a gravação do certificado
- **Procedimento:** A consulta ao registro de presença deve anteceder a gravação do certificado,
  conforme o diagrama A.6a (RN-05). O arquivo PDF só é gerado no download (A.6b).

---

## 4. Referência ao diagrama de componentes — seção 2.1, página 7 (opcional)

*"…encontra-se no Diagrama de Componentes, apresentado no Apêndice A."* →
*"…encontra-se no Diagrama de Componentes, apresentado no Apêndice A.7."*

---

## 5. Folha de rosto — página 2

Os campos **"Área de concentração:"** e **"Orientador."** estão sem preenchimento.

---

## Pendências da auditoria anterior (não aplicadas)

Continuam divergindo do código, e ficam a critério da equipe:

- **Seções 11.1, 14.1 e 5.2** afirmam branch `main` protegida, pipeline executado a cada envio e
  métricas sobre Pull Requests. O repositório tem uma única branch e o pipeline nunca rodou: o
  `ci.yml` dispara em `main`, que não existe.
- **Seção 14.2:** "a implantação é acionada de forma manual" — Render e Vercel publicam
  automaticamente a cada envio.
- **RN-11:** "extensão mínima definida em configuração" — o mínimo de 8 caracteres é fixo no
  código (`RegistrarRequest`).
- **Seções 2.1 e 9.1** citam participantes esclarecendo dúvidas com a IA. Essa funcionalidade não
  existe: a IA atende apenas o cadastro de evento e o resumo de avaliações.
