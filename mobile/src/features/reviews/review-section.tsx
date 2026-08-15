import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { colors, radii, spacing } from '@/constants/theme';
import { useAuth } from '@/features/auth/auth-context';

import { createReview, deleteReview, getReviews, reactToReview, updateReview } from './api';
import type { Review } from './types';

export function ReviewSection({ cid }: { cid: number }) {
  const router = useRouter();
  const { token, user } = useAuth();
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const ownReview = useMemo(() => reviews.find((review) => review.userId === user?.userId), [reviews, user]);

  useEffect(() => {
    const controller = new AbortController();
    getReviews(cid, controller.signal)
      .then(setReviews)
      .catch((requestError) => {
        if (requestError instanceof Error && requestError.name === 'AbortError') return;
        setError(requestError instanceof Error ? requestError.message : '평가를 불러오지 못했습니다.');
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [cid]);

  const react = async (review: Review, reaction: 'like' | 'dislike') => {
    if (!token) {
      router.push('/auth');
      return;
    }
    try {
      const updated = await reactToReview(review.id, reaction, token);
      setReviews((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '반응을 저장하지 못했습니다.');
    }
  };

  return (
    <View style={styles.section}>
      <Text style={styles.title}>선수 카드 평가</Text>
      {user ? (
        <ReviewEditor
          key={ownReview?.id ?? `new-${user.userId}`}
          cid={cid}
          onError={setError}
          onRemove={(id) => setReviews((current) => current.filter((review) => review.id !== id))}
          onSave={(saved) => setReviews((current) => [saved, ...current.filter((review) => review.id !== saved.id)])}
          review={ownReview}
          token={token!}
        />
      ) : (
        <Pressable onPress={() => router.push('/auth')} style={styles.loginButton}><Text style={styles.loginText}>로그인하고 평가 남기기</Text></Pressable>
      )}
      {error && <Text style={styles.error}>{error}</Text>}
      {loading ? <ActivityIndicator color={colors.accent} /> : reviews.length === 0 ? (
        <Text style={styles.empty}>아직 등록된 평가가 없습니다.</Text>
      ) : reviews.map((review) => (
        <View key={review.id} style={styles.review}>
          <View style={styles.reviewHeader}>
            <Text style={styles.author}>{review.authorName}</Text>
            <Text style={styles.reviewRating}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</Text>
          </View>
          <Text style={styles.content}>{review.content}</Text>
          <View style={styles.reactions}>
            <Pressable onPress={() => void react(review, 'like')}><Text style={styles.reaction}>도움됨 {review.likes}</Text></Pressable>
            <Pressable onPress={() => void react(review, 'dislike')}><Text style={styles.reaction}>아쉬움 {review.dislikes}</Text></Pressable>
          </View>
        </View>
      ))}
    </View>
  );
}

function ReviewEditor({ cid, onError, onRemove, onSave, review, token }: {
  cid: number;
  onError(message: string | null): void;
  onRemove(id: number): void;
  onSave(review: Review): void;
  review?: Review;
  token: string;
}) {
  const [rating, setRating] = useState(review?.rating ?? 5);
  const [content, setContent] = useState(review?.content ?? '');
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    setSubmitting(true);
    onError(null);
    try {
      const saved = review
        ? await updateReview(review.id, { content: content.trim(), rating }, token)
        : await createReview(cid, { content: content.trim(), rating }, token);
      onSave(saved);
    } catch (requestError) {
      onError(requestError instanceof Error ? requestError.message : '평가를 저장하지 못했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const remove = async () => {
    if (!review) return;
    setSubmitting(true);
    try {
      await deleteReview(review.id, token);
      onRemove(review.id);
    } catch (requestError) {
      onError(requestError instanceof Error ? requestError.message : '평가를 삭제하지 못했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <View style={styles.form}>
      <View style={styles.ratingRow}>
        {[1, 2, 3, 4, 5].map((value) => (
          <Pressable key={value} onPress={() => setRating(value)}>
            <Text style={[styles.star, value <= rating && styles.activeStar]}>★</Text>
          </Pressable>
        ))}
      </View>
      <TextInput maxLength={100} multiline onChangeText={setContent} placeholder="이 카드에 대한 평가를 100자 이내로 남겨주세요." placeholderTextColor={colors.textMuted} style={styles.input} value={content} />
      <View style={styles.formActions}>
        {review && <Pressable disabled={submitting} onPress={() => void remove()} style={styles.deleteButton}><Text style={styles.deleteText}>삭제</Text></Pressable>}
        <Pressable disabled={submitting || !content.trim()} onPress={() => void submit()} style={styles.submitButton}>
          {submitting ? <ActivityIndicator color="#06130f" size="small" /> : <Text style={styles.submitText}>{review ? '수정' : '등록'}</Text>}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { backgroundColor: colors.surface, borderColor: colors.border, borderRadius: radii.md, borderWidth: 1, gap: spacing.md, padding: spacing.md },
  title: { color: colors.text, fontSize: 18, fontWeight: '900' },
  form: { gap: spacing.sm },
  ratingRow: { flexDirection: 'row', gap: 5 },
  star: { color: colors.textMuted, fontSize: 24 },
  activeStar: { color: colors.warning },
  input: { backgroundColor: colors.surfaceRaised, borderColor: colors.border, borderRadius: radii.sm, borderWidth: 1, color: colors.text, minHeight: 84, padding: spacing.md, textAlignVertical: 'top' },
  formActions: { flexDirection: 'row', gap: spacing.sm, justifyContent: 'flex-end' },
  submitButton: { alignItems: 'center', backgroundColor: colors.accent, borderRadius: radii.sm, justifyContent: 'center', minHeight: 42, minWidth: 86, paddingHorizontal: spacing.md },
  submitText: { color: '#06130f', fontWeight: '900' },
  deleteButton: { alignItems: 'center', borderColor: colors.danger, borderRadius: radii.sm, borderWidth: 1, justifyContent: 'center', minHeight: 42, paddingHorizontal: spacing.md },
  deleteText: { color: colors.danger, fontWeight: '800' },
  loginButton: { alignItems: 'center', borderColor: colors.accent, borderRadius: radii.sm, borderWidth: 1, justifyContent: 'center', minHeight: 46 },
  loginText: { color: colors.accent, fontWeight: '800' },
  error: { color: colors.danger, fontSize: 12 },
  empty: { color: colors.textMuted, fontSize: 13 },
  review: { borderTopColor: colors.border, borderTopWidth: 1, gap: spacing.sm, paddingTop: spacing.md },
  reviewHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  author: { color: colors.text, fontSize: 13, fontWeight: '900' },
  reviewRating: { color: colors.warning, fontSize: 12 },
  content: { color: colors.textSecondary, fontSize: 13, lineHeight: 20 },
  reactions: { flexDirection: 'row', gap: spacing.md },
  reaction: { color: colors.textMuted, fontSize: 11, fontWeight: '700' },
});
