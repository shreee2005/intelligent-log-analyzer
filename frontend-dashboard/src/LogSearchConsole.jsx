import { useState, useEffect } from 'react';
import axios from 'axios';
import { Search, Loader2, Calendar, Filter, Terminal, CheckCircle, AlertTriangle, ShieldAlert, FileText, Wrench } from 'lucide-react';
import { parseLogSemanticContext } from './LogSemanticParser';

const LogSearchConsole = ({ projectId }) => {
  const [allLogs, setAllLogs] = useState([]);
  const [filteredLogs, setFilteredLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

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
        const response = await axios.get(`http://localhost:8084/api/v1/search?projectId=${projectId}&query=`);
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

  // Extract unique services for the dropdown
  const uniqueServices = ['ALL', ...new Set(allLogs.map(l => l.serviceId))];

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
        {!loading && filteredLogs.length === 0 && (
          <div className="empty-state">No logs match your filters.</div>
        )}

        {filteredLogs.map(log => {
          const semanticNote = parseLogSemanticContext(log);
          const isError = log.level === 'ERROR';
          const isWarn = log.level === 'WARN';
          const isIgnorable = semanticNote.isIgnorable;

          return (
            <div key={log.id} className={`note-card ${isError ? 'note-error' : isWarn ? 'note-warn' : 'note-info'} ${isIgnorable ? 'note-ignorable' : ''}`}>
              
              <div className="note-header">
                <div className="note-meta">
                  <Calendar size={14} />
                  {new Date(log.timestamp).toLocaleString()}
                  <span className="separator">•</span>
                  <FileText size={14} />
                  <span className="service-name">{log.serviceId}</span>
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
        })}
      </div>
    </div>
  );
};

export default LogSearchConsole;
