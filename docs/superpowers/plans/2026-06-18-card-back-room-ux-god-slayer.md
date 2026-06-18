# Card Back Preset, Room UX, and GOD Slayer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a directly-uploaded recolored card-back preset, improve player-facing room/help UI, and implement a server-authoritative persistent GOD Slayer award with animated subset-font names and winner-only reward behavior.

**Architecture:** PostgreSQL and `UserStatsService` own the GOD Slayer achievement and pending reward acknowledgement by reusing existing `cof_user_stats` fields; no Flyway migration or schema change is needed. The game snapshot carries only the public newly-awarded winner marker, while Vue retrieves private pending reward state from the current user's profile. Reusable pure helpers handle card-back recoloring and room labels; a single `PlayerName` component handles all semantic username styling and per-player font loading.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, PostgreSQL/Flyway, Redis, JUnit 5/Mockito, Vue 3, TypeScript, Pinia, Vitest, Canvas, FontFace API, FontTools.

---

## File structure

- `cof-java-boot/cof-service/.../GodSlayerEligibility.java`: pure award qualification logic.
- `cof-java-boot/cof-service/.../FontSubsetService.java`: bounded character extraction, process execution, locking, and WOFF2 cache.
- `cof-web-vue3/src/components/PlayerName.vue`: the only component that applies GOD/GOD Slayer visual classes and subset fonts.
- `cof-web-vue3/src/components/GodSlayerRewardModal.vue`: winner-only modal and ten-second acknowledgement gate.
- `cof-web-vue3/src/components/CardBackPresetModal.vue`: modal UI and direct-upload event.
- `cof-web-vue3/src/utils/cardBackPreset.ts`: pure pixel mapping plus browser image/blob adapter.
- `cof-web-vue3/src/utils/playerName.ts`: identity classification and font URL/family helpers.
- `cof-web-vue3/src/utils/roomPresentation.ts`: public filtering and room/deck/rule labels.

## Task 1: Reject GOD-containing registrations and update the visible hint

**Files:**
- Create: `cof-web-vue3/src/utils/username.ts`
- Create: `cof-web-vue3/src/utils/username.test.ts`
- Modify: `cof-web-vue3/src/views/AuthView.vue`
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/AuthService.java`
- Modify: `cof-java-boot/cof-service/src/test/java/com/lumoxu/cof/service/AuthServiceTest.java`

- [ ] **Step 1: Write failing frontend and backend tests**

Add the frontend cases:

```ts
import { describe, expect, it } from "vitest";
import { godUsernameError } from "./username";

describe("godUsernameError", () => {
  it.each(["GOD", "god", "myGodName", "godzilla"])("rejects %s", (name) => {
    expect(godUsernameError(name)).toBe("用户名不能包含 GOD（不区分大小写）。");
  });

  it("accepts an unrelated username", () => {
    expect(godUsernameError("DrowningYu")).toBeNull();
  });
});
```

Add an `AuthServiceTest` case that calls `register("myGodName", "123456")`, expects `CofException`, and verifies `userMapper.insert` is never called. Add a login regression proving an existing legacy username containing GOD can still log in.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
cd cof-web-vue3
npm run test:unit -- src/utils/username.test.ts
cd ../cof-java-boot
mvn -pl cof-service -am "-Dtest=AuthServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: frontend import fails because `username.ts` does not exist; backend registration test fails because the name is accepted.

- [ ] **Step 3: Implement the shared rule at both trust boundaries**

Create the frontend helper exactly around the user-facing hint:

```ts
export const GOD_USERNAME_HINT = "用户名不能包含 GOD（不区分大小写）。";

