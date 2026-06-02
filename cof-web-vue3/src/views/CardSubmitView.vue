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
  deleteSubmissionCard,
  deleteSubmissionDeck,
  deleteSubmissionPmv,
  fetchMySubmissions,
  uploadSubmissionBack,
  type SubmissionCard,
  type SubmissionDeck,
  type SubmissionPmv,
} from "@/api/submissions";
import { useActionToast } from "@/composables/useActionToast";
import { useLobbyStore } from "@/stores/lobbyStore";
import type { PmvIndexRow } from "@/types/pmvIndex";
import { recordField } from "@/utils/record";

const SHOTS = Array.from({ length: 20 }, (_, index) => String.fromCharCode(97 + index));

const lobby = useLobbyStore();
const { toast, showAt, clear } = useActionToast();

const loading = ref(false);
const myDecks = ref<SubmissionDeck[]>([]);
const activeTab = ref<string>("");

const deckName = ref("");
const deckCurator = ref("");
const deckDescription = ref("");

const pmvId = ref(100);
const pmvName = ref("");
const pmvAuthor = ref("");
const pmvDescription = ref("");
const pmvLink = ref("");
const pmvSearch = ref("");

const backCropRef = ref<InstanceType<typeof ImageCropField> | null>(null);
const cardCropRef = ref<InstanceType<typeof ImageCropField> | null>(null);
const uploadTarget = ref<{ deckId: number; pmvId: number; shot: string } | null>(null);

const tabDeck = computed(() => myDecks.value.find((d) => String(d.id) === activeTab.value));
const activeDeckId = computed(() => tabDeck.value?.id ?? null);

const pmvRows = computed(() => {
  const q = pmvSearch.value.trim().toLocaleLowerCase();
  let list = (lobby.pmvIndex as PmvIndexRow[]).slice();
  if (q) {
    list = list.filter((row) =>
      [row.pmvId, row.name, row.author, row.libraryName].some((value) =>
        String(value ?? "").toLocaleLowerCase().includes(q),
      ),
    );
  }
  return list.sort((a, b) => Number(a.pmvId) - Number(b.pmvId));
});

