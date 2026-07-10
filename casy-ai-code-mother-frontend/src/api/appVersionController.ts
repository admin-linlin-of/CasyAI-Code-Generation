// @ts-ignore
/* eslint-disable */
import request from '@/axios/request'

/** 根据主键获取应用代码版本 GET /tAppVersion/getInfo/${param0} */
export async function getInfo1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getInfo1Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.AppVersion>(`/tAppVersion/getInfo/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 查询所有应用代码版本 GET /tAppVersion/list */
export async function list1(options?: { [key: string]: any }) {
  return request<API.AppVersion[]>('/tAppVersion/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 根据应用ID查询代码版本 GET /tAppVersion/list/${param0} */
export async function getAppVersionsByAppId(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppVersionsByAppIdParams,
  options?: { [key: string]: any }
) {
  const { appid: param0, ...queryParams } = params
  return request<API.AppVersion[]>(`/tAppVersion/list/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 管理员分页查询应用代码版本 POST /tAppVersion/list/page */
export async function listAppVersionByPage(
  body: API.AppVersionRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVersion>('/tAppVersion/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 分页查询应用代码版本 GET /tAppVersion/page */
export async function page1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.page1Params,
  options?: { [key: string]: any }
) {
  return request<API.PageAppVersion>('/tAppVersion/page', {
    method: 'GET',
    params: {
      ...params,
      page: undefined,
      ...params['page'],
    },
    ...(options || {}),
  })
}

/** 根据主键删除应用代码版本 DELETE /tAppVersion/remove/${param0} */
export async function remove1(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.remove1Params,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<boolean>(`/tAppVersion/remove/${param0}`, {
    method: 'DELETE',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 重新打包指定版本 POST /tAppVersion/retryBuild */
export async function retryBuild(
  body: API.AppVersionRetryBuildRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/tAppVersion/retryBuild', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 打包指定版本 POST /tAppVersion/build */
export async function buildVersion(
  body: API.AppVersionRetryBuildRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/tAppVersion/build', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 保存应用代码版本 POST /tAppVersion/save */
export async function save1(body: API.AppVersion, options?: { [key: string]: any }) {
  return request<boolean>('/tAppVersion/save', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 根据主键更新应用代码版本 PUT /tAppVersion/update */
export async function update1(body: API.AppVersion, options?: { [key: string]: any }) {
  return request<boolean>('/tAppVersion/update', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
