import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, radii, spacing } from '@/constants/theme';
import { PlayerCardVisual } from '@/components/player-card-visual';
import { formatPrice } from '@/features/players/format';
import type { PlayerSummary } from '@/features/players/types';

interface PlayerCardProps {
  player: PlayerSummary;
}

export function PlayerCard({ player }: PlayerCardProps) {
  const router = useRouter();
  const className = player.classes[0]?.name;

  return (
    <Pressable
      accessibilityHint="선수 카드 상세 화면을 엽니다"
      accessibilityLabel={`${player.playerKor}, OVR ${player.ovr}, ${player.position}`}
      accessibilityRole="button"
      onPress={() =>
        router.push({ pathname: '/player/[cid]', params: { cid: String(player.cid) } })
      }
      style={({ pressed }) => [styles.container, pressed && styles.pressed]}>
      <PlayerCardVisual player={player} size={148} />

      <View style={styles.content}>
        <View>
          <Text numberOfLines={1} style={styles.name}>
            {player.playerKor}
          </Text>
          <Text numberOfLines={1} style={styles.description}>
            {[className, player.team, player.nation].filter(Boolean).join(' · ')}
          </Text>
        </View>
        <View style={styles.bottomRow}>
          <Text style={[styles.trade, !player.tradeable && styles.untradeable]}>
            {player.tradeable ? '거래 가능' : '거래 불가'}
          </Text>
          <Text style={styles.price}>{formatPrice(player.n8Price0)}</Text>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: radii.md,
    borderWidth: 1,
    flexDirection: 'row',
    minHeight: 148,
    overflow: 'hidden',
  },
  pressed: { opacity: 0.72, transform: [{ scale: 0.995 }] },
  content: { flex: 1, justifyContent: 'space-between', padding: spacing.md },
  name: { color: colors.text, fontSize: 18, fontWeight: '900', letterSpacing: -0.4 },
  description: { color: colors.textSecondary, fontSize: 12, lineHeight: 18, marginTop: 4 },
  bottomRow: { alignItems: 'flex-end', gap: 4 },
  trade: { color: colors.accent, fontSize: 10, fontWeight: '800' },
  untradeable: { color: colors.textMuted },
  price: { color: colors.warning, fontSize: 13, fontWeight: '800' },
});
