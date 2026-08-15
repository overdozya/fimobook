import { requestJson } from '@/services/api';

import type { FilterOption, PlayerDetail, PlayerFilterMetadata, PlayerSearchFilters, PlayerSearchPage } from './types';

interface SearchPlayersRequest {
  filters?: PlayerSearchFilters;
  name: string;
  page: number;
  size?: number;
  signal?: AbortSignal;
}

export function searchPlayers({ filters, name, page, size = 20, signal }: SearchPlayersRequest) {
  const query = new URLSearchParams({
    name,
    page: String(page),
    size: String(size),
    sort: filters?.sort ?? 'ovrDesc',
    tradeable: 'true',
  });
  if (filters?.position) query.set('position', filters.position);
  if (filters?.classId) query.set('classId', filters.classId);
  if (filters?.leagueId) query.set('leagueId', filters.leagueId);
  if (filters?.nationId) query.set('nationId', filters.nationId);
  if (filters?.minOvr) query.set('minOvr', filters.minOvr);
  if (filters?.maxOvr) query.set('maxOvr', filters.maxOvr);
  if (filters?.teamId) query.set('teamId', filters.teamId);
  if (filters?.traitId) query.set('traitId', filters.traitId);
  if (filters?.playStyleId) query.set('playStyleId', filters.playStyleId);
  if (filters?.priceLevel) query.set('priceLevel', filters.priceLevel);
  if (filters?.minPrice) query.set('minPrice', filters.minPrice);
  if (filters?.maxPrice) query.set('maxPrice', filters.maxPrice);
  return requestJson<PlayerSearchPage>(`/api/players/search?${query.toString()}`, signal);
}

export function getPlayerDetail(cid: number, signal?: AbortSignal) {
  return requestJson<PlayerDetail>(`/api/players/${cid}`, signal);
}

export function getPlayerFilterMetadata(signal?: AbortSignal) {
  return requestJson<PlayerFilterMetadata>('/api/player-metadata', signal);
}

export function getTeamOptions(leagueId: string | undefined, name: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ limit: '100', name });
  if (leagueId) query.set('leagueId', leagueId);
  return requestJson<FilterOption[]>(`/api/player-metadata/teams?${query.toString()}`, signal);
}
