<script setup>
import { ref, computed, onMounted } from "vue";
import { auth, apiFetch } from "../auth";
import PlayerCardVisual from "./PlayerCardVisual.vue";

const props = defineProps({
  player: Object,
});

const emit = defineEmits(["close"]);

// =========================
// 선수평
// =========================
const reviews = ref([]);

const reviewRating = ref(5);
const reviewContent = ref("");

const currentReviews = computed(() => {
  return reviews.value;
});

const loadReviews = async () => {
  const response = await fetch(`/api/players/${props.player.cid}/reviews`);
  if (response.ok) reviews.value = await response.json();
};

onMounted(loadReviews);

const averageRating = computed(() => {
  if (currentReviews.value.length === 0) {
    return 0;
  }

  const total = currentReviews.value.reduce((sum, review) => sum + review.rating, 0);

  return (total / currentReviews.value.length).toFixed(1);
});

const submitReview = async () => {
  if (!auth.value) {
    alert("평가 작성은 로그인 후 사용할 수 있습니다.");
    return;
  }
  const content = reviewContent.value.trim();

  if (!content) {
    alert("한줄평을 입력해주세요.");
    return;
  }

  const response = await apiFetch(`/api/players/${props.player.cid}/reviews`, {
    method: "POST",
    body: JSON.stringify({ rating: reviewRating.value, content }),
  });
  if (!response.ok) {
    alert("평가 등록에 실패했습니다.");
    return;
  }
  reviews.value.unshift(await response.json());

  reviewContent.value = "";
  reviewRating.value = 5;
};

const deleteReview = async (reviewId) => {
  const response = await apiFetch(`/api/reviews/${reviewId}`, { method: "DELETE" });
  if (response.ok) reviews.value = reviews.value.filter((review) => review.id !== reviewId);
};

const editReview = async (review) => {
  const content = prompt("평가를 수정해주세요.", review.content)?.trim();
  if (!content) return;
  const response = await apiFetch(`/api/reviews/${review.id}`, {
    method: "PUT",
    body: JSON.stringify({ rating: review.rating, content }),
  });
  if (response.ok) Object.assign(review, await response.json());
};

const reactReview = async (review, reaction) => {
  if (!auth.value) {
    alert("로그인 후 사용할 수 있습니다.");
    return;
  }
  const response = await apiFetch(`/api/reviews/${review.id}/${reaction}`, { method: "POST" });
  if (response.ok) Object.assign(review, await response.json());
};

// =========================
// 능력치 그룹
// =========================
const statGroups = [
  {
    title: "속도",
    stats: [
      ["가속", "ACC"],
      ["질주 속도", "SPD"],
    ],
  },

  {
    title: "슈팅",
    stats: [
      ["결정력", "FIN"],
      ["슈팅력", "SHO"],
      ["중거리 슛", "LSA"],
      ["발리 슛", "VOL"],
      ["페널티킥", "PEN"],
    ],
  },

  {
    title: "패스",
    stats: [
      ["짧은 패스", "SPA"],
      ["긴 패스", "LPA"],
      ["시야", "VIS"],
      ["크로스", "CRO"],
      ["감아차기", "CUR"],
      ["프리킥", "FRK"],
    ],
  },

  {
    title: "드리블",
    stats: [
      ["드리블", "DRI"],
      ["볼 컨트롤", "BAC"],
      ["민첩성", "AGI"],
      ["반응도", "REA"],
      ["밸런스", "BAL"],
    ],
  },

  {
    title: "수비",
    stats: [
      ["마크", "MRK"],
      ["태클", "STT"],
      ["슬라이딩 태클", "SLT"],
      ["가로채기", "AWR"],
      ["헤딩", "HEA"],
    ],
  },

  {
    title: "피지컬",
    stats: [
      ["힘", "STR"],
      ["공격성", "AGG"],
      ["점프", "JMP"],
      ["체력", "STA"],
    ],
  },
];

// =========================
// 가격
// =========================
const prices = computed(() => {
  const list = [];

  for (let level = 0; level <= 15; level++) {
    const key = `n8Price${level}`;
    const price = props.player[key];

    if (price !== undefined && price !== null) {
      list.push({
        level,
        price,
      });
    }
  }

  return list;
});

const formatPrice = (price) => {
  if (!price) return "-";

  return new Intl.NumberFormat("ko-KR").format(price);
};

