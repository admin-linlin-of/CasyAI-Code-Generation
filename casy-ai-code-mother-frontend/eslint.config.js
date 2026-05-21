import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import ts from 'typescript-eslint'
import prettier from 'eslint-config-prettier'
import globals from 'globals'

export default ts.config(
  js.configs.recommended,
  ...ts.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: ts.parser,
      },
    },
  },
  {
    ignores: ['dist/**', 'node_modules/**', '.idea/**', '.vscode/**', 'public/**'],
  },
  prettier,
  // 防止ESLint 不知道 console 是浏览器内置的，当成未定义变量
  {
    languageOptions: {
      globals: {
        ...globals.browser,
      },
    },
  },
)
