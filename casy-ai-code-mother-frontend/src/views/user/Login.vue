<template>
  <div id="userLoginPage">
    <h2 class="title">Casy AI 应用生成 - 用户登录</h2>
    <div class="desc">不写一行代码，生成完整应用</div>
    <div v-if="demoAccount && demoPassword" class="demo-box">
      <div class="demo-box__label">体验账号</div>
      <div class="demo-box__row">
        <span>账号 {{ demoAccount }}</span>
        <span>密码 {{ demoPassword }}</span>
      </div>
      <button class="demo-box__fill" type="button" @click="fillDemo">填入并使用</button>
    </div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[{ required: true, message: '请输入密码' }]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <div class="tips">
        没有账号？
        <RouterLink to="/user/register">去注册</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">登录</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { login } from '@/api/userController.ts'
import { listPublicSysParams } from '@/api/sysParamController'
import { SYS_PARAM_KEY } from '@/constant/sysParam'
import { message } from 'ant-design-vue'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const demoAccount = ref('')
const demoPassword = ref('')

const fillDemo = () => {
  if (!demoAccount.value || !demoPassword.value) return
  formState.userAccount = demoAccount.value
  formState.userPassword = demoPassword.value
}

const loadDemoAccount = async () => {
  try {
    const res = await listPublicSysParams()
    if (res.data.code !== 0 || !res.data.data) return
    const params = res.data.data
    demoAccount.value = params[SYS_PARAM_KEY.LOGIN_DEMO_ACCOUNT]?.trim() || ''
    demoPassword.value = params[SYS_PARAM_KEY.LOGIN_DEMO_PASSWORD]?.trim() || ''
    if (demoAccount.value && demoPassword.value) {
      fillDemo()
    }
  } catch {
    // 公开参数失败不影响登录
  }
}

onMounted(loadDemoAccount)

const router = useRouter()
const loginUserStore = useLoginUserStore()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const res = await login(values)
  console.log('res.data: ', res.data);
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>
<style scoped>
#userLoginPage {
  padding-top: 60px;
  max-width: 360px;
  margin: 0 auto;
}

.title {
  text-align: center;
  margin-bottom: 16px;
  color: var(--text-main);
}

.desc {
  text-align: center;
  color: var(--text-sub);
  margin-bottom: 16px;
}

.demo-box {
  margin-bottom: 20px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--tag-border);
  background: var(--tag-bg);
  color: var(--tag-text);
}

.demo-box__label {
  font-size: 12px;
  opacity: 0.75;
  margin-bottom: 6px;
}

.demo-box__row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  font-weight: 600;
}

.demo-box__fill {
  margin-top: 10px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--tag-bg-active);
  font-size: 13px;
  cursor: pointer;
}

.tips {
  margin-bottom: 16px;
  color: var(--text-sub);
  font-size: 13px;
  text-align: right;
}
</style>
