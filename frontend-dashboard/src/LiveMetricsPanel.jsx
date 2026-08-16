import { useState, useEffect } from 'react';
import axios from 'axios';
import { Activity, AlertTriangle } from 'lucide-react';

const LiveMetricsPanel = () => {
  const [metrics, setMetrics] = useState({ errorCount: 0, totalLogs: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Hardcode auth-service for now to see metrics
  const serviceId = 'auth-service';

  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const response = await axios.get(`http://localhost:8083/api/v1/analytics/metrics/${serviceId}`);
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
    // Poll every 5 seconds
    const interval = setInterval(fetchMetrics, 5000);
    return () => clearInterval(interval);
  }, [serviceId]);

  return (
    <div className="card">
      <div className="card-title">
        <Activity size={18} />
        Live Metrics (auth-service)
      </div>

      {loading && <p style={{ color: 'var(--text-muted)' }}>Loading...</p>}
      
      {error && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--error)' }}>
          <AlertTriangle size={16} />
          <span style={{ fontSize: '0.875rem' }}>{error}</span>
        </div>
      )}

      {!loading && !error && (
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
