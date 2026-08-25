import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { Plus, Trash2, Activity, RefreshCw, BarChart2 } from 'lucide-react';

function MetricsDashboard({ projectId, token }) {
  const [metrics, setMetrics] = useState([]);
  const [selectedMetric, setSelectedMetric] = useState(null);
  const [chartData, setChartData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingChart, setLoadingChart] = useState(false);
  const [error, setError] = useState('');

  const [showModal, setShowModal] = useState(false);
  const [metricName, setMetricName] = useState('');
  const [metricRegex, setMetricRegex] = useState('');
  const [modalError, setModalError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [hoveredPoint, setHoveredPoint] = useState(null);
  const chartRef = useRef(null);

  useEffect(() => {
    if (projectId) fetchMetrics();
  }, [projectId]);

  useEffect(() => {
    let interval;
    if (selectedMetric?.id) {
      fetchChartData(true);
      interval = setInterval(() => fetchChartData(false), 5000);
    } else {
      setChartData([]);
    }
    return () => interval && clearInterval(interval);
  }, [selectedMetric?.id, projectId, token]);

  const fetchMetrics = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await axios.get(`http://localhost:8091/api/projects/${projectId}/metrics`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      const payload = response.data;
      const list = Array.isArray(payload)
        ? payload
        : Array.isArray(payload?.metrics)
        ? payload.metrics
        : [];

      if (!Array.isArray(payload) && !Array.isArray(payload?.metrics)) {
        console.warn('Unexpected metrics response shape:', payload);
      }

      setMetrics(list);
      setSelectedMetric(list.length > 0 ? list[0] : null);
    } catch (err) {
      console.error(err);
      setError('Failed to fetch metrics schemas.');
    } finally {
      setLoading(false);
    }
  };

  const fetchChartData = async (showLoader = false) => {
    if (!selectedMetric?.id) return;
    if (showLoader) setLoadingChart(true);

    try {
      const response = await axios.get(
        `http://localhost:8091/api/projects/${projectId}/metrics/${selectedMetric.id}/data?minutes=30`,
        { headers: { Authorization: `Bearer ${token}` } }
      );

      const payload = response.data;
      const points = Array.isArray(payload)
        ? payload
        : Array.isArray(payload?.data)
        ? payload.data
        : [];

      if (!Array.isArray(payload) && !Array.isArray(payload?.data)) {
        console.warn('Unexpected chart response shape:', payload);
      }

      const sanitized = points.filter((p) => p && typeof p.count !== 'undefined' && p.timestamp);
      setChartData(sanitized);
    } catch (err) {
      console.error(err);
    } finally {
      if (showLoader) setLoadingChart(false);
    }
  };

  const handleCreateMetric = async (e) => {
    e.preventDefault();
    setModalError('');
    setSubmitting(true);
    try {
      const response = await axios.post(
        `http://localhost:8091/api/projects/${projectId}/metrics`,
        { name: metricName, regexPattern: metricRegex },
        { headers: { Authorization: `Bearer ${token}` } }
      );

      const created = response.data;
      setMetrics((prev) => [...prev, created]);
      setSelectedMetric(created);
      setShowModal(false);
      setMetricName('');
      setMetricRegex('');
    } catch (err) {
      console.error(err);
      setModalError(err.response?.data?.error || 'Failed to save metric rule.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteMetric = async (metricId, e) => {
    e.stopPropagation();
    if (!confirm('Are you sure you want to delete this metric rule? History stats in Redis will be lost.')) return;

    try {
      await axios.delete(`http://localhost:8091/api/projects/${projectId}/metrics/${metricId}`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      setMetrics((prev) => {
        const remaining = prev.filter((m) => m.id !== metricId);
        if (selectedMetric?.id === metricId) {
          setSelectedMetric(remaining.length > 0 ? remaining[0] : null);
        }
        return remaining;
      });
    } catch (err) {
      console.error(err);
      alert('Failed to delete metric rule.');
    }
  };

  const formatChartTime = (timestamp) => {
    const date = new Date(timestamp);
    if (Number.isNaN(date.getTime())) return String(timestamp ?? '');
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const renderSvgChart = () => {
    const safeChartData = Array.isArray(chartData) ? chartData : [];
    if (safeChartData.length === 0) return null;

    const width = 600;
    const height = 280;
    const paddingLeft = 40;
    const paddingRight = 20;
    const paddingTop = 20;
    const paddingBottom = 30;

    const chartWidth = width - paddingLeft - paddingRight;
    const chartHeight = height - paddingTop - paddingBottom;

    const maxCount = Math.max(...safeChartData.map((d) => Number(d.count) || 0), 5);
    const minCount = 0;

    const points = safeChartData.map((d, index) => {
      const x = safeChartData.length === 1
        ? paddingLeft + chartWidth / 2
        : paddingLeft + (index / (safeChartData.length - 1)) * chartWidth;

      const y = paddingTop + chartHeight - (((Number(d.count) || 0) - minCount) / (maxCount - minCount || 1)) * chartHeight;
      return { x, y, data: d, index };
    });

    const linePath = points.reduce((path, p, i) => (i === 0 ? `M ${p.x} ${p.y}` : `${path} L ${p.x} ${p.y}`), '');
    const areaPath = points.length
      ? `${linePath} L ${points[points.length - 1].x} ${paddingTop + chartHeight} L ${points[0].x} ${paddingTop + chartHeight} Z`
      : '';

    const yGridLines = [];
    const divisions = 4;
    for (let i = 0; i <= divisions; i++) {
      const val = Math.round(minCount + (i / divisions) * (maxCount - minCount));
      const y = paddingTop + chartHeight - (i / divisions) * chartHeight;
      yGridLines.push({ val, y });
    }

    const xGridLabels = points.filter((_, i) => i % 5 === 0 || points.length <= 5);

    const handleMouseMove = (e) => {
      if (!chartRef.current) return;
      const rect = chartRef.current.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;

      let closest = null;
      let minDistance = Infinity;

      points.forEach((p) => {
        const dist = Math.abs(p.x - mouseX);
        if (dist < minDistance) {
          minDistance = dist;
          closest = p;
        }
      });

      setHoveredPoint(closest && minDistance < 30 ? closest : null);
    };

    return (
      <div style={{ position: 'relative' }}>
        <svg
          ref={chartRef}
          width="100%"
          height={height}
          viewBox={`0 0 ${width} ${height}`}
          onMouseMove={handleMouseMove}
          onMouseLeave={() => setHoveredPoint(null)}
          style={{ overflow: 'visible', userSelect: 'none', cursor: 'crosshair' }}
        >
          <defs>
            <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.3" />
              <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {yGridLines.map((line, i) => (
            <g key={i}>
              <line x1={paddingLeft} y1={line.y} x2={width - paddingRight} y2={line.y} stroke="var(--border)" strokeDasharray="4,4" strokeWidth={0.5} />
              <text x={paddingLeft - 10} y={line.y + 4} fill="var(--text-muted)" fontSize="10" textAnchor="end" fontFamily="monospace">
                {line.val}
              </text>
            </g>
          ))}

          {areaPath && <path d={areaPath} fill="url(#chartGradient)" />}
          {linePath && <path d={linePath} fill="none" stroke="var(--primary)" strokeWidth={2} strokeLinecap="round" />}

          {xGridLabels.map((p, i) => (
            <g key={i}>
              <line x1={p.x} y1={paddingTop + chartHeight} x2={p.x} y2={paddingTop + chartHeight + 5} stroke="var(--border)" />
              <text x={p.x} y={paddingTop + chartHeight + 18} fill="var(--text-muted)" fontSize="9" textAnchor="middle" fontFamily="monospace">
                {formatChartTime(p.data.timestamp)}
              </text>
            </g>
          ))}

          {hoveredPoint && (
            <g>
              <line x1={hoveredPoint.x} y1={paddingTop} x2={hoveredPoint.x} y2={paddingTop + chartHeight} stroke="var(--primary)" strokeOpacity={0.3} strokeWidth={1} />
              <circle cx={hoveredPoint.x} cy={hoveredPoint.y} r={5} fill="var(--primary)" stroke="var(--bg-card)" strokeWidth={1.5} />
            </g>
          )}
        </svg>

        {hoveredPoint && (
          <div
            style={{
              position: 'absolute',
              top: '10px',
              left: `${Math.min(hoveredPoint.x + 10, width - 140)}px`,
              backgroundColor: 'rgba(15, 23, 42, 0.95)',
              border: '1px solid var(--border)',
              padding: '0.5rem 0.75rem',
              borderRadius: '4px',
              fontSize: '0.75rem',
              fontFamily: 'monospace',
              color: 'var(--text-main)',
              boxShadow: '0 4px 12px rgba(0,0,0,0.5)',
              pointerEvents: 'none',
              zIndex: 10
            }}
          >
            <div>Time: {formatChartTime(hoveredPoint.data.timestamp)}</div>
            <div style={{ color: 'var(--primary)', fontWeight: 'bold', marginTop: '0.25rem' }}>
              Count: {hoveredPoint.data.count} logs
            </div>
          </div>
        )}
      </div>
    );
  };

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '300px 1fr', gap: '2rem', padding: '2rem', minHeight: 'calc(100vh - 80px)' }}>
      <div className="card" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', height: 'fit-content', border: '1px solid var(--border)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
          <h3 style={{ fontSize: '1.1rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Activity size={18} style={{ color: 'var(--primary)' }} />
            Custom Metrics
          </h3>
          <button className="nav-item active" onClick={() => setShowModal(true)} style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', cursor: 'pointer' }}>
            <Plus size={14} /> Add
          </button>
        </div>

        {error && <div className="error-message" style={{ marginBottom: '1rem' }}>{error}</div>}

        {loading ? (
          <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)', fontSize: '0.875rem' }}>Loading schema...</div>
        ) : metrics.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            No metrics defined. Create one using the button above to begin tracking live data!
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {metrics.map((m) => (
              <div
                key={m.id}
                onClick={() => setSelectedMetric(m)}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '0.75rem 1rem',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  backgroundColor: selectedMetric?.id === m.id ? 'rgba(99, 102, 241, 0.1)' : 'transparent',
                  border: '1px solid',
                  borderColor: selectedMetric?.id === m.id ? 'var(--primary)' : 'var(--border)',
                  transition: 'all 0.2s'
                }}
              >
                <div style={{ overflow: 'hidden' }}>
                  <div style={{ fontSize: '0.875rem', fontWeight: 500, color: selectedMetric?.id === m.id ? 'var(--text-main)' : 'var(--text-muted)' }}>{m.name}</div>
                  <div style={{ fontSize: '0.7rem', fontFamily: 'monospace', color: 'var(--text-muted)', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap', marginTop: '0.25rem' }}>{m.regexPattern}</div>
                </div>
                <Trash2
                  size={14}
                  style={{ color: 'rgba(239, 68, 68, 0.6)', cursor: 'pointer', flexShrink: 0, marginLeft: '0.5rem' }}
                  onClick={(e) => handleDeleteMetric(m.id, e)}
                  onMouseEnter={(e) => (e.target.style.color = '#ef4444')}
                  onMouseLeave={(e) => (e.target.style.color = 'rgba(239, 68, 68, 0.6)')}
                />
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card" style={{ padding: '2rem', display: 'flex', flexDirection: 'column', border: '1px solid var(--border)' }}>
        {!selectedMetric ? (
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', color: 'var(--text-muted)', minHeight: '350px' }}>
            <BarChart2 size={48} style={{ opacity: 0.3, marginBottom: '1rem' }} />
            <h3>Select a Telemetry Metric</h3>
            <p style={{ fontSize: '0.875rem', marginTop: '0.5rem' }}>Select or create a metric rules key from the sidebar to load telemetry graphs.</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '2rem' }}>
              <div>
                <h2 style={{ fontSize: '1.5rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  {selectedMetric.name}
                  <span style={{ fontSize: '0.8rem', padding: '0.25rem 0.5rem', backgroundColor: 'rgba(255,255,255,0.05)', borderRadius: '4px', border: '1px solid var(--border)', color: 'var(--primary)', fontFamily: 'monospace', fontWeight: 'normal', marginLeft: '0.5rem' }}>
                    Regex: {selectedMetric.regexPattern}
                  </span>
                </h2>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.5rem' }}>
                  Real-time rolling logs matching count bucketed in 1-minute intervals (last 30 minutes). Updates automatically.
                </p>
              </div>
              <button onClick={() => fetchChartData(true)} className="nav-item" style={{ padding: '0.5rem', borderRadius: '6px', cursor: 'pointer' }} disabled={loadingChart}>
                <RefreshCw size={14} className={loadingChart ? 'spin' : ''} />
              </button>
            </div>

            {loadingChart ? (
              <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '280px', color: 'var(--text-muted)' }}>
                Loading telemetry chart data...
              </div>
            ) : chartData.length === 0 ? (
              <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '280px', color: 'var(--text-muted)' }}>
                No telemetry counts recorded yet in the last 30 minutes. Run some events to verify!
              </div>
            ) : (
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>{renderSvgChart()}</div>
            )}
          </div>
        )}
      </div>

      {showModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
          <div className="card" style={{ width: '450px', padding: '2rem', border: '1px solid var(--border)' }}>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '1.5rem' }}>Create Custom Metric Rule</h3>
            {modalError && <div className="error-message" style={{ marginBottom: '1rem' }}>{modalError}</div>}

            <form onSubmit={handleCreateMetric} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>Metric Name</label>
                <input type="text" className="search-input" style={{ width: '100%', padding: '0.75rem' }} placeholder="e.g. Email Sent Count, Exception Rates" value={metricName} onChange={(e) => setMetricName(e.target.value)} required />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>Regex Filter Pattern</label>
                <input type="text" className="search-input" style={{ width: '100%', padding: '0.75rem', fontFamily: 'monospace' }} placeholder="e.g. .*MOCK EMAIL SENT.* or .*Exception.*" value={metricRegex} onChange={(e) => setMetricRegex(e.target.value)} required />
                <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'block', marginTop: '0.25rem' }}>
                  Must be a valid Java regular expression matching the target logs.
                </span>
              </div>

              <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
                <button type="button" className="nav-item" onClick={() => setShowModal(false)} style={{ padding: '0.75rem 1.5rem', cursor: 'pointer' }}>
                  Cancel
                </button>
                <button type="submit" className="nav-item active" style={{ padding: '0.75rem 1.5rem', cursor: 'pointer' }} disabled={submitting}>
                  {submitting ? 'Saving...' : 'Save Rule'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default MetricsDashboard;