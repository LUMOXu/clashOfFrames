"use strict";

const fs = require("fs");
const path = require("path");

function toUrlPath(parts) {
  return `/${parts.map((part) => encodeURIComponent(part).replace(/%2F/g, "/")).join("/")}`;
}

function parseManifest(text) {
  const names = new Map();
  text.split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .forEach((line) => {
      const comma = line.indexOf(",");
      if (comma < 0) return;
      const id = Number.parseInt(line.slice(0, comma), 10);
      const name = line.slice(comma + 1).trim();
      if (Number.isFinite(id)) names.set(id, name);
    });
  return names;
}

function parseCardFile(fileName) {
  const match = /^(\d+)([a-z]+)\.(?:png|jpe?g)$/i.exec(fileName);
  if (!match) return null;
  return {
    pmvId: Number.parseInt(match[1], 10),
    shot: match[2],
  };
}

function discoverCardLibraries(rootDir) {
  const cardsRoot = path.join(rootDir, "cards");
  if (!fs.existsSync(cardsRoot)) return [];

  return fs.readdirSync(cardsRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => {
      const libraryId = entry.name;
      const libraryDir = path.join(cardsRoot, libraryId);
      const manifestPath = path.join(libraryDir, "manifest.txt");
      const cardsDir = path.join(libraryDir, "cards");
      const backPath = path.join(libraryDir, "back.png");
      if (!fs.existsSync(manifestPath) || !fs.existsSync(cardsDir) || !fs.existsSync(backPath)) {
        return null;
      }

      const manifest = parseManifest(fs.readFileSync(manifestPath, "utf8"));
      const backUrl = toUrlPath(["cards", libraryId, "back.png"]);
      const cards = fs.readdirSync(cardsDir, { withFileTypes: true })
        .filter((cardEntry) => cardEntry.isFile() && /\.(?:png|jpe?g)$/i.test(cardEntry.name))
        .map((cardEntry) => {
          const parsed = parseCardFile(cardEntry.name);
          if (!parsed) return null;
          return {
            id: `${libraryId}/${cardEntry.name}`,
            libraryId,
            fileName: cardEntry.name,
            pmvId: parsed.pmvId,
            pmvName: manifest.get(parsed.pmvId) || `PMV ${parsed.pmvId}`,
            shot: parsed.shot,
            imageUrl: toUrlPath(["cards", libraryId, "cards", cardEntry.name]),
            backUrl,
          };
        })
        .filter(Boolean)
        .sort((a, b) => a.pmvId - b.pmvId || a.shot.localeCompare(b.shot));

      return {
        id: libraryId,
        name: libraryId,
        manifest: [...manifest.entries()].map(([pmvId, name]) => ({ pmvId, name })),
        backUrl,
        cardCount: cards.length,
        pmvCount: manifest.size,
        cards,
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.name.localeCompare(b.name, "zh-Hans-CN"));
}

module.exports = {
  discoverCardLibraries,
  parseManifest,
  parseCardFile,
};