// =========================
// 주발
// =========================
const mainFootText = computed(() => {
  if (props.player.mainFoot === 1) {
    return "오른발";
  }

  if (props.player.mainFoot === 2) {
    return "왼발";
  }

  return "-";
});
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="detail">
      <button class="close" @click="emit('close')">X</button>

      <!-- ========================= -->
      <!-- 상단 -->
      <!-- ========================= -->
      <section class="top-section">
        <div class="card-area">
          <div class="card-image">
            <PlayerCardVisual :player="player" />
          </div>
        </div>

        <div class="basic-info">
          <h1>{{ player.playerKor }}</h1>

          <p class="english-name">
            {{ player.playerEng }}
          </p>

          <div class="main-info">
            <strong> OVR {{ player.ovr }} </strong>

            <span>
              {{ player.position }}
            </span>

            <span>
              {{ player.team }}
            </span>
          </div>

          <div class="info-grid">
            <div>
              <span>국가</span>
              <strong class="value-with-icon">
                <img v-if="player.assets?.flag" :src="player.assets.flag" alt="" />
                {{ player.nation }}
              </strong>
            </div>

            <div>
              <span>리그</span>
              <strong class="value-with-icon">
                <img v-if="player.assets?.league" :src="player.assets.league" alt="" />
                {{ player.league || "-" }}
              </strong>
            </div>

            <div>
              <span>키</span>
              <strong>{{ player.height }}cm</strong>
            </div>

            <div>
              <span>몸무게</span>
              <strong>{{ player.weight }}kg</strong>
            </div>

            <div>
              <span>주발</span>
              <strong>{{ mainFootText }}</strong>
            </div>

            <div>
              <span>약발</span>
              <strong>
                {{ player.WFA || "-" }}
              </strong>
            </div>
          </div>
        </div>
      </section>

      <!-- ========================= -->
      <!-- 개인기 / 특성 -->
      <!-- ========================= -->
      <section class="section">
        <h2>개인기 / 특성</h2>

        <div class="feature-grid">
          <div class="feature-card">
            <span>고유 개인기</span>

            <strong>
              {{ player.skillMovesName || "-" }}
            </strong>
          </div>

          <div class="feature-card">
            <span>개인기 등급</span>

            <strong>
              {{ player.skillMovesLevel || "-" }}
            </strong>
          </div>

          <div class="feature-card">
            <span>잠재 포지션</span>

            <strong>
              {{ player.potentialPosition || "-" }}
            </strong>
          </div>
        </div>

        <div class="traits">
          <h3>특성</h3>

          <span v-for="trait in player.Trait" :key="trait.id" class="trait">
            <img v-if="trait.iconUrl" :src="trait.iconUrl" alt="" />
            {{ trait.name }}
          </span>

          <p v-if="!player.Trait || player.Trait.length === 0">특성 없음</p>
        </div>

        <div v-if="player.playStyles?.length" class="traits">
          <h3>플레이스타일</h3>
          <span v-for="playStyle in player.playStyles" :key="playStyle.id" class="trait">
            <img v-if="playStyle.iconUrl" :src="playStyle.iconUrl" alt="" />
            {{ playStyle.name }}
          </span>
        </div>
      </section>

      <!-- ========================= -->
      <!-- 세부 능력치 -->
      <!-- ========================= -->
      <section class="section">
        <h2>세부 능력치</h2>

        <div class="stats-grid">
          <div v-for="group in statGroups" :key="group.title" class="stat-group">
            <h3>{{ group.title }}</h3>

            <div v-for="[label, key] in group.stats" :key="key" class="stat">
              <span>{{ label }}</span>

              <strong>
                {{ player[key] ?? "-" }}
              </strong>
            </div>
          </div>
        </div>
      </section>

      <!-- ========================= -->
      <!-- 진화별 가격 -->
      <!-- ========================= -->
      <section class="section">
        <h2>진화별 선수 가치</h2>

        <div class="price-list">
          <div v-for="item in prices" :key="item.level" class="price-item">
            <strong> {{ item.level }}진 </strong>

            <span> {{ formatPrice(item.price) }} MP </span>
          </div>
        </div>
      </section>

      <!-- ========================= -->
      <!-- 선수평 -->
      <!-- ========================= -->
      <section class="section review-section">
        <div class="review-title">
          <h2>선수 평가</h2>

          <div>⭐ {{ averageRating }} / 5 · {{ currentReviews.length }}개</div>
        </div>

        <div class="review-form">
          <select v-model="reviewRating">
            <option :value="5">⭐⭐⭐⭐⭐</option>

            <option :value="4">⭐⭐⭐⭐</option>

            <option :value="3">⭐⭐⭐</option>

            <option :value="2">⭐⭐</option>

            <option :value="1">⭐</option>
          </select>

          <input
            v-model="reviewContent"
            placeholder="이 선수 어땠음?"
            maxlength="100"
            @keyup.enter="submitReview"
          />

          <button @click="submitReview">등록</button>
        </div>

        <p v-if="currentReviews.length === 0" class="empty-review">아직 평가가 없습니다.</p>

        <div v-for="review in currentReviews" :key="review.id" class="review">
          <div class="review-top">
            <strong> ⭐ {{ review.rating }}/5 · {{ review.authorName }} </strong>

            <span v-if="auth?.userId === review.userId">
              <button @click="editReview(review)">수정</button>
              <button class="delete" @click="deleteReview(review.id)">삭제</button>
            </span>
          </div>

          <p>
            {{ review.content }}
          </p>

          <div class="review-actions">
            <button @click="reactReview(review, 'like')">👍 {{ review.likes }}</button>

            <button @click="reactReview(review, 'dislike')">👎 {{ review.dislikes }}</button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
