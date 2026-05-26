<template>
  <img v-if="src" :src="src" :alt="alt" referrerpolicy="no-referrer" v-bind="$attrs" />
</template>

<script setup lang="ts">
defineProps<{
  src?: string
  alt?: string
}>()
</script>
