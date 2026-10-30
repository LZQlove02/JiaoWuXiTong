import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'

export default [
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'android/**',
      'preview/**',
      '.screenshots/**',
      '*.html'
    ]
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.{js,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        window: 'readonly',
        document: 'readonly',
        localStorage: 'readonly',
        fetch: 'readonly',
        navigator: 'readonly',
        DOMParser: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        console: 'readonly',
        URL: 'readonly',
        process: 'readonly'
      }
    },
    rules: {
      // 现有代码里有大量 catch (error) 仅用于忽略异常，先保持可运行，逐步收敛
      'no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }
      ],
      'no-empty': ['warn', { allowEmptyCatch: true }],
      'vue/multi-word-component-names': 'off',
      // TabBar 等使用内联静态 SVG，属于受控内容
      'vue/no-v-html': 'off',
      'vue/require-default-prop': 'off'
    }
  }
]
