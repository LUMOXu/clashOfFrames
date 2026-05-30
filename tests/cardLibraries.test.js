"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  parseManifest,
  cardsGroupedByPmv,
  libraryCopyLimit,
} = require("../src/cardLibraries");

test("parses manifest json with optional PMV metadata", () => {
  const manifest = parseManifest(JSON.stringify([
    {
      pmv_id: 7,
      name: "Song",
      author: "Editor",
      description: "A sharp little cut.",
      link: "https://example.test/pmv",
    },
  ]));

  assert.deepEqual(manifest.entries, [{
    pmvId: 7,
    name: "Song",
    author: "Editor",
    description: "A sharp little cut.",
    link: "https://example.test/pmv",
  }]);
  assert.equal(manifest.byId.get(7).author, "Editor");
});

test("groups cards by PMV id using manifest details", () => {
  const grouped = cardsGroupedByPmv({
    manifest: [
      { pmvId: 3, name: "Three", author: null, description: null, link: null },
    ],
    cards: [
      { id: "3b", pmvId: 3, shot: "b", imageUrl: "/3b.jpg" },
      { id: "3a", pmvId: 3, shot: "a", imageUrl: "/3a.jpg" },
    ],
  });

  assert.equal(grouped.length, 1);
  assert.equal(grouped[0].pmvId, 3);
  assert.deepEqual(grouped[0].shots.map((shot) => shot.shot), ["a", "b"]);
});

test("limits per-library copy count by the 120 card cap", () => {
  assert.equal(libraryCopyLimit({ cardCount: 50 }), 2);
  assert.equal(libraryCopyLimit({ cardCount: 121 }), 1);
});
