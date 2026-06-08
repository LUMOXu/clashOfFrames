# Clash of Frames 图鉴审核对接文档（V10  schema）

本文档面向**外部审核系统**，说明如何连接生产数据库、读取待审内容、执行通过/驳回，并在操作后让游戏服生效。

> **schema 版本**：Flyway **V10**（`cof_deck` / `cof_pmv` / `cof_card` 重建）。旧表已备份为 `old_cof_deck`、`old_cof_deck_pmv`、`old_cof_card`，**请勿**再对接旧表。  
> **仓库内 `deploy/review_submissions.py` 仍引用旧 schema，不可直接使用**；请按本文 SQL 或自行调用后端 `DeckCatalogReviewService` 同等逻辑。

---

## 1. 生产环境连接信息

### 1.1 服务器与对外地址

| 项 | 值 |
|----|-----|
| 公网 IP | `120.53.245.110` |
| 前端（Web） | `http://120.53.245.110:9001` |
| API（Spring Boot） | `http://120.53.245.110:9002` |
| API 前缀 | `/api/v1` |
| 代码目录（服务器） | `/opt/cof-java` |
| 分支 | `feature-dyu` |
| 静态资源根目录 | `/opt/cof-java/cof-resource` |

### 1.2 PostgreSQL（生产）

配置来源：`/opt/cof-java/cof-java-boot/cof-boot/src/main/resources/application-prod.yml`（仅存在于服务器，未进 git）。

| 项 | 值 |
|----|-----|
| Host | `127.0.0.1`（仅服务器本机可连；外网审核系统需 SSH 隧道或 VPN） |
| Port | `5432` |
| Database | `cof_db` |
| Schema | `public` |
| Username | `cof` |
| Password | `cof` |
| JDBC URL | `jdbc:postgresql://127.0.0.1:5432/cof_db?currentSchema=public` |
| `DATABASE_URL`（psql / Python） | `postgresql://cof:cof@127.0.0.1:5432/cof_db` |

**SSH 隧道示例**（在外部机器上访问生产库）：

```bash
ssh -i ~/.ssh/id_ed25519_cof_deploy -L 15432:127.0.0.1:5432 root@120.53.245.110
# 然后连接 postgresql://cof:cof@127.0.0.1:15432/cof_db
```

### 1.3 Redis（仅供了解，审核一般不需写 Redis）

| 项 | 值 |
|----|-----|
| Host | `127.0.0.1` |
| Port | `6379` |
| Database | `2` |

图鉴列表缓存在 Redis；**审核写库后必须通知后端刷新缓存**（见第 8 节）。

---

## 2. 业务表结构（V10）

三张核心表 + 用户表（关联提交者）。

### 2.1 `cof_deck`（牌组）

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | BIGSERIAL PK | 牌组 ID；对局 `libraryIds` 使用其**字符串形式** |
| `name` | VARCHAR(255) NOT NULL | 牌组名称（未删除时全局唯一） |
| `description` | TEXT | 说明 |
| `back_url` | VARCHAR(512) NOT NULL | 牌背 URL 路径，如 `/cards/backs/3.jpg` |
| `enabled` | BOOLEAN | 是否启用；对局可见还需 `review_status=approved` |
| `review_status` | VARCHAR(16) | 主审核状态：`pending` / `approved` / `rejected` |
| `pending_review_status` | VARCHAR(16) | 修改待审标记：`pending` 表示有修改待审；驳回修改时为 `rejected` |
| `pending_name` | VARCHAR(255) | 待审新名称 |
| `pending_description` | TEXT | 待审新说明 |
| `pending_back_url` | VARCHAR(512) | 待审新牌背路径 |
| `submitter_client_id` | VARCHAR(64) | 提交者 `cof_user.client_id`（UUID 字符串） |
| `created_at` / `updated_at` | TIMESTAMPTZ | 时间戳 |
| `deleted_at` | TIMESTAMPTZ | 软删；`NULL` 为有效行 |

