export interface PlayerClass {
  id: string;
  name: string;
  imageUrl: string | null;
}

export interface PlayerSummary {
  cid: number;
  pid: number;
  classes: PlayerClass[];
  playerKor: string;
  playerEng: string | null;
  ovr: number;
  position: string;
  team: string | null;
  league: string | null;
  nation: string | null;
  pimage: string | null;
  bimage: string | null;
  assets: PlayerAssets;
  cardTheme: CardVisualTheme;
  tradeable: boolean;
  n8Price0: number;
  priceLevel: number;
  price: number;
}

export interface PlayerSearchPage {
  players: PlayerSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FilterOption {
  id: string;
  imageUrl: string | null;
  name: string;
}

export interface PlayerFilterMetadata {
  classes: FilterOption[];
  nations: FilterOption[];
  leagues: FilterOption[];
  traits: FilterOption[];
  playStyles: FilterOption[];
}

export interface PlayerSearchFilters {
  classId?: string;
  leagueId?: string;
  maxOvr?: string;
  maxPrice?: string;
  minOvr?: string;
  minPrice?: string;
  nationId?: string;
  playStyleId?: string;
  position?: string;
  priceLevel?: string;
  sort: 'ovrDesc' | 'ovrAsc' | 'priceDesc' | 'priceAsc' | 'nameAsc';
  teamId?: string;
  traitId?: string;
}

export interface PlayerTrait {
  id: string | number;
  name: string;
  iconUrl?: string;
}

export interface PlayerPlayStyle {
  id: string;
  name: string;
  iconUrl?: string;
}

export interface PlayerAssets {
  flag?: string;
  team?: string;
  league?: string;
}

export interface CardVisualTheme {
  ovr: string;
  position: string;
  name: string;
}

export interface PlayerDetail {
  [key: string]: unknown;
  cid: number;
  pid: number;
  classes: PlayerClass[];
  playerKor: string;
  playerEng?: string;
  ovr: number;
  position: string;
  potentialPosition?: string;
  team?: string;
  league?: string;
  nation?: string;
  pimage?: string;
  bimage?: string;
  height?: number;
  weight?: number;
  mainFoot?: number;
  WFA?: number;
  skillMovesLevel?: number;
  skillMovesName?: string;
  noTrade?: number;
  Trait?: PlayerTrait[];
  playStyles?: PlayerPlayStyle[];
  staticPlayStyles?: string[];
  assets?: PlayerAssets;
  cardTheme?: CardVisualTheme;
}

export type PlayerStatKey =
  | 'ACC'
  | 'SPD'
  | 'FIN'
  | 'SHO'
  | 'LSA'
  | 'VOL'
  | 'SPA'
  | 'LPA'
  | 'VIS'
  | 'CRO'
  | 'DRI'
  | 'BAC'
  | 'AGI'
  | 'REA'
  | 'BAL'
  | 'MRK'
  | 'STT'
  | 'SLT'
  | 'AWR'
  | 'HEA'
  | 'STR'
  | 'AGG'
  | 'JMP'
  | 'STA';
