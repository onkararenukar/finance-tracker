import axios from 'axios';
import { Transaction, BankStatement, Category, TransactionCategory, User } from '../types';

const API_BASE_URL = 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Auth API
export const authAPI = {
  login: (username: string, password: string) =>
    api.post('/auth/login', { username, password }),
  
  register: (username: string, email: string, password: string, fullName: string) =>
    api.post('/auth/register', { username, email, password, fullName }),
};

// Statements API
export const statementsAPI = {
  upload: (file: File, bankName: string) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('bankName', bankName);
    return api.post('/statements', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  
  getAll: () => api.get<BankStatement[]>('/statements'),
  
  getById: (id: number) => api.get<BankStatement>(`/statements/${id}`),
};

// Transactions API
export const transactionsAPI = {
  getAll: () => api.get<Transaction[]>('/transactions'),
  
  getByUser: (userId: number) => api.get<Transaction[]>(`/transactions/user/${userId}`),
  
  getByDateRange: (startDate: string, endDate: string) =>
    api.get<Transaction[]>(`/transactions?startDate=${startDate}&endDate=${endDate}`),
};

// Categories API
export const categoriesAPI = {
  getAll: () => api.get<Category[]>('/categories'),
  
  create: (name: string, description: string, color: string) =>
    api.post<Category>('/categories', { name, description, color }),
  
  update: (id: number, data: Partial<Category>) =>
    api.put<Category>(`/categories/${id}`, data),
  
  delete: (id: number) => api.delete(`/categories/${id}`),
};

// Transaction Categories API
export const transactionCategoriesAPI = {
  getPending: () => api.get<TransactionCategory[]>('/transaction-categories/pending'),
  
  approve: (transactionId: number, categoryId: number) =>
    api.put(`/transaction-categories/${transactionId}`, { categoryId, status: 'CATEGORIZED' }),
  
  reject: (transactionId: number, suggestedName: string) =>
    api.put(`/transaction-categories/${transactionId}`, { 
      categoryId: null, 
      status: 'CATEGORIZED',
      suggestedCategoryName: suggestedName 
    }),
};

// Admin API
export const adminAPI = {
  getAllUsers: () => api.get<User[]>('/admin/users'),
  
  getUserById: (id: number) => api.get<User>(`/admin/users/${id}`),
  
  createUser: (username: string, email: string, password: string, fullName: string) =>
    api.post('/admin/users', { username, email, password, fullName }),
  
  updateUser: (id: number, data: Partial<User>) =>
    api.put(`/admin/users/${id}`, data),
  
  deleteUser: (id: number) => api.delete(`/admin/users/${id}`),
  
  resetPassword: (id: number, password: string) =>
    api.put(`/admin/users/${id}/password`, { password }),
  
  lockUser: (id: number) => api.post(`/admin/users/${id}/lock`),
  
  unlockUser: (id: number) => api.post(`/admin/users/${id}/unlock`),
};

export default api;