索引：`uk_cof_deck_name`（`name` 唯一，`deleted_at IS NULL`）。

### 2.2 `cof_pmv`（PMV，全局）

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | BIGSERIAL PK | **PMV 全局 ID**；对局内 `pmvId` 即此字段 |
| `name` | VARCHAR(512) NOT NULL | PMV 名称（未删除时全局唯一） |
| `author` | VARCHAR(255) | 作者 |
| `description` | TEXT | 描述 |
| `link` | VARCHAR(512) | 外链 |
| `review_status` | VARCHAR(16) | 同牌组 |
| `pending_review_status` | VARCHAR(16) | 同牌组 |
| `pending_name` / `pending_author` / `pending_description` / `pending_link` | | 待审修改字段 |
| `submitter_client_id` | VARCHAR(64) | 提交者 |
| `created_at` / `updated_at` / `deleted_at` | | 同牌组 |

> PMV **不绑定牌组**；卡牌通过 `cof_card.pmv_id` 引用。

### 2.3 `cof_card`（卡牌）

| 列名 | 类型 | 说明 |
|------|------|------|
| `id` | BIGSERIAL PK | 卡牌 ID |
| `deck_id` | BIGINT NOT NULL FK | 所属牌组 |
| `pmv_id` | BIGINT NOT NULL FK | 所属 PMV（`cof_pmv.id`） |
| `name` / `description` | | 卡牌名称/描述（可选） |
| `image_url` | VARCHAR(512) NOT NULL | 卡图路径，如 `/cards/uuid.jpg` |
| `review_status` | VARCHAR(16) | 主审核状态 |
| `pending_review_status` | VARCHAR(16) | 修改待审 |
| `pending_name` / `pending_description` / `pending_image_url` | | 待审修改 |
| `submitter_client_id` | VARCHAR(64) | 提交者 |
| `created_at` / `updated_at` / `deleted_at` | | |

索引：`uk_cof_card_image_url`（`image_url` 唯一）。

### 2.4 `cof_user`（提交者）

| 列名 | 说明 |
|------|------|
| `client_id` | UUID，主键；对应三张表中的 `submitter_client_id` |
| `username` | 登录名 |

关联查询示例：

```sql
SELECT d.id, d.name, d.review_status, u.username
FROM cof_deck d
LEFT JOIN cof_user u ON u.client_id::text = d.submitter_client_id
WHERE d.deleted_at IS NULL;
```

---

## 3. 审核状态语义

### 3.1 `review_status`（主状态）

| 值 | 含义 |
|----|------|
| `pending` | **首次提交**待审；对局/公开图鉴不可见（提交者本人可在「我的提交」看到） |
| `approved` | 已通过；当前 `name`/`back_url`/`image_url` 等**线上生效**字段可用 |
| `rejected` | 首次提交被驳回 |

### 3.2 `pending_review_status`（修改待审）

| 值 | 含义 |
|----|------|
| `NULL` | 无修改待审 |
| `pending` | 已上线内容有修改，等待审核；**线上仍用原字段**，`pending_*` 为候选值 |
| `rejected` | 最近一次**修改**被驳回；线上内容不变，`pending_*` 已清空 |

### 3.3 对局可见（可出牌）条件

一条卡牌同时满足才可进入对局牌池：

1. `cof_deck.deleted_at IS NULL` 且 `enabled = TRUE` 且 `review_status = 'approved'`
2. `cof_pmv.deleted_at IS NULL` 且 `review_status = 'approved'`
3. `cof_card.deleted_at IS NULL` 且 `review_status = 'approved'`

**审核顺序建议**：牌组 → PMV → 卡牌（卡牌通过时后端会校验牌组与 PMV 已 approved）。

---

## 4. 用户提交 API（本系统侧，供对照）

以下均需登录：`Authorization: Bearer <token>`。外部审核系统**通常不调用**这些接口，仅用于理解数据如何写入库。

