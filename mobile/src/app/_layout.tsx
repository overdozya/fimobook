import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

import { colors } from '@/constants/theme';
import { AuthProvider } from '@/features/auth/auth-context';
import { SquadProvider } from '@/features/squad/squad-context';
import { resolveApiResourceUrl } from '@/services/api';

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts({
    'FCOAllSans-Bold': { uri: resolveApiResourceUrl('/api/assets/fco.vod.nexoncdn.co.kr/fonts/FCOAllSans-Bold.woff2')! },
    'FCOAllSans-Regular': { uri: resolveApiResourceUrl('/api/assets/fco.vod.nexoncdn.co.kr/fonts/FCOAllSans-Regular.woff2')! },
  });

  if (!fontsLoaded && !fontError) return null;

  return (
    <AuthProvider>
      <SquadProvider>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          contentStyle: { backgroundColor: colors.background },
          headerBackButtonDisplayMode: 'minimal',
          headerShadowVisible: false,
          headerStyle: { backgroundColor: colors.surface },
          headerTintColor: colors.text,
          headerTitleStyle: { fontFamily: fontsLoaded ? 'FCOAllSans-Bold' : undefined },
        }}>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="auth" options={{ title: '계정' }} />
        <Stack.Screen name="squad" options={{ title: '내 스쿼드' }} />
        <Stack.Screen name="player/[cid]" options={{ title: '선수 상세' }} />
      </Stack>
      </SquadProvider>
    </AuthProvider>
  );
}
