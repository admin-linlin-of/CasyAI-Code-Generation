// @ts-ignore
/* eslint-disable */
import request from '@/axios/request'

/** 此处后端没有提供注释 GET /test-postgres */
export async function testConnection(options?: { [key: string]: any }) {
  return request<string>('/test-postgres', {
    method: 'GET',
    ...(options || {}),
  })
}