export function godUsernameError(username: string): string | null {
  return username.trim().toLocaleUpperCase().includes("GOD") ? GOD_USERNAME_HINT : null;
}
```

Show `GOD_USERNAME_HINT` beside the registration username field and stop registration submission when `godUsernameError(username)` is non-null. In `AuthService.register`, call a registration-only validator after normal credential validation:

```java
private void validateRegistrationUsername(String username) {
    String clean = cleanName(username);
    if (clean.toUpperCase(java.util.Locale.ROOT).contains("GOD")) {
        throw new CofException(
                ErrorCode.BAD_REQUEST,
                "You lowlife. 用户名不能包含 GOD（不区分大小写）。");
    }
}
```

Do not call this method from `login`.

- [ ] **Step 4: Verify GREEN and commit**

Run both commands from Step 2. Expected: all selected tests pass. Commit:

```powershell
git add cof-web-vue3/src/utils/username.ts cof-web-vue3/src/utils/username.test.ts cof-web-vue3/src/views/AuthView.vue cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/AuthService.java cof-java-boot/cof-service/src/test/java/com/lumoxu/cof/service/AuthServiceTest.java
git commit -m "feat(auth): block GOD in new usernames"
```

## Task 2: Persist and award GOD Slayer server-side

**Files:**
- Create: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/GodSlayerEligibility.java`
- Create: `cof-java-boot/cof-service/src/test/java/com/lumoxu/cof/service/GodSlayerEligibilityTest.java`
- Modify: `cof-java-boot/cof-game-engine/src/main/java/com/lumoxu/cof/engine/Game.java`
- Modify: `cof-java-boot/cof-game-engine/src/main/java/com/lumoxu/cof/engine/Player.java`
- Modify: `cof-java-boot/cof-game-engine/src/main/java/com/lumoxu/cof/engine/PublicPlayer.java`
- Modify: `cof-java-boot/cof-game-engine/src/main/java/com/lumoxu/cof/engine/PublicGame.java`
- Modify: `cof-java-boot/cof-game-engine/src/main/java/com/lumoxu/cof/engine/GameSummary.java`
- Modify: `cof-java-boot/cof-game-engine/src/main/java/com/lumoxu/cof/engine/GameCore.java`
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/UserStatsService.java`
- Modify: `cof-java-boot/cof-service/src/test/java/com/lumoxu/cof/service/UserStatsServiceTest.java`

- [ ] **Step 1: Write the pure eligibility matrix first**

Build fixture games and assert:

```java
assertTrue(GodSlayerEligibility.eligible(gameWith(List.of("1"), 3, true, false)));
assertTrue(GodSlayerEligibility.eligible(gameWith(List.of("2"), 3, true, false)));
assertTrue(GodSlayerEligibility.eligible(gameWith(List.of("1", "2"), 9, true, false)));
assertFalse(GodSlayerEligibility.eligible(gameWith(List.of("1", "3"), 3, true, false)));
assertFalse(GodSlayerEligibility.eligible(gameWith(List.of(), 3, true, false)));
assertFalse(GodSlayerEligibility.eligible(gameWith(List.of("1"), 2, true, false)));
assertFalse(GodSlayerEligibility.eligible(gameWith(List.of("1"), 3, false, false)));
assertFalse(GodSlayerEligibility.eligible(gameWith(List.of("1"), 3, true, true)));
```

Add a `UserStatsServiceTest` that records a qualifying game and asserts the winner receives `godDefeatedAt`, non-null `godRewardGameId`, `game.godSlayerAwardWinnerId`, and `winner.godSlayer = true`. Add negative and repeat-save tests.

- [ ] **Step 2: Run and verify RED**

Run:

```powershell
cd cof-java-boot
mvn -pl cof-service -am "-Dtest=GodSlayerEligibilityTest,UserStatsServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile/test failure because the eligibility class and new engine model fields do not exist.

- [ ] **Step 3: Add engine model fields without changing the schema**

Reuse the existing `CofUserStats.godDefeatedAt`, `CofUserStats.godRewardGameId`, and `defeatedComputers` fields. `godDefeatedAt` is the permanent title marker; a non-null `godRewardGameId` is the pending reward marker. Do not add SQL, a Flyway migration, a new column, or another acknowledgement field.

Add `boolean godSlayer` to engine `Player` and `PublicPlayer`; and `String godSlayerAwardWinnerId` to `Game` and `PublicGame`. Add `boolean eliminated` to `GameSummary.SummaryPlayer` and populate it from the engine player.

- [ ] **Step 4: Implement the pure eligibility rule**

The class must identify the winner and GOD from the game rather than accept caller-supplied booleans:

```java
public final class GodSlayerEligibility {
    private static final Set<String> ALLOWED_DECKS = Set.of("1", "2");
    private static final String GOD_COMPUTER_ID = "computer_god";

    private GodSlayerEligibility() {}

    public static boolean eligible(Game game) {
        if (game == null || game.settings == null || game.settings.libraryIds == null
                || game.settings.libraryIds.isEmpty()
                || !ALLOWED_DECKS.containsAll(game.settings.libraryIds)) return false;
        Player winner = game.players.stream()
                .filter(p -> java.util.Objects.equals(p.clientId, game.winnerId))
                .findFirst().orElse(null);
        if (winner == null || winner.isComputer || winner.godSlayer || winner.drawPile.size() < 3) return false;
        return game.players.stream().anyMatch(p -> GOD_COMPUTER_ID.equals(p.computerId) && p.eliminated);
    }
}
```

