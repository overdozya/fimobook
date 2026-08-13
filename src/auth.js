import { ref } from "vue";

const stored = JSON.parse(localStorage.getItem("fimo-auth") || "null");

export const auth = ref(stored);

export const setAuth = (session) => {
  auth.value = session;
  if (session) {
    localStorage.setItem("fimo-auth", JSON.stringify(session));
  } else {
    localStorage.removeItem("fimo-auth");
  }
};

export const apiFetch = async (path, options = {}) => {
  const headers = new Headers(options.headers || {});
  if (auth.value?.token) {
    headers.set("Authorization", `Bearer ${auth.value.token}`);
  }
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`http://localhost:8080${path}`, { ...options, headers });
  if (response.status === 401 || response.status === 403) {
    if (auth.value) setAuth(null);
  }
  return response;
};
