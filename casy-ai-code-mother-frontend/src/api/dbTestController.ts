// @ts-ignore
/* eslint-disable */
import request from '@/axios/request'

/** 测试PostgreSQL连接 GET /test-postgres */
export async function testConnection(options?: { [key: string]: any }) {
  return request<string>('/test-postgres', {
    method: 'GET',
    ...(options || {}),
  })
}