- [ ] **Step 5: Make award persistence idempotent**

In `recordFinishedGame`, after ordinary per-player stats are written, handle GOD separately. Preserve non-GOD defeated-computer counters. For a qualifying game, reload the winner stats row; only when `godDefeatedAt == null` set:

```java
stats.godDefeatedAt = summary.at;
stats.godRewardGameId = summary.gameId;
game.godSlayerAwardWinnerId = winner.clientId;
winner.godSlayer = true;
```

Update the row once, and do not mark any non-winner as a GOD Slayer. Copy `godSlayer` and `godSlayerAwardWinnerId` in `GameCore.publicGame` and include the winner marker in persisted match summary metadata.

- [ ] **Step 6: Verify GREEN and commit**

Run the command from Step 2 plus:

```powershell
mvn -pl cof-game-engine -Dtest=GameCoreTest test
```

Expected: selected engine/service tests pass. Commit all Task 2 files with:

```powershell
git commit -m "feat(game): award GOD Slayer achievements"
```

## Task 3: Expose public title state and private reward acknowledgement

**Files:**
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/UserStatsService.java`
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/AuthService.java`
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/RoomService.java`
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/model/PublicPlayerDto.java`
- Modify: `cof-java-boot/cof-api/src/main/java/com/lumoxu/cof/api/controller/ProfileController.java`
- Modify: `cof-java-boot/cof-service/src/test/java/com/lumoxu/cof/service/UserStatsServiceTest.java`
- Modify: `cof-java-boot/cof-api/src/test/java/com/lumoxu/cof/api/controller/ProfileControllerTest.java`

- [ ] **Step 1: Add failing profile, room, and acknowledgement tests**

Assert profile output contains `godSlayer=true` and this object only while `godRewardGameId` is non-null:

```java
Map<String, Object> pending = (Map<String, Object>) profile.get("pendingGodSlayerReward");
assertEquals("game-1", pending.get("gameId"));
assertEquals("Winner", pending.get("username"));
```

Test `acknowledgeGodSlayerReward(statsId)` clears `godRewardGameId` and removes the pending object while preserving `godDefeatedAt`. In controller tests, `POST /api/v1/profile/{clientId}/god-slayer-reward/acknowledge` succeeds only for the authenticated client. In `RoomServiceTest`, assert human and GOD rows contain `statsId`, `godSlayer`, and `computerId` as appropriate.

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd cof-java-boot
mvn -pl cof-api -am "-Dtest=UserStatsServiceTest,RoomServiceTest,ProfileControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: assertions fail because these response fields and endpoint are absent.

- [ ] **Step 3: Implement public and private projections**

Add:

```java
public boolean isGodSlayer(String statsId) {
    CofUserStats stats = statsMapper.selectById(statsId);
    return stats != null && !Boolean.TRUE.equals(stats.isComputer) && stats.godDefeatedAt != null;
}
```

`profileFor` always includes `godSlayer`; it includes `pendingGodSlayerReward` only when `godRewardGameId` is non-null. `acknowledgeGodSlayerReward` rejects users with no pending reward, clears `godRewardGameId`, preserves `godDefeatedAt`, and updates the row. `AuthService.toPublicPlayer`, `RoomService.summary`, game creation, leaderboard output, and profile/history player rows receive explicit title identity fields. Add this controller method:

```java
@PostMapping("/{clientId}/god-slayer-reward/acknowledge")
public ApiResponse<Map<String, Object>> acknowledge(@PathVariable String clientId) {
    String self = AuthContext.get().clientId.toString();
    if (!self.equals(clientId)) throw new CofException(ErrorCode.FORBIDDEN, "只能确认自己的奖励。");
    return ApiResponse.ok(Map.of("profile", userStatsService.acknowledgeGodSlayerReward(clientId)));
}
```

- [ ] **Step 4: Verify GREEN and commit**

Run Step 2. Expected: all selected tests pass. Commit:

```powershell
git commit -m "feat(profile): expose GOD Slayer reward state"
```

