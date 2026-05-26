<!--
    用户头像：圆形裁剪 + object-fit: cover 铺满
-->
<template>
  <span
    class="external-avatar"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size / 2.2)}px` }"
  >
    <ExternalImage v-if="src" :src="src" :alt="alt" class="external-avatar__img" />
    <span v-else class="external-avatar__fallback">{{ fallbackText }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ExternalImage from './ExternalImage.vue'

const props = withDefaults(
  defineProps<{
    src?: string
    alt?: string
    size?: number
    fallback?: string
  }>(),
  {
    alt: 'avatar',
    size: 32,
    fallback: 'U',
  },
)

const fallbackText = computed(() => props.fallback?.trim()?.[0]?.toUpperCase() || 'U')
</script>

<style scoped>
.external-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  overflow: hidden;
  background: #1677ff;
  color: #fff;
  flex-shrink: 0;
  vertical-align: middle;
}

.external-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.external-avatar__fallback {
  font-weight: 500;
  line-height: 1;
  user-select: none;
}
</style>