基础 URL：`http://120.53.245.110:9002/api/v1`（或经 nginx `http://120.53.245.110:9001/api/v1`）。

### 4.1 牌组

| 操作 | 方法 | 路径 |
|------|------|------|
| 创建牌组 | `POST` | `/submissions/decks` |
| 修改牌组元数据 | `PATCH` | `/submissions/decks/{deckId}` |
| 上传牌背 | `POST` | `/submissions/decks/{deckId}/back` |
| 我的提交列表 | `GET` | `/submissions/mine` |

**创建牌组**（`POST /submissions/decks`）后端流程：

1. 校验名称唯一
2. `INSERT cof_deck`：`review_status='pending'`，`enabled=true`，`submitter_client_id=当前用户`
3. 设置 `back_url='/cards/backs/{id}.jpg'`（占位，上传牌背后更新）

**修改牌组**（`PATCH /submissions/decks/{deckId}`）：

- 若 `review_status='approved'`（已上线）：写入 `pending_name` / `pending_description`，`pending_review_status='pending'`
- 若仍为首次待审：直接改 `name` / `description`，`review_status` 保持 `pending`

**上传牌背**（`POST /submissions/decks/{deckId}/back`）：

- `Content-Type: multipart/form-data`，字段 `file`（JPEG，最大 15MB）
- 可选裁剪：`cropX`, `cropY`, `cropWidth`, `cropHeight`
- 文件写入磁盘：`{cof-resource}/cards/backs/{deckId}.jpg`
- 若已上线：`pending_back_url='/cards/backs/{deckId}.jpg'`，`pending_review_status='pending'`
- 若首次待审：直接更新 `back_url`，`review_status='pending'`

### 4.2 PMV

| 操作 | 方法 | 路径 |
|------|------|------|
| 创建 PMV | `POST` | `/submissions/pmvs` |
| 修改 PMV | `PATCH` | `/submissions/pmvs/{pmvId}` |
| PMV 列表（选择器） | `GET` | `/submissions/pmvs` |

**创建 PMV**（`POST /submissions/pmvs`，JSON body）：

```json
{
  "name": "PMV 名称（全局唯一）",
  "author": "作者（可选）",
  "description": "描述（可选）",
  "link": "https://...（可选）"
}
```

后端：`INSERT cof_pmv`，`review_status='pending'`。

**修改 PMV**（`PATCH /submissions/pmvs/{pmvId}`）：逻辑同牌组（已上线写 `pending_*`，否则改主字段）。

### 4.3 卡牌

| 操作 | 方法 | 路径 |
|------|------|------|
| 上传卡牌 | `POST` | `/submissions/decks/{deckId}/cards` |
| 修改卡牌 | `PATCH` | `/submissions/cards/{cardId}` |

**上传卡牌**（`POST /submissions/decks/{deckId}/cards`）：

- `multipart/form-data`：`file`（必填），`pmvId`（必填，全局 PMV id），`name`、`description`（可选）
- 卡图存为 `{cof-resource}/cards/{uuid}.jpg`，`image_url='/cards/{uuid}.jpg'`
- `INSERT cof_card`：`review_status='pending'`

**修改卡牌**（`PATCH /submissions/cards/{cardId}`）：已上线则新图进 `pending_image_url` 并 `pending_review_status='pending'`。

---

## 5. 静态资源与牌背 URL

数据库中存的是**路径**，不是完整 URL。浏览器访问时需加 **Host**。

### 5.1 推荐 Host（生产）

| 资源类型 | 路径示例 | 完整 URL 示例 |
|----------|----------|----------------|
| 牌背 | `/cards/backs/{deckId}.jpg` | `http://120.53.245.110:9001/cards/backs/3.jpg` |
| 卡图 | `/cards/{uuid}.jpg` | `http://120.53.245.110:9001/cards/abc-uuid.jpg` |
| 待审牌背（预览） | `pending_back_url` 字段 | 同上，用 pending 路径 |