## Task 4: Generate and cache real Source Han Serif subsets

**Files:**
- Create: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/FontSubsetService.java`
- Create: `cof-java-boot/cof-service/src/test/java/com/lumoxu/cof/service/FontSubsetServiceTest.java`
- Modify: `cof-java-boot/cof-api/src/main/java/com/lumoxu/cof/api/controller/FontController.java`
- Create: `cof-java-boot/cof-api/src/test/java/com/lumoxu/cof/api/controller/FontControllerTest.java`

- [ ] **Step 1: Write failing normalization/cache tests**

Test NFC normalization, duplicate removal, a 64-character limit, stable SHA-256 key, GOD's fixed `GOD` set, cache reuse, temp-file replacement, and runner failure cleanup. Inject a package-private runner in tests so no external Python process is required for unit tests.

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd cof-java-boot
mvn -pl cof-api -am "-Dtest=FontSubsetServiceTest,FontControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because `FontSubsetService` and the new controller contract do not exist.

- [ ] **Step 3: Implement bounded process execution and cache**

Resolve source in this order: `${cof.resource-root}/../old/SourceHanSerifSC-VF.otf.woff2`, then `${cof.resource-root}/assets/fonts/SourceHanSerifSC-VF.otf.woff2`. Cache under `${cof.resource-root}/assets/fonts/name-subsets`. Run:

```java
List.of(
    pythonCommand,
    "-m", "fontTools.subset",
    source.toString(),
    "--text=" + normalizedText,
    "--flavor=woff2",
    "--layout-features=*",
    "--output-file=" + temporary.toString()
)
```

Use a per-cache-key `ReentrantLock`, require a non-empty output, atomically move the temp file, and return immutable cache headers. Obtain a player's text from `UserStatsService` by `statsId`; reject non-Slayers instead of accepting arbitrary query text.

- [ ] **Step 4: Replace the fixed endpoint with authoritative endpoints**

Expose:

```text
GET /api/v1/fonts/god-name-subset.woff2
GET /api/v1/fonts/players/{statsId}.woff2
```

The first subsets `GOD`; the second subsets only the persisted Slayer username. Return `font/woff2`, `Cache-Control: public, max-age=31536000, immutable`, and the generated file resource.

- [ ] **Step 5: Verify GREEN and one real local subset**

Run Step 2, then invoke `FontTools` once against `old/SourceHanSerifSC-VF.otf.woff2` in a temporary directory and verify the resulting file is non-empty and substantially smaller than the source. Do not commit generated cache output.

- [ ] **Step 6: Commit**

```powershell
git commit -m "feat(fonts): generate cached player name subsets"
```

## Task 5: Centralize semantic player-name rendering

**Files:**
- Create: `cof-web-vue3/src/utils/playerName.ts`
- Create: `cof-web-vue3/src/utils/playerName.test.ts`
- Create: `cof-web-vue3/src/components/PlayerName.vue`
- Modify: `cof-web-vue3/src/types/api.ts`
- Modify: `cof-web-vue3/src/components/AppShell.vue`
- Modify: `cof-web-vue3/src/components/RoomChat.vue`
- Modify: `cof-web-vue3/src/components/game/GameTable.vue`
- Modify: `cof-web-vue3/src/components/game/GameResultModal.vue`
- Modify: `cof-web-vue3/src/components/game/GameResultChart.vue`
- Modify: `cof-web-vue3/src/components/room/ComputerPickerList.vue`
- Modify: `cof-web-vue3/src/views/WaitingView.vue`
- Modify: `cof-web-vue3/src/views/SettingsView.vue`
- Modify: `cof-web-vue3/src/views/RoomsView.vue`
- Modify: `cof-web-vue3/src/views/LeaderboardView.vue`
- Modify: `cof-web-vue3/src/views/ProfileView.vue`
- Modify: `cof-web-vue3/src/views/MatchReplayView.vue`
- Modify: `cof-web-vue3/src/assets/styles.css`

- [ ] **Step 1: Write failing identity and font URL tests**

Test GOD precedence over Slayer, ordinary names, authoritative `statsId` URL encoding, and the fixed GOD endpoint:

```ts
expect(playerNameKind({ username: "GOD", computerId: "computer_god", godSlayer: true })).toBe("god");
expect(playerNameKind({ username: "Winner", godSlayer: true })).toBe("slayer");
expect(playerNameKind({ username: "Guest" })).toBe("normal");
expect(playerFontUrl({ username: "Winner", statsId: "abc", godSlayer: true }))
  .toBe("/api/v1/fonts/players/abc.woff2");
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd cof-web-vue3
npm run test:unit -- src/utils/playerName.test.ts
```

Expected: module import failure.

- [ ] **Step 3: Implement the helper and component**

`PlayerName.vue` renders exactly one `<span>`, selects `normal`, `god`, or `slayer`, loads a unique `FontFace` family for special names, and assigns that family only to its own span. It must never query/replace page text. Add animated gold and pale-red multi-layer shadows plus:

```css
@media (prefers-reduced-motion: reduce) {
  .player-name--god,
  .player-name--slayer { animation: none; }
}
```

- [ ] **Step 4: Replace semantic username slots**

Use `PlayerName` for header identity, room player lists, waiting players, settings host options where custom option rendering is possible, game stations, chat sender spans, result winner/legend, leaderboard username cells, profile username/history, computer picker, and replay player labels. For native `<option>` text that cannot host components, preserve plain text and style the selected summary outside the native control. Pass player identity objects to `RoomChat` so it resolves senders by `clientId`. Do not alter free-form log text.

- [ ] **Step 5: Verify GREEN and build**

Run:

```powershell
npm run test:unit -- src/utils/playerName.test.ts
npm run build
```

Expected: tests pass and Vite build exits 0.

- [ ] **Step 6: Commit**

```powershell
git commit -m "feat(web): style GOD and GOD Slayer names"
```

## Task 6: Add winner-only reward modal and audio routing

**Files:**
- Create: `cof-web-vue3/src/api/profile.test.ts`
- Create: `cof-web-vue3/src/components/GodSlayerRewardModal.vue`
- Create: `cof-web-vue3/src/utils/godSlayerReward.ts`
- Create: `cof-web-vue3/src/utils/godSlayerReward.test.ts`
- Modify: `cof-web-vue3/src/api/profile.ts`
- Modify: `cof-web-vue3/src/composables/useGameAudio.ts`
- Modify: `cof-web-vue3/src/composables/useGameAudio.test.ts`
- Modify: `cof-web-vue3/src/views/GameView.vue`
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/RoomService.java`
- Include: `cof-resource/audio/cheers.mp3`

