export interface VoteOption {
  id: string;
  label: string;
  thumbnailUrl?: string | null;
  description?: string | null;
}

export interface VoteOptionsResponse {
  multi: boolean;
  maxChoicePerVote: number;
  totalVotes: number;
  options: VoteOption[];
}

export interface VoteMeResponse {
  hasVoted: boolean;
  optionIds?: string[];
}

export interface VoteSubmitResponse {
  message: string;
  optionIds: string[];
}
