<script setup>
import AuthPanel from "./components/AuthPanel.vue";
</script>

<template>
  <div class="app">
    <header>
      <h1>피모북</h1>
      <AuthPanel />
    </header>

    <RouterView />
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  background: #111;
}

header {
  height: 70px;

  display: flex;
  align-items: center;

  padding: 0 25px;

  background: #181820;
  color: white;
}

header h1 {
  margin: 0;
}
</style>
