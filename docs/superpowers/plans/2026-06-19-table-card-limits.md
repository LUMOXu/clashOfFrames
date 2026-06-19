# Table Card Limits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require more than 150 actual game cards for GOD Slayer and replace per-deck copy caps with a 216-card global room cap.

**Architecture:** The engine owns deterministic normalization under one global card budget. The Vue picker mirrors the same calculation for immediate feedback. GOD Slayer eligibility derives the conserved actual-card total from the finished game state.

**Tech Stack:** Java 17/Spring Boot, Vue 3/TypeScript, JUnit 5, Vitest.

---

### Task 1: GOD Slayer actual-card threshold

**Files:**
- Modify: `cof-java-boot/cof-service/src/main/java/com/lumoxu/cof/service/GodSlayerEligibility.java`
- Modify: `cof-java-boot/cof-service/src/test/java/com/lumoxu/cof/service/GodSlayerEligibilityTest.java`

- [ ] Add a package-visible `tableCardCount(Game)` helper that sums every player's `drawPile` and `displayPile`, then adds `game.discardedCards`.
- [ ] Require `tableCardCount(game) > 150` in `eligible(Game)` without changing the existing deck-ID, GOD elimination, human winner, three-card, or prior-title conditions.
- [ ] Add 150/151-card boundary cases to the existing eligibility test and run:

```powershell
mvn -pl cof-service -am -Dtest=GodSlayerEligibilityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all `GodSlayerEligibilityTest` cases pass.

### Task 2: Backend 216-card global budget

**Files:**
- Modify: `cof-java-boot/cof-game-engine/src/main/java/com/lumoxu/cof/engine/GameCore.java`
- Modify: `cof-java-boot/cof-game-engine/src/test/java/com/lumoxu/cof/engine/GameCoreTest.java`

- [ ] Replace `floor(120 / cardCount)` per-library clamping with one `remainingCards = 216` budget.
- [ ] First retain selected libraries whose first copy fits, preserving selection order; then allocate requested extra copies while enough budget remains.
- [ ] Add engine cases proving two libraries share the 216 budget and a small single deck may exceed the former 120-card total.
- [ ] Run:

```powershell
mvn -pl cof-game-engine -am -Dtest=GameCoreTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all `GameCoreTest` cases pass.

### Task 3: Frontend global-budget picker and delivery

**Files:**
- Modify: `cof-web-vue3/src/components/room/LibraryPicker.vue`
- Modify: `cof-web-vue3/src/utils/format.ts`
- Create: `cof-web-vue3/src/utils/libraryCardLimit.ts`
- Create: `cof-web-vue3/src/utils/libraryCardLimit.test.ts`
- Preserve and include: current user-authored wording/config changes already present in the worktree.

- [ ] Add pure helpers for total cards, remaining capacity, whether a first copy fits, and dynamic maximum copies under 216.
- [ ] Use those helpers to prevent selecting a deck that cannot fit, clamp copy inputs to the global budget, and display `当前 N / 216 张` plus an over-limit hint.
- [ ] Remove the obsolete `libraryCopyLimit()` per-deck helper.
- [ ] Run the green test, full frontend build, and backend package:

```powershell
npm run test:unit -- --run src/utils/libraryCardLimit.test.ts
npm run build
mvn -pl cof-boot -am package -DskipTests
```

Expected: all commands exit 0.

- [ ] Stage implementation plus the user's current wording/config changes, excluding generated `tsconfig.app.tsbuildinfo` and unrelated untracked card JPGs; commit and push `feature-dyu`.
