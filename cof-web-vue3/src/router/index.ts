import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { useRoomStore } from "@/stores/roomStore";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    name: "home",
    component: () => import("@/views/HomeView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/auth",
    name: "auth",
    component: () => import("@/views/AuthView.vue"),
    meta: { guest: true },
  },
  {
    path: "/rooms/create",
    name: "create-room",
    component: () => import("@/views/CreateRoomView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/rooms/join",
    name: "join-room",
    component: () => import("@/views/JoinRoomView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/rooms",
    name: "rooms",
    component: () => import("@/views/RoomsView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/rules",
    name: "rules",
    component: () => import("@/views/RulesView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/room/:roomId/waiting",
    name: "waiting",
    component: () => import("@/views/WaitingView.vue"),
    meta: { requiresAuth: true, roomRoute: true },
  },
  {
    path: "/room/:roomId/settings",
    name: "settings",
    component: () => import("@/views/SettingsView.vue"),
    meta: { requiresAuth: true, roomRoute: true },
  },
  {
    path: "/room/:roomId/loading",
    name: "loading",
    component: () => import("@/views/LoadingView.vue"),
    meta: { requiresAuth: true, roomRoute: true, gameRoute: true },
  },
  {
    path: "/room/:roomId/game",
    name: "game",
    component: () => import("@/views/GameView.vue"),
    meta: { requiresAuth: true, roomRoute: true, gameRoute: true },
  },
  {
    path: "/profile",
    name: "profile",
    component: () => import("@/views/ProfileView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/profile/replay/:gameId",
    name: "match-replay",
    component: () => import("@/views/MatchReplayView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/leaderboard",
    name: "leaderboard",
    component: () => import("@/views/LeaderboardView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/cards",
    name: "card-select",
    component: () => import("@/views/CardSelectView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/cards/loading",
    name: "card-loading",
    component: () => import("@/views/CardLoadingView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/cards/info",
    name: "card-info",
    component: () => import("@/views/CardInfoView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/pmv-index",
    name: "pmv-index",
    component: () => import("@/views/PmvIndexView.vue"),
    meta: { requiresAuth: true },
  },
  { path: "/home", redirect: { name: "home" } },
  { path: "/create-room", redirect: "/rooms/create" },
  { path: "/join-room", redirect: "/rooms/join" },
  { path: "/waiting/:roomId?", redirect: (to) => ({ path: `/room/${to.params.roomId || ""}/waiting` }) },
  { path: "/settings", redirect: (to) => ({ path: `/room/${String(to.query.roomId || "")}/settings` }) },
  { path: "/loading", redirect: (to) => ({ path: `/room/${String(to.query.roomId || "")}/loading`, query: to.query }) },
  { path: "/game", redirect: (to) => ({ path: `/room/${String(to.query.roomId || "")}/game`, query: to.query }) },
  { path: "/card-select", redirect: "/cards" },
  { path: "/card-loading", redirect: "/cards/loading" },
  { path: "/card-info", redirect: "/cards/info" },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!auth.bootstrap && !auth.loading) {
    try {
      await auth.refreshBootstrap();
    } catch {
      /* bootstrap may fail when offline */
    }
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: "auth", query: { redirect: to.fullPath } };
  }
  if (to.meta.guest && auth.isAuthenticated) {
    return { name: "home" };
  }

  if (to.meta.roomRoute) {
    const roomStore = useRoomStore();
    const roomId = String(to.params.roomId || "");
    if (!roomId) {
      return { name: "rooms" };
    }
    roomStore.activeRoomId = roomId;
    const gameStore = useGameStore();
    const status = roomStore.currentRoom?.status;
    const gameStatus = gameStore.currentGame?.status;
    if (to.name === "waiting" && status && status !== "waiting") {
      if (status === "loading" || gameStatus === "loading") {
        return { name: "loading", params: { roomId }, query: to.query };
      }
      if (status === "playing" && gameStatus === "playing") {
        return { name: "game", params: { roomId }, query: to.query };
      }
    }
    if (to.name === "loading" && status === "playing" && gameStatus === "playing") {
      return { name: "game", params: { roomId }, query: to.query };
    }
    if (to.name === "game" && (status === "loading" || gameStatus === "loading")) {
      return { name: "loading", params: { roomId }, query: to.query };
    }
    if (to.name === "game" && status === "waiting") {
      return { name: "waiting", params: { roomId } };
    }
  }

  return true;
});

export function syncBodyRouteClass(routeName: string | symbol | null | undefined): void {
  document.body.classList.toggle("menu-backdrop", routeName !== "game" && routeName !== "loading");
  document.body.classList.toggle("game-route", routeName === "game");
}

router.afterEach((to) => {
  syncBodyRouteClass(to.name);
});
