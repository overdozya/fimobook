import type { CardVisualTheme, PlayerAssets, PlayerDetail } from '@/features/players/types';

export const SQUAD_SLOTS = [
  { id: 'gk', label: 'GK' },
  { id: 'lb', label: 'LB' },
  { id: 'cb1', label: 'CB 1' },
  { id: 'cb2', label: 'CB 2' },
  { id: 'rb', label: 'RB' },
  { id: 'cm1', label: 'CM 1' },
  { id: 'cdm', label: 'CDM' },
  { id: 'cm2', label: 'CM 2' },
  { id: 'lw', label: 'LW' },
  { id: 'st', label: 'ST' },
  { id: 'rw', label: 'RW' },
] as const;

export type SquadSlotId = (typeof SQUAD_SLOTS)[number]['id'];

export interface SquadPlayerSnapshot {
  assets?: PlayerAssets;
  bimage?: string | null;
  cardTheme?: CardVisualTheme;
  cid: number;
  n8Price0: number;
  ovr: number;
  pid: number;
  pimage?: string | null;
  playerKor: string;
  position: string;
}

export interface SquadEntry {
  player: SquadPlayerSnapshot;
  slotId: SquadSlotId;
}

export function snapshotPlayer(player: PlayerDetail): SquadPlayerSnapshot {
  const rawPrice = player.n8Price0;
  return {
    assets: player.assets,
    bimage: player.bimage,
    cardTheme: player.cardTheme,
    cid: player.cid,
    n8Price0: typeof rawPrice === 'number' ? rawPrice : Number(rawPrice) || 0,
    ovr: player.ovr,
    pid: player.pid,
    pimage: player.pimage,
    playerKor: player.playerKor,
    position: player.position,
  };
}
