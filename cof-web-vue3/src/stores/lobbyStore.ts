import { defineStore } from "pinia";
import { ref } from "vue";
import * as metaApi from "@/api/meta";
import { fetchLeaderboard } from "@/api/leaderboard";
import { fetchProfile } from "@/api/profile";
import type { LeaderboardEntry } from "@/types/api";

export const useLobbyStore = defineStore("lobby", () => {
  const cardLibraries = ref<unknown[]>([]);
  const computerPlayers = ref<unknown[]>([]);
  const pmvIndex = ref<unknown[]>([]);
  const profile = ref<Record<string, unknown> | null>(null);
  const leaderboard = ref<LeaderboardEntry[]>([]);
  const cardViewerPayload = ref<unknown | null>(null);
  const selectedLibraryIds = ref<string[]>([]);
  const loadingMeta = ref(false);
  const loadingProfile = ref(false);
  const loadingLeaderboard = ref(false);
  const loadingPmvIndex = ref(false);

  async function loadMeta(): Promise<void> {
    loadingMeta.value = true;
    try {
      const [libraries, computers] = await Promise.all([
        metaApi.fetchCardLibraries(),
        metaApi.fetchComputerPlayers(),
      ]);
      cardLibraries.value = libraries.libraries ?? [];
      computerPlayers.value = computers.players ?? [];
    } finally {
      loadingMeta.value = false;
    }
  }

  async function loadProfile(clientId: string): Promise<void> {
    loadingProfile.value = true;
    try {
      const result = await fetchProfile(clientId);
      profile.value = result.profile;
    } finally {
      loadingProfile.value = false;
    }
  }

  async function loadLeaderboard(): Promise<void> {
    loadingLeaderboard.value = true;
    try {
      leaderboard.value = await fetchLeaderboard();
    } finally {
      loadingLeaderboard.value = false;
    }
  }

  async function loadPmvIndex(): Promise<void> {
    loadingPmvIndex.value = true;
    try {
      pmvIndex.value = await metaApi.fetchPmvIndex();
    } finally {
      loadingPmvIndex.value = false;
    }
  }

  function setCardSelection(libraryIds: string[]): void {
    selectedLibraryIds.value = libraryIds;
  }

  function setCardViewerPayload(payload: unknown | null): void {
    cardViewerPayload.value = payload;
  }

  return {
    cardLibraries,
    computerPlayers,
    pmvIndex,
    profile,
    leaderboard,
    cardViewerPayload,
    selectedLibraryIds,
    loadingMeta,
    loadingProfile,
    loadingLeaderboard,
    loadingPmvIndex,
    loadMeta,
    loadProfile,
    loadLeaderboard,
    loadPmvIndex,
    setCardSelection,
    setCardViewerPayload,
  };
});
