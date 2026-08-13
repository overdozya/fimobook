<script setup>
import { ref } from "vue";
import PlayerCard from "./PlayerCard.vue";
import PlayerDetail from "./PlayerDetail.vue";

defineProps({
  targetPosition: String,
});

const emit = defineEmits(["select", "close"]);

const inputKeyword = ref("");
const players = ref([]);
const selectedPlayer = ref(null);
const positionFilter = ref("");
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);

const loading = ref(false);
const searched = ref(false);

const search = async (nextPage = 0) => {
  const keyword = inputKeyword.value.trim();

  if (!keyword) {
    players.value = [];
    searched.value = false;
    return;
  }

  loading.value = true;
  searched.value = true;

  try {
    const params = new URLSearchParams({ name: keyword, position: positionFilter.value, page: nextPage, size: 12 });
    const response = await fetch(`http://localhost:8080/api/players/search?${params}`);

    if (!response.ok) {
      throw new Error("선수 검색 API 요청 실패");
    }

    const data = await response.json();
    players.value = data.players;
    page.value = data.page;
    totalPages.value = data.totalPages;
    totalElements.value = data.totalElements;
  } catch (error) {
    console.error(error);
    players.value = [];
    alert("선수 검색 중 오류가 발생했습니다.");
  } finally {
    loading.value = false;
  }
};

const openDetail = async (player) => {
  try {
    const response = await fetch(`http://localhost:8080/api/players/${player.cid}`);
    if (!response.ok) throw new Error();
    selectedPlayer.value = await response.json();
  } catch {
    alert("선수 상세 정보를 불러오지 못했습니다.");
  }
};

const closeDetail = () => {
  selectedPlayer.value = null;
};

const selectPlayer = (player) => {
  emit("select", player);
};
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="picker">
      <div class="top">
        <h2>{{ targetPosition }} 선수 등록</h2>

        <button class="close-button" @click="emit('close')">X</button>
      </div>

      <div class="search">
        <input v-model="inputKeyword" placeholder="선수 이름" @keyup.enter="search()" />

        <select v-model="positionFilter">
          <option value="">전체 포지션</option>
          <option v-for="position in ['GK','LB','CB','RB','CDM','CM','CAM','LW','RW','CF','ST']" :key="position">
            {{ position }}
          </option>
        </select>

        <button @click="search()">검색</button>
      </div>

      <p v-if="loading" class="message">검색 중...</p>

      <p v-else-if="!searched" class="message">선수명을 입력하고 검색을 눌러주세요.</p>

      <p v-else-if="players.length === 0" class="message">검색 결과가 없습니다.</p>

      <div v-else class="player-list">
        <PlayerCard
          v-for="player in players"
          :key="player.cid"
          :player="player"
          @open="openDetail"
          @add-squad="selectPlayer"
        />
      </div>

      <div v-if="searched && players.length" class="pagination">
        <button :disabled="page === 0" @click="search(page - 1)">이전</button>
        <span>{{ page + 1 }} / {{ totalPages }} · 총 {{ totalElements }}장</span>
        <button :disabled="page + 1 >= totalPages" @click="search(page + 1)">다음</button>
      </div>
    </div>

    <PlayerDetail v-if="selectedPlayer" :player="selectedPlayer" @close="closeDetail" />
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.82);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.picker {
  width: 90%;
  max-width: 1100px;
  max-height: 85vh;
  overflow-y: auto;
  background: #20202c;
  color: white;
  padding: 20px;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.top h2 {
  margin: 0;
}

.close-button {
  padding: 8px 12px;
  cursor: pointer;
}

.search {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.search input {
  flex: 1;
  padding: 11px;
  font-size: 16px;
}

.search select { padding: 10px; }

.search button {
  padding: 10px 22px;
  cursor: pointer;
}

.message {
  color: #aaa;
  padding: 30px 0;
  text-align: center;
}

.player-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}

.pagination { display: flex; justify-content: center; align-items: center; gap: 15px; margin-top: 20px; }
.pagination button { padding: 8px 15px; }
</style>
