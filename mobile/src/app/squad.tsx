import { Stack, useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PlayerCardVisual } from '@/components/player-card-visual';
import { colors, radii, spacing } from '@/constants/theme';
import { useAuth } from '@/features/auth/auth-context';
import { formatPrice } from '@/features/players/format';
import { loadServerSquad, saveServerSquad } from '@/features/squad/api';
import { useSquad } from '@/features/squad/squad-context';
import { SQUAD_SLOTS } from '@/features/squad/types';

export default function SquadScreen() {
  const router = useRouter();
  const { token, user } = useAuth();
  const { clear, entries, hydrated, removePlayer, replace } = useSquad();
  const [message, setMessage] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);
  const totalPrice = useMemo(() => entries.reduce((sum, entry) => sum + entry.player.n8Price0, 0), [entries]);

  const sync = async (direction: 'load' | 'save') => {
    if (!token) {
      router.push('/auth');
      return;
    }
    setSyncing(true);
    setMessage(null);
    try {
      if (direction === 'load') {
        const result = replace(await loadServerSquad(token));
        setMessage(result.ok ? '서버 스쿼드를 이 기기에 불러왔습니다.' : result.message ?? null);
      } else {
        replace(await saveServerSquad(entries, token));
        setMessage('현재 스쿼드를 서버에 저장했습니다.');
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '스쿼드 동기화에 실패했습니다.');
    } finally {
      setSyncing(false);
    }
  };

  if (!hydrated) return <View style={styles.loading}><ActivityIndicator color={colors.accent} /></View>;

  return (
    <SafeAreaView edges={['bottom']} style={styles.screen}>
      <Stack.Screen options={{ title: '내 스쿼드' }} />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.summary}>
          <View><Text style={styles.summaryLabel}>팀 OVR</Text><Text style={styles.ovr}>0</Text></View>
          <View><Text style={styles.summaryLabel}>선수</Text><Text style={styles.summaryValue}>{entries.length}/11</Text></View>
          <View><Text style={styles.summaryLabel}>총 가치</Text><Text style={styles.summaryValue}>{formatPrice(totalPrice)}</Text></View>
        </View>
        <View style={styles.actions}>
          <Pressable disabled={syncing} onPress={() => void sync('save')} style={styles.actionButton}><Text style={styles.actionText}>{user ? '서버 저장' : '로그인 후 서버 저장'}</Text></Pressable>
          {user && <Pressable disabled={syncing} onPress={() => void sync('load')} style={styles.actionButton}><Text style={styles.actionText}>서버에서 불러오기</Text></Pressable>}
          <Pressable onPress={clear} style={[styles.actionButton, styles.dangerButton]}><Text style={styles.dangerText}>전체 비우기</Text></Pressable>
        </View>
        {message && <Text style={styles.message}>{message}</Text>}
        <View style={styles.slots}>
          {SQUAD_SLOTS.map((slot) => {
            const entry = entries.find((item) => item.slotId === slot.id);
            return (
              <View key={slot.id} style={styles.slot}>
                <View style={styles.positionBadge}><Text style={styles.positionText}>{slot.label}</Text></View>
                {entry ? (
                  <>
                    <PlayerCardVisual player={entry.player} size={88} />
                    <Pressable onPress={() => router.push({ pathname: '/player/[cid]', params: { cid: String(entry.player.cid) } })} style={styles.playerInfo}>
                      <Text numberOfLines={1} style={styles.playerName}>{entry.player.playerKor}</Text>
                      <Text style={styles.playerMeta}>OVR {entry.player.ovr} · {entry.player.position}</Text>
                      <Text style={styles.playerPrice}>{formatPrice(entry.player.n8Price0)}</Text>
                    </Pressable>
                    <Pressable onPress={() => removePlayer(slot.id)} style={styles.removeButton}><Text style={styles.removeText}>삭제</Text></Pressable>
                  </>
                ) : <Text style={styles.empty}>선수 상세에서 이 슬롯에 등록하세요.</Text>}
              </View>
            );
          })}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: colors.background, flex: 1 },
  loading: { alignItems: 'center', backgroundColor: colors.background, flex: 1, justifyContent: 'center' },
  content: { gap: spacing.md, padding: spacing.md, paddingBottom: 64 },
  summary: { alignItems: 'center', backgroundColor: colors.surface, borderColor: colors.border, borderRadius: radii.md, borderWidth: 1, flexDirection: 'row', justifyContent: 'space-around', padding: spacing.lg },
  summaryLabel: { color: colors.textMuted, fontSize: 11, textAlign: 'center' },
  ovr: { color: colors.accent, fontSize: 34, fontWeight: '900', textAlign: 'center' },
  summaryValue: { color: colors.text, fontSize: 15, fontWeight: '900', marginTop: 5, textAlign: 'center' },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  actionButton: { borderColor: colors.accent, borderRadius: radii.pill, borderWidth: 1, paddingHorizontal: 13, paddingVertical: 9 },
  actionText: { color: colors.accent, fontSize: 11, fontWeight: '800' },
  dangerButton: { borderColor: colors.danger },
  dangerText: { color: colors.danger, fontSize: 11, fontWeight: '800' },
  message: { color: colors.textSecondary, fontSize: 12 },
  slots: { gap: spacing.sm },
  slot: { alignItems: 'center', backgroundColor: colors.surface, borderColor: colors.border, borderRadius: radii.md, borderWidth: 1, flexDirection: 'row', minHeight: 96, overflow: 'hidden', paddingRight: spacing.md },
  positionBadge: { alignItems: 'center', alignSelf: 'stretch', backgroundColor: colors.surfaceRaised, justifyContent: 'center', width: 54 },
  positionText: { color: colors.accent, fontSize: 11, fontWeight: '900' },
  playerInfo: { flex: 1, paddingHorizontal: spacing.sm },
  playerName: { color: colors.text, fontSize: 15, fontWeight: '900' },
  playerMeta: { color: colors.textSecondary, fontSize: 11, marginTop: 3 },
  playerPrice: { color: colors.warning, fontSize: 11, fontWeight: '800', marginTop: 5 },
  removeButton: { padding: spacing.sm },
  removeText: { color: colors.danger, fontSize: 11, fontWeight: '800' },
  empty: { color: colors.textMuted, flex: 1, fontSize: 12, paddingLeft: spacing.md },
});
