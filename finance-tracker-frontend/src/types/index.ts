export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: 'USER' | 'ADMIN';
  isActive: boolean;
  isVerified: boolean;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface LoginCredentials {
  username: string;
  password: string;
}

export interface RegisterData {
  username: string;
  email: string;
  password: string;
  fullName: string;
}

export interface Transaction {
  id: number;
  statementId: number;
  userId: number;
  bankName: string;
  transactionDate: string;
  description: string;
  amount: number;
  direction: 'CREDIT' | 'DEBIT';
  status: string;
  createdAt: string;
}

export interface BankStatement {
  id: number;
  userId: number;
  bankName: string;
  originalFilename: string;
  fileType: string;
  status: string;
  createdAt: string;
}

export interface Category {
  id: number;
  name: string;
  description: string;
  color: string;
}

export interface TransactionCategory {
  transactionId: number;
  categoryId: number | null;
  status: string;
  suggestedCategoryName: string;
  oneLineDescription: string;
}
