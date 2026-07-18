export default {
  requestLibPath: "import request from '@/axios/request'",
  schemaPath: 'http://localhost:8124/api/v3/api-docs',
  serversPath: './src',
  hook: {
    customType(schemaObject: { type?: string; format?: string } | undefined) {
      if (schemaObject?.format === 'int64') {
        return 'string'
      }
      return undefined
    },
  },
}
