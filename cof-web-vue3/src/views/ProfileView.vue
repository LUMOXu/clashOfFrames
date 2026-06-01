<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useAuthStore } from "@/stores/authStore";
import { useLobbyStore } from "@/stores/lobbyStore";
import { fmtNum, fmtPct, formatDate, isGodComputer } from "@/utils/format";
import { recordField } from "@/utils/record";

interface HistoryRow {
  gameId?: string;
  at?: number;
  roomId?: string;
  playerCount?: number;
  rank?: number;
  plays?: number;
  rings?: number;
  wonCards?: number;
  hasReplay?: boolean;
}

type SortState = { key: keyof HistoryRow; dir: "asc" | "desc" };

const auth = useAuthStore();
const lobby = useLobbyStore();
const historySort = ref<SortState>({ key: "at", dir: "desc" });
const loadError = ref("");

const profile = computed(() => lobby.profile);

const username = computed(() => {
  const fromProfile = profile.value?.username;
  if (typeof fromProfile === "string" && fromProfile) return fromProfile;
  return auth.player?.username || auth.clientId || "";
});

const history = computed(() => {
  const raw = profile.value?.history;
  const rows = Array.isArray(raw) ? (raw as HistoryRow[]) : [];
  const copy = [...rows];
  copy.sort((a, b) => {
    const key = historySort.value.key;
    const av = a[key];
    const bv = b[key];
    const an = typeof av === "number" ? av : String(av ?? "");
    const bn = typeof bv === "number" ? bv : String(bv ?? "");
    if (an < bn) return historySort.value.dir === "asc" ? -1 : 1;
    if (an > bn) return historySort.value.dir === "asc" ? 1 : -1;
    return 0;
  });
  return copy;
});

function defeatedCount(computerId: string): number {
  const defeated = profile.value?.defeatedComputers;
  if (!defeated || typeof defeated !== "object") return 0;
  return Number((defeated as Record<string, unknown>)[computerId]) || 0;
}

function canReplay(row: HistoryRow): boolean {
  return Boolean(row.gameId) && row.hasReplay !== false;
}

function toggleHistorySort(key: keyof HistoryRow): void {
  if (historySort.value.key === key) {
    historySort.value = { key, dir: historySort.value.dir === "asc" ? "desc" : "asc" };
  } else {
    historySort.value = { key, dir: "desc" };
  }
}

onMounted(async () => {
  loadError.value = "";
  try {
    await lobby.loadMeta();
    if (auth.clientId) {
      await lobby.loadProfile(auth.clientId);
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "加载失败";
  }
});
</script>

<template>
  <AppShell>
    <PagePanel title="个人信息">
      <p v-if="lobby.loadingProfile" class="muted">加载中…</p>
      <p v-else-if="loadError" class="error-text">{{ loadError }}</p>
      <template v-else-if="profile">
        <p class="status-line">当前账号：{{ username }}</p>

        <div class="menu-grid profile-stats">
          <div class="card">参与 {{ recordField(profile, "gamesPlayed", 0) }}</div>
          <div class="card">胜场 {{ recordField(profile, "wins", 0) }}</div>
          <div class="card">胜率 {{ fmtPct(profile.winRate) }}</div>
          <div class="card">
            拍铃 {{ recordField(profile, "rings", 0) }} / 正确 {{ recordField(profile, "correctRings", 0) }} /
            错误 {{ recordField(profile, "wrongRings", 0) }}
          </div>
          <div class="card">赢牌 {{ recordField(profile, "wonCards", 0) }}</div>
          <div class="card">平均排名 {{ fmtNum(profile.averageRank) }}</div>
        </div>

        <h3 class="section-title">战胜人机</h3>
        <div class="menu-grid profile-stats">
          <div
            v-for="computer in lobby.computerPlayers"
            :key="computer.id"
            class="card"
            :class="{ 'god-name': isGodComputer(computer) }"
          >
            {{ computer.name }}：{{ defeatedCount(computer.id) }}
          </div>
        </div>

        <h3 class="section-title">历史对局</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>
                  <button type="button" class="sort-btn" @click="toggleHistorySort('at')">时间</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleHistorySort('roomId')">房间</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleHistorySort('playerCount')">人数</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleHistorySort('rank')">排名</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleHistorySort('plays')">出牌</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleHistorySort('rings')">拍铃</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleHistorySort('wonCards')">赢牌</button>
                </th>
                <th>回放</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!history.length">
                <td colspan="8">暂无历史记录</td>
              </tr>
              <tr v-for="(row, index) in history" :key="`${row.gameId ?? row.at}-${row.roomId}-${index}`">
                <td>{{ formatDate(row.at) }}</td>
                <td>{{ row.roomId ?? "—" }}</td>
                <td>{{ row.playerCount ?? "—" }}</td>
                <td>{{ row.rank ?? "—" }}</td>
                <td>{{ row.plays ?? "—" }}</td>
                <td>{{ row.rings ?? "—" }}</td>
                <td>{{ row.wonCards ?? "—" }}</td>
                <td>
                  <RouterLink
                    v-if="canReplay(row)"
                    class="action-link"
                    :to="{ name: 'match-replay', params: { gameId: row.gameId } }"
                  >
                    观看回放
                  </RouterLink>
                  <span v-else class="muted">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
      <p v-else class="muted">暂无战绩数据。</p>

      <div class="actions">
        <RouterLink class="action-link" to="/">
          <button type="button">返回</button>
        </RouterLink>
      </div>
    </PagePanel>
  </AppShell>
</template>