- [ ] **Step 1: Write failing reward and audio tests**

With fake timers, assert the acknowledgement gate returns 10 initially, remains disabled at 9 seconds, and enables at 10 seconds. Assert:

```ts
expect(gameEndSound({ selfId: "winner", awardWinnerId: "winner" })).toBe("/audio/cheers.mp3");
expect(gameEndSound({ selfId: "other", awardWinnerId: "winner" })).toBe("/audio/endgame.wav");
expect(gameEndSound({ selfId: "winner" })).toBe("/audio/endgame.wav");
```

Add profile API tests for pending reward parsing and acknowledgement POST.

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd cof-web-vue3
npm run test:unit -- src/utils/godSlayerReward.test.ts src/composables/useGameAudio.test.ts src/api/profile.test.ts
```

Expected: missing helper/API failures and old audio selection assertions.

- [ ] **Step 3: Implement private reward refresh and modal**

When a finished game arrives, `GameView` refreshes the current user's profile. Show the modal only when `pendingGodSlayerReward.gameId === game.id`. The component starts a fresh 10 秒 timer on every mount, preloads the `PlayerName` font immediately, uses the approved exact Chinese copy, and emits acknowledge only after the gate opens. On API failure it stays open and restores the button.

- [ ] **Step 4: Route end audio before playback**

Select the URL synchronously from `game.godSlayerAwardWinnerId` and authenticated self ID so the new winner never starts `endgame.wav`. Add `/audio/cheers.mp3` to `RoomService.assetManifest`; retain all existing audio assets.

- [ ] **Step 5: Verify GREEN and commit**

Run Step 2 and `npm run build`. Expected: all pass. Commit:

```powershell
git add cof-resource/audio/cheers.mp3 cof-web-vue3 cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/RoomService.java
git commit -m "feat(game): celebrate new GOD Slayers"
```

## Task 7: Add recolored card-back preset with direct upload

**Files:**
- Create: `cof-web-vue3/src/utils/cardBackPreset.ts`
- Create: `cof-web-vue3/src/utils/cardBackPreset.test.ts`
- Create: `cof-web-vue3/src/components/CardBackPresetModal.vue`
- Modify: `cof-web-vue3/src/views/CardSubmitView.vue`
- Include: `cof-resource/cards/placeholder.png`

- [ ] **Step 1: Write failing pixel tests**

Use an exported `mapPresetPixel` and assert exact endpoints and midpoint:

```ts
expect(mapPresetPixel([0, 0, 0, 255], [100, 20, 200])).toEqual([100, 20, 200, 255]);
expect(mapPresetPixel([255, 255, 255, 255], [100, 20, 200])).toEqual([255, 255, 255, 255]);
expect(mapPresetPixel([128, 128, 128, 64], [0, 100, 200])).toEqual([128, 178, 228, 64]);
```

Use the BT.709 luminance coefficients and round output channels once.

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd cof-web-vue3
npm run test:unit -- src/utils/cardBackPreset.test.ts
```

