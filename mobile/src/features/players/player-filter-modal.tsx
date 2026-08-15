import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, radii, spacing } from '@/constants/theme';

import type { FilterOption, PlayerFilterMetadata, PlayerSearchFilters } from './types';

const POSITIONS = ['GK', 'LB', 'CB', 'RB', 'LWB', 'CDM', 'RWB', 'LM', 'CM', 'RM', 'CAM', 'LW', 'ST', 'RW'];
const SORTS: { id: PlayerSearchFilters['sort']; name: string }[] = [
  { id: 'ovrDesc', name: 'OVR 높은순' },
  { id: 'ovrAsc', name: 'OVR 낮은순' },
  { id: 'priceDesc', name: '가격 높은순' },
  { id: 'priceAsc', name: '가격 낮은순' },
  { id: 'nameAsc', name: '이름순' },
];

interface Props {
  filters: PlayerSearchFilters;
  metadata: PlayerFilterMetadata | null;
  onApply(filters: PlayerSearchFilters): void;
  onClose(): void;
  onTeamSearch(name: string, leagueId?: string): void;
  teamOptions: FilterOption[];
  visible: boolean;
}

function OptionRow({ label, options, selected, onSelect }: {
  label: string;
  onSelect(id: string | undefined): void;
  options: { id: string; name: string }[];
  selected?: string;
}) {
  return (
    <View style={styles.group}>
      <Text style={styles.label}>{label}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false}>
        <View style={styles.optionRow}>
          <Pressable onPress={() => onSelect(undefined)} style={[styles.chip, !selected && styles.selectedChip]}>
            <Text style={[styles.chipText, !selected && styles.selectedChipText]}>전체</Text>
          </Pressable>
          {options.map((option) => (
            <Pressable key={option.id} onPress={() => onSelect(option.id)} style={[styles.chip, selected === option.id && styles.selectedChip]}>
              <Text style={[styles.chipText, selected === option.id && styles.selectedChipText]}>{option.name}</Text>
            </Pressable>
          ))}
        </View>
      </ScrollView>
    </View>
  );
}

function compactOptions(options: FilterOption[] | undefined) {
  return options ?? [];
}

