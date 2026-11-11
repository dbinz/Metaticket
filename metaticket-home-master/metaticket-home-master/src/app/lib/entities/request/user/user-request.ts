export interface CreateUserRequest {
  phone: string;
  password: string;
  otp: string;
  email: string;
  name: string;
  avatar?: string;
}