也可直接用 API 端口：`http://120.53.245.110:9002/cards/backs/3.jpg`（nginx 9001 对 `/cards/` 会反代到 9002）。

### 5.2 请求方式

- **方法**：`GET`
- **认证**：不需要
- **磁盘位置**：`/opt/cof-java/cof-resource/cards/backs/{deckId}.jpg` 或 `cards/{uuid}.jpg`

### 5.3 审核界面展示建议

- 牌组列表：展示 `back_url`；若有修改待审，同时展示 `pending_back_url`
- 卡牌：展示 `image_url`；修改待审展示 `pending_image_url`

---

## 6. 读取待审队列（SQL）

统一条件：`deleted_at IS NULL`。

### 6.1 首次提交待审

```sql
-- 牌组
SELECT id, name, description, back_url, submitter_client_id, created_at, updated_at
FROM cof_deck
WHERE deleted_at IS NULL AND review_status = 'pending'
ORDER BY created_at;

-- PMV
SELECT id, name, author, description, link, submitter_client_id, created_at, updated_at
FROM cof_pmv
WHERE deleted_at IS NULL AND review_status = 'pending'
ORDER BY created_at;

-- 卡牌（可 JOIN 牌组、PMV 名称）
SELECT c.id, c.deck_id, c.pmv_id, c.name, c.image_url,
       d.name AS deck_name, p.name AS pmv_name,
       c.submitter_client_id, c.created_at
FROM cof_card c
JOIN cof_deck d ON d.id = c.deck_id AND d.deleted_at IS NULL
JOIN cof_pmv p ON p.id = c.pmv_id AND p.deleted_at IS NULL
WHERE c.deleted_at IS NULL AND c.review_status = 'pending'
ORDER BY c.created_at;
```

### 6.2 修改待审

```sql
-- 牌组修改
SELECT id, name, pending_name, description, pending_description,
       back_url, pending_back_url, submitter_client_id, updated_at
FROM cof_deck
WHERE deleted_at IS NULL
  AND review_status = 'approved'
  AND pending_review_status = 'pending';

-- PMV 修改
SELECT id, name, pending_name, author, pending_author,
       description, pending_description, link, pending_link, updated_at
FROM cof_pmv
WHERE deleted_at IS NULL
  AND review_status = 'approved'
  AND pending_review_status = 'pending';

-- 卡牌修改
SELECT id, deck_id, pmv_id, name, pending_name,
       image_url, pending_image_url, updated_at
FROM cof_card
WHERE deleted_at IS NULL
  AND review_status = 'approved'
  AND pending_review_status = 'pending';
```

### 6.3 汇总视图（单条记录判断审核类型）

```sql
SELECT
  'deck' AS entity_type,
  id AS entity_id,
  CASE
    WHEN review_status = 'pending' THEN 'initial'
    WHEN pending_review_status = 'pending' THEN 'revision'
    ELSE 'none'
  END AS review_queue
FROM cof_deck
WHERE deleted_at IS NULL
  AND (review_status = 'pending' OR pending_review_status = 'pending');
-- 对 cof_pmv、cof_card 同理
```

---

## 7. 审核操作（SQL，与后端 `DeckCatalogReviewService` 一致）

> **重要**：本系统**没有**对外暴露 `POST /approve` 类 HTTP 接口；外部系统通过 **直接更新 PostgreSQL** 实现审核，完成后调用第 8 节缓存刷新。

以下 SQL 请在事务中执行；`updated_at` 使用 `NOW()`。

### 7.1 牌组 — 首次通过

```sql
UPDATE cof_deck
SET review_status = 'approved',
    updated_at = NOW()
WHERE id = :deck_id
  AND deleted_at IS NULL
  AND review_status = 'pending'
  AND (pending_review_status IS NULL OR pending_review_status <> 'pending');
```