async function refreshMine(): Promise<void> {
  loading.value = true;
  try {
    const mine = await fetchMySubmissions();
    myDecks.value = mine.decks ?? [];
    if (!activeTab.value || !myDecks.value.some((deck) => String(deck.id) === activeTab.value)) {
      activeTab.value = String(myDecks.value[0]?.id ?? "");
    }
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void Promise.all([refreshMine(), lobby.loadPmvIndex()]);
});

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
    activeTab.value = String(deck.id ?? "");
    deckName.value = "";
    deckCurator.value = "";
    deckDescription.value = "";
    showAt(event, `已创建卡组「${deck.name}」`, "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function submitBack(event: MouseEvent): Promise<void> {
  if (!activeDeckId.value) {
    showAt(event, "请先在“我的提交”选择一个卡组", "error");
    return;
  }
  try {
    const { blob } = await backCropRef.value!.getCroppedBlob();
    await uploadSubmissionBack(activeDeckId.value, blob);
    showAt(event, "牌背已上传", "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function submitPmv(event: MouseEvent): Promise<void> {
  if (!activeDeckId.value) {
    showAt(event, "请先在“我的提交”选择一个卡组", "error");
    return;
  }
  try {
    await addSubmissionPmv(activeDeckId.value, {
      pmvId: pmvId.value,
      name: pmvName.value.trim(),
      author: pmvAuthor.value.trim() || undefined,
      description: pmvDescription.value.trim() || undefined,
      link: pmvLink.value.trim() || undefined,
    });
    pmvName.value = "";
    pmvAuthor.value = "";
    pmvDescription.value = "";
    pmvLink.value = "";
    showAt(event, "PMV 已添加", "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function removeDeck(event: MouseEvent, deck: SubmissionDeck): Promise<void> {
  if (!deck.id || !window.confirm(`确定删除卡组「${deck.name}」吗？`)) return;
  try {
    await deleteSubmissionDeck(deck.id);
    activeTab.value = "";
    showAt(event, "卡组已删除", "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function removePmv(event: MouseEvent, pmv: SubmissionPmv): Promise<void> {
  if (!activeDeckId.value || !pmv.pmvId || !window.confirm(`确定删除 PMV「${pmv.name}」吗？`)) return;
  try {
    await deleteSubmissionPmv(activeDeckId.value, pmv.pmvId);
    showAt(event, "PMV 已删除", "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function removeCard(event: MouseEvent, card: SubmissionCard): Promise<void> {
  if (!activeDeckId.value || !card.pmvId || !card.shot || !window.confirm(`确定删除编号 ${shotNumber(card.shot)} 吗？`)) return;
  try {
    await deleteSubmissionCard(activeDeckId.value, card.pmvId, card.shot);
    showAt(event, "截图已删除", "success");
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

function cardsForPmv(deck: SubmissionDeck, pmvId?: number): SubmissionCard[] {
  return (deck.cards || [])
    .filter((card) => card.pmvId === pmvId)
    .sort((a, b) => shotNumber(a.shot) - shotNumber(b.shot));
}

function nextShot(deck: SubmissionDeck, pmvId?: number): string | null {
  const used = new Set(cardsForPmv(deck, pmvId).map((card) => card.shot));
  return SHOTS.find((shot) => !used.has(shot)) ?? null;
}

function shotNumber(shot?: string): number {
  if (!shot) return 0;
  const index = SHOTS.indexOf(shot.toLowerCase());
  return index >= 0 ? index + 1 : 0;
}

function beginShotUpload(deck: SubmissionDeck, pmv: SubmissionPmv): void {
  if (!deck.id || !pmv.pmvId) return;
  const shot = nextShot(deck, pmv.pmvId);
  if (!shot) return;
  uploadTarget.value = { deckId: deck.id, pmvId: pmv.pmvId, shot };
}

async function confirmShotUpload(event: MouseEvent): Promise<void> {
  if (!uploadTarget.value) return;
  try {
    const { blob } = await cardCropRef.value!.getCroppedBlob();
    await addSubmissionCard(
      uploadTarget.value.deckId,
      uploadTarget.value.pmvId,
      uploadTarget.value.shot,
      blob,
    );
    showAt(event, `编号 ${shotNumber(uploadTarget.value.shot)} 已上传`, "success");
    uploadTarget.value = null;
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}
</script>

<template>
  <AppShell>
    <PagePanel title="提交卡组">
      <p class="muted">
        提交的卡组、PMV 与截图默认进入待审核；审核通过前仅自己可在图鉴中预览，不会进入对局卡池。
      </p>

      <section class="submit-section">
        <h3 class="section-title">1. 创建卡组</h3>
        <div class="form-grid">
          <label>
            卡组名称 <span class="req">*</span>
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
        <button type="button" :disabled="!deckName.trim()" @click="submitDeck($event)">创建卡组</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">2. 上传牌背 (720x1087)</h3>
        <p class="muted">上传到“我的提交”当前选中的卡组。</p>
        <ImageCropField
          ref="backCropRef"
          label="牌背图片"
          :aspect-ratio="720 / 1087"
          :output-width="720"
          :output-height="1087"
          hint="牌背竖版 720x1087，提交前会裁剪并压缩为 JPEG。"
        />
        <button type="button" :disabled="!activeDeckId" @click="submitBack($event)">上传牌背</button>
      </section>

      <section id="pmv-guide" class="submit-section">
        <h3 class="section-title">3. 添加 PMV</h3>
        <div class="guide-compact muted">
          <p>
            每个 PMV 使用数字 <code>pmv_id</code> 标识；同一 PMV 的截图在“我的提交”中上传，单个 PMV 最多 20 张。
          </p>
        </div>
        <div class="pmv-form">
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
          <label>
            PMV 描述
            <textarea v-model="pmvDescription" rows="3" maxlength="2000" />
          </label>
          <label>
            链接
            <input v-model="pmvLink" type="url" maxlength="512" />
          </label>
        </div>
        <label class="pmv-search-row">
          查询已有 PMV（id / 名称 / 作者 / 卡组）
          <input v-model="pmvSearch" autocomplete="off" placeholder="搜索..." />
        </label>
        <div class="pmv-index-scroll table-wrap">
          <table>
            <thead>
              <tr>
                <th>id</th>
                <th>PMV 名</th>
                <th>作者</th>
                <th>卡组</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!pmvRows.length">
                <td colspan="4">无匹配条目</td>
              </tr>
              <tr v-for="(row, index) in pmvRows" :key="`${row.pmvId}-${index}`">
                <td>{{ recordField(row, "pmvId", "-") }}</td>
                <td>{{ recordField(row, "name", "-") }}</td>
                <td>{{ recordField(row, "author", "") || "-" }}</td>
                <td>{{ recordField(row, "libraryName", "-") }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <button type="button" :disabled="!activeDeckId || !pmvName.trim()" @click="submitPmv($event)">
          添加 PMV
        </button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">我的提交</h3>
        <p v-if="loading" class="muted">加载中...</p>
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
            <div class="submission-header">
              <div>
                <p>
                  <span class="badge">{{ statusLabel(tabDeck.reviewStatus) }}</span>
                  <span class="muted">#{{ tabDeck.id }} · {{ tabDeck.cardCount ?? 0 }} 张 · {{ tabDeck.pmvCount ?? 0 }} PMV</span>
                </p>
                <p v-if="tabDeck.description" class="muted">{{ tabDeck.description }}</p>
              </div>
              <button type="button" class="danger" @click="removeDeck($event, tabDeck)">删除卡组</button>
            </div>

            <div v-if="tabDeck.backUrl" class="thumb-row">
              <img :src="tabDeck.backUrl" alt="牌背" class="submit-thumb back-thumb" />
            </div>

            <div v-if="tabDeck.pmvs?.length" class="pmv-mini-list">
              <div v-for="pmv in tabDeck.pmvs" :key="`${pmv.pmvId}`" class="pmv-mini">
                <div class="pmv-mini-head">
                  <div>
                    <strong>{{ pmv.name }}</strong>
                    <span class="muted"> id {{ pmv.pmvId }}</span>
                    <p v-if="pmv.description" class="muted">{{ pmv.description }}</p>
                  </div>
                  <button type="button" class="danger" @click="removePmv($event, pmv)">删除 PMV</button>
                </div>

                <div class="submission-shots">
                  <figure
                    v-for="card in cardsForPmv(tabDeck, pmv.pmvId)"
                    :key="card.id"
                    class="shot-tile"
                  >
                    <button type="button" class="shot-remove" title="删除截图" @click="removeCard($event, card)">x</button>
                    <img :src="card.imageUrl" alt="" />
                    <figcaption>编号 {{ shotNumber(card.shot) }}</figcaption>
                  </figure>

                  <button
                    v-if="nextShot(tabDeck, pmv.pmvId)"
                    type="button"
                    class="shot-add"
                    @click="beginShotUpload(tabDeck, pmv)"
                  >
                    <span>+</span>
                  </button>
                </div>

                <div
                  v-if="uploadTarget?.deckId === tabDeck.id && uploadTarget?.pmvId === pmv.pmvId"
                  class="shot-uploader"
                >
                  <p class="muted">上传编号 {{ shotNumber(uploadTarget.shot) }}（1500x1080）</p>
                  <ImageCropField
                    ref="cardCropRef"
                    label="PMV 截图"
                    :aspect-ratio="1500 / 1080"
                    :output-width="1500"
                    :output-height="1080"
                    hint="横版 1500x1080，提交前会裁剪并压缩为 JPEG。"
                  />
                  <div class="actions">
                    <button type="button" class="primary" @click="confirmShotUpload($event)">确认上传</button>
                    <button type="button" @click="uploadTarget = null">取消</button>
                  </div>
                </div>
              </div>
            </div>
            <p v-else class="muted">这个卡组还没有 PMV。</p>
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
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin: 1.25rem 0;
  padding-bottom: 1rem;
}

.pmv-form {
  display: grid;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
  max-width: 720px;
}

.form-grid {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  margin-bottom: 0.75rem;
}

.form-grid label,
.pmv-form label {
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

.badge {
  background: rgba(255, 200, 80, 0.2);
  border-radius: 4px;
  font-size: 0.75rem;
  margin-right: 0.35rem;
  padding: 0.1rem 0.35rem;
}

.pmv-index-scroll {
  margin: 0.5rem 0 0.75rem;
  max-height: 200px;
  overflow: auto;
}

.submit-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-bottom: 0.75rem;
}

.submit-tab {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: inherit;
  cursor: pointer;
  padding: 0.35rem 0.65rem;
}

.submit-tab.active {
  background: rgba(120, 180, 255, 0.2);
}

.submission-header,
.pmv-mini-head {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.submit-thumb {
  border-radius: 4px;
  max-height: 160px;
  max-width: 120px;
  object-fit: contain;
}

.back-thumb {
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.pmv-mini {
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  margin: 0.8rem 0;
  padding-top: 0.8rem;
}

.submission-shots {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  margin-top: 0.75rem;
}

.shot-tile {
  margin: 0;
  position: relative;
}

.shot-tile img,
.shot-add {
  aspect-ratio: 1500 / 1080;
  border-radius: 6px;
  width: 100%;
}

.shot-tile img {
  border: 1px solid rgba(255, 255, 255, 0.2);
  display: block;
  object-fit: cover;
}

.shot-tile figcaption {
  color: var(--muted);
  font-size: 12px;
  margin-top: 0.3rem;
  text-align: center;
}

.shot-remove {
  align-items: center;
  background: rgba(0, 0, 0, 0.72);
  border-radius: 999px;
  color: #fff;
  display: inline-flex;
  height: 24px;
  justify-content: center;
  min-height: 24px;
  padding: 0;
  position: absolute;
  right: 6px;
  top: 6px;
  width: 24px;
  z-index: 2;
}

.shot-add {
  align-items: center;
  background: rgba(255, 255, 255, 0.03);
  border: 1px dashed rgba(255, 255, 255, 0.45);
  color: var(--muted);
  display: flex;
  justify-content: center;
  min-height: 96px;
}

.shot-add span {
  font-size: 42px;
  line-height: 1;
}

.shot-uploader {
  background: rgba(8, 11, 8, 0.36);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  margin-top: 0.75rem;
  padding: 0.75rem;
}

.guide-compact {
  font-size: 0.9rem;
  margin-bottom: 0.75rem;
}
</style>
