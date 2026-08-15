export interface AuthUser {
  userId: number;
  email: string;
  displayName: string;
}

export interface AuthResponse extends AuthUser {
  refreshToken: string;
  token: string;
}

export interface Credentials {
  email: string;
  password: string;
  displayName?: string;
}
