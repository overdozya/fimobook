import { createRouter, createWebHistory } from "vue-router";
import SquadView from "../views/SquadView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "squad",
      component: SquadView,
    },
  ],
});

export default router;
