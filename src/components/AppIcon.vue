<template>
  <span class="icon" v-html="icons[name]"></span>
</template>

<script setup>
import { icons } from '../utils/icons'

defineProps({
  name: { type: String, required: true }
})
</script>

<style scoped>
.icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
</style>
