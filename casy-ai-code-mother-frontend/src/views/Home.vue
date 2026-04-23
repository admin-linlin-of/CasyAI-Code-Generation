<template>
  <h2>首页</h2>
</template>
<script setup lang="ts">
import { onMounted } from 'vue'
import { healthCheck } from '@/api/healthController.ts'

onMounted(() => {
  healthCheck().then((res) => {
    console.log(res)
  })
})
</script>
<style scoped></style>
