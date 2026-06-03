import type { GameSettings, PublicCard } from "@/types/api";

export interface CompactCard {
  i: string;
  l?: string;
  p?: number;
  s?: number;
  h?: string;
  b?: number;
  u?: string;
  bk?: string;
}

function defaultLibrary(settings?: GameSettings): string | undefined {
  const ids = settings?.libraryIds;
  return ids?.length ? ids[0] : undefined;
}

function encodePathSegment(segment: string): string {
  return segment
    .split("/")
    .map((part) => encodeURIComponent(part))
    .join("/");
}

function deckBackUrl(libraryId: string): string {
  if (/^\d+$/.test(libraryId)) {
    return `/cards/backs/${libraryId}.jpg`;
  }
  return `/cards/${encodePathSegment(libraryId)}/back.png`;
}

function canonicalCardUrls(
  lib: string,
  pmvId: number,
  shot: string,
): { imageUrl: string; backUrl: string } {
  const deck = encodePathSegment(lib);
  const ext = "jpg";
  return {
    imageUrl: `/cards/${deck}/${pmvId}/${shot}.${ext}`,
    backUrl: deckBackUrl(lib),
  };
}

export function expandCompactCard(
  compact: CompactCard | PublicCard,
  settings?: GameSettings,
): PublicCard {
  if (!compact || typeof compact !== "object") {
    return compact as PublicCard;
  }
  if ("clientId" in compact || ("imageUrl" in compact && compact.imageUrl)) {
    return compact as PublicCard;
  }
  const raw = compact as CompactCard & PublicCard;
  const id = raw.i ?? raw.id;
  if (!id) {
    return raw as PublicCard;
  }
  const lib = raw.l ?? raw.libraryId ?? defaultLibrary(settings);
  const pmvId = raw.p ?? raw.pmvId;
  const playedSeq = raw.s ?? raw.playedSeq;
  const shot = (raw.h ?? raw.shot ?? "a").toLowerCase();
  const backOnly = raw.b === 1 || (!pmvId && !raw.imageUrl && !raw.u);

  if (raw.u || raw.bk) {
    return {
      id,
      libraryId: lib,
      pmvId,
      playedSeq,
      shot,
      imageUrl: raw.u ?? raw.imageUrl,
      backUrl: raw.bk ?? raw.backUrl,
    };
  }

  if (!lib) {
    return { id, libraryId: lib, pmvId, playedSeq, shot };
  }

  const urls = canonicalCardUrls(lib, pmvId ?? 0, shot);
  if (backOnly) {
    return { id, libraryId: lib, backUrl: urls.backUrl };
  }
  if (!pmvId) {
    return { id, libraryId: lib, playedSeq, shot, backUrl: urls.backUrl };
  }
  return {
    id,
    libraryId: lib,
    pmvId,
    playedSeq,
    shot,
    imageUrl: urls.imageUrl,
    backUrl: urls.backUrl,
  };
}

export function expandCompactCards(
  cards: unknown,
  settings?: GameSettings,
): PublicCard[] | undefined {
  if (!Array.isArray(cards)) {
    return undefined;
  }
  return cards.map((card) => expandCompactCard(card as CompactCard, settings));
}