* {
  box-sizing: border-box;
}

.overlay {
  position: fixed;
  inset: 0;

  background: rgba(0, 0, 0, 0.88);

  display: flex;
  align-items: center;
  justify-content: center;

  padding: 25px;

  z-index: 200;
}

.detail {
  position: relative;

  width: 100%;
  max-width: 1100px;
  max-height: 92vh;

  overflow-y: auto;

  background: #20202c;
  color: white;

  padding: 35px;
}

.close {
  position: absolute;

  right: 20px;
  top: 20px;

  z-index: 10;

  padding: 8px 12px;

  cursor: pointer;
}

.top-section {
  display: grid;

  grid-template-columns: 320px 1fr;

  gap: 35px;

  padding-bottom: 30px;

  border-bottom: 1px solid #454554;
}

.card-area {
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-image {
  position: relative;

  width: 280px;
  height: 280px;
}

.value-with-icon { align-items: center; display: flex; gap: 8px; }
.value-with-icon img { height: 22px; object-fit: contain; width: 22px; }

.basic-info h1 {
  margin-bottom: 4px;

  font-size: 36px;
}

.english-name {
  color: #999;
}

.main-info {
  display: flex;
  gap: 18px;
  align-items: center;

  margin: 25px 0;

  font-size: 20px;
}

.main-info strong {
  font-size: 30px;
}

.info-grid {
  display: grid;

  grid-template-columns: repeat(2, 1fr);

  gap: 12px;
}

.info-grid > div {
  display: flex;
  justify-content: space-between;

  padding: 13px;

  background: #292938;
}

.info-grid span {
  color: #999;
}

.section {
  padding: 30px 0;

  border-bottom: 1px solid #454554;
}

.section h2 {
  margin-top: 0;
}

.feature-grid {
  display: grid;

  grid-template-columns: repeat(3, 1fr);

  gap: 12px;
}

.feature-card {
  display: flex;
  flex-direction: column;

  gap: 8px;

  padding: 18px;

  background: #292938;
}

.feature-card span {
  color: #999;
}

.traits {
  margin-top: 25px;
}

.trait {
  align-items: center;
  display: inline-flex;
  gap: 7px;
  display: inline-block;

  margin-right: 8px;
  margin-bottom: 8px;

  padding: 7px 12px;

  background: #343447;

  border-radius: 20px;
}

.trait img { height: 24px; object-fit: contain; width: 24px; }

.stats-grid {
  display: grid;

  grid-template-columns: repeat(3, 1fr);

  gap: 12px;
}

.stat-group {
  padding: 18px;

  background: #292938;
}

.stat-group h3 {
  margin-top: 0;

  padding-bottom: 10px;

  border-bottom: 1px solid #444;
}

.stat {
  display: flex;
  justify-content: space-between;

  padding: 6px 0;
}

.stat span {
  color: #aaa;
}

.price-list {
  display: flex;
  flex-direction: column;
  gap: 5px;

  width: 100%;
  max-width: 340px;

  margin-right: auto;
}

.price-item {
  display: flex;
  justify-content: space-between;
  align-items: center;

  width: 100%;

  padding: 7px 10px;

  background: #292938;

  font-size: 13px;
}

.price-item strong {
  width: 45px;
  font-size: 13px;
}

.price-item span {
  color: #ccc;
  font-size: 13px;
}

.review-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.review-form {
  display: grid;

  grid-template-columns: 130px 1fr 80px;

  gap: 8px;

  margin: 20px 0;
}

.review-form select,
.review-form input,
.review-form button {
  padding: 11px;
}

.empty-review {
  color: #999;
}

.review {
  padding: 18px 0;

  border-top: 1px solid #444;
}

.review-top {
  display: flex;
  justify-content: space-between;
}

.delete {
  background: transparent;

  border: none;

  color: #ff7777;

  cursor: pointer;
}

.review-actions {
  display: flex;

  gap: 8px;
}

.review-actions button {
  padding: 6px 10px;
  cursor: pointer;
}

@media (max-width: 800px) {
  .overlay {
    padding: 0;
  }

  .detail {
    max-height: 100vh;

    min-height: 100vh;

    padding: 20px;
  }

  .top-section {
    grid-template-columns: 1fr;
  }

  .card-image {
    width: 220px;
    height: 220px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .price-list {
    grid-template-columns: repeat(2, 1fr);
  }

  .review-form {
    grid-template-columns: 1fr;
  }
}
</style>
