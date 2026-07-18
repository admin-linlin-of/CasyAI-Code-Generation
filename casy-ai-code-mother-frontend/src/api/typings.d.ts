declare namespace API {
  type App = {
    id?: string
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    appTypes?: string[]
    deployKey?: string
    deployedTime?: string
    priority?: number
    isPublish?: number
    userId?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type AppAddRequest = {
    appName?: string
    initPrompt?: string
    codeGenType?: string
    appTypes?: string[]
    modelType?: string
  }

  type AppAdminUpdateRequest = {
    id?: string
    appName?: string
    cover?: string
    priority?: number
    appTypes?: string[]
    isPublish?: number
  }

  type AppCodeFileListVO = {
    files?: string[]
  }

  type AppDeployRequest = {
    appId?: string
    codeDir?: string
  }

  type AppQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    appTypes?: string[]
    deployKey?: string
    priority?: number
    userId?: string
    isPublish?: number
  }

  type AppUpdateRequest = {
    id?: string
    appName?: string
    appTypes?: string[]
    isPublish?: number
  }

  type AppVersion = {
    id?: string
    appId?: string
    chatHistoryId?: string
    versionNum?: number
    codeDir?: string
    modelType?: string
    buildStatus?: string
    buildError?: string
    deployStatus?: string
    userId?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type AppVersionRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string
    appId?: string
    chatHistoryId?: string
    versionNum?: number
    codeDir?: string
    modelType?: string
    userId?: string
  }

  type AppVersionRetryBuildRequest = {
    appId?: string
    codeDir?: string
  }

  type AppVO = {
    id?: string
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    appTypes?: string[]
    deployKey?: string
    deployedTime?: string
    priority?: number
    isPublish?: number
    userId?: string
    createTime?: string
    updateTime?: string
    user?: UserVO
  }

  type BaseResponseApp = {
    code?: number
    data?: App
    message?: string
  }

  type BaseResponseAppCodeFileListVO = {
    code?: number
    data?: AppCodeFileListVO
    message?: string
  }

  type BaseResponseAppVO = {
    code?: number
    data?: AppVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponsePageAppVersion = {
    code?: number
    data?: PageAppVersion
    message?: string
  }

  type BaseResponsePageAppVO = {
    code?: number
    data?: PageAppVO
    message?: string
  }

  type BaseResponsePageChatHistoryVO = {
    code?: number
    data?: PageChatHistoryVO
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string
    message?: string
    messageType?: string
    appId?: string
    userId?: string
    lastCreateTime?: string
  }

  type ChatHistoryVO = {
    id?: string
    message?: string
    messageType?: string
    appId?: string
    userId?: string
    parentId?: string
    createTime?: string
    app?: AppVO
    user?: UserVO
  }

  type chatToGenCodeParams = {
    appId: string
    message: string
    modelType: string
  }

  type DeleteRequest = {
    id?: string
  }

  type downloadAppCodeParams = {
    appId: string
    version: string
  }

  type getAppByIdParams = {
    id: string
  }

  type getAppVersionsByAppIdParams = {
    appid: string
  }

  type getAppVOByIdParams = {
    id: string
  }

  type getInfo1Params = {
    id: string
  }

  type getInfoParams = {
    id: string
  }

  type getUserByIdParams = {
    id: string
  }

  type getUserVOByIdParams = {
    id: string
  }

  type listCodeFilesParams = {
    appId: string
    codeDir: string
  }

  type LoginUserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
    updateTime?: string
    permissions?: string[]
  }

  type page1Params = {
    page: PageAppVersion
  }

  type PageAppVersion = {
    records?: AppVersion[]
    pageNumber?: string
    pageSize?: string
    totalPage?: string
    totalRow?: string
    optimizeCountQuery?: boolean
  }

  type PageAppVO = {
    records?: AppVO[]
    pageNumber?: string
    pageSize?: string
    totalPage?: string
    totalRow?: string
    optimizeCountQuery?: boolean
  }

  type PageChatHistoryVO = {
    records?: ChatHistoryVO[]
    pageNumber?: string
    pageSize?: string
    totalPage?: string
    totalRow?: string
    optimizeCountQuery?: boolean
  }

  type pageParams = {
    page: PageUser
  }

  type PageUser = {
    records?: User[]
    pageNumber?: string
    pageSize?: string
    totalPage?: string
    totalRow?: string
    optimizeCountQuery?: boolean
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: string
    pageSize?: string
    totalPage?: string
    totalRow?: string
    optimizeCountQuery?: boolean
  }

  type remove1Params = {
    id: string
  }

  type removeParams = {
    id: string
  }

  type ServerSentEventString = true

  type serveStaticResourceParams = {
    deployKey: string
  }

  type User = {
    id?: string
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    vipExpireTime?: string
    vipCode?: string
    vipNumber?: string
    shareCode?: string
    inviteUser?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
    permissions?: string[]
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }
}
