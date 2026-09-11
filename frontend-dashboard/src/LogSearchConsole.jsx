import { useState, useEffect } from 'react';
import axios from 'axios';
import { Search, Loader2, Calendar, Terminal, FileText, Wrench } from 'lucide-react';
import { parseLogSemanticContext } from './LogSemanticParser';
import { API_BASE_URL } from './config';

const LogSearchConsole = ({ projectId, token }) => {
  const [allLogs, setAllLogs] = useState([]);
  const [filteredLogs, setFilteredLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [openTraces, setOpenTraces] = useState({});

  const [query, setQuery] = useState('');
  const [selectedService, setSelectedService] = useState('ALL');
  const [selectedLevel, setSelectedLevel] = useState('ALL');

    useEffect(() => {
    if (!projectId) return;

    const fetchData = async (isInitial = false) => {
      if (isInitial) setLoading(true);
      try {
        // Fetch logs
        const headers = { Authorization: `Bearer ${token}` };
        const response = await axios.get(`${API_BASE_URL}/api/v1/search?projectId=${projectId}&query=&t=${new Date().getTime()}`, { headers });
        const payload = response.data;
        const logs = Array.isArray(payload) ? payload : Array.isArray(payload?.logs) ? payload.logs : [];

        // Fetch anomalies
        let anomalyLogs = [];
        try {
            const anomalyResponse = await axios.get(`${API_BASE_URL}/api/projects/${projectId}/anomalies`, { headers });
            const parsedAnomalies = anomalyResponse.data.map(str => JSON.parse(str));
            anomalyLogs = parsedAnomalies.map(a => {
               // Java Instant serializes to epoch seconds (double) by default, convert to milliseconds for JS Date
               const tsMs = typeof a.timestamp === 'number' ? a.timestamp * 1000 : a.timestamp;
               const zVal = a.zScore !== undefined ? a.zScore : a.zscore;
               
               return {
                 id: 'anomaly-' + a.timestamp + '-' + a.serviceId,
                 timestamp: tsMs,
                 serviceId: a.serviceId,
                 level: 'ANOMALY',
                 message: `ML System Insight: ${a.type === 'SPIKE' ? 'Massive Spike' : 'Silent Failure (Drop)'} detected in log volume. Z-Score: ${Number(zVal || 0).toFixed(2)}. (Volume: ${a.currentVolume}, Normal: ~${Number(a.meanVolume || 0).toFixed(0)})`,
                 isAnomaly: true,
                 anomalyData: a
               };
            });
        } catch (anomalyErr) {
            console.warn('Could not fetch anomalies:', anomalyErr);
        }

        const combinedLogs = [...logs, ...anomalyLogs];
        const sortedLogs = combinedLogs.sort((a, b) => new Date(b?.timestamp || 0) - new Date(a?.timestamp || 0));
        setAllLogs(sortedLogs);
      } catch (err) {
        console.error('Fetch error', err);
        if (isInitial) setError('Failed to connect to Search Service. Make sure it is running on port 8084.');
      } finally {
        if (isInitial) setLoading(false);
      }
    };

    fetchData(true);
    const interval = setInterval(() => fetchData(false), 5000);
    return () => clearInterval(interval);
  }, [projectId, token]);

  useEffect(() => {
    let result = Array.isArray(allLogs) ? allLogs : [];

    if (query) {
      const lowerQuery = query.toLowerCase();
      result = result.filter((log) => {
        const msg = String(log?.message ?? '').toLowerCase();
        const svc = String(log?.serviceId ?? '').toLowerCase();
        return msg.includes(lowerQuery) || svc.includes(lowerQuery);
      });
    }

    if (selectedService !== 'ALL') {
      result = result.filter((log) => log?.serviceId === selectedService);
    }

    if (selectedLevel !== 'ALL') {
      result = result.filter((log) => log?.level === selectedLevel);
    }

    setFilteredLogs(result);
  }, [query, selectedService, selectedLevel, allLogs]);

  const groupLogs = (logs) => {
    const safeLogs = Array.isArray(logs) ? logs : [];
    const groups = [];
    const traceMap = {};

    safeLogs.forEach((log) => {
      if (!log?.traceId) {
        groups.push({ type: 'standalone', log });
      } else {
        if (!traceMap[log.traceId]) {
          traceMap[log.traceId] = {
            type: 'group',
            traceId: log.traceId,
            logs: [],
            timestamp: log.timestamp,
            serviceId: log.serviceId,
            level: log.level
          };
          groups.push(traceMap[log.traceId]);
        }

        traceMap[log.traceId].logs.push(log);

        if (log.level === 'ERROR') {
          traceMap[log.traceId].level = 'ERROR';
        } else if (log.level === 'WARN' && traceMap[log.traceId].level !== 'ERROR') {
          traceMap[log.traceId].level = 'WARN';
        }

        if (new Date(log.timestamp) > new Date(traceMap[log.traceId].timestamp)) {
          traceMap[log.traceId].timestamp = log.timestamp;
        }
      }
    });

    return groups;
  };

  const groupedLogs = groupLogs(filteredLogs);

  const getGroupPreview = (group) => {
    const safeGroupLogs = Array.isArray(group?.logs) ? group.logs : [];
    const errorLog = safeGroupLogs.find((l) => l?.level === 'ERROR');
    const warnLog = safeGroupLogs.find((l) => l?.level === 'WARN');
    const primaryLog = errorLog || warnLog || safeGroupLogs[safeGroupLogs.length - 1];
    if (!primaryLog) return '';

    let cleanMsg = String(primaryLog?.message ?? '').replace(/[\r\n\s]+/g, ' ').trim();
    cleanMsg = cleanMsg.replace(/[=]{3,}/g, '').trim();

    return cleanMsg.length > 90 ? `${cleanMsg.substring(0, 90)}...` : cleanMsg;
  };

  const uniqueServices = ['ALL', ...new Set((Array.isArray(allLogs) ? allLogs : []).map((l) => l?.serviceId).filter(Boolean))];

  const renderLogCard = (log, isSubCard = false) => {
    const semanticNote = parseLogSemanticContext(log || {});
    const isError = log?.level === 'ERROR';
    const isWarn = log?.level === 'WARN';
    const isAnomaly = log?.isAnomaly;
    const isIgnorable = semanticNote?.isIgnorable;

    const getCardClass = () => {
        if (isAnomaly) return 'note-anomaly';
        if (isError) return 'note-error';
        if (isWarn) return 'note-warn';
        return 'note-info';
    };

    return (
      <div key={log?.id || `${log?.timestamp}-${log?.traceId || 'standalone'}`} className={`note-card ${getCardClass()} ${isIgnorable ? 'note-ignorable' : ''} ${isSubCard ? 'sub-note-card' : ''}`}>
        <div className="note-header">
          <div className="note-meta">
            <Calendar size={14} />
            {log?.timestamp ? new Date(log.timestamp).toLocaleString() : 'N/A'}
            <span className="separator">•</span>
            <FileText size={14} />
            <span className="service-name">{log?.serviceId || 'Unknown Service'}</span>
            {log?.spanId && (
              <>
                <span className="separator">•</span>
                <span style={{ fontFamily: 'monospace', opacity: 0.6 }}>span: {log.spanId}</span>
              </>
            )}
          </div>
          <div className={`badge ${isAnomaly ? 'anomaly' : isError ? 'error' : isWarn ? 'warn' : 'info'}`}>
            {isAnomaly ? '🚨 ' + log?.level : log?.level || 'INFO'}
          </div>
        </div>

        {isIgnorable && <div className="ignorable-badge">Ignorable Warning</div>}

        <div className="note-summary">
          {semanticNote?.action && <div className="context-pill"><strong>Action:</strong> {semanticNote.action}</div>}
          {semanticNote?.user && <div className="context-pill"><strong>User:</strong> {semanticNote.user}</div>}
        </div>

        <div className="note-message">
          {semanticNote?.extractedMessage || String(log?.message ?? '')}
        </div>

        {isAnomaly && log?.anomalyData && (() => {
          const a = log.anomalyData;
          const zVal = a.zScore !== undefined ? a.zScore : a.zscore;
          const isDrop = a.type === 'DROP';
          
          const rootCause = isDrop
            ? `Service "${a.serviceId}" has completely stopped emitting logs. The expected baseline is ~${Number(a.meanVolume || 0).toFixed(0)} logs/min, but the current volume dropped to ${a.currentVolume}. This indicates the service may have crashed, lost network connectivity, or its container was terminated unexpectedly.`
            : `Service "${a.serviceId}" is emitting an abnormally high volume of logs. The expected baseline is ~${Number(a.meanVolume || 0).toFixed(0)} logs/min, but the current volume spiked to ${a.currentVolume}. This could indicate an infinite retry loop, a cascading failure, a DDoS attack, or a severe bug generating excessive logging.`;

          const suggestedFix = isDrop
            ? `1. Check if the container/pod is still running:\n   docker ps | grep ${a.serviceId}\n   kubectl get pods | grep ${a.serviceId}\n\n2. Check recent container logs for crash reason:\n   docker logs --tail 50 ${a.serviceId}\n\n3. If the container exited, restart it:\n   docker compose up -d ${a.serviceId}\n\n4. Verify network connectivity between services.\n\n5. Check health endpoint: curl http://${a.serviceId}:PORT/actuator/health`
            : `1. Check for infinite retry loops or recursive calls in ${a.serviceId}.\n\n2. Look for cascading errors in downstream dependencies.\n\n3. Check CPU/Memory usage:\n   docker stats ${a.serviceId}\n\n4. Consider enabling rate-limiting on the logging framework.\n\n5. If under attack, enable WAF rules or IP blocking.\n\n6. Restart the service if it's stuck:\n   docker compose restart ${a.serviceId}`;

          return (
            <div className="ai-suggestion-box" style={{ borderColor: 'rgba(239, 68, 68, 0.3)', backgroundColor: 'rgba(239, 68, 68, 0.05)' }}>
              <div className="ai-header" style={{ color: '#f87171' }}>
                <Wrench size={14} /> ML Anomaly Diagnostics
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>
                <strong>Severity:</strong> Z-Score {Number(zVal || 0).toFixed(2)} ({isDrop ? 'Volume dropped to near-zero' : 'Volume spiked massively'} — threshold is ±3.0)
              </div>
              <div className="ai-cause"><strong>Root Cause:</strong> {rootCause}</div>
              <div className="ai-fix">
                <strong>Suggested Fix:</strong>
                <pre>{suggestedFix}</pre>
              </div>
            </div>
          );
        })()}

        {(isError || isWarn) && !isIgnorable && semanticNote?.suggestedFix && (
          <div className="ai-suggestion-box">
            <div className="ai-header">
              <Wrench size={14} /> AI Diagnostics
            </div>
            <div className="ai-cause"><strong>Root Cause:</strong> {semanticNote.rootCause}</div>
            <div className="ai-fix">
              <strong>Suggested Fix:</strong>
              <pre>{semanticNote.suggestedFix}</pre>
            </div>
          </div>
        )}

        {semanticNote?.hasStackTrace && (
          <details className="raw-log-details">
            <summary>View Raw Stack Trace</summary>
            <pre className="raw-log-block">{String(log?.message ?? '')}</pre>
          </details>
        )}
      </div>
    );
  };

  return (
    <div className="card console-card">
      <div className="card-title">
        <Terminal size={18} />
        Smart Log Reader
      </div>

      <div className="filters-container">
        <div className="search-bar-wrapper">
          <Search size={16} className="search-icon" />
          <input
            type="text"
            className="search-input"
            placeholder="Search raw text, filenames, or trace IDs..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        <div className="dropdowns">
          <select value={selectedService} onChange={(e) => setSelectedService(e.target.value)} className="filter-select">
            {uniqueServices.map((svc) => (
              <option key={svc} value={svc}>
                {svc === 'ALL' ? 'All Services' : svc}
              </option>
            ))}
          </select>

          <select value={selectedLevel} onChange={(e) => setSelectedLevel(e.target.value)} className="filter-select">
            <option value="ALL">All Levels</option>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
            <option value="ANOMALY">ANOMALY</option>
          </select>
        </div>
      </div>

      {error && <p className="error-message">{error}</p>}
      {loading && <div className="loader"><Loader2 className="spin" size={24} /> Fetching logs...</div>}

      <div className="notes-feed">
        {!loading && groupedLogs.length === 0 && (
          <div className="empty-state">No logs match your filters.</div>
        )}

        {groupedLogs.map((item) => {
          if (item.type === 'standalone') {
            return renderLogCard(item.log);
          }

          const isOpen = !!openTraces[item.traceId];
          const isError = item.level === 'ERROR';
          const isWarn = item.level === 'WARN';

          return (
            <div
              key={item.traceId}
              className={`note-card ${isError ? 'note-error' : isWarn ? 'note-warn' : 'note-info'}`}
              style={{ cursor: 'pointer', padding: '1rem', borderLeftWidth: '4px' }}
            >
              <div
                className="note-header"
                onClick={() => setOpenTraces((prev) => ({ ...prev, [item.traceId]: !isOpen }))}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', width: '100%', gap: '0.5rem' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
                  <div className="note-meta" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Terminal size={14} style={{ color: 'var(--accent)' }} />
                    <span style={{ fontWeight: '600', color: 'var(--accent)', fontFamily: 'monospace' }}>
                      <span style={{ marginRight: '0.5rem', display: 'inline-block', transition: 'transform 0.15s', transform: isOpen ? 'rotate(90deg)' : 'none' }}>▶</span>
                      Transaction [Trace ID: {String(item.traceId).substring(0, 8)}...]
                    </span>
                    <span className="trace-group-badge-count">{Array.isArray(item.logs) ? item.logs.length : 0} events</span>
                  </div>
                  <div className="note-meta">
                    <Calendar size={14} />
                    {item?.timestamp ? new Date(item.timestamp).toLocaleString() : 'N/A'}
                    <span className={`badge ${isError ? 'error' : isWarn ? 'warn' : 'info'}`}>{item.level}</span>
                  </div>
                </div>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', paddingLeft: '1.5rem', fontFamily: 'monospace', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap', width: '100%', borderLeft: '2px solid rgba(255, 255, 255, 0.05)' }}>
                  {getGroupPreview(item)}
                </div>
              </div>

              {isOpen && (
                <div className="trace-group-body" style={{ marginTop: '1rem', borderTop: '1px solid var(--border)', paddingTop: '1rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  {(Array.isArray(item.logs) ? item.logs : []).map((subLog, idx) => (
                    <div key={subLog?.id || `${item.traceId}-${idx}`}>
                      {renderLogCard(subLog, true)}
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default LogSearchConsole;