Expected: module import failure.

- [ ] **Step 3: Implement the image pipeline and modal**

Load `/cards/placeholder.png`, draw to a canvas, map every pixel with:

```ts
const luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255;
const channel = (selected: number) => Math.round(selected + luminance * (255 - selected));
```

Render a custom color wheel with a visible pointer and hex value. Recompute preview without mutating the source image data. Export JPEG Blob at high quality through `canvas.toBlob`.

- [ ] **Step 4: Wire direct upload**

`CardSubmitView` opens the modal only with an active deck. On confirm, call `uploadSubmissionBack(activeDeckId, blob)` immediately, show existing success/error toasts, refresh submissions on success, and leave the modal open on failure.

- [ ] **Step 5: Verify GREEN, build, and commit**

Run Step 2 and `npm run build`. Expected: pass. Commit only the helper/component/view and the single placeholder asset:

```powershell
git add cof-web-vue3/src/utils/cardBackPreset.ts cof-web-vue3/src/utils/cardBackPreset.test.ts cof-web-vue3/src/components/CardBackPresetModal.vue cof-web-vue3/src/views/CardSubmitView.vue cof-resource/cards/placeholder.png
git commit -m "feat(submissions): add recolored card back preset"
```

Do not stage unrelated untracked card images.

## Task 8: Improve public rooms, waiting copy, settings, help, home, and footer

**Files:**
- Create: `cof-web-vue3/src/utils/roomPresentation.ts`
- Create: `cof-web-vue3/src/utils/roomPresentation.test.ts`
- Modify: `cof-web-vue3/src/views/RoomsView.vue`
- Modify: `cof-web-vue3/src/views/WaitingView.vue`
- Modify: `cof-web-vue3/src/views/SettingsView.vue`
- Modify: `cof-web-vue3/src/views/SettingsView.test.ts`
- Modify: `cof-web-vue3/src/views/RulesView.vue`
- Modify: `cof-web-vue3/src/views/HomeView.vue`
- Modify: `cof-web-vue3/src/components/AppShell.vue`
- Modify: `cof-web-vue3/src/assets/styles.css`
- Modify: `cof-web-vue3/src/api/rooms.ts`
- Modify: `cof-web-vue3/src/stores/roomStore.ts`
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/RoomService.java`
- Modify: `cof-java-boot/cof-service/src/test/java/com/lumoxu/cof/service/RoomServiceTest.java`

- [ ] **Step 1: Write failing room presentation and visibility tests**

Test strict public filtering, deck names/copies, unknown deck fallback, and rule labels:

```ts
expect(publicRooms([{ id: "a", settings: { isPublic: true } }, { id: "b", settings: { isPublic: false } }]))
  .toEqual([{ id: "a", settings: { isPublic: true } }]);
expect(deckSummary(room, [{ id: "1", name: "基础包" }, { id: "2", name: "拓展包" }]))
  .toBe("基础包 ×2、拓展包 ×1");
