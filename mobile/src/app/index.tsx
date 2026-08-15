import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Keyboard,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { PlayerCard } from '@/components/player-card';
import { colors, radii, spacing } from '@/constants/theme';
import { getPlayerFilterMetadata, getTeamOptions, searchPlayers } from '@/features/players/api';
import { PlayerFilterModal } from '@/features/players/player-filter-modal';
import type { FilterOption, PlayerFilterMetadata, PlayerSearchFilters, PlayerSearchPage, PlayerSummary } from '@/features/players/types';

const PAGE_SIZE = 20;

function mergeByCid(current: PlayerSummary[], incoming: PlayerSummary[]) {
  const players = new Map(current.map((player) => [player.cid, player]));
  incoming.forEach((player) => players.set(player.cid, player));
  return [...players.values()];
}

export default function PlayerSearchScreen() {
  const router = useRouter();
  const [input, setInput] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [filters, setFilters] = useState<PlayerSearchFilters>({ sort: 'ovrDesc' });
  const [submittedFilters, setSubmittedFilters] = useState<PlayerSearchFilters>({ sort: 'ovrDesc' });
  const [filterMetadata, setFilterMetadata] = useState<PlayerFilterMetadata | null>(null);
  const [filterVisible, setFilterVisible] = useState(false);
  const [teamOptions, setTeamOptions] = useState<FilterOption[]>([]);
  const [players, setPlayers] = useState<PlayerSummary[]>([]);
  const [pageInfo, setPageInfo] = useState<Pick<
    PlayerSearchPage,
    'page' | 'totalElements' | 'totalPages'
  > | null>(null);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const activeRequest = useRef<AbortController | null>(null);
  const loadingMore = useRef(false);

  const loadFirstPage = useCallback(async (query: string, nextFilters: PlayerSearchFilters) => {
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    setIsInitialLoading(true);
    setError(null);

    try {
      const result = await searchPlayers({
        name: query,
        filters: nextFilters,
        page: 0,
        size: PAGE_SIZE,
        signal: controller.signal,
      });
      if (activeRequest.current !== controller) return;
      setPlayers(result.players);
      setPageInfo(result);
    } catch (requestError) {
      if (requestError instanceof Error && requestError.name === 'AbortError') return;
      if (activeRequest.current !== controller) return;
      setPlayers([]);
      setPageInfo(null);
      setError(requestError instanceof Error ? requestError.message : '선수 검색에 실패했습니다.');
    } finally {
      if (activeRequest.current === controller) {
        activeRequest.current = null;
        setIsInitialLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const initialFilters: PlayerSearchFilters = { sort: 'ovrDesc' };
    const initialRequest = setTimeout(() => void loadFirstPage('', initialFilters), 0);
    return () => {
      clearTimeout(initialRequest);
      activeRequest.current?.abort();
    };
  }, [loadFirstPage]);

  useEffect(() => {
    const controller = new AbortController();
    getPlayerFilterMetadata(controller.signal).then(setFilterMetadata).catch(() => undefined);
    return () => controller.abort();
  }, []);

  const submitSearch = useCallback(() => {
    const query = input.trim();
    Keyboard.dismiss();
    setSubmittedQuery(query);
    setSubmittedFilters(filters);
    void loadFirstPage(query, filters);
  }, [filters, input, loadFirstPage]);

  const loadNextPage = useCallback(async () => {
    if (
      isInitialLoading ||
      loadingMore.current ||
      !pageInfo ||
      pageInfo.page + 1 >= pageInfo.totalPages
    ) {
      return;
    }

    loadingMore.current = true;
    setIsLoadingMore(true);
    setError(null);
    const controller = new AbortController();
    activeRequest.current = controller;
    try {
      const result = await searchPlayers({
        name: submittedQuery,
        filters: submittedFilters,
        page: pageInfo.page + 1,
        size: PAGE_SIZE,
        signal: controller.signal,
      });
      if (activeRequest.current !== controller) return;
      setPlayers((current) => mergeByCid(current, result.players));
      setPageInfo(result);
    } catch (requestError) {
      if (requestError instanceof Error && requestError.name === 'AbortError') return;
      if (activeRequest.current !== controller) return;
      setError(requestError instanceof Error ? requestError.message : '다음 선수를 불러오지 못했습니다.');
    } finally {
      if (activeRequest.current === controller) activeRequest.current = null;
      loadingMore.current = false;
      setIsLoadingMore(false);
    }
  }, [isInitialLoading, pageInfo, submittedFilters, submittedQuery]);

  const clearSearch = useCallback(() => {
    setInput('');
    setSubmittedQuery('');
    void loadFirstPage('', submittedFilters);
  }, [loadFirstPage, submittedFilters]);

  const activeFilterCount = Object.entries(filters).filter(([key, value]) => key !== 'sort' && Boolean(value)).length + (filters.sort === 'ovrDesc' ? 0 : 1);

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <View style={styles.header}>
        <View>
          <Text style={styles.eyebrow}>FC MOBILE DATABASE</Text>
          <Text style={styles.title}>피모북</Text>
        </View>
        <View style={styles.headerActions}>
          <Pressable onPress={() => router.push('/squad')} style={styles.headerButton}><Text style={styles.headerButtonText}>스쿼드</Text></Pressable>
          <Pressable onPress={() => router.push('/auth')} style={styles.headerButton}><Text style={styles.headerButtonText}>계정</Text></Pressable>
        </View>
      </View>

      <View style={styles.searchBar}>
        <TextInput
          accessibilityLabel="선수 이름 검색"
          autoCapitalize="none"
          autoCorrect={false}
          enterKeyHint="search"
          onChangeText={setInput}
          onSubmitEditing={submitSearch}
          placeholder="선수 이름을 입력하세요"
          placeholderTextColor={colors.textMuted}
          returnKeyType="search"
          style={styles.input}
          value={input}
        />
        {input.length > 0 && (
          <Pressable accessibilityRole="button" onPress={clearSearch} style={styles.clearButton}>
            <Text style={styles.clearButtonText}>×</Text>
          </Pressable>
        )}
        <Pressable
          accessibilityRole="button"
          onPress={submitSearch}
          style={({ pressed }) => [styles.searchButton, pressed && styles.buttonPressed]}>
          <Text style={styles.searchButtonText}>검색</Text>
        </Pressable>
      </View>

      <View style={styles.filterBar}>
        <Pressable onPress={() => setFilterVisible(true)} style={styles.filterButton}>
          <Text style={styles.filterButtonText}>필터{activeFilterCount > 0 ? ` ${activeFilterCount}` : ''}</Text>
        </Pressable>
      </View>

      <View style={styles.resultHeader}>
        <Text style={styles.resultTitle}>{submittedQuery ? `‘${submittedQuery}’ 검색 결과` : '전체 선수'}</Text>
        <Text style={styles.resultCount}>
          {pageInfo ? `${pageInfo.totalElements.toLocaleString('ko-KR')}장` : ''}
        </Text>
      </View>

      {isInitialLoading ? (
        <View style={styles.centerState}>
          <ActivityIndicator color={colors.accent} size="large" />
          <Text style={styles.stateText}>선수 카드를 불러오는 중</Text>
        </View>
      ) : (
        <FlatList
          contentContainerStyle={players.length === 0 ? styles.emptyList : styles.list}
          data={players}
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          keyExtractor={(player) => String(player.cid)}
          ListEmptyComponent={
            <View style={styles.centerState}>
              <Text style={styles.emptyTitle}>{error ? '서버에 연결할 수 없습니다' : '검색 결과가 없습니다'}</Text>
              <Text style={styles.stateText}>{error ?? '다른 선수 이름으로 검색해보세요.'}</Text>
              <Pressable onPress={submitSearch} style={styles.retryButton}>
                <Text style={styles.retryText}>다시 시도</Text>
              </Pressable>
            </View>
          }
          ListFooterComponent={
            players.length > 0 ? (
              <View style={styles.footer}>
                {isLoadingMore ? (
                  <ActivityIndicator color={colors.accent} />
                ) : pageInfo && pageInfo.page + 1 >= pageInfo.totalPages ? (
                  <Text style={styles.footerText}>모든 카드를 불러왔습니다</Text>
                ) : error ? (
                  <Pressable onPress={loadNextPage} style={styles.retryButton}>
                    <Text style={styles.retryText}>다음 페이지 다시 불러오기</Text>
                  </Pressable>
                ) : null}
              </View>
            ) : null
          }
          onEndReached={loadNextPage}
          onEndReachedThreshold={0.45}
          renderItem={({ item }) => <PlayerCard player={item} />}
          showsVerticalScrollIndicator={false}
        />
      )}
      {filterVisible && <PlayerFilterModal
        filters={filters}
        metadata={filterMetadata}
        onApply={(nextFilters) => {
          setFilters(nextFilters);
          setSubmittedFilters(nextFilters);
          setFilterVisible(false);
          void loadFirstPage(submittedQuery, nextFilters);
        }}
        onClose={() => setFilterVisible(false)}
        onTeamSearch={(name, leagueId) => {
          void getTeamOptions(leagueId, name).then(setTeamOptions).catch(() => setTeamOptions([]));
        }}
        teamOptions={teamOptions}
        visible
      />}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  header: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: spacing.lg, paddingBottom: spacing.md, paddingTop: spacing.sm },
  headerActions: { flexDirection: 'row', gap: spacing.sm },
  headerButton: { borderColor: colors.border, borderRadius: radii.pill, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 8 },
  headerButtonText: { color: colors.textSecondary, fontSize: 11, fontWeight: '800' },
  eyebrow: { color: colors.accent, fontSize: 10, fontWeight: '800', letterSpacing: 1.6 },
  title: { color: colors.text, fontSize: 32, fontWeight: '900', letterSpacing: -1.5 },
  searchBar: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderRadius: radii.md,
    borderWidth: 1,
    flexDirection: 'row',
    marginHorizontal: spacing.lg,
    minHeight: 54,
    overflow: 'hidden',
  },
  input: { color: colors.text, flex: 1, fontSize: 16, paddingHorizontal: spacing.md, paddingVertical: 12 },
  clearButton: { alignItems: 'center', height: 44, justifyContent: 'center', width: 38 },
  clearButtonText: { color: colors.textSecondary, fontSize: 26, fontWeight: '300' },
  searchButton: {
    alignItems: 'center',
    alignSelf: 'stretch',
    backgroundColor: colors.accent,
    justifyContent: 'center',
    paddingHorizontal: spacing.lg,
  },
  searchButtonText: { color: '#06130f', fontSize: 15, fontWeight: '900' },
  buttonPressed: { opacity: 0.72 },
  filterBar: { alignItems: 'flex-end', paddingHorizontal: spacing.lg, paddingTop: spacing.sm },
  filterButton: { backgroundColor: colors.surfaceRaised, borderColor: colors.border, borderRadius: radii.pill, borderWidth: 1, paddingHorizontal: 16, paddingVertical: 9 },
  filterButtonText: { color: colors.accent, fontSize: 12, fontWeight: '900' },
  resultHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.sm,
    paddingTop: spacing.lg,
  },
  resultTitle: { color: colors.text, fontSize: 16, fontWeight: '800' },
  resultCount: { color: colors.textSecondary, fontSize: 13, fontWeight: '600' },
  list: { gap: spacing.sm, paddingHorizontal: spacing.lg, paddingBottom: 48 },
  emptyList: { flexGrow: 1, paddingHorizontal: spacing.lg },
  centerState: { alignItems: 'center', flex: 1, gap: spacing.sm, justifyContent: 'center', padding: spacing.xl },
  emptyTitle: { color: colors.text, fontSize: 18, fontWeight: '800', textAlign: 'center' },
  stateText: { color: colors.textSecondary, fontSize: 14, lineHeight: 21, textAlign: 'center' },
  footer: { alignItems: 'center', minHeight: 76, justifyContent: 'center' },
  footerText: { color: colors.textMuted, fontSize: 12 },
  retryButton: { borderColor: colors.accent, borderRadius: radii.pill, borderWidth: 1, marginTop: spacing.sm, paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  retryText: { color: colors.accent, fontSize: 13, fontWeight: '800' },
});
