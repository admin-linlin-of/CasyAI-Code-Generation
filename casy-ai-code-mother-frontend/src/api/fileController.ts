// @ts-ignore
/* eslint-disable */
import request from '@/axios/request'

/** 上传图片到对象存储 POST /file/upload */
export async function uploadImage(file: File, options?: { [key: string]: any }) {
  // multipart：字段名必须与后端 @RequestParam("file") 一致
  const formData = new FormData()
  formData.append('file', file)
  return request<API.BaseResponseString>('/file/upload', {
    method: 'POST',
    // 勿手动设 Content-Type，交给浏览器带 boundary
    data: formData,
    ...(options || {}),
  })
}
