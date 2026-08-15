import { requestJson } from '@/services/api';

import type { Review, ReviewInput } from './types';

export function getReviews(cid: number, signal?: AbortSignal) {
  return requestJson<Review[]>(`/api/players/${cid}/reviews`, signal);
}

export function createReview(cid: number, input: ReviewInput, token: string) {
  return requestJson<Review>(`/api/players/${cid}/reviews`, { body: input, method: 'POST', token });
}

export function updateReview(id: number, input: ReviewInput, token: string) {
  return requestJson<Review>(`/api/reviews/${id}`, { body: input, method: 'PUT', token });
}

export function deleteReview(id: number, token: string) {
  return requestJson<void>(`/api/reviews/${id}`, { method: 'DELETE', token });
}

export function reactToReview(id: number, reaction: 'like' | 'dislike', token: string) {
  return requestJson<Review>(`/api/reviews/${id}/${reaction}`, { method: 'POST', token });
}
