<template>
  <a-config-provider :locale="locale" :theme="themeStore.antTheme">
    <BasicLayout />
  </a-config-provider>
</template>

<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { ref } from 'vue'
import { useThemeStore } from '@/stores/theme'

dayjs.locale('zh-cn')

const locale = ref(zhCN)
const themeStore = useThemeStore()
</script>
