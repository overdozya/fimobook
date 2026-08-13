<script setup>
import { ref, computed, watch } from "vue";
import PlayerPicker from "../components/PlayerPicker.vue";
import { auth, apiFetch } from "../auth";

const positions = [
  { id: "gk", position: "GK", x: 50, y: 86 },

  { id: "lb", position: "LB", x: 18, y: 68 },
  { id: "cb1", position: "CB", x: 39, y: 72 },
  { id: "cb2", position: "CB", x: 61, y: 72 },
  { id: "rb", position: "RB", x: 82, y: 68 },

  { id: "cm1", position: "CM", x: 30, y: 45 },
  { id: "cdm", position: "CDM", x: 50, y: 55 },
  { id: "cm2", position: "CM", x: 70, y: 45 },

  { id: "lw", position: "LW", x: 28, y: 18 },
  { id: "st", position: "ST", x: 50, y: 12 },
  { id: "rw", position: "RW", x: 72, y: 18 },
];

const squad = ref(JSON.parse(localStorage.getItem("fimo-guest-squad") || "{}"));
const selectedSlot = ref(null);
const saving = ref(false);

const totalMp = computed(() => Object.values(squad.value)
  .reduce((sum, player) => sum + Number(player.n8Price0 || 0), 0));

const formatPrice = (price) => new Intl.NumberFormat("ko-KR").format(price);

watch(squad, (value) => {
  if (!auth.value) localStorage.setItem("fimo-guest-squad", JSON.stringify(value));
}, { deep: true });

const loadSquad = async () => {
  if (!auth.value) {
    squad.value = JSON.parse(localStorage.getItem("fimo-guest-squad") || "{}");
    return;
  }
  const response = await apiFetch("/api/squads/me");
  if (!response.ok) return;
  const loaded = await response.json();
  squad.value = Object.fromEntries(loaded.map((player) => [player.slotId, player]));
};

watch(auth, loadSquad, { immediate: true });

const saveSquad = async () => {
  if (!auth.value) {
    alert("스쿼드 DB 저장은 로그인 후 사용할 수 있습니다.");
    return;
  }
  saving.value = true;
  try {
    const slots = Object.entries(squad.value).map(([slotId, player]) => ({ slotId, cid: player.cid }));
    const response = await apiFetch("/api/squads/me", { method: "PUT", body: JSON.stringify(slots) });
    if (!response.ok) throw new Error();
    alert("스쿼드를 저장했습니다.");
  } catch {
    alert("스쿼드를 저장하지 못했습니다.");
  } finally {
    saving.value = false;
  }
};

const openPlayerPicker = (slot) => {
  selectedSlot.value = slot;
};

const closePlayerPicker = () => {
  selectedSlot.value = null;
};

const selectPlayer = (player) => {
  const duplicated = Object.values(squad.value).some((item) => item.pid === player.pid);

  if (duplicated) {
    alert("같은 선수는 중복 등록할 수 없습니다.");
    return;
  }

  squad.value[selectedSlot.value.id] = player;

  closePlayerPicker();
};

const removePlayer = (slotId) => {
  delete squad.value[slotId];
};

const clearSquad = () => {
  squad.value = {};
};
</script>

<template>
  <main class="squad-page">
    <section class="field">
      <div
        v-for="slot in positions"
        :key="slot.id"
        class="player-slot"
        :style="{
          left: slot.x + '%',
          top: slot.y + '%',
        }"
      >
        <span class="position">
          {{ slot.position }}
        </span>

        <template v-if="squad[slot.id]">
          <img
            class="squad-player-image"
            :src="squad[slot.id].pimage"
            :alt="squad[slot.id].playerKor"
          />

          <strong>
            {{ squad[slot.id].playerKor }}
          </strong>

          <span> OVR {{ squad[slot.id].ovr }} </span>

          <button @click="removePlayer(slot.id)">빼기</button>
        </template>

        <template v-else>
          <button class="add-player" @click="openPlayerPicker(slot)">+</button>

          <span>선수 등록</span>
        </template>
      </div>
    </section>

    <aside class="sidebar">
      <h2>내 스쿼드</h2>

      <div class="summary">
        <p>선수</p>
        <strong> {{ Object.keys(squad).length }} / 11 </strong>
      </div>

      <div class="summary">
        <p>OVR</p>
        <strong>0</strong>
      </div>

      <div class="summary">
        <p>MP</p>
        <strong>{{ formatPrice(totalMp) }}</strong>
      </div>

      <button class="save-button" :disabled="saving" @click="saveSquad">
        {{ saving ? "저장 중..." : "스쿼드 저장" }}
      </button>
      <button class="reset-button" @click="clearSquad">스쿼드 초기화</button>
    </aside>

    <PlayerPicker
      v-if="selectedSlot"
      :target-position="selectedSlot.position"
      @select="selectPlayer"
      @close="closePlayerPicker"
    />
  </main>
</template>

<style scoped>
.squad-page {
  display: grid;
  grid-template-columns: 1fr 260px;
  width: 100%;
  min-height: calc(100vh - 70px);
  background: #111;
  color: white;
}

.field {
  position: relative;
  min-height: 850px;
  background: linear-gradient(rgba(10, 80, 30, 0.25), rgba(10, 40, 20, 0.5)), #28783a;
  overflow: hidden;
}

.player-slot {
  position: absolute;
  transform: translate(-50%, -50%);
  width: 115px;
  min-height: 145px;
  border: 2px solid #40ff75;
  background: rgba(0, 20, 10, 0.78);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border-radius: 8px;
  padding: 8px;
}

.position {
  position: absolute;
  top: -25px;
  padding: 3px 12px;
  background: #245d32;
  color: #49ff78;
  font-size: 13px;
  font-weight: bold;
}

.add-player {
  width: 55px;
  height: 55px;
  border: none;
  background: transparent;
  color: #34ff70;
  font-size: 50px;
  font-weight: 200;
  line-height: 1;
  cursor: pointer;
}

.player-slot > span:last-child {
  font-size: 14px;
}

.squad-player-image {
  width: 90px;
  height: 90px;
  object-fit: contain;
}

.player-slot strong {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-slot button:not(.add-player) {
  border: none;
  padding: 5px 9px;
  background: #e34c4c;
  color: white;
  cursor: pointer;
}

.sidebar {
  background: #1c1d2c;
  padding: 30px 20px;
}

.sidebar h2 {
  margin-top: 0;
  margin-bottom: 25px;
}

.summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 0;
  border-bottom: 1px solid #444;
}

.summary p {
  margin: 0;
}

.summary strong {
  font-size: 22px;
}

.reset-button {
  width: 100%;
  margin-top: 30px;
  padding: 14px;
  border: none;
  background: #2196f3;
  color: white;
  font-weight: bold;
  cursor: pointer;
}

.save-button { width: 100%; margin-top: 30px; padding: 14px; border: 0; background: #36a269; color: white; font-weight: bold; cursor: pointer; }
.save-button + .reset-button { margin-top: 10px; }

.reset-button:hover {
  filter: brightness(1.1);
}

@media (max-width: 800px) {
  .squad-page {
    grid-template-columns: 1fr;
  }

  .field {
    min-height: 720px;
  }

  .sidebar {
    display: none;
  }

  .player-slot {
    width: 82px;
    min-height: 105px;
    font-size: 12px;
  }

  .squad-player-image {
    width: 65px;
    height: 65px;
  }

  .add-player {
    width: 40px;
    height: 40px;
    font-size: 38px;
  }
}
</style>
