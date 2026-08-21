import { useState, useEffect } from 'react';
import axios from 'axios';
import { Search, Loader2, Calendar, Filter, Terminal, CheckCircle, AlertTriangle, ShieldAlert, FileText, Wrench } from 'lucide-react';
import { parseLogSemanticContext } from './LogSemanticParser';

const LogSearchConsole = ({ projectId }) => {
  const [allLogs, setAllLogs] = useState([]);
  const [filteredLogs, setFilteredLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [openTraces, setOpenTraces] = useState({});

  const handleToggle = (traceId, event) => {
    setOpenTraces(prev => ({
      ...prev,
      [traceId]: event.target.open
    }));
  };

  // Filter States
  const [query, setQuery] = useState('');
  const [selectedService, setSelectedService] = useState('ALL');
  const [selectedLevel, setSelectedLevel] = useState('ALL');

  // Fetch all logs initially and set up polling
  useEffect(() => {
    if (!projectId) return;
    
    const fetchLogs = async (isInitial = false) => {
      if (isInitial) setLoading(true);
      try {
        const response = await axios.get(`http://localhost:8084/api/v1/search?projectId=${projectId}&query=&t=${new Date().getTime()}`);
        const sortedLogs = response.data.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
        setAllLogs(sortedLogs);
      } catch (err) {
        console.error('Fetch error', err);
        if (isInitial) setError('Failed to connect to Search Service. Make sure it is running on port 8084.');
      } finally {
        if (isInitial) setLoading(false);
      }
    };
    
    // Initial fetch
    fetchLogs(true);

    // Set up auto-refresh every 5 seconds
    const interval = setInterval(() => {
      fetchLogs(false);
    }, 5000);

    return () => clearInterval(interval);
  }, [projectId]);

  // Apply Filters
  useEffect(() => {
    let result = allLogs;

    if (query) {
      const lowerQuery = query.toLowerCase();
      result = result.filter(log => log.message.toLowerCase().includes(lowerQuery) || log.serviceId.toLowerCase().includes(lowerQuery));
    }

    if (selectedService !== 'ALL') {
      result = result.filter(log => log.serviceId === selectedService);
    }

    if (selectedLevel !== 'ALL') {
      result = result.filter(log => log.level === selectedLevel);
    }

    setFilteredLogs(result);
  }, [query, selectedService, selectedLevel, allLogs]);

  // Group logs by traceId
  const groupLogs = (logs) => {
    const groups = [];
    const traceMap = {};

    logs.forEach(log => {
      if (!log.traceId) {
        // Standalone log
        groups.push({ type: 'standalone', log });
      } else {
        // Grouped log
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
        
        // Update group level (if any child is ERROR, group level is ERROR. If WARN, group level is WARN)
        if (log.level === 'ERROR') {
          traceMap[log.traceId].level = 'ERROR';
        } else if (log.level === 'WARN' && traceMap[log.traceId].level !== 'ERROR') {
          traceMap[log.traceId].level = 'WARN';
        }

        // Use the newest log's timestamp as the group's timestamp
        if (new Date(log.timestamp) > new Date(traceMap[log.traceId].timestamp)) {
          traceMap[log.traceId].timestamp = log.timestamp;
        }
      }
    });

    return groups;
  };

  const groupedLogs = groupLogs(filteredLogs);

  const getGroupPreview = (group) => {
    const errorLog = group.logs.find(l => l.level === 'ERROR');
    const warnLog = group.logs.find(l => l.level === 'WARN');
    const primaryLog = errorLog || warnLog || group.logs[group.logs.length - 1];
    
    if (!primaryLog) return '';
    
    let cleanMsg = primaryLog.message.replace(/[\r\n\s]+/g, ' ').trim();
    cleanMsg = cleanMsg.replace(/[=]{3,}/g, '').trim();
    
    if (cleanMsg.length > 90) {
      return cleanMsg.substring(0, 90) + '...';
    }
    return cleanMsg;
  };

  // Extract unique services for the dropdown
  const uniqueServices = ['ALL', ...new Set(allLogs.map(l => l.serviceId))];

  const renderLogCard = (log, isSubCard = false) => {
    const semanticNote = parseLogSemanticContext(log);
    const isError = log.level === 'ERROR';
    const isWarn = log.level === 'WARN';
    const isIgnorable = semanticNote.isIgnorable;

    return (
      <div key={log.id} className={`note-card ${isError ? 'note-error' : isWarn ? 'note-warn' : 'note-info'} ${isIgnorable ? 'note-ignorable' : ''} ${isSubCard ? 'sub-note-card' : ''}`}>
        
        <div className="note-header">
          <div className="note-meta">
            <Calendar size={14} />
            {new Date(log.timestamp).toLocaleString()}
            <span className="separator">•</span>
            <FileText size={14} />
            <span className="service-name">{log.serviceId}</span>
            {log.spanId && (
              <>
                <span className="separator">•</span>
                <span style={{ fontFamily: 'monospace', opacity: 0.6 }}>span: {log.spanId}</span>
              </>
            )}
          </div>
          <div className={`badge ${isError ? 'error' : isWarn ? 'warn' : 'info'}`}>
            {log.level}
          </div>
        </div>

        {isIgnorable && <div className="ignorable-badge">Ignorable Warning</div>}

        {/* Context Summary */}
        <div className="note-summary">
          {semanticNote.action && (
            <div className="context-pill"><strong>Action:</strong> {semanticNote.action}</div>
          )}
          {semanticNote.user && (
            <div className="context-pill"><strong>User:</strong> {semanticNote.user}</div>
          )}
        </div>

        <div className="note-message">
          {semanticNote.extractedMessage}
        </div>

        {/* AI Suggestions Engine */}
        {(isError || isWarn) && !isIgnorable && semanticNote.suggestedFix && (
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

        {/* Collapsed Raw Stack Trace */}
        {semanticNote.hasStackTrace && (
          <details className="raw-log-details">
            <summary>View Raw Stack Trace</summary>
            <pre className="raw-log-block">{log.message}</pre>
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

      {/* Advanced Filters */}
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
            {uniqueServices.map(svc => <option key={svc} value={svc}>{svc === 'ALL' ? 'All Services' : svc}</option>)}
          </select>

          <select value={selectedLevel} onChange={(e) => setSelectedLevel(e.target.value)} className="filter-select">
            <option value="ALL">All Levels</option>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
          </select>
        </div>
      </div>

      {error && <p className="error-message">{error}</p>}
      {loading && <div className="loader"><Loader2 className="spin" size={24}/> Fetching logs...</div>}

      {/* Smart Notes Feed */}
      <div className="notes-feed">
        {!loading && groupedLogs.length === 0 && (
          <div className="empty-state">No logs match your filters.</div>
        )}

        {groupedLogs.map(item => {
          if (item.type === 'standalone') {
            return renderLogCard(item.log);
          } else {
            const isOpen = openTraces[item.traceId] || false;
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
                  onClick={() => setOpenTraces(prev => ({ ...prev, [item.traceId]: !isOpen }))}
                  style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', width: '100%', gap: '0.5rem' }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
                    <div className="note-meta" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <Terminal size={14} style={{ color: 'var(--accent)' }} />
                      <span style={{ fontWeight: '600', color: 'var(--accent)', fontFamily: 'monospace' }}>
                        <span style={{ marginRight: '0.5rem', display: 'inline-block', transition: 'transform 0.15s', transform: isOpen ? 'rotate(90deg)' : 'none' }}>▶</span>
                        Transaction [Trace ID: {String(item.traceId).substring(0, 8)}...]
                      </span>
                      <span className="trace-group-badge-count">{item.logs.length} events</span>
                    </div>
                    <div className="note-meta">
                      <Calendar size={14} />
                      {new Date(item.timestamp).toLocaleString()}
                      <span className={`badge ${isError ? 'error' : isWarn ? 'warn' : 'info'}`}>
                        {item.level}
                      </span>
                    </div>
                  </div>
                  {/* One-line preview insight */}
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', paddingLeft: '1.5rem', fontFamily: 'monospace', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap', width: '100%', borderLeft: '2px solid rgba(255, 255, 255, 0.05)' }}>
                    {getGroupPreview(item)}
                  </div>
                </div>
                {isOpen && (
                  <div className="trace-group-body" style={{ marginTop: '1rem', borderTop: '1px solid var(--border)', paddingTop: '1rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    {item.logs.map(subLog => renderLogCard(subLog, true))}
                  </div>
                )}
              </div>
            );
          }
        })}
      </div>
    </div>
  );
};

export default LogSearchConsole;
