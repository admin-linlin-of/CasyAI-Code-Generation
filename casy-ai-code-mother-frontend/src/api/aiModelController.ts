import request from '@/axios/request'

/** 查询全部模型（含停用） GET /aiModel/list */
export async function listAiModels(options?: { [key: string]: any }) {
  return request<API.BaseResponseListAiModel>('/aiModel/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 查询可用模型 GET /aiModel/enabled */
export async function listEnabledAiModels(options?: { [key: string]: any }) {
  return request<API.BaseResponseListAiModel>('/aiModel/enabled', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 新增模型 POST /aiModel/add */
export async function addAiModel(body: API.AiModelAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/aiModel/add', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 更新模型 POST /aiModel/update */
export async function updateAiModel(body: API.AiModelUpdateRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/aiModel/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 删除模型 POST /aiModel/delete */
export async function deleteAiModel(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/aiModel/delete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}

/** 启用或停用 POST /aiModel/enabled */
export async function updateAiModelEnabled(
  body: API.AiModelEnabledUpdateRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/aiModel/enabled', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
    ...(options || {}),
  })
}
