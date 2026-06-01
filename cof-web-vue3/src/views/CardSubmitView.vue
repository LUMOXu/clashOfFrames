<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import ActionToast from "@/components/ActionToast.vue";
import AppShell from "@/components/AppShell.vue";
import ImageCropField from "@/components/ImageCropField.vue";
import PagePanel from "@/components/PagePanel.vue";
import {
  addSubmissionCard,
  addSubmissionPmv,
  createSubmissionDeck,
  fetchEditableDecks,
  fetchMySubmissions,
  uploadSubmissionBack,
  type EditableDeckOption,
  type SubmissionDeck,
} from "@/api/submissions";
import { useActionToast } from "@/composables/useActionToast";
import { useLobbyStore } from "@/stores/lobbyStore";
import type { PmvIndexRow } from "@/types/pmvIndex";
import { recordField } from "@/utils/record";

const lobby = useLobbyStore();
const { toast, showAt, clear } = useActionToast();

const loading = ref(false);
const myDecks = ref<SubmissionDeck[]>([]);
const editableDecks = ref<EditableDeckOption[]>([]);
const activeDeckId = ref<number | null>(null);
const activeTab = ref<string>("");

const deckName = ref("");
const deckCurator = ref("");
const deckDescription = ref("");

const pmvId = ref(100);
const pmvName = ref("");
const pmvAuthor = ref("");
const pmvLink = ref("");
const pmvSearch = ref("");

const cardShot = ref("a");
const cardPmvId = ref(100);

const backCropRef = ref<InstanceType<typeof ImageCropField> | null>(null);
const cardCropRef = ref<InstanceType<typeof ImageCropField> | null>(null);

const ownedDecks = computed(() => editableDecks.value.filter((d) => d.owned));

const selectedDeck = computed(() =>
  editableDecks.value.find((d) => d.id === activeDeckId.value),
);

const pmvRows = computed(() => {
  const q = pmvSearch.value.trim().toLocaleLowerCase();
  let list = (lobby.pmvIndex as PmvIndexRow[]).slice();
  if (q) {
    list = list.filter((row) =>
      [row.pmvId, row.name, row.author, row.libraryName].some((value) =>
        String(value ?? "")
          .toLocaleLowerCase()
          .includes(q),
      ),
    );
  }
  return list.sort((a, b) => Number(a.pmvId) - Number(b.pmvId));
});

async function refreshMine(): Promise<void> {
  loading.value = true;
  try {
    const [mine, editable] = await Promise.all([fetchMySubmissions(), fetchEditableDecks()]);
    myDecks.value = mine.decks ?? [];
    editableDecks.value = editable.decks ?? [];
    if (!activeDeckId.value && ownedDecks.value.length) {
      activeDeckId.value = ownedDecks.value[0]?.id ?? null;
    }
    if (!activeTab.value && myDecks.value.length) {
      activeTab.value = String(myDecks.value[0]?.id ?? "");
    }
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void Promise.all([refreshMine(), lobby.loadPmvIndex()]);
});

function pickDeck(id: number): void {
  activeDeckId.value = id;
}

function onError(event: MouseEvent, e: unknown): void {
  showAt(event, e instanceof Error ? e.message : "操作失败", "error");
}