export function PlayerFilterModal({ filters, metadata, onApply, onClose, onTeamSearch, teamOptions, visible }: Props) {
  const [draft, setDraft] = useState(filters);
  const [teamQuery, setTeamQuery] = useState('');

  const set = <K extends keyof PlayerSearchFilters>(key: K, value: PlayerSearchFilters[K]) => {
    setDraft((current) => ({ ...current, [key]: value }));
  };

  return (
    <Modal animationType="slide" onRequestClose={onClose} presentationStyle="pageSheet" visible={visible}>
      <SafeAreaView style={styles.screen}>
        <View style={styles.header}>
          <Pressable onPress={onClose}><Text style={styles.headerAction}>닫기</Text></Pressable>
          <Text style={styles.title}>선수 필터</Text>
          <Pressable onPress={() => setDraft({ sort: 'ovrDesc' })}><Text style={styles.headerAction}>초기화</Text></Pressable>
        </View>
        <ScrollView contentContainerStyle={styles.content}>
          <OptionRow label="정렬" onSelect={(id) => set('sort', (id ?? 'ovrDesc') as PlayerSearchFilters['sort'])} options={SORTS} selected={draft.sort} />
          <OptionRow label="포지션" onSelect={(id) => set('position', id)} options={POSITIONS.map((id) => ({ id, name: id }))} selected={draft.position} />
          <OptionRow label="클래스" onSelect={(id) => set('classId', id)} options={compactOptions(metadata?.classes)} selected={draft.classId} />
          <OptionRow label="리그" onSelect={(id) => set('leagueId', id)} options={compactOptions(metadata?.leagues)} selected={draft.leagueId} />
          <OptionRow label="국가" onSelect={(id) => set('nationId', id)} options={compactOptions(metadata?.nations)} selected={draft.nationId} />
          <View style={styles.group}>
            <Text style={styles.label}>팀</Text>
            <View style={styles.rangeRow}>
              <TextInput onChangeText={setTeamQuery} onSubmitEditing={() => onTeamSearch(teamQuery.trim(), draft.leagueId)} placeholder="팀 이름 검색" placeholderTextColor={colors.textMuted} style={styles.rangeInput} value={teamQuery} />
              <Pressable onPress={() => onTeamSearch(teamQuery.trim(), draft.leagueId)} style={styles.smallButton}><Text style={styles.smallButtonText}>찾기</Text></Pressable>
            </View>
            {teamOptions.length > 0 && <OptionRow label="검색된 팀" onSelect={(id) => set('teamId', id)} options={teamOptions} selected={draft.teamId} />}
          </View>
          <OptionRow label="특성" onSelect={(id) => set('traitId', id)} options={compactOptions(metadata?.traits)} selected={draft.traitId} />
          <OptionRow label="플레이스타일" onSelect={(id) => set('playStyleId', id)} options={compactOptions(metadata?.playStyles)} selected={draft.playStyleId} />
          <View style={styles.group}>
            <Text style={styles.label}>OVR 범위</Text>
            <View style={styles.rangeRow}>
              <TextInput keyboardType="number-pad" onChangeText={(value) => set('minOvr', value.replace(/\D/g, ''))} placeholder="최소" placeholderTextColor={colors.textMuted} style={styles.rangeInput} value={draft.minOvr ?? ''} />
              <Text style={styles.rangeDash}>~</Text>
              <TextInput keyboardType="number-pad" onChangeText={(value) => set('maxOvr', value.replace(/\D/g, ''))} placeholder="최대" placeholderTextColor={colors.textMuted} style={styles.rangeInput} value={draft.maxOvr ?? ''} />
            </View>
          </View>
          <OptionRow label="가격 강화 단계" onSelect={(id) => set('priceLevel', id)} options={Array.from({ length: 16 }, (_, level) => ({ id: String(level), name: `+${level}` }))} selected={draft.priceLevel} />
          <View style={styles.group}>
            <Text style={styles.label}>가격 범위 (MP)</Text>
            <View style={styles.rangeRow}>
              <TextInput keyboardType="number-pad" onChangeText={(value) => set('minPrice', value.replace(/\D/g, ''))} placeholder="최소 가격" placeholderTextColor={colors.textMuted} style={styles.rangeInput} value={draft.minPrice ?? ''} />
              <Text style={styles.rangeDash}>~</Text>
              <TextInput keyboardType="number-pad" onChangeText={(value) => set('maxPrice', value.replace(/\D/g, ''))} placeholder="최대 가격" placeholderTextColor={colors.textMuted} style={styles.rangeInput} value={draft.maxPrice ?? ''} />
            </View>
          </View>
        </ScrollView>
        <View style={styles.footer}>
          <Pressable onPress={() => onApply(draft)} style={styles.applyButton}>
            <Text style={styles.applyText}>필터 적용</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: colors.background, flex: 1 },
  header: { alignItems: 'center', borderBottomColor: colors.border, borderBottomWidth: 1, flexDirection: 'row', justifyContent: 'space-between', padding: spacing.md },
  title: { color: colors.text, fontSize: 18, fontWeight: '900' },
  headerAction: { color: colors.accent, fontSize: 14, fontWeight: '800', minWidth: 48 },
  content: { gap: spacing.lg, padding: spacing.lg },
  group: { gap: spacing.sm },
  label: { color: colors.text, fontSize: 15, fontWeight: '900' },
  optionRow: { flexDirection: 'row', gap: spacing.sm, paddingRight: spacing.lg },
  chip: { backgroundColor: colors.surfaceRaised, borderColor: colors.border, borderRadius: radii.pill, borderWidth: 1, paddingHorizontal: 14, paddingVertical: 9 },
  selectedChip: { backgroundColor: colors.accent, borderColor: colors.accent },
  chipText: { color: colors.textSecondary, fontSize: 12, fontWeight: '700' },
  selectedChipText: { color: '#06130f', fontWeight: '900' },
  rangeRow: { alignItems: 'center', flexDirection: 'row', gap: spacing.sm },
  rangeInput: { backgroundColor: colors.surfaceRaised, borderColor: colors.border, borderRadius: radii.sm, borderWidth: 1, color: colors.text, flex: 1, minHeight: 48, paddingHorizontal: spacing.md },
  rangeDash: { color: colors.textMuted },
  smallButton: { alignItems: 'center', backgroundColor: colors.surfaceRaised, borderColor: colors.accent, borderRadius: radii.sm, borderWidth: 1, justifyContent: 'center', minHeight: 48, paddingHorizontal: spacing.md },
  smallButtonText: { color: colors.accent, fontSize: 12, fontWeight: '900' },
  footer: { borderTopColor: colors.border, borderTopWidth: 1, padding: spacing.md },
  applyButton: { alignItems: 'center', backgroundColor: colors.accent, borderRadius: radii.sm, justifyContent: 'center', minHeight: 52 },
  applyText: { color: '#06130f', fontSize: 15, fontWeight: '900' },
});
