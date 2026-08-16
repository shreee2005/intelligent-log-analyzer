import React from 'react';
import LiveMetricsPanel from './LiveMetricsPanel';
import LogSearchConsole from './LogSearchConsole';
import { Activity } from 'lucide-react';

function App() {
  return (
    <div className="app-container">
      <header className="header">
        <h1>Intelligent Log Analyzer</h1>
        <div className="header-status">
          <div className="status-dot"></div>
          System Operational
        </div>
      </header>

      <div className="dashboard-grid">
        <LiveMetricsPanel />
        <LogSearchConsole />
      </div>
    </div>
  );
}

export default App;