### 7.2 牌组 — 修改通过

将 `pending_*` 合并到主字段并清空 pending：

```sql
UPDATE cof_deck
SET
  name = COALESCE(pending_name, name),
  description = COALESCE(pending_description, description),
  back_url = COALESCE(pending_back_url, back_url),
  pending_review_status = NULL,
  pending_name = NULL,
  pending_description = NULL,
  pending_back_url = NULL,
  updated_at = NOW()
WHERE id = :deck_id
  AND deleted_at IS NULL
  AND pending_review_status = 'pending';
```

合并前请确认 `pending_name` 不与其它牌组冲突（`uk_cof_deck_name`）。

### 7.3 牌组 — 首次驳回

```sql
UPDATE cof_deck
SET review_status = 'rejected',
    updated_at = NOW()
WHERE id = :deck_id
  AND deleted_at IS NULL
  AND review_status = 'pending';
```

### 7.4 牌组 — 修改驳回

```sql
UPDATE cof_deck
SET
  pending_review_status = 'rejected',
  pending_name = NULL,
  pending_description = NULL,
  pending_back_url = NULL,
  updated_at = NOW()
WHERE id = :deck_id
  AND deleted_at IS NULL
  AND pending_review_status = 'pending';
```

（`review_status` 保持 `approved`，线上牌组不变。）

---

### 7.5 PMV — 首次通过

```sql
UPDATE cof_pmv
SET review_status = 'approved',
    updated_at = NOW()
WHERE id = :pmv_id
  AND deleted_at IS NULL
  AND review_status = 'pending'
  AND (pending_review_status IS NULL OR pending_review_status <> 'pending');
```

### 7.6 PMV — 修改通过

```sql
UPDATE cof_pmv
SET
  name = COALESCE(pending_name, name),
  author = COALESCE(pending_author, author),
  description = COALESCE(pending_description, description),
  link = COALESCE(pending_link, link),
  pending_review_status = NULL,
  pending_name = NULL,
  pending_author = NULL,
  pending_description = NULL,
  pending_link = NULL,
  updated_at = NOW()
WHERE id = :pmv_id
  AND deleted_at IS NULL
  AND pending_review_status = 'pending';
```

### 7.7 PMV — 首次驳回 / 修改驳回

同牌组，将表名改为 `cof_pmv`，主键改为 `id = :pmv_id`。

---

### 7.8 卡牌 — 首次通过

**前置条件**：所属牌组、PMV 均已 `review_status = 'approved'`。

```sql
-- 可选：先校验
SELECT d.review_status AS deck_status, p.review_status AS pmv_status
FROM cof_card c
JOIN cof_deck d ON d.id = c.deck_id
JOIN cof_pmv p ON p.id = c.pmv_id
WHERE c.id = :card_id;

UPDATE cof_card
SET review_status = 'approved',
    updated_at = NOW()
WHERE id = :card_id
  AND deleted_at IS NULL
  AND review_status = 'pending'
  AND (pending_review_status IS NULL OR pending_review_status <> 'pending');
```

### 7.9 卡牌 — 修改通过

```sql
UPDATE cof_card
SET
  name = COALESCE(pending_name, name),
  description = COALESCE(pending_description, description),
  image_url = COALESCE(pending_image_url, image_url),
  pending_review_status = NULL,
  pending_name = NULL,
  pending_description = NULL,
  pending_image_url = NULL,
  updated_at = NOW()
WHERE id = :card_id
  AND deleted_at IS NULL
  AND pending_review_status = 'pending';
```

合并前确认 `pending_image_url` 不与其它卡 `image_url` 冲突。

### 7.10 卡牌 — 首次驳回 / 修改驳回

同牌组逻辑，表 `cof_card`，主键 `id = :card_id`。

---

## 8. 审核后必须刷新游戏服缓存

直接改库**不会**自动清 Redis。每次批量审核结束后，在能访问 API 的机器上执行：

