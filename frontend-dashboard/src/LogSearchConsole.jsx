import { useState } from 'react';
import axios from 'axios';
import { Search, Loader2 } from 'lucide-react';

const LogSearchConsole = () => {
  const [query, setQuery] = useState('');
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!query.trim()) return;

    setLoading(true);
    setError(null);
    try {
      const response = await axios.get(`http://localhost:8084/api/v1/search?query=${encodeURIComponent(query)}`);
      // Reversing so newest might be at the top if the array preserves insertion order, 
      // but ideally we sort by timestamp.
      const sortedLogs = response.data.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
      setLogs(sortedLogs);
    } catch (err) {
      console.error('Search error', err);
      setError('Search failed. Is the search-service running?');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <div className="card-title">
        <Search size={18} />
        Log Search Console
      </div>

      <form onSubmit={handleSearch} className="search-bar">
        <input 
          type="text"
          className="search-input"
          placeholder="Search for 'MELTDOWN', 'Database', or 'auth-service'..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button type="submit" className="btn" disabled={loading}>
          {loading ? <Loader2 size={16} className="spin" /> : <Search size={16} />}
          Search
        </button>
      </form>

      {error && <p style={{ color: 'var(--error)', marginBottom: '1rem', fontSize: '0.875rem' }}>{error}</p>}

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Service</th>
              <th>Level</th>
              <th>Message</th>
            </tr>
          </thead>
          <tbody>
            {logs.length === 0 && !loading && (
              <tr>
                <td colSpan="4" style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  No logs found. Try searching.
                </td>
              </tr>
            )}
            {logs.map((log) => (
              <tr key={log.id}>
                <td style={{ color: 'var(--text-muted)' }}>
                  {new Date(log.timestamp).toLocaleString()}
                </td>
                <td>{log.serviceId}</td>
                <td>
                  <span className={`badge ${log.level === 'ERROR' ? 'error' : 'info'}`}>
                    {log.level}
                  </span>
                </td>
                <td style={{ fontFamily: 'monospace' }}>{log.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default LogSearchConsole;
