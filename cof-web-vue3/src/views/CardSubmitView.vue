<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import ActionToast from "@/components/ActionToast.vue";
import AppShell from "@/components/AppShell.vue";
import ImageCropField from "@/components/ImageCropField.vue";
import PagePanel from "@/components/PagePanel.vue";
import {
  addSubmissionCard,
  createSubmissionDeck,
  createSubmissionPmv,
  deleteSubmissionCard,
  deleteSubmissionDeck,
  fetchMySubmissions,
  fetchSubmissionPmvs,
  uploadSubmissionBack,
  type SubmissionCard,
  type SubmissionDeck,
  type SubmissionPmv,
} from "@/api/submissions";
import { useActionToast } from "@/composables/useActionToast";

const { toast, showAt, clear } = useActionToast();

const loading = ref(false);
const myDecks = ref<SubmissionDeck[]>([]);
const pickerPmvs = ref<SubmissionPmv[]>([]);
const activeTab = ref<string>("");

const deckName = ref("");
const deckDescription = ref("");

const pmvName = ref("");
const pmvAuthor = ref("");
const pmvDescription = ref("");
const pmvLink = ref("");
const pmvSearch = ref("");

/** 与原生 <select> 一致使用字符串，避免 number 绑定导致选项无法选中 */
const selectedPmvId = ref("");
const cardName = ref("");
const cardDescription = ref("");

const backCropRef = ref<InstanceType<typeof ImageCropField> | null>(null);
const cardCropRef = ref<InstanceType<typeof ImageCropField> | null>(null);
const showCardUploader = ref(false);

interface PmvGroup {
  pmvId: number;
  label: string;
  cards: SubmissionCard[];
  reviewStatus?: string;
  pendingReviewStatus?: string;
}

function resolveCropRef(
  target: InstanceType<typeof ImageCropField> | InstanceType<typeof ImageCropField>[] | null,
): InstanceType<typeof ImageCropField> | null {
  if (!target) return null;
  if (Array.isArray(target)) {
    return target.find((c) => c && typeof c.getCroppedBlob === "function") ?? null;
  }
  return typeof target.getCroppedBlob === "function" ? target : null;
}

const tabDeck = computed(() => myDecks.value.find((d) => String(d.id) === activeTab.value));
const activeDeckId = computed(() => tabDeck.value?.id ?? null);

/** 第 4 步上传区卡组选择，与下方「我的提交」标签双向同步 */
const uploadDeckId = computed({
  get: () => activeTab.value,
  set: (value: string) => {
    activeTab.value = value;
  },
});

function pmvRowId(pmv: SubmissionPmv): number {
  const id = pmv.id ?? pmv.pmvId;
  return typeof id === "number" && Number.isFinite(id) ? id : NaN;
}

const sortedPickerPmvs = computed(() =>
  pickerPmvs.value
    .filter((row) => Number.isFinite(pmvRowId(row)))
    .sort((a, b) =>
      String(a.name ?? "").localeCompare(String(b.name ?? ""), undefined, { sensitivity: "base" }),
    ),
);

/** 仅用于第 3 步 PMV 索引表，不影响上传卡牌的下拉选项 */
const filteredPmvIndexRows = computed(() => {
  const q = pmvSearch.value.trim().toLocaleLowerCase();
  let list = sortedPickerPmvs.value;
  if (q) {
    list = list.filter((row) =>
      [pmvRowId(row), row.name, row.author].some((value) =>
        String(value ?? "").toLocaleLowerCase().includes(q),
      ),
    );
  }
  return list;
});

const pmvSelectDisabledReason = computed((): string | null => {
  if (!myDecks.value.length) return "请先在第 1 步创建卡组。";
  if (!uploadDeckId.value) return "请先选择要上传到的卡组。";
  if (!sortedPickerPmvs.value.length) return "请先在第 3 步创建 PMV，或等待已有 PMV 审核通过。";
  return null;
});

