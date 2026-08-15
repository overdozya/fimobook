export interface Review {
  authorName: string;
  cid: number;
  content: string;
  createdAt: string;
  dislikes: number;
  id: number;
  likes: number;
  rating: number;
  updatedAt: string;
  userId: number;
}

export interface ReviewInput {
  content: string;
  rating: number;
}
