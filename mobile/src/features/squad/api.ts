import { requestJson } from '@/services/api';

import type { SquadEntry, SquadPlayerSnapshot, SquadSlotId } from './types';

interface ServerSquadPlayer {
  assets?: SquadPlayerSnapshot['assets'];
  bimage?: string;
  cardTheme?: SquadPlayerSnapshot['cardTheme'];
  cid: number;
  n8Price0: number;
  ovr: number;
  pid: number;
  pimage?: string;
  playerKor: string;
  position: string;
  slotId: SquadSlotId;
}

function toEntry(player: ServerSquadPlayer): SquadEntry {
  const snapshot: SquadPlayerSnapshot = {
    cid: player.cid,
    assets: player.assets,
    bimage: player.bimage,
    cardTheme: player.cardTheme,
    n8Price0: player.n8Price0,
    ovr: player.ovr,
    pid: player.pid,
    pimage: player.pimage,
    playerKor: player.playerKor,
    position: player.position,
  };
  return { player: snapshot, slotId: player.slotId };
}

export async function loadServerSquad(token: string) {
  const players = await requestJson<ServerSquadPlayer[]>('/api/squads/me', { token });
  return players.map(toEntry);
}

export async function saveServerSquad(entries: SquadEntry[], token: string) {
  const players = await requestJson<ServerSquadPlayer[]>('/api/squads/me', {
    body: entries.map((entry) => ({ cid: entry.player.cid, slotId: entry.slotId })),
    method: 'PUT',
    token,
  });
  return players.map(toEntry);
}