function pmvOptionLabel(pmv: SubmissionPmv): string {
  const author = pmv.author?.trim() || "未知作者";
  return `${pmv.name ?? "未命名"} — ${author}`;
}

function pmvLabel(pmvId: number): string {
  const pmv = pickerPmvs.value.find((p) => (p.id ?? p.pmvId) === pmvId);
  return pmv ? pmvOptionLabel(pmv) : `PMV #${pmvId}`;
}

function cardsForPmv(deck: SubmissionDeck, pmvId: number): SubmissionCard[] {
  return (deck.cards ?? []).filter((c) => c.pmvId === pmvId);
}

function pmvGroups(deck: SubmissionDeck): PmvGroup[] {
  const ids = new Set<number>();
  for (const card of deck.cards ?? []) {
    if (card.pmvId != null) ids.add(card.pmvId);
  }
  return Array.from(ids).map((pmvId) => {
    const cards = cardsForPmv(deck, pmvId);
    const first = cards[0];
    const pmv = pickerPmvs.value.find((p) => (p.id ?? p.pmvId) === pmvId);
    return {
      pmvId,
      label: pmvLabel(pmvId),
      cards,
      reviewStatus: first?.pmvReviewStatus ?? pmv?.reviewStatus,
      pendingReviewStatus: first?.pmvPendingReviewStatus ?? pmv?.pendingReviewStatus,
    };
  });
}

async function refreshPmvs(): Promise<void> {
  const res = await fetchSubmissionPmvs();
  pickerPmvs.value = res.pmvs ?? [];
}

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
  void Promise.all([refreshMine(), refreshPmvs()]);
});

function onError(event: MouseEvent, e: unknown): void {
  showAt(event, e instanceof Error ? e.message : "操作失败", "error");
}

async function submitDeck(event: MouseEvent): Promise<void> {
  try {
    const { deck } = await createSubmissionDeck({
      name: deckName.value.trim(),
      description: deckDescription.value.trim() || undefined,
    });
    activeTab.value = String(deck.id ?? "");
    deckName.value = "";
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
    const crop = resolveCropRef(backCropRef.value);
    if (!crop) {
      showAt(event, "请先选择牌背图片", "error");
      return;
    }
    const { blob } = await crop.getCroppedBlob();
    await uploadSubmissionBack(activeDeckId.value, blob);
    showAt(event, "牌背已上传", "success");
    await refreshMine();
  } catch (e) {
    onError(event, e);
  }
}

