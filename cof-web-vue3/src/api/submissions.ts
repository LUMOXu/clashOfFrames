import { unwrap } from "./client";

export interface SubmissionDeck {
  id: number;
  folderName?: string;
  name?: string;
  curator?: string;
  description?: string;
  reviewStatus?: string;
  backUrl?: string;
  cardCount?: number;
  pmvCount?: number;
  visibleInGame?: boolean;
  pmvs?: SubmissionPmv[];
  cards?: SubmissionCard[];
}

export interface SubmissionPmv {
  id?: number;
  deckId?: number;
  pmvId?: number;
  name?: string;
  author?: string;
  description?: string;
  link?: string;
  reviewStatus?: string;
}

export interface SubmissionCard {
  id?: number;
  deckId?: number;
  pmvId?: number;
  shot?: string;
  imageUrl?: string;
  reviewStatus?: string;
}

export interface EditableDeckOption {
  id: number;
  name?: string;
  curator?: string;
  reviewStatus?: string;
  owned?: boolean;
}

export async function fetchEditableDecks(): Promise<{ decks: EditableDeckOption[] }> {
  return unwrap<{ decks: EditableDeckOption[] }>({ method: "GET", url: "/submissions/decks/editable" });
}

export async function fetchMySubmissions(): Promise<{ decks: SubmissionDeck[] }> {
  return unwrap<{ decks: SubmissionDeck[] }>({ method: "GET", url: "/submissions/mine" });
}

export async function createSubmissionDeck(body: {
  name: string;
  folderSlug?: string;
  curator?: string;
  description?: string;
  version?: string;
  link?: string;
}): Promise<{ deck: SubmissionDeck }> {
  return unwrap<{ deck: SubmissionDeck }>({ method: "POST", url: "/submissions/decks", data: body });
}

export async function uploadSubmissionBack(
  deckId: number,
  file: Blob,
): Promise<{ deck: SubmissionDeck }> {
  const form = new FormData();
  form.append("file", file, "back.jpg");
  return unwrap<{ deck: SubmissionDeck }>({
    method: "POST",
    url: `/submissions/decks/${deckId}/back`,
    data: form,
  });
}

export async function addSubmissionPmv(
  deckId: number,
  body: { pmvId: number; name: string; author?: string; description?: string; link?: string },
): Promise<{ pmv: SubmissionPmv }> {
  return unwrap<{ pmv: SubmissionPmv }>({
    method: "POST",
    url: `/submissions/decks/${deckId}/pmvs`,
    data: body,
  });
}

export async function addSubmissionCard(
  deckId: number,
  pmvId: number,
  shot: string,
  file: Blob,
): Promise<{ card: SubmissionCard }> {
  const form = new FormData();
  form.append("pmvId", String(pmvId));
  form.append("shot", shot);
  form.append("file", file, `${shot}.jpg`);
  return unwrap<{ card: SubmissionCard }>({
    method: "POST",
    url: `/submissions/decks/${deckId}/cards`,
    data: form,
  });
}
