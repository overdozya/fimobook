import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, radii, spacing } from '@/constants/theme';
import type { PlayerDetail } from '@/features/players/types';

import { useSquad } from './squad-context';
import { SQUAD_SLOTS } from './types';

export function AddToSquad({ player }: { player: PlayerDetail }) {
  const { addPlayer, entries } = useSquad();
  const [visible, setVisible] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  return (
    <>
      <Pressable onPress={() => setVisible(true)} style={styles.addButton}>
        <Text style={styles.addButtonText}>스쿼드에 등록</Text>
      </Pressable>
      <Modal animationType="slide" onRequestClose={() => setVisible(false)} presentationStyle="pageSheet" visible={visible}>
        <SafeAreaView style={styles.modal}>
          <View style={styles.header}>
            <Text style={styles.title}>등록할 포지션</Text>
            <Pressable onPress={() => setVisible(false)}><Text style={styles.close}>닫기</Text></Pressable>
          </View>
          {message && <Text style={styles.message}>{message}</Text>}
          <ScrollView contentContainerStyle={styles.slotList}>
            {SQUAD_SLOTS.map((slot) => {
              const current = entries.find((entry) => entry.slotId === slot.id);
              return (
                <Pressable
                  key={slot.id}
                  onPress={() => {
                    const result = addPlayer(slot.id, player);
                    if (!result.ok) setMessage(result.message ?? '등록할 수 없습니다.');
                    else setVisible(false);
                  }}
                  style={styles.slot}>
                  <Text style={styles.slotName}>{slot.label}</Text>
                  <Text numberOfLines={1} style={styles.currentPlayer}>{current?.player.playerKor ?? '빈 슬롯'}</Text>
                </Pressable>
              );
            })}
          </ScrollView>
        </SafeAreaView>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  addButton: { alignItems: 'center', backgroundColor: colors.accent, borderRadius: radii.sm, justifyContent: 'center', minHeight: 50, width: '100%' },
  addButtonText: { color: '#06130f', fontSize: 15, fontWeight: '900' },
  modal: { backgroundColor: colors.background, flex: 1 },
  header: { alignItems: 'center', borderBottomColor: colors.border, borderBottomWidth: 1, flexDirection: 'row', justifyContent: 'space-between', padding: spacing.lg },
  title: { color: colors.text, fontSize: 19, fontWeight: '900' },
  close: { color: colors.accent, fontWeight: '800' },
  message: { color: colors.danger, paddingHorizontal: spacing.lg, paddingTop: spacing.md },
  slotList: { gap: spacing.sm, padding: spacing.lg },
  slot: { alignItems: 'center', backgroundColor: colors.surface, borderColor: colors.border, borderRadius: radii.sm, borderWidth: 1, flexDirection: 'row', justifyContent: 'space-between', minHeight: 56, paddingHorizontal: spacing.md },
  slotName: { color: colors.accent, fontWeight: '900' },
  currentPlayer: { color: colors.textSecondary, flex: 1, marginLeft: spacing.lg, textAlign: 'right' },
});
