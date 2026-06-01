<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import type { LeaderboardEntry } from "@/types/api";
import { fmtLeaderboardRate, fmtNum, formatDate, isGodComputer } from "@/utils/format";
import { recordField } from "@/utils/record";

const lobby = useLobbyStore();
const loadError = ref("");

type SortState = { key: string; dir: "asc" | "desc" };

const playerSort = ref<SortState>({ key: "wins", dir: "desc" });
const matchSort = ref<SortState>({ key: "playCount", dir: "desc" });

const nullableSortKeys = new Set(["winRate", "correctRate", "averageRank"]);

onMounted(async () => {
  loadError.value = "";
  try {
    await Promise.all([lobby.loadMeta(), lobby.loadLeaderboard()]);
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "加载失败";
  }
});

const players = computed(() => {
  const rows = lobby.leaderboard?.players ?? [];
  const enriched = rows.map((row) => {
    const copy: LeaderboardEntry = { ...row };
    for (const col of computerColumns.value) {
      copy[`defeated_${col.id}`] = defeatedCount(row, col.id);
    }
    return copy;
  });
  return sortRows(enriched, playerSort.value);
});
const matches = computed(() => sortRows(lobby.leaderboard?.matches ?? [], matchSort.value));

const computerColumns = computed(() =>
  lobby.computerPlayers.map((computer) => ({
    id: computer.id,
    label: `胜 ${computer.name}`,
    isGod: isGodComputer(computer),
  })),
);

function sortValue(row: LeaderboardEntry, key: string): number | string | null {
  if (key === "winRate") {
    const games = Number(row.gamesPlayed);
    if (!Number.isFinite(games) || games <= 0) return null;
    return typeof row.winRate === "number" ? row.winRate : null;
  }
  if (key === "correctRate") {
    const rings = Number(row.rings);
    if (!Number.isFinite(rings) || rings <= 0) return null;
    return typeof row.correctRate === "number" ? row.correctRate : null;
  }
  if (key === "averageRank") {
    const games = Number(row.gamesPlayed);
    if (!Number.isFinite(games) || games <= 0) return null;
    return typeof row.averageRank === "number" ? row.averageRank : null;
  }
  const raw = row[key];
  if (raw === null || raw === undefined || raw === "") return null;
  return typeof raw === "number" ? raw : String(raw);
}

function sortRows(rows: LeaderboardEntry[], sort: SortState): LeaderboardEntry[] {
  const copy = [...rows];
  const nullsLast = nullableSortKeys.has(sort.key);
  copy.sort((a, b) => {
    const av = sortValue(a, sort.key);
    const bv = sortValue(b, sort.key);
    if (nullsLast) {
      if (av === null && bv === null) return 0;
      if (av === null) return 1;
      if (bv === null) return -1;
    }
    const an = av === null ? "" : av;
    const bn = bv === null ? "" : bv;
    if (an < bn) return sort.dir === "asc" ? -1 : 1;
    if (an > bn) return sort.dir === "asc" ? 1 : -1;
    return 0;
  });
  return copy;
}

function toggleSort(target: "players" | "matches", key: string): void {
  const state = target === "players" ? playerSort : matchSort;
  if (state.value.key === key) {
    state.value = { key, dir: state.value.dir === "asc" ? "desc" : "asc" };
  } else {
    state.value = { key, dir: "desc" };
  }
}

function defeatedCount(row: LeaderboardEntry, computerId: string): number {
  const defeated = row.defeatedComputers;
  if (!defeated || typeof defeated !== "object") return 0;
  const value = (defeated as Record<string, unknown>)[computerId];
  return Number(value) || 0;
}

function playerLabel(row: LeaderboardEntry): string {
  return recordField(row, "username", "—");
}

function isComputerRow(row: LeaderboardEntry): boolean {
  return Boolean(row.isComputer);
}

function replayLink(gameId?: string): { name: string; params: { gameId: string } } | null {
  if (!gameId) return null;
  return { name: "match-replay", params: { gameId } };
}
</script>

<template>
  <AppShell>
    <PagePanel title="排行榜">
      <p v-if="lobby.loadingLeaderboard" class="muted">加载中…</p>
      <p v-else-if="loadError" class="error-text">{{ loadError }}</p>
      <template v-else-if="lobby.leaderboard">
        <h3 class="section-title">玩家排行</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('players', 'username')">玩家</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('players', 'gamesPlayed')">对局</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('players', 'wins')">胜场</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('players', 'winRate')">胜率</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('players', 'correctRate')">
                    按铃正确率
                  </button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('players', 'wonCards')">赢牌</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('players', 'averageRank')">平均排名</button>
                </th>
                <th v-for="col in computerColumns" :key="col.id">
                  <button
                    type="button"
                    class="sort-btn"
                    :class="{ 'god-name': col.isGod }"
                    @click="toggleSort('players', `defeated_${col.id}`)"
                  >
                    {{ col.label }}
                  </button>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!players.length">
                <td :colspan="7 + computerColumns.length">暂无数据</td>
              </tr>
              <tr v-for="(row, index) in players" :key="String(row.statsId ?? index)">
                <td>
                  <span
                    :class="{
                      'god-name': isGodComputer({ id: String(row.computerId || ''), name: playerLabel(row) }),
                    }"
                  >
                    {{ playerLabel(row) }}
                  </span>
                  <span v-if="isComputerRow(row)" class="pill muted">人机</span>
                </td>
                <td>{{ recordField(row, "gamesPlayed", 0) }}</td>
                <td>{{ recordField(row, "wins", 0) }}</td>
                <td>{{ fmtLeaderboardRate(row.winRate, Number(row.gamesPlayed)) }}</td>
                <td>{{ fmtLeaderboardRate(row.correctRate, Number(row.gamesPlayed), Number(row.rings)) }}</td>
                <td>{{ recordField(row, "wonCards", 0) }}</td>
                <td>
                  {{
                    Number(row.gamesPlayed) > 0 && typeof row.averageRank === "number"
                      ? fmtNum(row.averageRank)
                      : "-"
                  }}
                </td>
                <td v-for="col in computerColumns" :key="`${index}-${col.id}`">
                  {{ defeatedCount(row, col.id) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <h3 class="section-title">对局记录</h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('matches', 'at')">时间</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('matches', 'roomId')">房间</button>
                </th>
                <th>回放</th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('matches', 'playerCount')">人数</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('matches', 'playCount')">出牌数</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('matches', 'bellCount')">拍铃数</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('matches', 'successBellCount')">成功</button>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!matches.length">
                <td colspan="7">暂无数据</td>
              </tr>
              <tr v-for="(row, index) in matches" :key="String(row.gameId ?? row.roomId ?? index)">
                <td>{{ formatDate(row.at) }}</td>
                <td>{{ recordField(row, "roomId", "—") }}</td>
                <td>
                  <RouterLink
                    v-if="replayLink(String(row.gameId || ''))"
                    class="action-link"
                    :to="replayLink(String(row.gameId || ''))!"
                  >
                    观看回放
                  </RouterLink>
                  <span v-else class="muted">—</span>
                </td>
                <td>{{ recordField(row, "playerCount", "—") }}</td>
                <td>{{ recordField(row, "playCount", "—") }}</td>
                <td>{{ recordField(row, "bellCount", "—") }}</td>
                <td>{{ recordField(row, "successBellCount", "—") }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
      <p v-else class="muted">暂无数据。</p>
      <div class="actions">
        <RouterLink class="action-link" to="/">
          <button type="button">返回</button>
        </RouterLink>
      </div>
    </PagePanel>
  </AppShell>
</template>
