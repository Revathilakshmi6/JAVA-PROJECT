import api from './client';

export const getWindowSeconds = () => api.get('/config/window');
export const updateWindowSeconds = (windowSeconds: number) => api.put('/config/window', { windowSeconds });

export const getIdentityFields = () => api.get('/config/identity-fields');
export const updateIdentityFields = (identityFields: string[]) => api.put('/config/identity-fields', { identityFields });

export const getAmountTolerance = () => api.get('/config/amount-tolerance');
export const updateAmountTolerance = (amountTolerance: number) => api.put('/config/amount-tolerance', { amountTolerance });
