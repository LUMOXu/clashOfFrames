export interface ComputerPlayer {
  id: string;
  name: string;
  description?: string;
  playDelayMeanSeconds?: number;
  playDelayStdSeconds?: number;
  reactionMeanSeconds?: number;
  reactionStdSeconds?: number;
  matchDetectionProbability?: number;
  falseRingProbability?: number;
}

export interface CardLibraryMeta {
  id: string;
  name: string;
  cardCount?: number;
  pmvCount?: number;
}
