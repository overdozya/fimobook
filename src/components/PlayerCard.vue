<script setup>
import PlayerCardVisual from "./PlayerCardVisual.vue";

defineProps({
  player: Object,
});

const emit = defineEmits(["open", "add-squad"]);
</script>

<template>
  <div class="player-card">
    <div class="card-image">
      <PlayerCardVisual :player="player" />
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
  width: 168px;
  height: 168px;
  margin: 0 auto;
}

button {
  margin: 4px;
  padding: 7px 10px;
  cursor: pointer;
}
</style>
