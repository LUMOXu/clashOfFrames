"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  normalizeSettings,
  dealCards,
  createGame,
  startPlaying,
  findCurrentMatch,
  setTurnTiming,
  performPlayCard,
  performRingBell,
  markConnection,
  summarizeGameForStats,
} = require("../src/gameCore");

function sampleCard(id, pmvId) {
  return {
    id,
    libraryId: "lib",
    fileName: `${pmvId}a.png`,
    pmvId,
    pmvName: `PMV ${pmvId}`,
    shot: "a",
    imageUrl: `/cards/lib/cards/${pmvId}a.png`,
    backUrl: "/cards/lib/back.png",
  };
}

function sampleGame(settings = {}) {
  const room = {
    id: "1",
    settings: normalizeSettings(settings, ["lib"]),
  };
  const players = [
    { clientId: "a", username: "A", connected: true },
    { clientId: "b", username: "B", connected: true },
    { clientId: "c", username: "C", connected: true },
  ];
  const cards = Array.from({ length: 12 }, (_, index) => sampleCard(`card-${index}`, Math.floor(index / 2) + 1));
  const game = createGame({ room, players, cards, now: 1000, rng: () => 0.42 });
  startPlaying(game, 1000);
  return game;
}

test("normalizes room settings and clamps player counts", () => {
  const settings = normalizeSettings({ minPlayers: 1, maxPlayers: 2, libraryIds: ["x"] }, ["lib"]);
  assert.equal(settings.minPlayers, 3);
  assert.equal(settings.maxPlayers, 3);
  assert.deepEqual(settings.libraryIds, ["lib"]);
  assert.equal(settings.conflictResolution, true);
});

test("deals equal card counts and discards leftovers", () => {
  const cards = Array.from({ length: 10 }, (_, index) => sampleCard(`c${index}`, index));
  const dealt = dealCards(cards, 3);
  assert.equal(dealt.perPlayer, 3);
  assert.equal(dealt.discarded, 1);
  assert.deepEqual(dealt.hands.map((hand) => hand.length), [3, 3, 3]);
});

test("detects matching top display cards by PMV id", () => {
  const game = sampleGame();
  game.players[0].displayPile.push({ ...sampleCard("a1", 7), playedSeq: 1 });
  game.players[1].displayPile.push({ ...sampleCard("b1", 7), playedSeq: 2 });
  game.players[2].displayPile.push({ ...sampleCard("c1", 8), playedSeq: 3 });
  const match = findCurrentMatch(game);
  assert.equal(match.pmvId, 7);
  assert.equal(match.cards.length, 2);
});

test("correct bell clears display piles and awards won cards", () => {
  const game = sampleGame();
  game.players[0].displayPile.push({ ...sampleCard("a1", 2), playedSeq: 1 });
  game.players[1].displayPile.push({ ...sampleCard("b1", 2), playedSeq: 2 });
  game.players[2].displayPile.push({ ...sampleCard("c1", 3), playedSeq: 3 });
  const before = game.players[0].drawPile.length;
  const result = performRingBell(game, "a", { now: 2000, rng: () => 0.1 });
  assert.equal(result.ok, true);
  assert.deepEqual(game.players.map((player) => player.displayPile.length), [0, 0, 0]);
  assert.equal(game.players[0].drawPile.length, before + 3);
  assert.equal(game.successBellCount, 1);
  assert.equal(game.lastMatch.pmvId, 2);
  assert.equal(game.lastAnimation.type, "success");
  assert.equal(game.lastAnimation.piles.length, 3);
  assert.equal(game.lockedUntil, 6200);
});

test("wrong bell gives cards to other active players", () => {
  const game = sampleGame();
  game.players[0].drawPile = [sampleCard("x1", 1), sampleCard("x2", 2), sampleCard("x3", 3)];
  game.players[1].drawPile = [];
  game.players[2].drawPile = [];
  const result = performRingBell(game, "a", { now: 2000 });
  assert.equal(result.ok, true);
  assert.equal(game.players[0].drawPile.length, 1);
  assert.equal(game.players[1].drawPile.length, 1);
  assert.equal(game.players[2].drawPile.length, 1);
  assert.equal(game.failBellCount, 1);
  assert.equal(game.lastAnimation.type, "fail");
  assert.equal(game.lastAnimation.transfers.length, 2);
});

