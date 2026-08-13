<script setup>
defineProps({
  player: Object,
});

const emit = defineEmits(["open", "add-squad"]);
</script>

<template>
  <div class="player-card">
    <div class="card-image">
      <img class="card-background" :src="player.bimage" alt="" />

      <img class="player-image" :src="player.pimage" :alt="player.playerKor" />
    </div>

    <h3>{{ player.playerKor }}</h3>

    <p>OVR {{ player.ovr }}</p>
    <p>{{ player.position }}</p>
    <p>{{ player.team }}</p>

    <button @click="emit('open', player)">상세보기</button>

    <button @click="emit('add-squad', player)">스쿼드 추가</button>
  </div>
</template>

<style scoped>
.player-card {
  background: #292938;
  color: white;
  border: 1px solid #444;
  padding: 12px;
  text-align: center;
}

.card-image {
  position: relative;
  width: 150px;
  height: 180px;
  margin: 0 auto;
}

.card-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.player-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

button {
  margin: 4px;
  padding: 7px 10px;
  cursor: pointer;
}
</style>