async function submitPmv(event: MouseEvent): Promise<void> {
  try {
    await createSubmissionPmv({
      name: pmvName.value.trim(),
      author: pmvAuthor.value.trim() || undefined,
      description: pmvDescription.value.trim() || undefined,
      link: pmvLink.value.trim() || undefined,
    });
    pmvName.value = "";
    pmvAuthor.value = "";
    pmvDescription.value = "";
    pmvLink.value = "";
    showAt(event, "PMV 已创建", "success");
    await refreshPmvs();
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

async function removeCard(event: MouseEvent, card: SubmissionCard): Promise<void> {
  if (!card.id || !window.confirm("确定删除这张卡牌吗？")) return;
  try {
    await deleteSubmissionCard(card.id);
    showAt(event, "卡牌已删除", "success");
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

function isApprovedStatus(status?: string): boolean {
  return status === "approved";
}

function statusClass(status?: string): string {
  if (status === "approved") return "approved";
  if (status === "rejected") return "rejected";
  return "pending";
}

function isPmvPlayable(deck: SubmissionDeck, group: PmvGroup): boolean {
  return isApprovedStatus(deck.reviewStatus) && isApprovedStatus(group.reviewStatus);
}

function isCardPlayable(deck: SubmissionDeck, card: SubmissionCard): boolean {
  return (
    isApprovedStatus(deck.reviewStatus) &&
    isApprovedStatus(card.pmvReviewStatus) &&
    isApprovedStatus(card.reviewStatus)
  );
}

function cardEffectiveStatus(deck: SubmissionDeck, card: SubmissionCard): string | undefined {
  if (!isApprovedStatus(deck.reviewStatus)) return deck.reviewStatus;
  if (!isApprovedStatus(card.pmvReviewStatus)) return card.pmvReviewStatus;
  return card.reviewStatus;
}

function cardEffectiveLabel(deck: SubmissionDeck, card: SubmissionCard): string {
  if (!isApprovedStatus(deck.reviewStatus)) return `卡组${statusLabel(deck.reviewStatus)}`;
  if (!isApprovedStatus(card.pmvReviewStatus)) return `PMV${statusLabel(card.pmvReviewStatus)}`;
  return statusLabel(card.reviewStatus);
}

function beginCardUpload(): void {
  if (!activeDeckId.value || !selectedPmvId.value) {
    return;
  }
  showCardUploader.value = true;
}

async function confirmCardUpload(event: MouseEvent): Promise<void> {
  if (!activeDeckId.value || !selectedPmvId.value) return;
  const pmvId = Number(selectedPmvId.value);
  if (!Number.isFinite(pmvId)) return;
  try {
    const crop = resolveCropRef(cardCropRef.value);
    if (!crop) {
      showAt(event, "请先选择卡牌图片", "error");
      return;
    }
    const { blob } = await crop.getCroppedBlob();
    await addSubmissionCard(activeDeckId.value, pmvId, blob, {
      name: cardName.value.trim() || undefined,
      description: cardDescription.value.trim() || undefined,
    });
    showAt(event, "卡牌已上传", "success");
    showCardUploader.value = false;
    cardName.value = "";
    cardDescription.value = "";
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
        提交的卡组、PMV 与卡牌默认进入待审核；已上线内容修改会进入「修改待审」，通过前对局仍使用旧数据。
      </p>

      <section class="submit-section">
        <h3 class="section-title">1. 创建卡组</h3>
        <div class="form-grid">
          <label>
            卡组名称 <span class="req">*</span>
            <input v-model="deckName" type="text" maxlength="120" placeholder="全局唯一" />
          </label>
          <label class="full-width">
            说明
            <textarea v-model="deckDescription" rows="2" maxlength="2000" />
          </label>
        </div>
        <button type="button" :disabled="!deckName.trim()" @click="submitDeck($event)">创建卡组</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">2. 上传牌背 (720×1087)</h3>
        <p class="muted">上传到「我的提交」当前选中的卡组。</p>
        <ImageCropField
          ref="backCropRef"
          label="牌背图片"
          :aspect-ratio="720 / 1087"
          :output-width="720"
          :output-height="1087"
          hint="牌背竖版 720×1087，提交前会裁剪并压缩为 JPEG。"
        />
        <button type="button" :disabled="!activeDeckId" @click="submitBack($event)">上传牌背</button>
      </section>

      <section id="pmv-guide" class="submit-section">
        <h3 class="section-title">3. 创建 PMV</h3>
        <p class="muted">PMV 名称全局唯一；编号由系统自动分配。上传卡牌时按名称选择 PMV。</p>
        <div class="pmv-form">
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
        <button type="button" :disabled="!pmvName.trim()" @click="submitPmv($event)">创建 PMV</button>

        <label class="pmv-search-row">
          已有 PMV（名称 / 作者）
          <input v-model="pmvSearch" autocomplete="off" placeholder="搜索..." />
        </label>
        <div class="pmv-index-scroll table-wrap">
          <table>
            <thead>
              <tr>
                <th>PMV 名</th>
                <th>作者</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filteredPmvIndexRows.length">
                <td colspan="3">无匹配条目</td>
              </tr>
              <tr v-for="row in filteredPmvIndexRows" :key="pmvRowId(row)">
                <td>{{ row.name }}</td>
                <td>{{ row.author || "—" }}</td>
                <td>
                  <span class="badge" :class="statusClass(row.reviewStatus)">
                    {{ statusLabel(row.reviewStatus) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="submit-section">
        <h3 class="section-title">4. 上传卡牌 (1500×1080)</h3>
        <p class="muted">选择要上传到的卡组与 PMV（与下方「我的提交」当前标签同步）。</p>
        <div class="form-grid">
          <label>
            选择卡组 <span class="req">*</span>
            <select v-model="uploadDeckId" :disabled="!myDecks.length">
              <option value="">请选择卡组</option>
              <option v-for="deck in myDecks" :key="deck.id" :value="String(deck.id)">
                {{ deck.name }}
              </option>
            </select>
          </label>
          <label>
            选择 PMV <span class="req">*</span>
            <select
              v-model="selectedPmvId"
              :disabled="Boolean(pmvSelectDisabledReason)"
              :title="pmvSelectDisabledReason ?? undefined"
            >
              <option value="">请选择 PMV</option>
              <option v-for="pmv in sortedPickerPmvs" :key="pmvRowId(pmv)" :value="String(pmvRowId(pmv))">
                {{ pmvOptionLabel(pmv) }}
              </option>
            </select>
          </label>
          <label>
            卡牌名称（选填）
            <input v-model="cardName" type="text" maxlength="512" />
          </label>
          <label class="full-width">
            卡牌描述（选填）
            <textarea v-model="cardDescription" rows="2" maxlength="2000" />
          </label>
        </div>
        <p v-if="pmvSelectDisabledReason" class="muted field-hint">{{ pmvSelectDisabledReason }}</p>
        <button
          type="button"
          :disabled="!activeDeckId || !selectedPmvId"
          @click="beginCardUpload"
        >
          选择图片并上传
        </button>
        <div v-if="showCardUploader" class="shot-uploader">
          <ImageCropField
            :key="`card-${activeDeckId}-${selectedPmvId}`"
            ref="cardCropRef"
            label="卡牌截图"
            :aspect-ratio="1500 / 1080"
            :output-width="1500"
            :output-height="1080"
            hint="横版 1500×1080，提交前会裁剪并压缩为 JPEG。"
          />
          <div class="actions">
            <button type="button" class="primary" @click="confirmCardUpload($event)">确认上传</button>
            <button type="button" @click="showCardUploader = false">取消</button>
          </div>
        </div>
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
                  <span class="badge" :class="statusClass(tabDeck.reviewStatus)">
                    卡组{{ statusLabel(tabDeck.reviewStatus) }}
                  </span>
                  <span v-if="tabDeck.pendingReviewStatus === 'pending'" class="badge muted">修改待审</span>
                  <span class="muted">#{{ tabDeck.id }} · {{ tabDeck.cardCount ?? 0 }} 张 · {{ tabDeck.pmvCount ?? 0 }} PMV</span>
                </p>
                <p v-if="tabDeck.description" class="muted">{{ tabDeck.description }}</p>
              </div>
              <button type="button" class="danger" @click="removeDeck($event, tabDeck)">删除卡组</button>
            </div>

            <div v-if="tabDeck.backUrl" class="thumb-row">
              <img :src="tabDeck.backUrl" alt="牌背" class="submit-thumb back-thumb" />
            </div>

            <div v-if="pmvGroups(tabDeck).length" class="pmv-mini-list">
              <div
                v-for="group in pmvGroups(tabDeck)"
                :key="group.pmvId"
                class="pmv-mini"
                :class="{ blocked: !isPmvPlayable(tabDeck, group) }"
              >
                <div class="pmv-mini-head">
                  <span>{{ group.label }}</span>
                  <span class="badge" :class="statusClass(group.reviewStatus)">
                    PMV{{ statusLabel(group.reviewStatus) }}
                  </span>
                  <span v-if="group.pendingReviewStatus === 'pending'" class="badge muted">PMV 修改待审</span>
                  <span v-if="!isApprovedStatus(tabDeck.reviewStatus)" class="badge pending">卡组未通过</span>
                </div>
                <div class="submission-shots">
                  <figure
                    v-for="card in group.cards"
                    :key="card.id"
                    class="shot-tile"
                    :class="{ dimmed: !isCardPlayable(tabDeck, card) }"
                  >
                    <button type="button" class="shot-remove" title="删除" @click="removeCard($event, card)">×</button>
                    <img :src="card.imageUrl" alt="" />
                    <figcaption>
                      <span class="shot-title">{{ card.name || `#${card.id}` }}</span>
                      <span class="badge mini" :class="statusClass(cardEffectiveStatus(tabDeck, card))">
                        {{ cardEffectiveLabel(tabDeck, card) }}
                      </span>
                      <span v-if="card.pendingReviewStatus === 'pending'" class="badge mini muted">卡牌修改待审</span>
                    </figcaption>
                  </figure>
                </div>
              </div>
            </div>
            <p v-else class="muted">这个卡组还没有卡牌。</p>
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

.section-title {
  margin: 0 0 0.75rem;
}

.form-grid {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  margin-bottom: 0.75rem;
}

.form-grid .full-width {
  grid-column: 1 / -1;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.92rem;
}

.req {
  color: #f87171;
}

.field-hint {
  margin: -0.25rem 0 0.75rem;
}

.pmv-form {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  margin-bottom: 0.75rem;
}

.pmv-search-row {
  display: block;
  margin: 1rem 0 0.5rem;
}

.pmv-index-scroll {
  max-height: 220px;
  overflow: auto;
  margin-bottom: 0.75rem;
}

.submit-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.submit-tab {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  color: inherit;
  cursor: pointer;
  padding: 0.35rem 0.85rem;
}

.submit-tab.active {
  background: rgba(96, 165, 250, 0.25);
  border-color: rgba(96, 165, 250, 0.6);
}

.submission-header {
  align-items: flex-start;
  display: flex;
  gap: 1rem;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.badge {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  font-size: 0.8rem;
  margin-right: 0.35rem;
  padding: 0.1rem 0.45rem;
}

.badge.approved {
  background: rgba(34, 197, 94, 0.18);
  color: #bbf7d0;
}

.badge.pending {
  background: rgba(148, 163, 184, 0.18);
  color: rgba(255, 255, 255, 0.68);
}

.badge.rejected {
  background: rgba(239, 68, 68, 0.18);
  color: #fecaca;
}

.badge.mini {
  display: inline-block;
  margin-top: 0.25rem;
}

.thumb-row {
  margin-bottom: 1rem;
}

.submit-thumb.back-thumb {
  max-height: 160px;
  width: auto;
}

.submission-shots {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.pmv-mini {
  margin-bottom: 1rem;
}

.pmv-mini.blocked {
  color: rgba(255, 255, 255, 0.62);
}

.pmv-mini-head {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-bottom: 0.5rem;
}

.shot-tile {
  margin: 0;
  position: relative;
  width: 140px;
}

.shot-tile img {
  border-radius: 6px;
  display: block;
  height: 100px;
  object-fit: cover;
  width: 100%;
}

.shot-tile.dimmed img {
  filter: grayscale(35%);
  opacity: 0.46;
}

.shot-tile figcaption {
  font-size: 0.8rem;
  margin-top: 0.25rem;
}

.shot-title {
  display: block;
  overflow-wrap: anywhere;
}

.shot-remove {
  background: rgba(0, 0, 0, 0.65);
  border: none;
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  height: 22px;
  position: absolute;
  right: 4px;
  top: 4px;
  width: 22px;
  z-index: 1;
}

.shot-uploader {
  margin-top: 1rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1rem;
}

button.primary {
  background: #3b82f6;
  border: none;
  color: #fff;
}

button.danger {
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid rgba(239, 68, 68, 0.5);
  color: #fecaca;
}

.muted {
  color: rgba(255, 255, 255, 0.65);
}

.table-wrap table {
  width: 100%;
}
</style>
