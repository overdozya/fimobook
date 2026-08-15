import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, radii, spacing } from '@/constants/theme';
import { useAuth } from '@/features/auth/auth-context';

export default function AuthScreen() {
  const router = useRouter();
  const { login, logout, register, user } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      if (mode === 'register') await register({ displayName, email, password });
      else await login({ email, password });
      router.back();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '인증에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  if (user) {
    return (
      <SafeAreaView edges={['bottom']} style={styles.screen}>
        <Stack.Screen options={{ title: '내 계정' }} />
        <View style={styles.card}>
          <Text style={styles.title}>{user.displayName}</Text>
          <Text style={styles.description}>{user.email}</Text>
          <Pressable onPress={() => void logout()} style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>로그아웃</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView edges={['bottom']} style={styles.screen}>
      <Stack.Screen options={{ title: mode === 'login' ? '로그인' : '회원가입' }} />
      <View style={styles.card}>
        <Text style={styles.title}>{mode === 'login' ? '피모북 로그인' : '피모북 회원가입'}</Text>
        <Text style={styles.description}>검색과 로컬 스쿼드는 로그인 없이 사용할 수 있습니다.</Text>
        {mode === 'register' && (
          <TextInput
            autoCapitalize="none"
            onChangeText={setDisplayName}
            placeholder="닉네임 (2~50자)"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
            value={displayName}
          />
        )}
        <TextInput
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          onChangeText={setEmail}
          placeholder="이메일"
          placeholderTextColor={colors.textMuted}
          style={styles.input}
          value={email}
        />
        <TextInput
          autoCapitalize="none"
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          onChangeText={setPassword}
          onSubmitEditing={() => void submit()}
          placeholder="비밀번호 (8자 이상)"
          placeholderTextColor={colors.textMuted}
          secureTextEntry
          style={styles.input}
          value={password}
        />
        {error && <Text style={styles.error}>{error}</Text>}
        <Pressable disabled={submitting} onPress={() => void submit()} style={styles.primaryButton}>
          {submitting ? <ActivityIndicator color="#06130f" /> : <Text style={styles.primaryButtonText}>{mode === 'login' ? '로그인' : '가입하기'}</Text>}
        </Pressable>
        <Pressable onPress={() => setMode((current) => current === 'login' ? 'register' : 'login')}>
          <Text style={styles.switchText}>{mode === 'login' ? '처음이라면 회원가입' : '이미 계정이 있다면 로그인'}</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: colors.background, flex: 1, padding: spacing.lg },
  card: { backgroundColor: colors.surface, borderColor: colors.border, borderRadius: radii.lg, borderWidth: 1, gap: spacing.md, padding: spacing.lg },
  title: { color: colors.text, fontSize: 24, fontWeight: '900' },
  description: { color: colors.textSecondary, fontSize: 13, lineHeight: 20 },
  input: { backgroundColor: colors.surfaceRaised, borderColor: colors.border, borderRadius: radii.sm, borderWidth: 1, color: colors.text, fontSize: 15, minHeight: 52, paddingHorizontal: spacing.md },
  error: { color: colors.danger, fontSize: 13, lineHeight: 19 },
  primaryButton: { alignItems: 'center', backgroundColor: colors.accent, borderRadius: radii.sm, minHeight: 50, justifyContent: 'center' },
  primaryButtonText: { color: '#06130f', fontSize: 15, fontWeight: '900' },
  secondaryButton: { alignItems: 'center', borderColor: colors.danger, borderRadius: radii.sm, borderWidth: 1, minHeight: 48, justifyContent: 'center' },
  secondaryButtonText: { color: colors.danger, fontWeight: '800' },
  switchText: { color: colors.accent, fontSize: 13, fontWeight: '800', textAlign: 'center' },
});
