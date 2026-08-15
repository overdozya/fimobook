<script setup>
import { computed } from "vue";

const props = defineProps({
  player: { type: Object, required: true },
});

const themeStyle = computed(() => ({
  "--card-ovr-color": props.player.cardTheme?.ovr || "#ffffff",
  "--card-position-color": props.player.cardTheme?.position || "#ffffff",
  "--card-name-color": props.player.cardTheme?.name || "#ffffff",
}));

const hideMissingImage = (event) => {
  event.currentTarget.hidden = true;
};
</script>

<template>
  <div class="player-card-visual" :style="themeStyle">
    <img
      v-if="player.bimage"
      class="background"
      :src="player.bimage"
      alt=""
      @error="hideMissingImage"
    />
    <img
      v-if="player.pimage"
      class="render"
      :src="player.pimage"
      :alt="`${player.playerKor} 선수 이미지`"
      @error="hideMissingImage"
    />

    <strong class="ovr">{{ player.ovr }}</strong>
    <span class="position">{{ player.position }}</span>
    <span class="name">{{ player.playerKor }}</span>

    <span class="icons">
      <img
        v-if="player.assets?.flag"
        :src="player.assets.flag"
        :alt="player.nation || '국가'"
        @error="hideMissingImage"
      />
      <img
        v-if="player.assets?.league"
        :src="player.assets.league"
        :alt="player.league || '리그'"
        @error="hideMissingImage"
      />
      <img
        v-if="player.assets?.team"
        :src="player.assets.team"
        :alt="player.team || '팀'"
        @error="hideMissingImage"
      />
    </span>
  </div>
</template>

<style scoped>
.player-card-visual {
  aspect-ratio: 1;
  color: white;
  container-type: inline-size;
  isolation: isolate;
  position: relative;
  width: 100%;
}

.background,
.render {
  height: 100%;
  left: 50%;
  object-fit: contain;
  pointer-events: none;
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 100%;
}

.background { z-index: 0; }
.render { max-height: 93%; max-width: 93%; z-index: 1; }

.ovr,
.position,
.name {
  line-height: 1;
  position: absolute;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.38);
  z-index: 2;
}

.ovr {
  color: var(--card-ovr-color);
  font-family: "FCOAllSans-Bold", sans-serif;
  font-size: 12.5cqi;
  left: 22%;
  top: 10%;
}

.position {
  color: var(--card-position-color);
  font-family: "FCOAllSans-Regular", sans-serif;
  font-size: 8.4cqi;
  left: 23%;
  top: 24%;
}

.name {
  bottom: 25.5%;
  color: var(--card-name-color);
  font-family: "FCOAllSans-Regular", sans-serif;
  font-size: 8.75cqi;
  left: 50%;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  transform: translateX(-50%);
  white-space: nowrap;
  width: 55%;
}

.icons {
  align-items: center;
  bottom: 16.1%;
  display: flex;
  gap: 5%;
  justify-content: center;
  left: 0;
  position: absolute;
  right: 0;
  z-index: 2;
}

.icons img {
  height: 8.33cqi;
  object-fit: contain;
  width: auto;
}

@supports not (font-size: 1cqi) {
  .ovr { font-size: 12.5%; }
  .position { font-size: 8.4%; }
  .name { font-size: 8.75%; }
}
</style>
