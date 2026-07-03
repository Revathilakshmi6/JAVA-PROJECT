import api from './client';

export const getDuplicates = (params: Record<string, string | number>) =>
  api.get('/duplicates', { params });

export const getDuplicateById = (id: string) => api.get(`/duplicates/${id}`);

export const resolveDuplicate = (id: string, resolution: 'APPROVED' | 'REJECTED', note?: string) =>
  api.post(`/duplicates/${id}/resolve`, { resolution, note });

export const getDuplicateReport = (params: Record<string, string>) =>
  api.get('/reports/duplicates', { params });

export const exportDuplicateReport = (params: Record<string, string>) =>
  api.get('/reports/duplicates/export', { params, responseType: 'blob' });
