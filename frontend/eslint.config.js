import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

/**
 * Lint do frontend.
 *
 * As regras com sufixo `TypeChecked` usam o type checker, não só a árvore sintática — é o que
 * pega promessa não aguardada e `await` em valor que não é promessa, justamente os erros que
 * o `tsc` sozinho deixa passar.
 *
 * `typescript-eslint` ainda não suporta a API do TypeScript 7 (issue 10940 do projeto), por
 * isso o `package.json` fixa o TypeScript em 6.x.
 */
export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**'] },

  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommendedTypeChecked,
      tseslint.configs.stylisticTypeChecked,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,

      // O Vite só consegue atualizar o módulo em memória se o arquivo exportar apenas
      // componentes; exportar outra coisa junto derruba o estado a cada salvamento.
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      // Variável só para descartar um valor é legítima quando prefixada com _.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],

      // Desligada de propósito, e só esta: a regra proíbe chamar de dentro de um efeito
      // qualquer função cujo corpo contenha setState, mesmo quando o setState só acontece
      // depois do await — ela não consegue provar a fronteira assíncrona. Na prática isso
      // veta o padrão "buscar dados no efeito e guardar o resultado no estado", que é o que
      // a própria documentação do React indica para quem não usa biblioteca de data
      // fetching. Este projeto não usa nenhuma, por decisão: a especificação pede que não se
      // antecipe requisito nem se acrescente camada de abstração extra. Se um dia entrar um
      // TanStack Query da vida, esta linha sai junto.
      'react-hooks/set-state-in-effect': 'off',
    },
  },

  // Arquivos de configuração rodam no Node e ficam fora do tsconfig da aplicação.
  {
    files: ['*.config.{js,ts}'],
    languageOptions: { globals: globals.node },
    extends: [tseslint.configs.disableTypeChecked],
  },
)