```

Add `RoomServiceTest` proving `listRooms(clientId, false)` excludes a private room even when the caller is a member. Update `SettingsView.test.ts` to assert the visible template no longer contains “卡牌耗尽仍可抢铃” while the script still preserves `allowEmptyBell`.

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd cof-web-vue3
npm run test:unit -- src/utils/roomPresentation.test.ts src/views/SettingsView.test.ts
cd ../cof-java-boot
mvn -pl cof-service -am "-Dtest=RoomServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: missing utility, old visible setting, and private-member room leakage failures.

- [ ] **Step 3: Make public listing strict and informative**

Change the public page to request the normal public list, not `{ all: 1 }`. Make `includePrivate=false` strict in `RoomService.listRooms`; direct room join remains the path for private rooms. Defensively run `publicRooms` before rendering. Load meta, render usernames with `PlayerName`, map library IDs to names and `libraryCopies`, render rule tags, and remove vote text.

- [ ] **Step 4: Add the waiting-room “复制房间 ID” button and small visual changes**

Add a `copyRoomId` action using `navigator.clipboard.writeText(roomId.value)` with success/failure toast. Center `.game-intro` and remove its left border/padding. Hide only the advanced option label for `allowEmptyBell`. Set the footer exactly to:

```text
Version 2.0, Built by DrowningYu & LUMO_Xu with good vibes.
```

- [ ] **Step 5: Rewrite the detailed player instructions**

Replace the six short rule cards with clear sections covering every approved item: main-menu buttons, create/direct/public join, room settings and deck copies, waiting-room host/player buttons, loading, play pile/bell/turn/chat, ring outcomes, elimination/win, result replay/continue, and card browsing/submission. Use existing `rules-grid` responsive styling and add only focused spacing styles.

- [ ] **Step 6: Verify GREEN and commit**

Run Step 2 and `npm run build`. Expected: all pass. Commit:

```powershell
git commit -m "feat(web): improve room and player guidance UX"
```

## Task 9: Full verification, scope audit, final commit, and push

**Files:**
- Include the user's existing modified copy files: `cof-web-vue3/index.html`, `cof-web-vue3/src/views/CardInfoView.vue`, `cof-web-vue3/src/views/CardSubmitView.vue`, `cof-web-vue3/src/views/HomeView.vue`, `cof-web-vue3/src/views/LeaderboardView.vue`
- Include: `docs/superpowers/plans/2026-06-18-card-back-room-ux-god-slayer.md`
- Include: `docs/database/god-slayer-persistence.md`
- Exclude unrelated untracked files under `cof-resource/cards/`.

- [ ] **Step 1: Run the complete frontend suite and build**

```powershell
cd cof-web-vue3
npm run test:unit
npm run build
```

Expected: Vitest reports zero failed tests; Vite exits 0.

- [ ] **Step 2: Run focused backend suites**

```powershell
cd ../cof-java-boot
mvn -pl "cof-service,cof-api,cof-game-engine" -am test "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: all tests in selected modules pass. If branch-known unrelated failures remain, capture exact names and still run every new/modified test directly to prove this feature.

- [ ] **Step 3: Run package builds**

```powershell
$env:COF_RESOURCE_ROOT=(Resolve-Path '..\cof-resource').Path
mvn -pl cof-boot -am package -DskipTests
```

Expected: backend package exits 0.

- [ ] **Step 4: Audit every requirement and staged path**

Check the design section-by-section, then run:

```powershell
git diff --check
git status --short
git diff --stat origin/feature-dyu...HEAD
git diff --name-only --cached
```

Confirm the two requested assets and `docs/database/god-slayer-persistence.md` are included, no GOD Slayer Flyway/schema migration is present, and random UUID card images plus `cards/backs/1.jpg` and `cards/backs/2.jpg` remain unstaged unless the user separately authorizes them.

- [ ] **Step 5: Perform browser smoke tests when services are available**

Verify card preset direct upload, strict public rooms, clipboard feedback, hidden setting, detailed help, exact footer, GOD/Slayer animations, font response size, reward refresh countdown, acknowledgement, and mutually exclusive end sounds.

- [ ] **Step 6: Commit remaining approved user copy and plan**

```powershell
git add -f docs/superpowers/plans/2026-06-18-card-back-room-ux-god-slayer.md
git add cof-web-vue3/index.html cof-web-vue3/src/views/CardInfoView.vue cof-web-vue3/src/views/CardSubmitView.vue cof-web-vue3/src/views/HomeView.vue cof-web-vue3/src/views/LeaderboardView.vue
git commit -m "chore(web): refresh player-facing copy"
```

Skip this commit if all listed files were already included intentionally in earlier task commits.

- [ ] **Step 7: Push the verified branch**

```powershell
git push origin feature-dyu
```

Expected: push succeeds and local `feature-dyu` is aligned with `origin/feature-dyu`.
