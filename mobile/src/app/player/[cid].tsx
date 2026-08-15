import { Image } from 'expo-image';
import { Stack, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, radii, spacing } from '@/constants/theme';
import { PlayerCardVisual } from '@/components/player-card-visual';
import { resolveApiResourceUrl } from '@/services/api';
import { getPlayerDetail } from '@/features/players/api';
import { formatPrice, getPrice, getStat, STAT_GROUPS } from '@/features/players/format';
import type { PlayerDetail } from '@/features/players/types';
import { AddToSquad } from '@/features/squad/add-to-squad';
import { ReviewSection } from '@/features/reviews/review-section';

function InfoItem({ label, value }: { label: string; value: string | number | undefined }) {
  if (value === undefined || value === '') return null;
  return (
    <View style={styles.infoItem}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

export default function PlayerDetailScreen() {
  const params = useLocalSearchParams<{ cid: string }>();
  const cid = Number(params.cid);
  const [player, setPlayer] = useState<PlayerDetail | null>(null);
  const [requestError, setRequestError] = useState<{ cid: number; message: string } | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const invalidCid = !Number.isSafeInteger(cid) || cid <= 0;
  const visiblePlayer = player?.cid === cid ? player : null;
  const error = invalidCid
    ? '올바르지 않은 카드 ID입니다.'
    : requestError?.cid === cid
      ? requestError.message
      : null;

  useEffect(() => {
    if (invalidCid) return;

    const controller = new AbortController();
    getPlayerDetail(cid, controller.signal)
      .then(setPlayer)
      .catch((requestError: unknown) => {
        if (requestError instanceof Error && requestError.name === 'AbortError') return;
        setRequestError({
          cid,
          message: requestError instanceof Error ? requestError.message : '선수 상세를 불러오지 못했습니다.',
        });
      });
    return () => controller.abort();
  }, [cid, invalidCid, retryCount]);

  const visibleStatGroups = useMemo(
    () =>
      visiblePlayer
        ? STAT_GROUPS.map((group) => ({
            ...group,
            stats: group.stats
              .map((stat) => ({ ...stat, value: getStat(visiblePlayer, stat.key) }))
              .filter((stat) => stat.value !== null),
          })).filter((group) => group.stats.length > 0)
        : [],
    [visiblePlayer],
  );

  if (!visiblePlayer) {
    return (
      <SafeAreaView edges={['bottom']} style={styles.stateScreen}>
        {error ? (
          <>
            <Text style={styles.errorTitle}>상세 정보를 열 수 없습니다</Text>
            <Text style={styles.errorText}>{error}</Text>
            <Pressable
              onPress={() => {
                setRequestError(null);
                setRetryCount((value) => value + 1);
              }}
              style={styles.retryButton}>
              <Text style={styles.retryText}>다시 시도</Text>
            </Pressable>
          </>
        ) : (
          <>
            <ActivityIndicator color={colors.accent} size="large" />
            <Text style={styles.errorText}>선수 상세를 불러오는 중</Text>
          </>
        )}
      </SafeAreaView>
    );
  }

  const playerDetail = visiblePlayer;
  const traits = Array.isArray(playerDetail.Trait) ? playerDetail.Trait : [];
  const playStyles = Array.isArray(playerDetail.playStyles) ? playerDetail.playStyles : [];

  return (
    <>
      <Stack.Screen options={{ title: playerDetail.playerKor }} />
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}>
        <View style={styles.hero}>
          <View style={styles.cardVisual}>
            <PlayerCardVisual player={playerDetail} size={220} />
          </View>

          <View style={styles.heroInfo}>
            <View style={styles.ovrRow}>
              <Text style={styles.ovr}>{playerDetail.ovr}</Text>
              <Text style={styles.position}>{playerDetail.position}</Text>
            </View>
            <Text style={styles.name}>{playerDetail.playerKor}</Text>
            {playerDetail.playerEng && <Text style={styles.englishName}>{playerDetail.playerEng}</Text>}
            <Text style={styles.meta}>
              {[playerDetail.team, playerDetail.league, playerDetail.nation].filter(Boolean).join(' · ')}
            </Text>
            <Text style={[styles.trade, playerDetail.noTrade !== 0 && styles.untradeable]}>
              {playerDetail.noTrade === 0 ? '거래 가능' : '거래 불가'}
            </Text>
            <View style={styles.squadAction}><AddToSquad player={playerDetail} /></View>
          </View>
        </View>

        <View style={styles.classRow}>
          {playerDetail.classes.map((playerClass) => (
            <View key={playerClass.id} style={styles.classChip}>
              {playerClass.imageUrl && (
                <Image
                  cachePolicy="memory-disk"
                  contentFit="contain"
                  source={{ uri: resolveApiResourceUrl(playerClass.imageUrl) }}
                  style={styles.classImage}
                />
              )}
              <Text style={styles.classText}>{playerClass.name}</Text>
            </View>
          ))}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>기본 정보</Text>
          <View style={styles.infoGrid}>
            <InfoItem label="주 포지션" value={playerDetail.position} />
            <InfoItem label="부 포지션" value={playerDetail.potentialPosition} />
            <InfoItem label="키" value={playerDetail.height ? `${playerDetail.height} cm` : undefined} />
            <InfoItem label="몸무게" value={playerDetail.weight ? `${playerDetail.weight} kg` : undefined} />
            <InfoItem label="주발" value={playerDetail.mainFoot === 2 ? '왼발' : playerDetail.mainFoot === 1 ? '오른발' : undefined} />
            <InfoItem label="약발" value={playerDetail.WFA} />
            <InfoItem label="개인기" value={playerDetail.skillMovesLevel ? `${playerDetail.skillMovesLevel}성` : undefined} />
            <InfoItem label="고유 개인기" value={playerDetail.skillMovesName} />
          </View>
          <View style={styles.assetRow}>
            {Object.entries(playerDetail.assets ?? {}).map(([key, uri]) =>
              uri ? (
                <View key={key} style={styles.assetBox}>
                  <Image cachePolicy="memory-disk" contentFit="contain" source={{ uri: resolveApiResourceUrl(uri) }} style={styles.assetImage} />
                </View>
              ) : null,
            )}
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>강화 단계별 가격</Text>
          <Text style={styles.sectionDescription}>현재 DB에 저장된 0진부터 15진 가격입니다.</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <View style={styles.priceRow}>
              {Array.from({ length: 16 }, (_, level) => (
                <View key={level} style={styles.priceCard}>
                  <Text style={styles.priceLevel}>+{level}</Text>
                  <Text style={styles.priceValue}>{formatPrice(getPrice(playerDetail, level))}</Text>
                </View>
              ))}
            </View>
          </ScrollView>
        </View>

        {traits.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>특성</Text>
            <View style={styles.chipRow}>
              {traits.map((trait) => (
                <View key={String(trait.id)} style={styles.traitChip}>
                  {trait.iconUrl && (
                    <Image
                      cachePolicy="memory-disk"
                      contentFit="contain"
                      source={{ uri: resolveApiResourceUrl(trait.iconUrl) }}
                      style={styles.traitIcon}
                    />
                  )}
                  <Text style={styles.traitText}>{trait.name}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        {playStyles.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>플레이스타일</Text>
            <View style={styles.chipRow}>
              {playStyles.map((playStyle) => (
                <View key={playStyle.id} style={styles.traitChip}>
                  {playStyle.iconUrl && (
                    <Image
                      cachePolicy="memory-disk"
                      contentFit="contain"
                      source={{ uri: resolveApiResourceUrl(playStyle.iconUrl) }}
                      style={styles.traitIcon}
                    />
                  )}
                  <Text style={styles.traitText}>{playStyle.name}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>세부 능력치</Text>
          <View style={styles.statsGrid}>
            {visibleStatGroups.map((group) => (
              <View key={group.title} style={styles.statGroup}>
                <Text style={styles.statGroupTitle}>{group.title}</Text>
                {group.stats.map((stat) => (
                  <View key={stat.key} style={styles.statRow}>
                    <Text style={styles.statLabel}>{stat.label}</Text>
                    <Text style={styles.statValue}>{stat.value}</Text>
                  </View>
                ))}
              </View>
            ))}
          </View>
        </View>
        <ReviewSection cid={playerDetail.cid} />
      </ScrollView>
    </>
  );
}

const styles = StyleSheet.create({
  stateScreen: { alignItems: 'center', backgroundColor: colors.background, flex: 1, gap: spacing.md, justifyContent: 'center', padding: spacing.xl },
  errorTitle: { color: colors.text, fontSize: 20, fontWeight: '900', textAlign: 'center' },
  errorText: { color: colors.textSecondary, lineHeight: 21, textAlign: 'center' },
  retryButton: { borderColor: colors.accent, borderRadius: radii.pill, borderWidth: 1, paddingHorizontal: spacing.lg, paddingVertical: spacing.sm },
  retryText: { color: colors.accent, fontWeight: '800' },
  scrollContent: { backgroundColor: colors.background, gap: spacing.md, padding: spacing.md, paddingBottom: 64 },
  hero: { alignItems: 'center', backgroundColor: colors.surface, borderColor: colors.border, borderRadius: radii.lg, borderWidth: 1, overflow: 'hidden', paddingTop: spacing.sm },
  cardVisual: { alignItems: 'center', height: 220, justifyContent: 'center', width: 220 },
  heroInfo: { alignItems: 'center', padding: spacing.md, width: '100%' },
  ovrRow: { alignItems: 'baseline', flexDirection: 'row', gap: spacing.sm },
  ovr: { color: colors.accent, fontSize: 44, fontWeight: '900', letterSpacing: -2 },
  position: { color: colors.text, fontSize: 15, fontWeight: '900' },
  name: { color: colors.text, fontSize: 23, fontWeight: '900', letterSpacing: -0.8, marginTop: spacing.xs },
  englishName: { color: colors.textSecondary, fontSize: 12, marginTop: 3 },
  meta: { color: colors.textSecondary, fontSize: 12, lineHeight: 18, marginTop: spacing.md },
  trade: { color: colors.accent, fontSize: 11, fontWeight: '800', marginTop: spacing.sm },
  untradeable: { color: colors.textMuted },
  squadAction: { marginTop: spacing.md, width: '100%' },
  classRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  classChip: { alignItems: 'center', backgroundColor: colors.surfaceRaised, borderRadius: radii.pill, flexDirection: 'row', gap: spacing.sm, paddingHorizontal: 12, paddingVertical: 7 },
  classImage: { height: 20, width: 20 },
  classText: { color: colors.textSecondary, fontSize: 11, fontWeight: '700' },
  section: { backgroundColor: colors.surface, borderColor: colors.border, borderRadius: radii.md, borderWidth: 1, gap: spacing.md, padding: spacing.md },
  sectionTitle: { color: colors.text, fontSize: 18, fontWeight: '900' },
  sectionDescription: { color: colors.textMuted, fontSize: 12, marginTop: -8 },
  infoGrid: { flexDirection: 'row', flexWrap: 'wrap' },
  infoItem: { borderBottomColor: colors.border, borderBottomWidth: StyleSheet.hairlineWidth, gap: 4, paddingVertical: 10, width: '50%' },
  infoLabel: { color: colors.textMuted, fontSize: 11 },
  infoValue: { color: colors.text, fontSize: 14, fontWeight: '700' },
  assetRow: { flexDirection: 'row', gap: spacing.sm },
  assetBox: { alignItems: 'center', backgroundColor: colors.surfaceRaised, borderRadius: radii.sm, height: 48, justifyContent: 'center', width: 48 },
  assetImage: { height: 34, width: 34 },
  priceRow: { flexDirection: 'row', gap: spacing.sm, paddingBottom: spacing.xs },
  priceCard: { backgroundColor: colors.surfaceRaised, borderRadius: radii.sm, gap: 6, minWidth: 122, padding: 12 },
  priceLevel: { color: colors.accent, fontSize: 13, fontWeight: '900' },
  priceValue: { color: colors.warning, fontSize: 11, fontWeight: '700' },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  traitChip: { alignItems: 'center', backgroundColor: colors.surfaceRaised, borderRadius: radii.pill, flexDirection: 'row', gap: 7, paddingHorizontal: 12, paddingVertical: 8 },
  traitIcon: { height: 24, width: 24 },
  traitText: { color: colors.textSecondary, fontSize: 12, fontWeight: '700' },
  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  statGroup: { backgroundColor: colors.surfaceRaised, borderRadius: radii.sm, minWidth: '47%', padding: 12, flexGrow: 1 },
  statGroupTitle: { color: colors.accentBlue, fontSize: 14, fontWeight: '900', marginBottom: spacing.sm },
  statRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 3 },
  statLabel: { color: colors.textSecondary, fontSize: 12 },
  statValue: { color: colors.text, fontSize: 12, fontWeight: '900' },
});