test("conflict resolution accepts the previous table match", () => {
  const game = sampleGame({ conflictResolution: true });
  game.preLastTopCards = [
    { playerId: "a", username: "A", card: { ...sampleCard("old-a", 5), playedSeq: 1 }, playedSeq: 1 },
    { playerId: "b", username: "B", card: { ...sampleCard("old-b", 5), playedSeq: 2 }, playedSeq: 2 },
  ];
  game.players[0].displayPile.push({ ...sampleCard("new-a", 1), playedSeq: 3 });
  game.players[1].displayPile.push({ ...sampleCard("new-b", 2), playedSeq: 4 });
  const result = performRingBell(game, "c", { now: 2000, rng: () => 0.1 });
  assert.equal(result.ok, true);
  assert.equal(game.successBellCount, 1);
  assert.equal(game.lastMatch.pmvId, 5);
});

test("disabled conflict resolution ignores the previous table match", () => {
  const game = sampleGame({ conflictResolution: false });
  game.preLastTopCards = [
    { playerId: "a", username: "A", card: { ...sampleCard("old-a", 5), playedSeq: 1 }, playedSeq: 1 },
    { playerId: "b", username: "B", card: { ...sampleCard("old-b", 5), playedSeq: 2 }, playedSeq: 2 },
  ];
  game.players[0].displayPile.push({ ...sampleCard("new-a", 1), playedSeq: 3 });
  game.players[1].displayPile.push({ ...sampleCard("new-b", 2), playedSeq: 4 });
  const result = performRingBell(game, "c", { now: 2000 });
  assert.equal(result.ok, true);
  assert.equal(game.failBellCount, 1);
});

test("playing last draw card eliminates player when empty bell is disabled", () => {
  const game = sampleGame();
  game.turnIndex = 0;
  game.players[0].drawPile = [sampleCard("last", 9)];
  const result = performPlayCard(game, "a", { now: 2000 });
  assert.equal(result.ok, true);
  assert.equal(game.players[0].eliminated, true);
});

test("disconnected protected player gets a two second auto-play deadline", () => {
  const game = sampleGame({ disconnectProtection: true });
  game.turnIndex = 1;
  markConnection(game, "b", false, { disconnectProtection: true });
  setTurnTiming(game, 3000, 0);
  assert.equal(game.turnDeadlineAt, 5000);
  assert.equal(game.players[1].eliminated, false);
});

test("allow empty bell still eliminates an empty player in a two player duel", () => {
  const game = sampleGame({ allowEmptyBell: true });
  game.players[2].eliminated = true;
  game.eliminatedOrder = ["c"];
  game.turnIndex = 0;
  game.players[0].drawPile = [sampleCard("last", 9)];
  game.players[1].drawPile = [sampleCard("other", 10)];
  const result = performPlayCard(game, "a", { now: 2000 });
  assert.equal(result.ok, true);
  assert.equal(game.players[0].eliminated, true);
  assert.equal(game.status, "finished");
  assert.equal(game.winnerId, "b");
});

test("finished game summary includes winner and bell totals", () => {
  const game = sampleGame();
  game.players[1].eliminated = true;
  game.players[2].eliminated = true;
  game.eliminatedOrder = ["b", "c"];
  game.status = "playing";
  game.successBellCount = 2;
  game.bellCount = 3;
  const { checkGameOver } = require("../src/gameCore");
  checkGameOver(game, 5000);
  const summary = summarizeGameForStats(game);
  assert.equal(summary.winnerId, "a");
  assert.equal(summary.bellCount, 3);
  assert.equal(summary.averageRoundLength, game.playCount / 2);
});
