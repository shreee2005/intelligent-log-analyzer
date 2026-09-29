import { useState } from 'react';
import { Terminal, Code, Server, Copy, Check, AlertCircle, ExternalLink } from 'lucide-react';
import { API_BASE_URL } from './config';

const IntegrationHub = ({ project }) => {
  const [activeTab, setActiveTab] = useState('java');
  const [copied, setCopied] = useState(false);

  const apiKey = project ? project.apiKey : null;
  const hasApiKey = !!apiKey;

  const handleCopy = (code) => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getSnippetCode = (template) => {
    if (!hasApiKey) {
      return template.replace(/\$\{apiKey\}/g, 'YOUR_API_KEY_HERE_ROTATE_IN_SETTINGS');
    }
    return template.replace(/\$\{apiKey\}/g, apiKey);
  };

  const javaTemplate = `<!-- src/main/resources/logback-spring.xml -->
<configuration>
    <!-- Standard Console Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Intelligent Log Analyzer HTTP Appender -->
    <appender name="HTTP" class="com.loganalyzer.client.HttpAppender">
        <url>${API_BASE_URL}/api/v1/logs</url>
        <serviceId>\${spring.application.name}</serviceId>
        <apiKey>${apiKey || 'YOUR_API_KEY_HERE_ROTATE_IN_SETTINGS'}</apiKey>
        <format>JSON</format>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <appender-ref ref="HTTP" />
    </root>
</configuration>`;

  const nodeTemplate = `// logger.js
const winston = require('winston');
const axios = require('axios');

class IntelligentLogTransport extends winston.Transport {
  constructor(opts) {
    super(opts);
    this.serviceId = opts.serviceId || 'node-service';
  }

  log(info, callback) {
    setImmediate(() => {
      this.emit('logged', info);
    });

    axios.post('${API_BASE_URL}/api/v1/logs', {
      serviceId: this.serviceId,
      level: info.level.toUpperCase(),
      format: 'JSON',
      message: info.message,
      timestamp: new Date().toISOString()
    }, {
      headers: { 'X-API-KEY': '${apiKey || 'YOUR_API_KEY_HERE_ROTATE_IN_SETTINGS'}' }
    }).catch(err => console.error("Log forwarding failed"));

    callback();
  }
}

const logger = winston.createLogger({
  level: 'info',
  transports: [
    new winston.transports.Console(),
    new IntelligentLogTransport({ serviceId: 'my-express-app' })
  ]
});

module.exports = logger;`;

  const powershellTemplate = `Invoke-RestMethod -Uri "${API_BASE_URL}/api/v1/logs" \`
  -Method Post \`
  -Headers @{ "X-API-KEY" = "${apiKey || 'YOUR_API_KEY_HERE_ROTATE_IN_SETTINGS'}" } \`
  -ContentType "application/json" \`
  -Body '{
    "serviceId": "auth-service", 
    "level": "ERROR", 
    "format": "JSON", 
    "message": "Database connection refused", 
    "timestamp": "2026-08-16T10:00:00Z"
  }'`;

  const snippets = {
    java: {
      title: "Java (Spring Boot)",
      icon: <Server size={18} />,
      description: "Connect your Spring Boot application using our Logback HTTP Appender. This will automatically intercept all standard logger.info() and logger.error() calls, as well as uncaught global exceptions, and stream them directly to the Intelligent Log Analyzer.",
      code: javaTemplate
    },
    node: {
      title: "Node.js (Winston)",
      icon: <Code size={18} />,
      description: "Use our custom Winston transport to automatically stream logs from your Express.js or Node applications. It handles global unhandled rejections automatically.",
      code: nodeTemplate
    },
    powershell: {
      title: "PowerShell (Manual Test)",
      icon: <Terminal size={18} />,
      description: "Use this simple script to manually push logs into the system for testing alerts and dashboards without running a full application.",
      code: powershellTemplate
    }
  };

  const activeSnippet = snippets[activeTab];

  return (
    <div className="card integration-card">
      {!hasApiKey && (
        <div style={{ 
          padding: '1rem', 
          backgroundColor: 'rgba(239, 68, 68, 0.1)', 
          border: '1px solid var(--error)', 
          borderRadius: '8px', 
          marginBottom: '1rem',
          display: 'flex', 
          alignItems: 'center', 
          gap: '0.75rem',
          color: 'var(--error)'
        }}>
          <AlertCircle size={20} />
          <div>
            <strong>No API Key Found</strong>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.875rem' }}>
              API keys are only shown once at creation for security. 
              Click the <strong>Settings</strong> tab and use <strong>Rotate API Key</strong> to generate a new one.
            </p>
          </div>
        </div>
      )}

      <div className="integration-layout">
        
        {/* Sidebar */}
        <div className="integration-sidebar">
          <h3 className="sidebar-title">Connect your App</h3>
          <ul className="sidebar-menu">
            {Object.keys(snippets).map(key => (
              <li 
                key={key} 
                className={activeTab === key ? 'active' : ''}
                onClick={() => setActiveTab(key)}
              >
                {snippets[key].icon}
                {snippets[key].title}
              </li>
            ))}
          </ul>
        </div>

        {/* Main Content */}
        <div className="integration-content">
          <h2>{activeSnippet.title} Integration</h2>
          <p className="integration-desc">{activeSnippet.description}</p>
          
          <div className="code-block-container">
            <div className="code-header">
              <span className="code-lang">{activeTab.toUpperCase()}</span>
              <button className="btn-copy" onClick={() => handleCopy(activeSnippet.code)}>
                {copied ? <><Check size={14}/> Copied</> : <><Copy size={14}/> Copy Snippet</>}
              </button>
            </div>
            <pre className="code-block">
              <code>{activeSnippet.code}</code>
            </pre>
          </div>
        </div>

      </div>
    </div>
  );
};

export default IntegrationHub;
