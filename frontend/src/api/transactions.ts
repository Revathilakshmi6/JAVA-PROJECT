import api from './client';

export interface TransactionRequest {
  payerId: string;
  payeeId: string;
  amount: number;
  currency: string;
  idempotencyRef?: string;
}

export const submitTransaction = (data: TransactionRequest, idempotencyKey?: string) =>
  api.post('/transactions', data, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    validateStatus: (s) => s < 500,
  });

export const getTransactions = (params: Record<string, string | number>) =>
  api.get('/transactions', { params });

export const getTransactionById = (id: string) => api.get(`/transactions/${id}`);

export const getWindowStatus = () => api.get('/transactions/window/status');
