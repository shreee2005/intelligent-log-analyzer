import { useState, useEffect } from 'react';
import axios from 'axios';
import { Radio, Plus, Trash2, ShieldCheck, AlertCircle, RefreshCw, Clock, CheckCircle, XCircle, Eye, RotateCcw, ChevronDown, ChevronUp } from 'lucide-react';
import { API_BASE_URL } from './config';

function WebhookManagement({ projectId, token }) {
  const [webhooks, setWebhooks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const [showModal, setShowModal] = useState(false);
  const [url, setUrl] = useState('');
  const [secret, setSecret] = useState('');
  const [modalError, setModalError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [deliveryModal, setDeliveryModal] = useState(null);
  const [deliveryLoading, setDeliveryLoading] = useState(false);

  useEffect(() => {
    if (projectId) fetchWebhooks();
  }, [projectId]);

  const fetchWebhooks = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await axios.get(`${API_BASE_URL}/api/projects/${projectId}/webhooks`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      const list = Array.isArray(response.data) ? response.data : [];
      setWebhooks(list);
    } catch (err) {
      console.error(err);
      setError('Failed to fetch webhook subscriptions.');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateWebhook = async (e) => {
    e.preventDefault();
    setModalError('');
    setSubmitting(true);
    try {
      await axios.post(
        `${API_BASE_URL}/api/projects/${projectId}/webhooks`,
        { url, secret },
        { headers: { Authorization: `Bearer ${token}` } }
      );
      setSuccessMsg('Webhook subscription created successfully.');
      setShowModal(false);
      setUrl('');
      setSecret('');
      fetchWebhooks();
    } catch (err) {
      console.error(err);
      setModalError(err.response?.data?.error || 'Failed to create webhook. Note: URL must be external HTTP/HTTPS and secret at least 32 characters.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDisableWebhook = async (webhookId) => {
    if (!confirm('Are you sure you want to disable this webhook subscription?')) return;
    try {
      await axios.delete(`${API_BASE_URL}/api/projects/${projectId}/webhooks/${webhookId}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setSuccessMsg('Webhook disabled.');
      fetchWebhooks();
    } catch (err) {
      console.error(err);
      alert('Failed to disable webhook.');
    }
  };

  const handleViewDeliveries = async (webhook) => {
    setDeliveryLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/api/projects/${projectId}/webhooks/${webhook.id}/deliveries`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      const list = Array.isArray(response.data) ? response.data : [];
      setDeliveryModal({ webhook, deliveries: list });
    } catch (err) {
      console.error(err);
      alert('Failed to fetch delivery history.');
    } finally {
      setDeliveryLoading(false);
    }
  };

  const handleReplayDelivery = async (deliveryId) => {
    if (!confirm('Replay this failed delivery?')) return;
    try {
      await axios.post(
        `${API_BASE_URL}/api/projects/${projectId}/webhooks/deliveries/${deliveryId}/replay`,
        {},
        { headers: { Authorization: `Bearer ${token}` } }
      );
      setSuccessMsg('Delivery replayed successfully.');
      if (deliveryModal) {
        const response = await axios.get(`${API_BASE_URL}/api/projects/${projectId}/webhooks/${deliveryModal.webhook.id}/deliveries`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        const list = Array.isArray(response.data) ? response.data : [];
        setDeliveryModal({ ...deliveryModal, deliveries: list });
      }
    } catch (err) {
      console.error(err);
      alert(err.response?.data?.error || 'Failed to replay delivery.');
    }
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'DELIVERED': return <CheckCircle size={14} style={{ color: 'var(--success)' }} />;
      case 'FAILED': return <XCircle size={14} style={{ color: 'var(--error)' }} />;
      case 'RETRY_PENDING': return <Clock size={14} style={{ color: 'var(--warning)' }} />;
      case 'PENDING': return <Clock size={14} style={{ color: 'var(--text-muted)' }} />;
      default: return <Clock size={14} style={{ color: 'var(--text-muted)' }} />;
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return 'N/A';
    try {
      return new Date(dateStr).toLocaleString();
    } catch {
      return dateStr;
    }
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '1000px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <div>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Radio size={22} style={{ color: 'var(--primary)' }} />
            Webhook Subscriptions
          </h2>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
            Configure external endpoints to receive real-time anomaly payloads signed with HMAC-SHA256.
          </p>
        </div>
        <button className="nav-item active" onClick={() => setShowModal(true)} style={{ padding: '0.5rem 1rem', cursor: 'pointer' }}>
          <Plus size={16} /> Add Webhook
        </button>
      </div>

      {successMsg && (
        <div style={{ padding: '0.75rem 1rem', backgroundColor: 'rgba(34, 197, 94, 0.1)', border: '1px solid var(--success)', borderRadius: '6px', color: 'var(--success)', marginBottom: '1.5rem', fontSize: '0.875rem' }}>
          {successMsg}
        </div>
      )}

      {error && <div className="error-message" style={{ marginBottom: '1.5rem' }}>{error}</div>}

      <div className="card" style={{ padding: '1.5rem', border: '1px solid var(--border)' }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>Loading subscriptions...</div>
        ) : webhooks.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
            <Radio size={40} style={{ opacity: 0.3, marginBottom: '1rem' }} />
            <p>No webhook subscriptions configured for this project.</p>
            <p style={{ fontSize: '0.85rem', marginTop: '0.5rem' }}>Click "Add Webhook" above to connect alert endpoints.</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {webhooks.map((wh) => (
              <div
                key={wh.id}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '1rem',
                  borderRadius: '6px',
                  backgroundColor: 'rgba(255,255,255,0.02)',
                  border: '1px solid var(--border)'
                }}
              >
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, fontFamily: 'monospace', fontSize: '0.9rem', color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <ShieldCheck size={16} style={{ color: wh.active ? 'var(--success)' : 'var(--text-muted)' }} />
                    {wh.url}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                    Status: <span style={{ color: wh.active ? 'var(--success)' : 'var(--error)' }}>{wh.active ? 'ACTIVE' : 'DISABLED'}</span> | Signature: HMAC-SHA256
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  <button
                    onClick={() => handleViewDeliveries(wh)}
                    className="nav-item"
                    style={{ padding: '0.4rem 0.75rem', fontSize: '0.75rem', cursor: 'pointer' }}
                  >
                    <Eye size={14} /> History
                  </button>

                  {wh.active && (
                    <button
                      onClick={() => handleDisableWebhook(wh.id)}
                      className="nav-item"
                      style={{ padding: '0.4rem 0.75rem', fontSize: '0.75rem', color: 'var(--error)', borderColor: 'rgba(239, 68, 68, 0.3)', cursor: 'pointer' }}
                    >
                      <Trash2 size={14} /> Disable
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {showModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
          <div className="card" style={{ width: '480px', padding: '2rem', border: '1px solid var(--border)' }}>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '1.5rem' }}>Register Webhook Endpoint</h3>
            {modalError && <div className="error-message" style={{ marginBottom: '1rem' }}>{modalError}</div>}

            <form onSubmit={handleCreateWebhook} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>Payload Destination URL</label>
                <input
                  type="url"
                  className="search-input"
                  style={{ width: '100%', padding: '0.75rem', fontFamily: 'monospace' }}
                  placeholder="https://example.com/api/alerts/webhook"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  required
                />
                <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'block', marginTop: '0.25rem' }}>
                  Must be an external HTTP/HTTPS URL. Internal/localhost IPs are blocked for SSRF security.
                </span>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>Signing Secret (min 32 chars)</label>
                <input
                  type="password"
                  className="search-input"
                  style={{ width: '100%', padding: '0.75rem', fontFamily: 'monospace' }}
                  placeholder="Secret key for HMAC-SHA256 signature verification"
                  value={secret}
                  onChange={(e) => setSecret(e.target.value)}
                  minLength={32}
                  required
                />
              </div>

              <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
                <button type="button" className="nav-item" onClick={() => setShowModal(false)} style={{ padding: '0.75rem 1.5rem', cursor: 'pointer' }}>
                  Cancel
                </button>
                <button type="submit" className="nav-item active" style={{ padding: '0.75rem 1.5rem', cursor: 'pointer' }} disabled={submitting}>
                  {submitting ? 'Saving...' : 'Register Webhook'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deliveryModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1001 }}>
          <div className="card" style={{ width: '900px', maxHeight: '80vh', padding: '1.5rem', border: '1px solid var(--border)', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', paddingBottom: '1rem', borderBottom: '1px solid var(--border)' }}>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Radio size={22} style={{ color: 'var(--primary)' }} />
                Delivery History: {deliveryModal.webhook.url}
              </h3>
              <button className="nav-item" onClick={() => setDeliveryModal(null)} style={{ padding: '0.5rem', cursor: 'pointer' }}>
                <XCircle size={20} />
              </button>
            </div>

            {deliveryLoading && (
              <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>Loading delivery history...</div>
            )}

            {!deliveryLoading && deliveryModal.deliveries.length === 0 && (
              <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                <Radio size={40} style={{ opacity: 0.3, marginBottom: '1rem' }} />
                <p>No deliveries recorded for this webhook yet.</p>
              </div>
            )}

            {!deliveryLoading && deliveryModal.deliveries.length > 0 && (
              <div style={{ flex: 1, overflow: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                      <th style={{ padding: '0.5rem', width: '8%' }}>Event ID</th>
                      <th style={{ padding: '0.5rem', width: '10%' }}>Status</th>
                      <th style={{ padding: '0.5rem', width: '8%' }}>Attempts</th>
                      <th style={{ padding: '0.5rem', width: '10%' }}>HTTP Status</th>
                      <th style={{ padding: '0.5rem', width: '8%' }}>Latency</th>
                      <th style={{ padding: '0.5rem', width: '15%' }}>Created</th>
                      <th style={{ padding: '0.5rem', width: '15%' }}>Delivered / Next Retry</th>
                      <th style={{ padding: '0.5rem', width: '15%' }}>Last Error</th>
                      <th style={{ padding: '0.5rem', width: '11%' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {deliveryModal.deliveries.map((d) => (
                      <tr key={d.id} style={{ borderBottom: '1px solid var(--border)', verticalAlign: 'top' }}>
                        <td style={{ padding: '0.5rem', fontFamily: 'monospace', fontSize: '0.7rem' }}>
                          {d.eventId?.substring(0, 12)}...
                        </td>
                        <td style={{ padding: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                          {getStatusIcon(d.status)}
                          <span style={{ 
                            textTransform: 'capitalize', 
                            fontWeight: 500,
                            color: d.status === 'DELIVERED' ? 'var(--success)' : d.status === 'FAILED' ? 'var(--error)' : 'var(--warning)'
                          }}>
                            {d.status?.toLowerCase().replace('_', ' ')}
                          </span>
                        </td>
                        <td style={{ padding: '0.5rem', textAlign: 'center' }}>{d.attempts || 0}</td>
                        <td style={{ padding: '0.5rem', textAlign: 'center', fontFamily: 'monospace' }}>
                          {d.httpStatus ? (
                            <span style={{ 
                              color: d.httpStatus >= 200 && d.httpStatus < 300 ? 'var(--success)' : 'var(--error)',
                              fontWeight: d.httpStatus >= 200 && d.httpStatus < 300 ? 600 : 400
                            }}>
                              {d.httpStatus}
                            </span>
                          ) : (
                            <span style={{ color: 'var(--text-muted)' }}>—</span>
                          )}
                        </td>
                        <td style={{ padding: '0.5rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                          {d.latencyMs ? `${d.latencyMs}ms` : '—'}
                        </td>
                        <td style={{ padding: '0.5rem', color: 'var(--text-muted)', fontSize: '0.75rem' }}>
                          {formatDate(d.createdAt)}
                        </td>
                        <td style={{ padding: '0.5rem', color: 'var(--text-muted)', fontSize: '0.75rem' }}>
                          {d.deliveredAt ? formatDate(d.deliveredAt) : (d.nextAttemptAt ? `Next: ${formatDate(d.nextAttemptAt)}` : '—')}
                        </td>
                        <td style={{ padding: '0.5rem', color: 'var(--error)', fontSize: '0.7rem', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {d.lastError || '—'}
                        </td>
                        <td style={{ padding: '0.5rem' }}>
                          {(d.status === 'FAILED' || d.status === 'RETRY_PENDING') && (
                            <button
                              onClick={() => handleReplayDelivery(d.id)}
                              className="nav-item"
                              style={{ padding: '0.3rem 0.6rem', fontSize: '0.7rem', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '0.25rem' }}
                            >
                              <RotateCcw size={12} /> Replay
                            </button>
                          )}
                          {d.status === 'DELIVERED' && (
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem' }}>Completed</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default WebhookManagement;