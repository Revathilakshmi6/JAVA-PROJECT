import React, { useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import {
  submitTransaction,
  getTransactions,
  getWindowStatus,
} from './api/transactions';
import {
  getDuplicates,
  resolveDuplicate,
  getDuplicateReport,
  exportDuplicateReport,
} from './api/duplicates';
import {
  getWindowSeconds,
  updateWindowSeconds,
  getIdentityFields,
  updateIdentityFields,
  getAmountTolerance,
  updateAmountTolerance,
} from './api/config';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';

interface Transaction {
  id: string;
  payerId: string;
  payeeId: string;
  amount: number;
  currency: string;
  idempotencyRef: string;
  status: 'POSTED' | 'SUPPRESSED' | 'FLAGGED';
  createdAt: string;
}

interface DuplicateRecord {
  id: string;
  originalTransaction: Transaction;
  duplicateTransaction: Transaction;
  matchTier: 'EXACT' | 'PROBABLE';
  matchedFields: string[];
  timeDeltaMs: number;
  resolution: 'PENDING' | 'APPROVED' | 'REJECTED';
  resolvedAt?: string;
  resolvedBy?: string;
  note?: string;
  detectedAt: string;
}

export default function App() {
  // Connection State
  const [wsConnected, setWsConnected] = useState(false);

  // App Metrics
  const [metrics, setMetrics] = useState({
    totalInWindow: 0,
    suppressedCount: 0,
    flaggedCount: 0,
    windowSeconds: 60,
  });

  // Dynamic configuration state
  const [config, setConfig] = useState({
    windowSeconds: 60,
    identityFields: ['payerId', 'payeeId', 'amount', 'currency', 'idempotencyRef'],
    amountTolerance: 0.0,
  });

  // Data States
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [duplicates, setDuplicates] = useState<DuplicateRecord[]>([]);
  const [alertFeed, setAlertFeed] = useState<DuplicateRecord[]>([]);
  const [chartData, setChartData] = useState<any[]>([]);

  // Form State
  const [form, setForm] = useState({
    payerId: 'payer-100',
    payeeId: 'payee-200',
    amount: '150.00',
    currency: 'USD',
    idempotencyRef: '',
  });
  const [formLoading, setFormLoading] = useState(false);
  const [formResponse, setFormResponse] = useState<{
    success?: boolean;
    status?: number;
    message?: string;
    txnId?: string;
  } | null>(null);

  // Filter States
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [activeTab, setActiveTab] = useState<'duplicates' | 'transactions'>('duplicates');

  // WebSocket Subscription Ref
  const stompClientRef = useRef<Client | null>(null);

  // Load Initial Data
  const fetchData = async () => {
    try {
      // 1. Fetch transactions — backend returns Spring Page<T>: { content: [...], totalElements, ... }
      const txnsRes = await getTransactions({ size: 50 });
      setTransactions(txnsRes.data?.content || []);

      // 2. Fetch duplicates — same Page<T> wrapper
      const dupsRes = await getDuplicates({ size: 50 });
      const dupsContent: DuplicateRecord[] = dupsRes.data?.content || [];
      setDuplicates(dupsContent);

      // 3. Fetch window status
      const windowRes = await getWindowStatus();
      setMetrics((prev) => ({
        ...prev,
        totalInWindow: windowRes.data?.size || 0,
        windowSeconds: windowRes.data?.windowSeconds || 60,
      }));

      // 4. Fetch reports for chart
      try {
        const reportRes = await getDuplicateReport({ groupBy: 'hour' });
        const reportData = Array.isArray(reportRes.data) ? reportRes.data : [];
        const formattedChart = reportData
          .filter((item: any) => item.period !== 'TOTAL')
          .map((item: any) => ({
            time: new Date(item.period).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
            count: item.totalDuplicates,
          }));
        setChartData(formattedChart);
      } catch {
        setChartData([]);
      }

      // Recalculate quick aggregate metrics
      const suppressed = dupsContent.filter((r) => r.matchTier === 'EXACT').length;
      const flagged = dupsContent.filter((r) => r.resolution === 'PENDING').length;
      setMetrics((prev) => ({
        ...prev,
        suppressedCount: suppressed,
        flaggedCount: flagged,
      }));
    } catch (err) {
      console.error('Error fetching dashboard data:', err);
    }
  };


  const fetchConfig = async () => {
    try {
      const windowRes = await getWindowSeconds();
      const fieldsRes = await getIdentityFields();
      const toleranceRes = await getAmountTolerance();

      setConfig({
        windowSeconds: windowRes.data?.windowSeconds || 60,
        identityFields: fieldsRes.data?.identityFields || [],
        amountTolerance: Number(toleranceRes.data?.amountTolerance) || 0.0,
      });
    } catch (err) {
      console.error('Error fetching system config:', err);
    }
  };

  useEffect(() => {
    fetchData();
    fetchConfig();
    const interval = setInterval(fetchData, 10000); // refresh metadata every 10s
    return () => clearInterval(interval);
  }, []);

  // Connect WebSocket for live alerts
  useEffect(() => {
    const getStompUrl = () => {
      if (import.meta.env.VITE_WS_URL) {
        return import.meta.env.VITE_WS_URL;
      }
      const apiBase = import.meta.env.VITE_API_BASE_URL;
      if (apiBase && apiBase.startsWith('http')) {
        try {
          const url = new URL(apiBase);
          const protocol = url.protocol === 'https:' ? 'wss' : 'ws';
          return `${protocol}://${url.host}/ws/websocket`;
        } catch (e) {
          console.error('Failed to parse VITE_API_BASE_URL for WebSocket', e);
        }
      }
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
      return `${protocol}://${window.location.host}/ws/websocket`;
    };
    // Hardcoded fallback: always use Render backend for WebSocket if on Vercel
    const hardcodedStompUrl = 'wss://java-project-yidh.onrender.com/ws/websocket';

    const stompUrl = getStompUrl();
    const client = new Client({
      brokerURL: hardcodedStompUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setWsConnected(true);
        client.subscribe('/topic/duplicate-alerts', (message) => {
          const newAlert: DuplicateRecord = JSON.parse(message.body);
          // Add to alert feed
          setAlertFeed((prev) => [newAlert, ...prev].slice(0, 10));
          // Refresh data immediately
          fetchData();
        });
      },
      onDisconnect: () => {
        setWsConnected(false);
      },
      onWebSocketError: (err) => {
        console.error('WebSocket Error:', err);
        setWsConnected(false);
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, []);

  // Ingest Transaction Form Submit
  const handleIngest = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormLoading(true);
    setFormResponse(null);

    const payload = {
      payerId: form.payerId,
      payeeId: form.payeeId,
      amount: parseFloat(form.amount),
      currency: form.currency,
      idempotencyRef: form.idempotencyRef || undefined,
    };

    try {
      const res = await submitTransaction(payload);
      if (res.status === 201 || res.status === 202) {
        setFormResponse({
          success: true,
          status: res.status,
          message: res.status === 202 ? 'Flagged as Probable Duplicate' : 'Transaction Posted Successfully',
          txnId: res.data?.id,
        });
      } else if (res.status === 409) {
        setFormResponse({
          success: false,
          status: 409,
          message: 'Suppressed: Exact Duplicate Transaction Detected',
        });
      } else {
        setFormResponse({
          success: false,
          status: res.status,
          message: res.data?.message || 'Error occurred during ingestion',
        });
      }
      fetchData();
    } catch (err: any) {
      setFormResponse({
        success: false,
        status: err.response?.status || 500,
        message: err.response?.data?.message || 'Network/Server Error occurred',
      });
    } finally {
      setFormLoading(false);
    }
  };

  // Resolve Tier-2 Flagged duplicates
  const handleResolve = async (id: string, resolution: 'APPROVED' | 'REJECTED') => {
    try {
      await resolveDuplicate(id, resolution, 'Analyst manual action');
      fetchData();
    } catch (err) {
      console.error('Failed to resolve duplicate record:', err);
    }
  };

  // Save Config update
  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await updateWindowSeconds(config.windowSeconds);
      await updateIdentityFields(config.identityFields);
      await updateAmountTolerance(config.amountTolerance);
      alert('System configuration updated successfully.');
      fetchData();
      fetchConfig();
    } catch (err) {
      console.error('Error saving system config:', err);
      alert('Failed to update system configuration.');
    }
  };

  const handleFieldCheckboxChange = (field: string) => {
    const isChecked = config.identityFields.includes(field);
    const newFields = isChecked
      ? config.identityFields.filter((f) => f !== field)
      : [...config.identityFields, field];
    setConfig({ ...config, identityFields: newFields });
  };

  // CSV Export
  const handleExportCsv = async () => {
    try {
      const res = await exportDuplicateReport({});
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `duplicate_records_${new Date().toISOString()}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      console.error('CSV Export failed:', err);
    }
  };

  // Filters & Search
  const filteredDuplicates = duplicates.filter((record) => {
    const matchesSearch =
      record.originalTransaction?.payerId.toLowerCase().includes(searchQuery.toLowerCase()) ||
      record.originalTransaction?.payeeId.toLowerCase().includes(searchQuery.toLowerCase()) ||
      record.id.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesStatus =
      statusFilter === 'ALL' || record.resolution === statusFilter;

    return matchesSearch && matchesStatus;
  });

  const filteredTransactions = transactions.filter((txn) => {
    const matchesSearch =
      txn.payerId.toLowerCase().includes(searchQuery.toLowerCase()) ||
      txn.payeeId.toLowerCase().includes(searchQuery.toLowerCase()) ||
      txn.id.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesStatus =
      statusFilter === 'ALL' || txn.status === statusFilter;

    return matchesSearch && matchesStatus;
  });

  const availableFields = ['payerId', 'payeeId', 'amount', 'currency', 'idempotencyRef'];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      {/* Navigation Bar */}
      <nav className="navbar">
        <a href="/" className="navbar-brand">
          🛡️ Duplicate Detection Engine <span className="brand-badge">PS-62</span>
        </a>
        <div className="navbar-status">
          <span className={`status-dot ${wsConnected ? '' : 'disconnected'}`}></span>
          {wsConnected ? 'WebSocket Live Connected' : 'WebSocket Disconnected (reconnecting...)'}
        </div>
      </nav>

      {/* Main Content Area */}
      <main className="container">
        {/* Metrics Strip */}
        <div className="stats-strip">
          <div className="stat-card">
            <div className="stat-info">
              <h4>Active in Window</h4>
              <p>{metrics.totalInWindow}</p>
            </div>
            <div className="stat-icon primary">⏱️</div>
          </div>
          <div className="stat-card">
            <div className="stat-info">
              <h4>Suppressed (Tier-1)</h4>
              <p>{metrics.suppressedCount}</p>
            </div>
            <div className="stat-icon danger">⛔</div>
          </div>
          <div className="stat-card">
            <div className="stat-info">
              <h4>Flagged (Tier-2 Queue)</h4>
              <p>{metrics.flaggedCount}</p>
            </div>
            <div className="stat-icon warning">⚠️</div>
          </div>
          <div className="stat-card">
            <div className="stat-info">
              <h4>Window (Seconds)</h4>
              <p>{metrics.windowSeconds}s</p>
            </div>
            <div className="stat-icon success">⚙️</div>
          </div>
        </div>

        {/* Dashboard Grid */}
        <div className="dashboard-grid">
          {/* Ingestion Simulator */}
          <div className="card span-8">
            <div className="card-header">
              <h3 className="card-title">🔌 Manual Transaction Simulator</h3>
            </div>
            <form onSubmit={handleIngest} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div className="form-row">
                <div className="form-group">
                  <label>Payer ID</label>
                  <input
                    type="text"
                    className="form-control"
                    value={form.payerId}
                    onChange={(e) => setForm({ ...form, payerId: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Payee ID</label>
                  <input
                    type="text"
                    className="form-control"
                    value={form.payeeId}
                    onChange={(e) => setForm({ ...form, payeeId: e.target.value })}
                    required
                  />
                </div>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>Amount</label>
                  <input
                    type="number"
                    step="0.01"
                    className="form-control"
                    value={form.amount}
                    onChange={(e) => setForm({ ...form, amount: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Currency</label>
                  <select
                    className="form-control"
                    value={form.currency}
                    onChange={(e) => setForm({ ...form, currency: e.target.value })}
                  >
                    <option value="USD">USD</option>
                    <option value="EUR">EUR</option>
                    <option value="GBP">GBP</option>
                  </select>
                </div>
              </div>
              <div className="form-group">
                <label>Idempotency Reference (Optional - leaves empty to auto-generate hash)</label>
                <input
                  type="text"
                  className="form-control"
                  placeholder="e.g., retry-uuid-123"
                  value={form.idempotencyRef}
                  onChange={(e) => setForm({ ...form, idempotencyRef: e.target.value })}
                />
              </div>

              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <button type="submit" className="btn btn-primary" disabled={formLoading}>
                  {formLoading ? 'Processing Ingestion...' : 'Ingest Transaction'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setForm({ ...form, idempotencyRef: `idemp-${Math.random().toString(36).substr(2, 9)}` })}
                >
                  Generate Idempotency Ref
                </button>
              </div>

              {formResponse && (
                <div
                  style={{
                    marginTop: '1rem',
                    padding: '1rem',
                    borderRadius: '6px',
                    backgroundColor:
                      formResponse.success && formResponse.status === 201
                        ? 'rgba(16, 185, 129, 0.15)'
                        : formResponse.status === 202
                        ? 'rgba(245, 158, 11, 0.15)'
                        : 'rgba(239, 68, 68, 0.15)',
                    border: `1px solid ${
                      formResponse.success && formResponse.status === 201
                        ? 'var(--color-success)'
                        : formResponse.status === 202
                        ? 'var(--color-warning)'
                        : 'var(--color-danger)'
                    }`,
                  }}
                >
                  <p style={{ fontWeight: 'bold' }}>{formResponse.message}</p>
                  {formResponse.txnId && <span style={{ fontSize: '0.8rem', opacity: 0.8 }}>TXID: {formResponse.txnId}</span>}
                </div>
              )}
            </form>
          </div>

          {/* Real-time alert feed */}
          <div className="card span-4">
            <div className="card-header">
              <h3 className="card-title">📡 Real-Time Alert Feed</h3>
            </div>
            <div className="feed-list">
              {alertFeed.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', textAlign: 'center', padding: '2rem 0' }}>
                  No duplicate alerts caught in this session.
                </div>
              ) : (
                alertFeed.map((alert) => (
                  <div key={alert.id} className={`feed-item ${alert.matchTier.toLowerCase()}`}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span className={`badge badge-${alert.matchTier.toLowerCase()}`}>{alert.matchTier}</span>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                        {new Date(alert.detectedAt).toLocaleTimeString()}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.85rem' }}>
                      <strong>Payer:</strong> {alert.originalTransaction?.payerId} → <strong>Payee:</strong>{' '}
                      {alert.originalTransaction?.payeeId}
                    </div>
                    <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>
                      {alert.originalTransaction?.amount} {alert.originalTransaction?.currency}
                    </div>
                    <div style={{ fontSize: '0.75rem', opacity: 0.7 }}>Delta: {alert.timeDeltaMs}ms</div>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Analyst Action Queue (Tier-2) */}
          <div className="card span-8">
            <div className="card-header">
              <h3 className="card-title">🧑‍💻 Analyst Review Queue</h3>
            </div>
            <div className="table-container">
              <table className="custom-table">
                <thead>
                  <tr>
                    <th>Payer</th>
                    <th>Payee</th>
                    <th>Amount</th>
                    <th>Time Delta</th>
                    <th>Matches</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {duplicates.filter((d) => d.resolution === 'PENDING' && d.matchTier === 'PROBABLE').length === 0 ? (
                    <tr>
                      <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>
                        All caught duplicates resolved. No pending reviews in queue.
                      </td>
                    </tr>
                  ) : (
                    duplicates
                      .filter((d) => d.resolution === 'PENDING' && d.matchTier === 'PROBABLE')
                      .map((d) => (
                        <tr key={d.id}>
                          <td>{d.originalTransaction?.payerId}</td>
                          <td>{d.originalTransaction?.payeeId}</td>
                          <td>
                            {d.originalTransaction?.amount} {d.originalTransaction?.currency}
                          </td>
                          <td>{d.timeDeltaMs}ms</td>
                          <td>
                            <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
                              {d.matchedFields.map((f) => (
                                <span
                                  key={f}
                                  style={{
                                    fontSize: '0.7rem',
                                    background: 'rgba(255,255,255,0.05)',
                                    padding: '0.1rem 0.3rem',
                                    borderRadius: '3px',
                                  }}
                                >
                                  {f}
                                </span>
                              ))}
                            </div>
                          </td>
                          <td>
                            <div style={{ display: 'flex', gap: '0.5rem' }}>
                              <button
                                onClick={() => handleResolve(d.id, 'APPROVED')}
                                className="btn btn-success btn-sm"
                              >
                                Suppress
                              </button>
                              <button
                                onClick={() => handleResolve(d.id, 'REJECTED')}
                                className="btn btn-danger btn-sm"
                              >
                                Release
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Historical Trend Chart */}
          <div className="card span-4">
            <div className="card-header">
              <h3 className="card-title">📈 Duplicates Caught (Trend)</h3>
            </div>
            <div style={{ width: '100%', height: 230 }}>
              {chartData.length === 0 ? (
                <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', textAlign: 'center', padding: '4rem 0' }}>
                  No charts data available.
                </div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData}>
                    <defs>
                      <linearGradient id="colorCount" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--color-primary)" stopOpacity={0.4} />
                        <stop offset="95%" stopColor="var(--color-primary)" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                    <XAxis dataKey="time" stroke="var(--text-muted)" fontSize={10} />
                    <YAxis stroke="var(--text-muted)" fontSize={10} />
                    <Tooltip
                      contentStyle={{ background: '#1e293b', border: '1px solid var(--border-color)', color: '#fff' }}
                    />
                    <Area
                      type="monotone"
                      dataKey="count"
                      stroke="var(--color-primary)"
                      fillOpacity={1}
                      fill="url(#colorCount)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          {/* System Configuration panel */}
          <div className="card span-12">
            <div className="card-header">
              <h3 className="card-title">⚙️ Dynamic System Configuration</h3>
            </div>
            <form onSubmit={handleSaveConfig} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div className="form-row">
                <div className="form-group">
                  <label>Sliding Window Duration (Seconds)</label>
                  <input
                    type="number"
                    className="form-control"
                    value={config.windowSeconds}
                    onChange={(e) => setConfig({ ...config, windowSeconds: parseInt(e.target.value) || 60 })}
                    min={1}
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Amount Variance Tolerance (Variance allowed for probable match, e.g., 0.00 to 5.00)</label>
                  <input
                    type="number"
                    step="0.01"
                    className="form-control"
                    value={config.amountTolerance}
                    onChange={(e) => setConfig({ ...config, amountTolerance: parseFloat(e.target.value) || 0.0 })}
                    min={0}
                    required
                  />
                </div>
              </div>
              <div className="form-group">
                <label>Matching Fields Configuration (Checked fields form the unique transaction identity key)</label>
                <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap', marginTop: '0.5rem' }}>
                  {availableFields.map((f) => (
                    <label key={f} style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={config.identityFields.includes(f)}
                        onChange={() => handleFieldCheckboxChange(f)}
                      />
                      <span style={{ fontSize: '0.9rem', color: 'var(--text-main)' }}>{f}</span>
                    </label>
                  ))}
                </div>
              </div>
              <div>
                <button type="submit" className="btn btn-primary">
                  Save Configurations
                </button>
              </div>
            </form>
          </div>

          {/* Full Audit Log */}
          <div className="card span-12">
            <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', width: '100%', flexWrap: 'wrap', gap: '1rem' }}>
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <h3 className="card-title">📋 Historical Audit Log</h3>
                <div style={{ display: 'flex', background: 'rgba(255,255,255,0.05)', borderRadius: '6px', padding: '0.25rem' }}>
                  <button
                    onClick={() => setActiveTab('duplicates')}
                    style={{
                      border: 'none',
                      background: activeTab === 'duplicates' ? 'var(--color-primary)' : 'transparent',
                      color: '#fff',
                      padding: '0.35rem 0.75rem',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                    }}
                  >
                    Duplicates Caught
                  </button>
                  <button
                    onClick={() => setActiveTab('transactions')}
                    style={{
                      border: 'none',
                      background: activeTab === 'transactions' ? 'var(--color-primary)' : 'transparent',
                      color: '#fff',
                      padding: '0.35rem 0.75rem',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                    }}
                  >
                    All Ingested Stream
                  </button>
                </div>
              </div>
              <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                <input
                  type="text"
                  placeholder="Search by payer/payee/ID..."
                  className="form-control"
                  style={{ width: '200px', padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
                <select
                  className="form-control"
                  style={{ width: '130px', padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                >
                  <option value="ALL">All States</option>
                  <option value="PENDING">Pending (Tier-2)</option>
                  <option value="APPROVED">Suppressed (Tier-1)</option>
                  <option value="REJECTED">Released (Posted)</option>
                  <option value="POSTED">Posted (Success)</option>
                  <option value="SUPPRESSED">Suppressed (Auto)</option>
                  <option value="FLAGGED">Flagged (Risk)</option>
                </select>
                <button onClick={handleExportCsv} className="btn btn-secondary btn-sm">
                  Export CSV
                </button>
              </div>
            </div>
            <div className="table-container">
              {activeTab === 'duplicates' ? (
                <table className="custom-table">
                  <thead>
                    <tr>
                      <th>Record ID</th>
                      <th>Match Tier</th>
                      <th>Payer / Payee</th>
                      <th>Amount / Currency</th>
                      <th>Time Delta</th>
                      <th>Resolution</th>
                      <th>Resolved At / By</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredDuplicates.length === 0 ? (
                      <tr>
                        <td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '3rem' }}>
                          No duplicate records found matching filter criteria.
                        </td>
                      </tr>
                    ) : (
                      filteredDuplicates.map((rec) => (
                        <tr key={rec.id}>
                          <td style={{ fontSize: '0.8rem', fontFamily: 'monospace' }}>{rec.id.substring(0, 8)}...</td>
                          <td>
                            <span className={`badge badge-${rec.matchTier.toLowerCase()}`}>{rec.matchTier}</span>
                          </td>
                          <td>
                            <div style={{ fontSize: '0.85rem' }}>
                              <strong>Payer:</strong> {rec.originalTransaction?.payerId}
                            </div>
                            <div style={{ fontSize: '0.85rem' }}>
                              <strong>Payee:</strong> {rec.originalTransaction?.payeeId}
                            </div>
                          </td>
                          <td>
                            <strong>{rec.originalTransaction?.amount}</strong> {rec.originalTransaction?.currency}
                          </td>
                          <td>{rec.timeDeltaMs}ms</td>
                          <td>
                            <span
                              className={`badge ${
                                rec.resolution === 'APPROVED'
                                  ? 'badge-posted'
                                  : rec.resolution === 'REJECTED'
                                  ? 'badge-suppressed'
                                  : 'badge-flagged'
                              }`}
                            >
                              {rec.resolution}
                            </span>
                          </td>
                          <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            {rec.resolvedAt ? (
                              <>
                                <div>{new Date(rec.resolvedAt).toLocaleString()}</div>
                                <div>by {rec.resolvedBy}</div>
                              </>
                            ) : (
                              'Pending Review'
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              ) : (
                <table className="custom-table">
                  <thead>
                    <tr>
                      <th>Transaction ID</th>
                      <th>Payer</th>
                      <th>Payee</th>
                      <th>Amount</th>
                      <th>Currency</th>
                      <th>Status</th>
                      <th>Ingestion Reference</th>
                      <th>Ingested At</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredTransactions.length === 0 ? (
                      <tr>
                        <td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '3rem' }}>
                          No ingested transactions found matching filter criteria.
                        </td>
                      </tr>
                    ) : (
                      filteredTransactions.map((txn) => (
                        <tr key={txn.id}>
                          <td style={{ fontSize: '0.8rem', fontFamily: 'monospace' }}>{txn.id.substring(0, 8)}...</td>
                          <td>{txn.payerId}</td>
                          <td>{txn.payeeId}</td>
                          <td style={{ fontWeight: 600 }}>{txn.amount}</td>
                          <td>{txn.currency}</td>
                          <td>
                            <span
                              className={`badge ${
                                txn.status === 'POSTED'
                                  ? 'badge-posted'
                                  : txn.status === 'SUPPRESSED'
                                  ? 'badge-exact'
                                  : 'badge-flagged'
                              }`}
                            >
                              {txn.status}
                            </span>
                          </td>
                          <td style={{ fontSize: '0.8rem', opacity: 0.8 }}>{txn.idempotencyRef || 'N/A'}</td>
                          <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            {new Date(txn.createdAt).toLocaleString()}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
