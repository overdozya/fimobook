<script setup>
import { ref } from "vue";
import { auth, setAuth } from "../auth";

const open = ref(false);
const mode = ref("login");
const email = ref("");
const password = ref("");
const displayName = ref("");
const loading = ref(false);
const error = ref("");

const submit = async () => {
  loading.value = true;
  error.value = "";
  try {
    const response = await fetch(`http://localhost:8080/api/auth/${mode.value}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email.value, password: password.value, displayName: displayName.value }),
    });
    if (!response.ok) throw new Error("입력 정보를 확인해주세요.");
    setAuth(await response.json());
    open.value = false;
    password.value = "";
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
};

const changeMode = (next) => {
  mode.value = next;
  error.value = "";
};
</script>

<template>
  <div class="auth-area">
    <template v-if="auth">
      <span>{{ auth.displayName }}님</span>
      <button @click="setAuth(null)">로그아웃</button>
    </template>
    <button v-else @click="open = true">로그인</button>

    <div v-if="open" class="overlay" @click.self="open = false">
      <form class="panel" @submit.prevent="submit">
        <h2>{{ mode === 'login' ? '로그인' : '회원가입' }}</h2>
        <input v-if="mode === 'register'" v-model="displayName" placeholder="닉네임 (2자 이상)" required />
        <input v-model="email" type="email" placeholder="이메일" required />
        <input v-model="password" type="password" placeholder="비밀번호 (8자 이상)" required minlength="8" />
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" :disabled="loading">{{ loading ? '처리 중...' : '확인' }}</button>
        <button type="button" class="switch" @click="changeMode(mode === 'login' ? 'register' : 'login')">
          {{ mode === 'login' ? '회원가입하기' : '로그인으로' }}
        </button>
        <button type="button" class="switch" @click="open = false">닫기</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.auth-area { margin-left: auto; display: flex; align-items: center; gap: 10px; }
button { padding: 8px 12px; cursor: pointer; }
.overlay { position: fixed; inset: 0; z-index: 500; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,.8); }
.panel { width: min(360px, 90vw); display: grid; gap: 12px; padding: 26px; background: #242432; color: white; }
.panel input { padding: 12px; font-size: 15px; }
.panel h2 { margin: 0 0 8px; }
.error { color: #ff7777; margin: 0; }
.switch { background: transparent; color: #bbb; border: 0; }
</style>