```bash
# 推荐：对账 enabled 并刷新全部图鉴缓存
curl -X POST http://120.53.245.110:9002/api/v1/admin/catalog/reconcile

# 或仅刷新缓存（不修正 enabled）
curl -X POST http://120.53.245.110:9002/api/v1/admin/catalog/refresh
```

`/admin/catalog/*` **当前无鉴权**（内网/运维使用）；外网调用请配合防火墙或后续加 Token。

`reconcile` 行为：对已 `approved` 但 `enabled=false` 的牌组置 `enabled=true`，并 `bustCaches()`。

---

## 9. 外部系统对接流程建议

```mermaid
flowchart TD
  A[轮询 PostgreSQL 待审队列] --> B{实体类型}
  B --> C[牌组]
  B --> D[PMV]
  B --> E[卡牌]
  C --> F[展示 back_url / pending_back_url]
  D --> G[展示元数据]
  E --> H[展示 image_url; 校验 deck+pmv 已 approved]
  F --> I{人工审核}
  G --> I
  H --> I
  I -->|通过| J[执行第 7 节对应 SQL]
  I -->|驳回| K[执行驳回 SQL]
  J --> L[POST /admin/catalog/reconcile]
  K --> L
```

### 9.1 推荐审核顺序

1. **牌组**首次通过（含牌背）
2. **PMV** 通过（全局，不依赖牌组）
3. **卡牌** 通过（依赖同 `deck_id` 的牌组 + `pmv_id` 的 PMV 均已 approved）

### 9.2 系统内置牌组

`id IN (1, 2)` 为默认「基础包 / 拓展包」，通常 `review_status=approved`，`submitter_client_id` 为空，**无需人工审核**。

### 9.3 与 Java 服务逻辑对齐（可选）

若外部系统用 Java 调用同一仓库，可直接注入：

- `DeckCatalogReviewService.approveDeck(deckId)` / `rejectDeck(deckId)`
- `approvePmv(pmvId)` / `rejectPmv(pmvId)`
- `approveCard(cardId)` / `rejectCard(cardId)`
- `reconcileAndRefreshCaches()`

这比手写 SQL 更不易出错（含名称唯一性校验）。

---

## 10. 状态机简表

| 场景 | review_status | pending_review_status | 审核通过效果 |
|------|---------------|----------------------|--------------|
| 新建牌组 | pending | NULL | → approved |
| 修改已上线牌组 | approved | pending | pending_* 合并到主字段，pending 清空 |
| 驳回新建 | pending → rejected | — | — |
| 驳回修改 | approved | pending → rejected | 主字段不变，pending 清空 |

PMV、卡牌同理。

---

## 11. 常见问题

### Q1：创建 PMV 报 500 / duplicate key

导入默认 PMV 使用显式 `id` 后，PostgreSQL 序列可能落后。确保 Flyway **V11** 已执行，或手动：

```sql
SELECT setval(pg_get_serial_sequence('cof_pmv', 'id'),
  GREATEST((SELECT COALESCE(MAX(id), 0) FROM cof_pmv), 1));
```

### Q2：审核通过但对局里仍看不到卡

1. 检查三表是否均为 `approved` 且 `deleted_at IS NULL`
2. 牌组 `enabled = true`
3. 是否已调用 `/admin/catalog/reconcile`

### Q3：`submitter_client_id` 是什么？

用户注册后 `cof_user.client_id`（UUID），与 JWT 内 `clientId` 一致。

---

## 12. 文档版本

| 项 | 值 |
|----|-----|
| 对应代码分支 | `feature-dyu` |
| Schema | Flyway V10 + V11 |
| 文档生成参考提交 | `ce271c9` 及以前图鉴审核实现 |

如有 HTTP 审核 API 需求，需在 `cof-api` 新增运维接口并加鉴权；当前生产对接以 **数据库 + reconcile** 为准。
