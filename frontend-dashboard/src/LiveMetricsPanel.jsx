import { useState, useEffect } from 'react';
import axios from 'axios';
import { Activity, AlertTriangle, RefreshCw } from 'lucide-react';
import { API_BASE_URL } from './config';

const LiveMetricsPanel = ({ projectId, token }) => {
  const [metrics, setMetrics] = useState({ errorCount: 0, totalLogs: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [services, setServices] = useState([]);
  const [selectedService, setSelectedService] = useState('');
  const [servicesLoading, setServicesLoading] = useState(true);

  useEffect(() => {
    if (!projectId) return;

    const fetchServices = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}/api/v1/search/services?projectId=${projectId}`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        const serviceList = response.data || [];
        setServices(serviceList);
        if (serviceList.length > 0 && !selectedService) {
          setSelectedService(serviceList[0]);
        }
      } catch (err) {
        console.error('Error fetching services', err);
      } finally {
        setServicesLoading(false);
      }
    };

    fetchServices();
  }, [projectId, token]);

  useEffect(() => {
    if (!projectId || !selectedService) {
      setLoading(false);
      return;
    }

    const fetchMetrics = async () => {
      try {
        const response = await axios.get(`${API_BASE_URL}/api/v1/analytics/metrics/${selectedService}?projectId=${projectId}`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        setMetrics(response.data);
        setError(null);
      } catch (err) {
        console.error('Error fetching metrics', err);
        setError('Failed to load metrics. Is the backend running?');
      } finally {
        setLoading(false);
      }
    };

    fetchMetrics();
    const interval = setInterval(fetchMetrics, 5000);
    return () => clearInterval(interval);
  }, [projectId, selectedService, token]);

  if (servicesLoading) {
    return (
      <div className="card">
        <div className="card-title">
          <Activity size={18} />
          Live Metrics
        </div>
        <p style={{ color: 'var(--text-muted)' }}>Loading services...</p>
      </div>
    );
  }

  return (
    <div className="card">
      <div className="card-title">
        <Activity size={18} />
        Live Metrics
        {services.length > 0 && (
          <select
            value={selectedService}
            onChange={(e) => setSelectedService(e.target.value)}
            className="service-select"
            style={{ marginLeft: '1rem', padding: '0.25rem 0.5rem', fontSize: '0.875rem' }}
          >
            {services.map((svc) => (
              <option key={svc} value={svc}>{svc}</option>
            ))}
          </select>
        )}
      </div>

      {loading && <p style={{ color: 'var(--text-muted)' }}>Loading...</p>}
      
      {error && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--error)' }}>
          <AlertTriangle size={16} />
          <span style={{ fontSize: '0.875rem' }}>{error}</span>
        </div>
      )}

      {services.length === 0 && !loading && !error && (
        <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
          No services found for this project. Send some logs first!
        </p>
      )}

      {!loading && !error && services.length > 0 && (
        <div className="metrics-grid">
          <div className="metric-box">
            <div className="metric-value">{metrics.totalLogs || 0}</div>
            <div className="metric-label">Total Logs</div>
          </div>
          <div className="metric-box">
            <div className="metric-value error">{metrics.errorCount || 0}</div>
            <div className="metric-label">Errors Detected</div>
          </div>
        </div>
      )}
    </div>
  );
};

export default LiveMetricsPanel;
