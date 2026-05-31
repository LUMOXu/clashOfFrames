"use strict";

const fs = require("fs");
const path = require("path");

function toUrlPath(parts) {
  return `/${parts.map((part) => encodeURIComponent(part).replace(/%2F/g, "/")).join("/")}`;
}

function parseManifest(text) {
  const trimmed = String(text || "").trim();
  if (!trimmed) return manifestResult([]);
  if (trimmed.startsWith("[")) {
    const parsed = JSON.parse(trimmed);
    if (!Array.isArray(parsed)) throw new Error("manifest.json must be a list.");
    return manifestResult(parsed.map((entry) => normalizeManifestEntry(entry)).filter(Boolean));
  }
  if (trimmed.startsWith("{")) {
    const parsed = JSON.parse(trimmed);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("manifest.json must be an object or a list.");
    }
    const rawEntries = Array.isArray(parsed.pmvs)
      ? parsed.pmvs
      : Array.isArray(parsed.entries)
        ? parsed.entries
        : Array.isArray(parsed.cards)
          ? parsed.cards
          : [];
    return manifestResult(
      rawEntries.map((entry) => normalizeManifestEntry(entry)).filter(Boolean),
      normalizeManifestMetadata(parsed),
      true,
    );
  }
  return parseLegacyManifest(trimmed);
}

function parseLegacyManifest(text) {
  const entries = [];
  text.split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .forEach((line) => {
      const comma = line.indexOf(",");
      if (comma < 0) return;
      const pmvId = Number.parseInt(line.slice(0, comma), 10);
      const name = line.slice(comma + 1).trim();
      if (Number.isFinite(pmvId)) {
        entries.push({ pmvId, name, author: null, description: null, link: null });
      }
    });
  return manifestResult(entries);
}

function normalizeManifestEntry(entry) {
  if (!entry || typeof entry !== "object") return null;
  const pmvId = Number.parseInt(entry.pmv_id ?? entry.pmvId, 10);
  if (!Number.isFinite(pmvId)) return null;
  return {
    pmvId,
    name: typeof entry.name === "string" && entry.name.trim() ? entry.name.trim() : `PMV ${pmvId}`,
    author: nullableString(entry.author),
    description: nullableString(entry.description),
    link: nullableString(entry.link),
  };
}

function normalizeManifestMetadata(input) {
  return {
    name: nullableString(input.name ?? input.title),
    curator: nullableString(input.curator ?? input.organizer ?? input.author),
    description: nullableString(input.description),
    version: nullableString(input.version),
    link: nullableString(input.link ?? input.url),
  };
}

function nullableString(value) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed || null;
}

function manifestResult(entries, metadata = normalizeManifestMetadata({}), hasMetadata = false) {
  const sorted = entries.slice().sort((a, b) => a.pmvId - b.pmvId);
  return {
    metadata,
    hasMetadata,
    entries: sorted,
    byId: new Map(sorted.map((entry) => [entry.pmvId, entry])),
  };
}

function parseCardFile(fileName) {
  const match = /^(\d+)([a-z]+)\.(?:png|jpe?g)$/i.exec(fileName);
  if (!match) return null;
  return {
    pmvId: Number.parseInt(match[1], 10),
    shot: match[2],
  };
}

function libraryCopyLimit(library) {
  const cardCount = Math.max(1, Number.parseInt(library?.cardCount, 10) || 1);
  return Math.max(1, Math.floor(120 / cardCount));
}

function cardsGroupedByPmv(library) {
  const manifestEntries = Array.isArray(library?.manifest) ? library.manifest : [];
  const byId = new Map(manifestEntries.map((entry) => [entry.pmvId, entry]));
  const cardsByPmv = new Map();
  (library?.cards || []).forEach((card) => {
    if (!cardsByPmv.has(card.pmvId)) cardsByPmv.set(card.pmvId, []);
    cardsByPmv.get(card.pmvId).push(card);
  });
  const ids = [...new Set([...byId.keys(), ...cardsByPmv.keys()])].sort((a, b) => a - b);
  return ids.map((pmvId) => {
    const meta = byId.get(pmvId) || { pmvId, name: `PMV ${pmvId}`, author: null, description: null, link: null };
    const shots = (cardsByPmv.get(pmvId) || [])
      .slice()
      .sort((a, b) => String(a.shot).localeCompare(String(b.shot), "en"));
    return {
      ...meta,
      shots: shots.map((card) => ({
        id: card.id,
        shot: card.shot,
        imageUrl: card.imageUrl,
      })),
    };
  });
}

function splitLibraryName(folderName) {
  const at = folderName.indexOf("@");
  if (at < 0) return { title: folderName, curator: null };
  return {
    title: folderName.slice(0, at).trim() || folderName,
    curator: folderName.slice(at + 1).trim() || null,
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
      const manifestJsonPath = path.join(libraryDir, "manifest.json");
      const manifestTxtPath = path.join(libraryDir, "manifest.txt");
      const manifestPath = fs.existsSync(manifestJsonPath) ? manifestJsonPath : manifestTxtPath;
      const cardsDir = path.join(libraryDir, "cards");
      const backPath = path.join(libraryDir, "back.png");
      if (!fs.existsSync(manifestPath) || !fs.existsSync(cardsDir) || !fs.existsSync(backPath)) {
        return null;
      }

      const manifest = parseManifest(fs.readFileSync(manifestPath, "utf8"));
      const backUrl = toUrlPath(["cards", libraryId, "back.png"]);
      const libraryName = splitLibraryName(libraryId);
      const metadata = manifest.metadata || normalizeManifestMetadata({});
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
            pmvName: manifest.byId.get(parsed.pmvId)?.name || `PMV ${parsed.pmvId}`,
            pmvAuthor: manifest.byId.get(parsed.pmvId)?.author || null,
            pmvDescription: manifest.byId.get(parsed.pmvId)?.description || null,
            pmvLink: manifest.byId.get(parsed.pmvId)?.link || null,
            shot: parsed.shot,
            imageUrl: toUrlPath(["cards", libraryId, "cards", cardEntry.name]),
            backUrl,
          };
        })
        .filter(Boolean)
        .sort((a, b) => a.pmvId - b.pmvId || a.shot.localeCompare(b.shot));

      return {
        id: libraryId,
        name: metadata.name || libraryId,
        folderName: libraryId,
        title: metadata.name || (manifest.hasMetadata ? "" : libraryName.title),
        curator: metadata.curator || (manifest.hasMetadata ? null : libraryName.curator),
        description: metadata.description,
        version: metadata.version,
        link: metadata.link,
        metadata,
        manifest: manifest.entries,
        backUrl,
        cardCount: cards.length,
        pmvCount: manifest.entries.length,
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
  cardsGroupedByPmv,
  libraryCopyLimit,
};
