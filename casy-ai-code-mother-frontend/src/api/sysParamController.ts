import request from '@/axios/request'

/** 公开参数（未登录可访问） GET /sysParam/public */
export async function listPublicSysParams(options?: { [key: string]: any }) {
  return request<API.BaseResponseMapStringString>('/sysParam/public', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 查询全部系统参数 GET /sysParam/list */
export async function listSysParams(options?: { [key: string]: any }) {
  return request<API.BaseResponseListSysParam>('/sysParam/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 新增系统参数 POST /sysParam/add */
export async function addSysParam(body: API.SysParamAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/sysParam/add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 更新系统参数 POST /sysParam/update */
export async function updateSysParam(body: API.SysParamUpdateRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/sysParam/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 删除系统参数 POST /sysParam/delete */
export async function deleteSysParam(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/sysParam/delete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}
