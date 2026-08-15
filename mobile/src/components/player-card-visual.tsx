import { Image } from 'expo-image';
import { StyleSheet, Text, View } from 'react-native';

import { resolveApiResourceUrl } from '@/services/api';
import type { CardVisualTheme, PlayerAssets } from '@/features/players/types';

interface VisualPlayer {
  playerKor: string;
  ovr: number;
  position: string;
  nation?: string | null;
  team?: string | null;
  league?: string | null;
  pimage?: string | null;
  bimage?: string | null;
  assets?: PlayerAssets;
  cardTheme?: CardVisualTheme;
}

interface PlayerCardVisualProps {
  player: VisualPlayer;
  size: number;
}

const fallbackTheme: CardVisualTheme = {
  name: '#FFFFFF',
  ovr: '#FFFFFF',
  position: '#FFFFFF',
};

export function PlayerCardVisual({ player, size }: PlayerCardVisualProps) {
  const theme = player.cardTheme ?? fallbackTheme;
  const iconSize = size / 12;

  return (
    <View style={{ height: size, position: 'relative', width: size }}>
      {player.bimage && (
        <Image
          cachePolicy="memory-disk"
          contentFit="contain"
          source={{ uri: resolveApiResourceUrl(player.bimage) }}
          style={StyleSheet.absoluteFill}
          transition={150}
        />
      )}
      {player.pimage && (
        <Image
          accessibilityLabel={`${player.playerKor} 선수 이미지`}
          cachePolicy="memory-disk"
          contentFit="contain"
          source={{ uri: resolveApiResourceUrl(player.pimage) }}
          style={{ height: size * 0.93, left: size * 0.035, position: 'absolute', top: size * 0.035, width: size * 0.93 }}
          transition={150}
        />
      )}

      <Text
        style={[styles.overlayText, styles.ovr, {
          color: theme.ovr,
          fontSize: size * 0.125,
          left: size * 0.22,
          top: size * 0.1,
        }]}>
        {player.ovr}
      </Text>
      <Text
        style={[styles.overlayText, styles.position, {
          color: theme.position,
          fontSize: size * 0.084,
          left: size * 0.23,
          top: size * 0.24,
        }]}>
        {player.position}
      </Text>
      <Text
        numberOfLines={1}
        style={[styles.overlayText, styles.name, {
          bottom: size * 0.255,
          color: theme.name,
          fontSize: size * 0.0875,
          left: size * 0.225,
          width: size * 0.55,
        }]}>
        {player.playerKor}
      </Text>

      <View style={[styles.icons, { bottom: size * 0.161, gap: size * 0.05 }]}>
        {player.assets?.flag && (
          <Image
            accessibilityLabel={player.nation ?? '국가'}
            cachePolicy="memory-disk"
            contentFit="contain"
            source={{ uri: resolveApiResourceUrl(player.assets.flag) }}
            style={{ height: iconSize, width: iconSize }}
          />
        )}
        {player.assets?.league && (
          <Image
            accessibilityLabel={player.league ?? '리그'}
            cachePolicy="memory-disk"
            contentFit="contain"
            source={{ uri: resolveApiResourceUrl(player.assets.league) }}
            style={{ height: iconSize, width: iconSize }}
          />
        )}
        {player.assets?.team && (
          <Image
            accessibilityLabel={player.team ?? '팀'}
            cachePolicy="memory-disk"
            contentFit="contain"
            source={{ uri: resolveApiResourceUrl(player.assets.team) }}
            style={{ height: iconSize, width: iconSize }}
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  icons: { alignItems: 'center', flexDirection: 'row', justifyContent: 'center', left: 0, position: 'absolute', right: 0 },
  name: { fontFamily: 'FCOAllSans-Regular', textAlign: 'center' },
  overlayText: { lineHeight: undefined, position: 'absolute' },
  ovr: { fontFamily: 'FCOAllSans-Bold' },
  position: { fontFamily: 'FCOAllSans-Regular' },
});
