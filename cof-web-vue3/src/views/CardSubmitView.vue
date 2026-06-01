<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import ImageCropField from "@/components/ImageCropField.vue";
import PagePanel from "@/components/PagePanel.vue";
import {
  addSubmissionCard,
  addSubmissionPmv,
  createSubmissionDeck,
  fetchMySubmissions,
  uploadSubmissionBack,
  type SubmissionDeck,
} from "@/api/submissions";

const loading = ref(false);
const error = ref("");
const success = ref("");
const myDecks = ref<SubmissionDeck[]>([]);

const deckName = ref("");
const deckCurator = ref("");
const deckDescription = ref("");
const activeDeckId = ref<number | null>(null);

const pmvId = ref(100);
const pmvName = ref("");
const pmvAuthor = ref("");
const pmvLink = ref("");

const cardShot = ref("a");
const cardPmvId = ref(100);

const backCropRef = ref<InstanceType<typeof ImageCropField> | null>(null);
const cardCropRef = ref<InstanceType<typeof ImageCropField> | null>(null);

async function refreshMine(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const data = await fetchMySubmissions();
    myDecks.value = data.decks ?? [];
  } catch (e) {
    error.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void refreshMine();
});

async function submitDeck(): Promise<void> {
  error.value = "";
  success.value = "";
  try {
    const { deck } = await createSubmissionDeck({
      name: deckName.value.trim(),
      curator: deckCurator.value.trim() || undefined,
      description: deckDescription.value.trim() || undefined,
    });
    activeDeckId.value = deck.id ?? null;
    success.value = `已创建牌组「${deck.name}」（待审核，仅自己可见）`;
    deckName.value = "";
    await refreshMine();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "创建失败";
  }
}

async function submitBack(): Promise<void> {
  if (!activeDeckId.value) {
    error.value = "请先创建或选择牌组";
    return;
  }
  error.value = "";
  try {
    const { blob, crop } = await backCropRef.value!.getCroppedBlob();
    await uploadSubmissionBack(activeDeckId.value, blob, crop);
    success.value = "牌背已上传（待审核）";
    await refreshMine();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "上传失败";
  }
}

async function submitPmv(): Promise<void> {
  if (!activeDeckId.value) {
    error.value = "请先创建或选择牌组";
    return;
  }
  error.value = "";
  try {
    await addSubmissionPmv(activeDeckId.value, {
      pmvId: pmvId.value,
      name: pmvName.value.trim(),
      author: pmvAuthor.value.trim() || undefined,
      link: pmvLink.value.trim() || undefined,
    });
    success.value = "PMV 已添加（待审核）";
    pmvName.value = "";
    await refreshMine();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "添加 PMV 失败";
  }
}

async function submitCard(): Promise<void> {
  if (!activeDeckId.value) {
    error.value = "请先创建或选择牌组";
    return;
  }
  error.value = "";
  try {
    const { blob, crop } = await cardCropRef.value!.getCroppedBlob();
    await addSubmissionCard(activeDeckId.value, cardPmvId.value, cardShot.value.trim(), blob, crop);
    success.value = "卡牌已上传（待审核）";
    await refreshMine();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "上传卡牌失败";
  }
}

function statusLabel(status?: string): string {
  if (status === "approved") return "已通过";
  if (status === "rejected") return "已拒绝";
  return "待审核";
}
</script>

<template>
  <AppShell>
    <PagePanel title="提交牌组">
      <p class="muted">
        提交的牌组、PMV 与卡牌默认为<strong>待审核</strong>，仅自己可在图鉴中预览，<strong>不能用于对局</strong>。管理员审核通过后才会公开。
      </p>
      <p v-if="error" class="error-text">{{ error }}</p>
      <p v-if="success" class="status-line">{{ success }}</p>

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
        <button type="button" :disabled="!deckName.trim()" @click="submitDeck">创建牌组</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">2. 当前编辑牌组</h3>
        <label>
          牌组 ID
          <input v-model.number="activeDeckId" type="number" min="1" placeholder="创建后自动填入" />
        </label>
      </section>

      <section class="submit-section">
        <h3 class="section-title">3. 上传牌背 (720×1087)</h3>
        <ImageCropField ref="backCropRef" label="牌背图片" :aspect-ratio="720 / 1087" />
        <button type="button" @click="submitBack">上传牌背</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">4. 添加 PMV</h3>
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
        <button type="button" :disabled="!pmvName.trim()" @click="submitPmv">添加 PMV</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">5. 上传卡牌镜头</h3>
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
        <ImageCropField ref="cardCropRef" label="卡牌正面" :aspect-ratio="720 / 1087" />
        <button type="button" @click="submitCard">上传卡牌</button>
      </section>

      <section class="submit-section">
        <h3 class="section-title">我的提交</h3>
        <p v-if="loading" class="muted">加载中…</p>
        <ul v-else-if="myDecks.length" class="deck-list">
          <li v-for="deck in myDecks" :key="deck.id">
            <strong>{{ deck.name }}</strong>
            <span class="badge">{{ statusLabel(deck.reviewStatus) }}</span>
            <span class="muted">#{{ deck.id }} · {{ deck.cardCount ?? 0 }} 张 · {{ deck.pmvCount ?? 0 }} PMV</span>
            <button type="button" class="linkish" @click="activeDeckId = deck.id ?? null">编辑此牌组</button>
          </li>
        </ul>
        <p v-else class="muted">暂无提交记录。</p>
      </section>

      <div class="actions">
        <RouterLink class="action-link" to="/cards/info">返回图鉴</RouterLink>
        <RouterLink class="action-link" to="/pmv-index">PMV 索引</RouterLink>
      </div>
    </PagePanel>
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
.deck-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.deck-list li {
  padding: 0.5rem 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
.badge {
  font-size: 0.8rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  background: rgba(255, 200, 80, 0.2);
}
.linkish {
  background: none;
  border: none;
  color: var(--link-color, #8cf);
  cursor: pointer;
  text-decoration: underline;
}
</style>
