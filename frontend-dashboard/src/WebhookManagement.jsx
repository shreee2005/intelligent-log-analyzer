import { useState, useEffect } from 'react';
import axios from 'axios';
import { Radio, Plus, Trash2, ShieldCheck, AlertCircle, RefreshCw } from 'lucide-react';
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
                <div>
                  <div style={{ fontWeight: 600, fontFamily: 'monospace', fontSize: '0.9rem', color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <ShieldCheck size={16} style={{ color: wh.active ? 'var(--success)' : 'var(--text-muted)' }} />
                    {wh.url}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                    Status: <span style={{ color: wh.active ? 'var(--success)' : 'var(--error)' }}>{wh.active ? 'ACTIVE' : 'DISABLED'}</span> | Signature: HMAC-SHA256
                  </div>
                </div>

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
    </div>
  );
}

export default WebhookManagement;
