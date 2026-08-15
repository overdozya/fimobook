import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import type { PlayerDetail } from '@/features/players/types';

import { snapshotPlayer, type SquadEntry, type SquadSlotId } from './types';

const STORAGE_KEY = 'fimobook.squad.default.v1';

interface SquadMutationResult {
  message?: string;
  ok: boolean;
}

interface SquadContextValue {
  addPlayer(slotId: SquadSlotId, player: PlayerDetail): SquadMutationResult;
  clear(): void;
  entries: SquadEntry[];
  hydrated: boolean;
  removePlayer(slotId: SquadSlotId): void;
  replace(entries: SquadEntry[]): SquadMutationResult;
}

const SquadContext = createContext<SquadContextValue | null>(null);

function validateEntries(entries: SquadEntry[]): SquadMutationResult {
  if (entries.length > 11) return { message: '스쿼드는 최대 11명입니다.', ok: false };
  if (new Set(entries.map((entry) => entry.slotId)).size !== entries.length) return { message: '같은 슬롯이 중복되었습니다.', ok: false };
  if (new Set(entries.map((entry) => entry.player.pid)).size !== entries.length) return { message: '같은 실제 선수는 한 명만 등록할 수 있습니다.', ok: false };
  return { ok: true };
}

export function SquadProvider({ children }: PropsWithChildren) {
  const [entries, setEntries] = useState<SquadEntry[]>([]);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then((stored) => {
        if (!stored) return;
        const parsed = JSON.parse(stored) as SquadEntry[];
        if (validateEntries(parsed).ok) setEntries(parsed);
      })
      .catch(() => undefined)
      .finally(() => setHydrated(true));
  }, []);

  useEffect(() => {
    if (hydrated) void AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  }, [entries, hydrated]);

  const addPlayer = useCallback((slotId: SquadSlotId, player: PlayerDetail) => {
    const duplicate = entries.find((entry) => entry.player.pid === player.pid && entry.slotId !== slotId);
    if (duplicate) return { message: `${player.playerKor}의 다른 카드가 이미 등록되어 있습니다.`, ok: false };
    setEntries((current) => [...current.filter((entry) => entry.slotId !== slotId), { player: snapshotPlayer(player), slotId }]);
    return { ok: true };
  }, [entries]);

  const removePlayer = useCallback((slotId: SquadSlotId) => {
    setEntries((current) => current.filter((entry) => entry.slotId !== slotId));
  }, []);

  const clear = useCallback(() => setEntries([]), []);
  const replace = useCallback((nextEntries: SquadEntry[]) => {
    const result = validateEntries(nextEntries);
    if (result.ok) setEntries(nextEntries);
    return result;
  }, []);

  const value = useMemo(() => ({ addPlayer, clear, entries, hydrated, removePlayer, replace }), [addPlayer, clear, entries, hydrated, removePlayer, replace]);
  return <SquadContext.Provider value={value}>{children}</SquadContext.Provider>;
}

export function useSquad() {
  const context = useContext(SquadContext);
  if (!context) throw new Error('useSquad must be used inside SquadProvider');
  return context;
}