async function submitDeck(event: MouseEvent): Promise<void> {
  try {
    const { deck } = await createSubmissionDeck({
      name: deckName.value.trim(),
      curator: deckCurator.value.trim() || undefined,
      description: deckDescription.value.trim() || undefined,
    });
    activeDeckId.value = deck.id ?? null;
    activeTab.value = String(deck.id ?? "");
    deckName.value = "";
    showAt(event, `已创建牌组「${deck.name}」（待审核）`, "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function submitBack(event: MouseEvent): Promise<void> {
  if (!activeDeckId.value) {
    showAt(event, "请先选择你自己创建的牌组", "error");
    return;
  }
  if (!selectedDeck.value?.owned) {
    showAt(event, "只能向自己创建的牌组上传", "error");
    return;
  }
  try {
    const { blob } = await backCropRef.value!.getCroppedBlob();
    await uploadSubmissionBack(activeDeckId.value, blob);
    showAt(event, "牌背已上传（待审核）", "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function submitPmv(event: MouseEvent): Promise<void> {
  if (!activeDeckId.value || !selectedDeck.value?.owned) {
    showAt(event, "请先选择你自己创建的牌组", "error");
    return;
  }
  try {
    await addSubmissionPmv(activeDeckId.value, {
      pmvId: pmvId.value,
      name: pmvName.value.trim(),
      author: pmvAuthor.value.trim() || undefined,
      link: pmvLink.value.trim() || undefined,
    });
    pmvName.value = "";
    showAt(event, "PMV 已添加（待审核）", "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function submitCard(event: MouseEvent): Promise<void> {
  if (!activeDeckId.value || !selectedDeck.value?.owned) {
    showAt(event, "请先选择你自己创建的牌组", "error");
    return;
  }
  try {
    const { blob } = await cardCropRef.value!.getCroppedBlob();
    await addSubmissionCard(activeDeckId.value, cardPmvId.value, cardShot.value.trim(), blob);
    showAt(event, "卡牌已上传（待审核）", "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

function statusLabel(status?: string): string {
  if (status === "approved") return "已通过";
  if (status === "rejected") return "已拒绝";
  return "待审核";
}

const tabDeck = computed(() => myDecks.value.find((d) => String(d.id) === activeTab.value));
</script>

<template>
  <AppShell>
    <PagePanel title="提交牌组">
      <p class="muted">
        提交的牌组、PMV 与卡牌默认为<strong>待审核</strong>，仅自己可在图鉴中预览，<strong>不能用于对局</strong>。审核通过后公开。
      </p>

      <section class="submit-section">
        <h3 class="section-title">1. 创建牌组</h3>
        <div class="form-grid">
          <label>
            牌组名称 <span class="req">*</span>
            <input v-model="deckName" type="text" maxlength="120" placeholder="例如：我的试做包" />
          </label>
          <label>
            整理者
            <input v-model="deckCurator" type="text" maxlength="120" />
          </label>
          <label class="full-width">
            说明
            <textarea v-model="deckDescription" rows="2" maxlength="2000" />
          </label>
        </div>
        <button type="button" :disabled="!deckName.trim()" @click="submitDeck($event)">创建牌组</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">2. 选择编辑牌组</h3>
        <p class="muted">
          下列包含<strong>已公开牌组</strong>与<strong>你创建但未审核</strong>的牌组。仅<strong>自己创建</strong>的牌组可上传内容。
        </p>
        <div class="deck-picker">
          <label
            v-for="deck in editableDecks"
            :key="deck.id"
            class="deck-picker-item"
            :class="{ active: activeDeckId === deck.id, disabled: !deck.owned }"
          >
            <input
              type="radio"
              name="active-deck"
              :value="deck.id"
              :disabled="!deck.owned"
              :checked="activeDeckId === deck.id"
              @change="pickDeck(deck.id)"
            />
            <span>
              <strong>{{ deck.name }}</strong>
              <span v-if="deck.curator" class="muted"> · {{ deck.curator }}</span>
              <span class="badge">{{ deck.owned ? statusLabel(deck.reviewStatus) : "仅浏览" }}</span>
            </span>
          </label>
        </div>
      </section>

      <section class="submit-section">
        <h3 class="section-title">3. 上传牌背 (720×1087)</h3>
        <ImageCropField
          ref="backCropRef"
          label="牌背图片"
          :aspect-ratio="720 / 1087"
          :output-width="720"
          :output-height="1087"
          hint="牌背竖版 720×1087，服务端压缩为 JPEG。"
        />
        <button type="button" @click="submitBack($event)">上传牌背</button>
      </section>

      <section id="pmv-guide" class="submit-section">
        <h3 class="section-title">4. 添加 PMV</h3>
        <div class="guide-compact muted">
          <p>
            每个 PMV 用数字 <code>pmv_id</code> 标识；每张镜头图为横版 <strong>1500×1080</strong> JPEG，文件名对应
            <code>{pmv_id}{镜头字母}.jpg</code>（如 <code>100a.jpg</code>）。添加 PMV 前请在下表查询是否已有相同 PMV，尽量沿用已有 id。
          </p>
        </div>
        <div class="form-grid">
          <label>
            PMV ID <span class="req">*</span>
            <input v-model.number="pmvId" type="number" min="1" />
          </label>
          <label>
            名称 <span class="req">*</span>
            <input v-model="pmvName" type="text" maxlength="512" />
          </label>
          <label>
            作者
            <input v-model="pmvAuthor" type="text" maxlength="255" />
          </label>
          <label class="full-width">
            链接
            <input v-model="pmvLink" type="url" maxlength="512" />
          </label>
        </div>
        <label class="pmv-search-row">
          查询已有 PMV（id / 名称 / 作者 / 卡包）
          <input v-model="pmvSearch" autocomplete="off" placeholder="搜索…" />
        </label>
        <div class="pmv-index-scroll table-wrap">
          <table>
            <thead>
              <tr>
                <th>id</th>
                <th>PMV 名</th>
                <th>作者</th>
                <th>卡包</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!pmvRows.length">
                <td colspan="4">无匹配条目</td>
              </tr>
              <tr v-for="(row, index) in pmvRows" :key="`${row.pmvId}-${index}`">
                <td>{{ recordField(row, "pmvId", "—") }}</td>
                <td>{{ recordField(row, "name", "—") }}</td>
                <td>{{ recordField(row, "author", "") || "—" }}</td>
                <td>{{ recordField(row, "libraryName", "—") }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <button type="button" :disabled="!pmvName.trim()" @click="submitPmv($event)">添加 PMV</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">5. 上传 PMV 镜头 (1500×1080)</h3>
        <div class="form-grid">
          <label>
            PMV ID
            <input v-model.number="cardPmvId" type="number" min="1" />
          </label>
          <label>
            镜头 (a-z) <span class="req">*</span>
            <input v-model="cardShot" type="text" maxlength="1" placeholder="a" />
          </label>
        </div>
        <ImageCropField
          ref="cardCropRef"
          label="卡牌正面"
          :aspect-ratio="1500 / 1080"
          :output-width="1500"
          :output-height="1080"
          hint="横版帧 1500×1080，服务端按短边缩放居中裁切并压缩。"
        />
        <button type="button" @click="submitCard($event)">上传卡牌</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">我的提交</h3>
        <p v-if="loading" class="muted">加载中…</p>
        <template v-else-if="myDecks.length">
          <div class="submit-tabs">
            <button
              v-for="deck in myDecks"
              :key="deck.id"
              type="button"
              class="submit-tab"
              :class="{ active: activeTab === String(deck.id) }"
              @click="activeTab = String(deck.id ?? '')"
            >
              {{ deck.name }}
            </button>
          </div>
          <div v-if="tabDeck" class="submit-tab-panel">
            <p>
              <span class="badge">{{ statusLabel(tabDeck.reviewStatus) }}</span>
              <span class="muted">#{{ tabDeck.id }} · {{ tabDeck.cardCount ?? 0 }} 张 · {{ tabDeck.pmvCount ?? 0 }} PMV</span>
            </p>
            <p v-if="tabDeck.description" class="muted">{{ tabDeck.description }}</p>
            <div v-if="tabDeck.backUrl" class="thumb-row">
              <img :src="tabDeck.backUrl" alt="牌背" class="submit-thumb" />
            </div>
            <div v-if="tabDeck.pmvs?.length" class="pmv-mini-list">
              <div v-for="pmv in tabDeck.pmvs" :key="`${pmv.pmvId}`" class="pmv-mini">
                <strong>{{ pmv.name }}</strong> (id {{ pmv.pmvId }})
                <div class="card-thumbs">
                  <img
                    v-for="card in (tabDeck.cards || []).filter((c) => c.pmvId === pmv.pmvId)"
                    :key="card.id"
                    :src="card.imageUrl"
                    alt=""
                    class="submit-thumb card-thumb"
                  />
                </div>
              </div>
            </div>
            <button type="button" class="linkish" @click="pickDeck(tabDeck.id!)">编辑此牌组</button>
          </div>
        </template>
        <p v-else class="muted">暂无提交记录。</p>
      </section>

      <div class="actions">
        <RouterLink class="action-link" to="/cards/info">返回图鉴</RouterLink>
        <RouterLink class="action-link" to="/">主菜单</RouterLink>
      </div>
    </PagePanel>

    <ActionToast
      v-if="toast"
      :message="toast.message"
      :x="toast.x"
      :y="toast.y"
      :variant="toast.variant"
      @close="clear"
    />
  </AppShell>
</template>

<style scoped>
.submit-section {
  margin: 1.25rem 0;
  padding-bottom: 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.form-grid {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  margin-bottom: 0.75rem;
}
.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
}
.form-grid .full-width {
  grid-column: 1 / -1;
}
.req {
  color: #f88;
}
.deck-picker {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.deck-picker-item {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
  padding: 0.4rem 0.5rem;
  border-radius: 6px;
  cursor: pointer;
}
.deck-picker-item.active {
  background: rgba(120, 180, 255, 0.12);
}
.deck-picker-item.disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.badge {
  font-size: 0.75rem;
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  background: rgba(255, 200, 80, 0.2);
  margin-left: 0.35rem;
}
.pmv-index-scroll {
  max-height: 200px;
  overflow: auto;
  margin: 0.5rem 0 0.75rem;
}
.submit-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-bottom: 0.75rem;
}
.submit-tab {
  padding: 0.35rem 0.65rem;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.submit-tab.active {
  background: rgba(120, 180, 255, 0.2);
}
.submit-thumb {
  max-width: 120px;
  max-height: 160px;
  object-fit: contain;
  border-radius: 4px;
}
.card-thumb {
  max-width: 80px;
  max-height: 56px;
  margin-right: 0.25rem;
}
.card-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  margin-top: 0.35rem;
}
.pmv-mini {
  margin: 0.5rem 0;
}
.linkish {
  background: none;
  border: none;
  color: var(--link-color, #8cf);
  cursor: pointer;
  text-decoration: underline;
}
.guide-compact {
  font-size: 0.9rem;
  margin-bottom: 0.75rem;
}
</style>
