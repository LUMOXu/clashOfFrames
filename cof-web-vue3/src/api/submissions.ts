import { unwrap } from "./client";

export interface SubmissionDeck {
  id: number;
  name?: string;
  description?: string;
  reviewStatus?: string;
  pendingReviewStatus?: string;
  pendingName?: string;
  pendingDescription?: string;
  backUrl?: string;
  pendingBackUrl?: string;
  cardCount?: number;
  pmvCount?: number;
  enabled?: boolean;
  visibleInGame?: boolean;
  cards?: SubmissionCard[];
}

export interface SubmissionPmv {
  id: number;
  pmvId?: number;
  name?: string;
  author?: string;
  description?: string;
  link?: string;
  reviewStatus?: string;
  pendingReviewStatus?: string;
}

export interface SubmissionCard {
  id: number;
  deckId?: number;
  pmvId?: number;
  pmvName?: string;
  pmvAuthor?: string;
  name?: string;
  description?: string;
  imageUrl?: string;
  pendingImageUrl?: string;
  reviewStatus?: string;
  pendingReviewStatus?: string;
}

export async function fetchSubmissionPmvs(): Promise<{ pmvs: SubmissionPmv[] }> {
  return unwrap<{ pmvs: SubmissionPmv[] }>({ method: "GET", url: "/submissions/pmvs" });
}

export async function fetchMySubmissions(): Promise<{ decks: SubmissionDeck[] }> {
  return unwrap<{ decks: SubmissionDeck[] }>({ method: "GET", url: "/submissions/mine" });
}

export async function createSubmissionDeck(body: {
  name: string;
  description?: string;
}): Promise<{ deck: SubmissionDeck }> {
  return unwrap<{ deck: SubmissionDeck }>({ method: "POST", url: "/submissions/decks", data: body });
}

export async function createSubmissionPmv(body: {
  name: string;
  author?: string;
  description?: string;
  link?: string;
}): Promise<{ pmv: SubmissionPmv }> {
  return unwrap<{ pmv: SubmissionPmv }>({ method: "POST", url: "/submissions/pmvs", data: body });
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

export async function addSubmissionCard(
  deckId: number,
  pmvId: number,
  file: Blob,
  options?: { name?: string; description?: string },
): Promise<{ card: SubmissionCard }> {
  const form = new FormData();
  form.append("pmvId", String(pmvId));
  form.append("file", file, "card.jpg");
  if (options?.name) form.append("name", options.name);
  if (options?.description) form.append("description", options.description);
  return unwrap<{ card: SubmissionCard }>({
    method: "POST",
    url: `/submissions/decks/${deckId}/cards`,
    data: form,
  });
}

export async function deleteSubmissionDeck(deckId: number): Promise<void> {
  await unwrap({ method: "DELETE", url: `/submissions/decks/${deckId}` });
}

export async function deleteSubmissionPmv(pmvId: number): Promise<void> {
  await unwrap({ method: "DELETE", url: `/submissions/pmvs/${pmvId}` });
}

export async function deleteSubmissionCard(cardId: number): Promise<void> {
  await unwrap({ method: "DELETE", url: `/submissions/cards/${cardId}` });
}
