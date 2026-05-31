<script setup lang="ts">
defineProps<{
  title: string;
  narrow?: boolean;
}>();
</script>

<template>
  <main class="page" :class="{ narrow }">
    <section class="panel">
      <h2>{{ title }}</h2>
      <slot />
    </section>
  </main>
</template>
