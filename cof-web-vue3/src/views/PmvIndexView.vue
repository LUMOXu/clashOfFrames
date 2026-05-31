<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import type { PmvIndexRow } from "@/types/pmvIndex";
import { recordField } from "@/utils/record";

const lobby = useLobbyStore();
const search = ref("");
const sortKey = ref<keyof PmvIndexRow | "pmvId">("pmvId");
const sortDir = ref<"asc" | "desc">("asc");

onMounted(() => {
  void lobby.loadPmvIndex();
});

const rows = computed(() => {
  const q = search.value.trim().toLocaleLowerCase();
  let list = (lobby.pmvIndex as PmvIndexRow[]).slice();
  if (q) {
    list = list.filter((row) =>
      [row.pmvId, row.name, row.author, row.libraryName]
        .some((value) => String(value ?? "").toLocaleLowerCase().includes(q)),
    );
  }
  list.sort((a, b) => {
    const av = a[sortKey.value];
    const bv = b[sortKey.value];
    const an = typeof av === "number" ? av : String(av ?? "");
    const bn = typeof bv === "number" ? bv : String(bv ?? "");
    if (an < bn) return sortDir.value === "asc" ? -1 : 1;
    if (an > bn) return sortDir.value === "asc" ? 1 : -1;
    return 0;
  });
  return list;
});

function toggleSort(key: keyof PmvIndexRow): void {
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === "asc" ? "desc" : "asc";
  } else {
    sortKey.value = key;
    sortDir.value = "asc";
  }
}
</script>

<template>
  <AppShell>
    <PagePanel title="卡组提交指南">
      <p v-if="lobby.loadingPmvIndex" class="muted">加载中…</p>
      <template v-else>
        <div class="guide-grid">
          <section>
            <h3>卡组由什么组成</h3>
            <p>
              一个卡组是一套 PMV 截图、一张背面图和一个 manifest.json。截图数量不限；背面图使用
              <strong>720 × 1087</strong> 分辨率，文件名固定为 <code>back.png</code>。
            </p>
            <p>
              <code>manifest.json</code> 保存卡组元数据和每个 PMV 的信息。元数据可以填写卡组名字、整理者、说明、版本、链接；没有内容时留空即可。
            </p>
          </section>
          <section>
            <h3>文件夹结构</h3>
            <pre><code>卡组文件夹
|_ manifest.json
|_ back.png
|_ cards
   |_ 100a.png
   |_ 100b.png
   |_ 101a.png
   |_ 101b.png
   |_ 101c.png</code></pre>
            <p>截图文件名由 <code>pmv_id + 字母</code> 组成。例子里 id 为 100 的 PMV 有 2 张截图，id 为 101 的 PMV 有 3 张截图。</p>
          </section>
          <section>
            <h3>manifest.json 格式</h3>
            <pre><code>{
  "name": "卡组名字",
  "curator": "整理者",
  "description": "卡组说明",
  "version": null,
  "link": null,
  "pmvs": [
    {
      "pmv_id": 100,
      "name": "PMV名字1",
      "author": "作者1",
      "description": "链接（需要加双引号），为空请填写null",
      "link": "链接，为空请填写null"
    },
    {
      "pmv_id": 101,
      "name": "PMV名字2",
      "author": "作者2",
      "description": null,
      "link": null
    }
  ]
}</code></pre>
          </section>
          <section>
            <h3>PMV id 怎么选？</h3>
            <p>请先用下方查询功能确认你的卡组里是否有已经出现过的 PMV。如果有，请沿用对应 PMV id；如果没有，可以选择任意尚未出现过的 id。</p>
            <p>完成后把整个卡组文件夹压缩，发给页面下方的管理员。审核通过后，管理员会手动添加卡包。</p>
          </section>
        </div>

        <label class="pmv-search-row">
          搜索 PMV id / 名字 / 作者 / 卡组
          <input v-model="search" autocomplete="off" placeholder="输入 PMV 名称或 id" />
        </label>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('pmvId')">id</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('name')">PMV 名</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('author')">作者</button>
                </th>
                <th>
                  <button type="button" class="sort-btn" @click="toggleSort('libraryName')">卡包</button>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!rows.length">
                <td colspan="4">暂无索引条目</td>
              </tr>
              <tr v-for="(row, index) in rows" :key="`${row.pmvId}-${row.libraryName}-${index}`">
                <td>{{ recordField(row, "pmvId", "—") }}</td>
                <td>{{ recordField(row, "name", "—") }}</td>
                <td>{{ recordField(row, "author", "") || "—" }}</td>
                <td>{{ recordField(row, "libraryName", "—") }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <div class="actions">
        <RouterLink class="action-link" to="/">
          <button type="button">返回</button>
        </RouterLink>
      </div>
    </PagePanel>
  </AppShell>
</template>
