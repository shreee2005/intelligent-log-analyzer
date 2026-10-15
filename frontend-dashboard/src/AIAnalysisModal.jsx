import { useState, useEffect, useRef } from 'react';
import { X, Loader2, CheckCircle, AlertCircle, FileText, Terminal, Zap, Lightbulb, Shield, Database, Network, Server, ArrowRight, Copy, Download, ChevronLeft, ChevronRight } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { API_BASE_URL } from './config';

const AIAnalysisModal = ({ 
  isOpen, 
  onClose, 
  anomaly, 
  projectId, 
  token 
}) => {
  const [suggestion, setSuggestion] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('analysis');
  const tabsRef = useRef({});

  const tabs = ['analysis', 'evidence', 'actions'];

  useEffect(() => {
    if (isOpen && anomaly) {
      fetchAnalysis();
    } else {
      setSuggestion(null);
      setError(null);
    }
  }, [isOpen, anomaly]);

  // Keyboard navigation for tabs
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (!isOpen) return;
      const currentIndex = tabs.indexOf(activeTab);
      if (e.key === 'ArrowRight' && currentIndex < tabs.length - 1) {
        setActiveTab(tabs[currentIndex + 1]);
        tabsRef.current[tabs[currentIndex + 1]]?.focus();
      } else if (e.key === 'ArrowLeft' && currentIndex > 0) {
        setActiveTab(tabs[currentIndex - 1]);
        tabsRef.current[tabs[currentIndex - 1]]?.focus();
      } else if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, activeTab]);

  const fetchAnalysis = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/suggestions/analyze`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          projectId: anomaly.projectId,
          anomalyId: anomaly.anomalyId || anomaly.eventId,
          serviceId: anomaly.serviceId,
          traceId: anomaly.traceId
        })
      });

      const data = await response.json();
      
      if (!response.ok) {
        throw new Error(data.message || data.error || 'Analysis failed');
      }

      // Poll for completion if async
      if (data.status === 'PROCESSING' || data.status === 'QUEUED') {
        await pollForResult(data.suggestionId);
      } else {
        setSuggestion(data);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const pollForResult = async (suggestionId) => {
    const maxAttempts = 30;
    for (let i = 0; i < maxAttempts; i++) {
      await new Promise(resolve => setTimeout(resolve, 2000));
      try {
        const response = await fetch(`${API_BASE_URL}/api/v1/suggestions/${suggestionId}`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await response.json();
        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          setSuggestion(data);
          return;
        }
      } catch (err) {
        console.warn('Polling error:', err);
      }
    }
    setError('Analysis timed out');
  };

  const getConfidenceColor = (confidence) => {
    if (confidence >= 0.8) return 'var(--success)';
    if (confidence >= 0.5) return 'var(--warning)';
    return 'var(--error)';
  };

  const formatTimestamp = (ts) => {
    if (!ts) return 'N/A';
    try {
      return new Date(ts).toLocaleString();
    } catch {
      return ts;
    }
  };

  // Export functions
  const exportAsMarkdown = () => {
    if (!suggestion) return;
    
    let md = `# AI Root Cause Analysis\n\n`;
    md += `**Anomaly ID:** ${suggestion.anomalyId}\n`;
    md += `**Service:** ${suggestion.serviceId}\n`;
    md += `**Project ID:** ${suggestion.projectId}\n`;
    md += `**Model:** ${suggestion.model} (${suggestion.modelVersion})\n`;
    md += `**Confidence:** ${(suggestion.confidence * 100).toFixed(0)}%\n`;
    md += `**Latency:** ${suggestion.latencyMs}ms\n`;
    md += `**Tokens:** ${suggestion.totalTokens} (est. $${suggestion.estimatedCostUsd?.toFixed(4)})\n\n`;

    md += `## Hypotheses\n`;
    suggestion.hypotheses?.forEach((h, i) => {
      md += `${i + 1}. ${h}\n`;
    });
    md += `\n`;

    if (suggestion.uncertainty?.length > 0) {
      md += `## Uncertainties\n`;
      suggestion.uncertainty.forEach(u => { md += `- ${u}\n`; });
      md += `\n`;
    }

    md += `## Verification Steps\n`;
    suggestion.verificationSteps?.forEach((step, i) => {
      md += `${i + 1}. ${step}\n`;
    });
    md += `\n`;

    md += `## Recommended Actions\n`;
    suggestion.recommendedActions?.forEach((action, i) => {
      md += `${i + 1}. ${action}\n`;
    });
    md += `\n`;

    if (suggestion.ragSources?.length > 0) {
      md += `## Similar Historical Incidents\n`;
      suggestion.ragSources.forEach(r => {
        md += `- **${r.title}** (${(r.similarity * 100).toFixed(0)}% match) - ${r.incidentId}\n`;
      });
    }

    downloadFile(md, `analysis-${suggestion.anomalyId}.md`, 'text/markdown');
  };

  const exportAsJSON = () => {
    if (!suggestion) return;
    const json = JSON.stringify(suggestion, null, 2);
    downloadFile(json, `analysis-${suggestion.anomalyId}.json`, 'application/json');
  };

  const downloadFile = (content, filename, mimeType) => {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const copyToClipboard = (text, label = 'Copied!') => {
    navigator.clipboard.writeText(text);
    // Could add toast notification here
  };

  // Custom renderers for ReactMarkdown
  const markdownRenderers = {
    code({ node, ...props }) {
      const code = node.literal || '';
      const language = node.lang || '';
      return (
        <div className="markdown-code-block">
          {language && <div className="code-language">{language}</div>}
          <pre><code className={`language-${language}`}>{code}</code></pre>
          <button 
            className="copy-code-btn"
            onClick={() => copyToClipboard(code)}
            title="Copy to clipboard"
          >
            <Copy size={12} />
          </button>
        </div>
      );
    },
    listItem({ children, ...props }) {
      return <li className="markdown-list-item">{children}</li>;
    },
    paragraph({ children, ...props }) {
      return <p className="markdown-paragraph">{children}</p>;
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-container" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Lightbulb size={20} style={{ color: 'var(--accent)' }} />
            AI Root Cause Analysis
          </h2>
          <button className="modal-close" onClick={onClose} aria-label="Close modal">
            <X size={20} />
          </button>
        </div>

        {error && (
          <div className="modal-error">
            <AlertCircle size={16} /> {error}
          </div>
        )}

        <div className="modal-tabs" role="tablist" aria-label="Analysis tabs">
          {tabs.map((tab, index) => (
            <button 
              key={tab}
              ref={(el) => { tabsRef.current[tab] = el; }}
              className={`modal-tab ${activeTab === tab ? 'active' : ''}`}
              onClick={() => setActiveTab(tab)}
              disabled={!suggestion && tab !== 'analysis'}
              role="tab"
              aria-selected={activeTab === tab}
              aria-controls={`${tab}-panel`}
              id={`${tab}-tab`}
            >
              {tab === 'analysis' && <Lightbulb size={14} />}
              {tab === 'evidence' && <FileText size={14} />}
              {tab === 'actions' && <Zap size={14} />}
              {tab.charAt(0).toUpperCase() + tab.slice(1)}
            </button>
          ))}
        </div>

        <div className="modal-content">
          {loading && (
            <div className="loading-state">
              <Loader2 className="spin" size={32} />
              <p>Analyzing anomaly with AI...</p>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                Fetching context, searching historical incidents, generating analysis...
              </p>
            </div>
          )}

          {!loading && error && (
            <div className="error-state">
              <AlertCircle size={32} style={{ color: 'var(--error)' }} />
              <p>Analysis failed: {error}</p>
              <button className="retry-btn" onClick={fetchAnalysis}>
                Retry Analysis
              </button>
            </div>
          )}

          {!loading && !error && suggestion && activeTab === 'analysis' && (
            <div className="analysis-tab" id="analysis-panel" role="tabpanel" aria-labelledby="analysis-tab">
              <div className="analysis-header">
                <div className="confidence-badge" style={{ borderColor: getConfidenceColor(suggestion.confidence) }}>
                  <span style={{ color: getConfidenceColor(suggestion.confidence) }}>
                    Confidence: {(suggestion.confidence * 100).toFixed(0)}%
                  </span>
                </div>
                <div className="meta-info">
                  <span><strong>Model:</strong> {suggestion.model} ({suggestion.modelVersion})</span>
                  <span><strong>Latency:</strong> {suggestion.latencyMs}ms</span>
                  <span><strong>Tokens:</strong> {suggestion.totalTokens} (${suggestion.estimatedCostUsd?.toFixed(4)})</span>
                </div>
              </div>

              {suggestion.hypotheses?.length > 0 && (
                <div className="section">
                  <h3><Lightbulb size={16} /> Root Cause Hypotheses</h3>
                  <ul className="hypotheses-list">
                    {suggestion.hypotheses.map((h, i) => (
                      <li key={i}>
                        <span className="hypothesis-number">{i + 1}</span>
                        {h}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {suggestion.uncertainty?.length > 0 && (
                <div className="section uncertainty">
                  <h3><AlertCircle size={16} style={{ color: 'var(--warning)' }} /> Uncertainties</h3>
                  <ul>
                    {suggestion.uncertainty.map((u, i) => (
                      <li key={i}>{u}</li>
                    ))}
                  </ul>
                </div>
              )}

              {suggestion.ragSources?.length > 0 && (
                <div className="section">
                  <h3><Database size={16} /> Similar Historical Incidents</h3>
                  <div className="rag-sources">
                    {suggestion.ragSources.map((r, i) => (
                      <div key={i} className="rag-source-card">
                        <div className="rag-source-header">
                          <span className="rag-source-title">{r.title}</span>
                          <span className="rag-source-similarity" style={{ color: getConfidenceColor(r.similarity) }}>
                            {(r.similarity * 100).toFixed(0)}% match
                          </span>
                        </div>
                        <span className="rag-source-id">{r.incidentId}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {!loading && !error && suggestion && activeTab === 'evidence' && (
            <div className="evidence-tab" id="evidence-panel" role="tabpanel" aria-labelledby="evidence-tab">
              {suggestion.evidenceLogIds?.length > 0 ? (
                <div>
                  <h3><FileText size={16} /> Supporting Log Entries</h3>
                  <p className="evidence-note">These log entries from the anomaly context support the hypotheses above.</p>
                  <ul className="evidence-list">
                    {suggestion.evidenceLogIds.map((id, i) => (
                      <li key={i} className="evidence-item">
                        <Terminal size={14} /> 
                        <code>{id}</code>
                        <button 
                          className="copy-btn"
                          onClick={() => copyToClipboard(id, 'Log ID copied!')}
                          title="Copy log ID"
                        >
                          <Copy size={12} />
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="no-evidence">No specific log IDs referenced in this analysis.</p>
              )}
            </div>
          )}

          {!loading && !error && suggestion && activeTab === 'actions' && (
            <div className="actions-tab" id="actions-panel" role="tabpanel" aria-labelledby="actions-tab">
              {suggestion.verificationSteps?.length > 0 && (
                <div className="section">
                  <div className="section-header">
                    <h3><Shield size={16} /> Verification Steps</h3>
                    <button 
                      className="export-btn"
                      onClick={exportAsMarkdown}
                      title="Export as Markdown"
                    >
                      <Download size={14} /> Export MD
                    </button>
                  </div>
                  <p className="actions-note">Run these commands to verify the root cause:</p>
                  <ol className="steps-list">
                    {suggestion.verificationSteps.map((step, i) => (
                      <li key={i} className="step-item">
                        <ReactMarkdown 
                          components={markdownRenderers}
                          children={step}
                        />
                        <button 
                          className="copy-step-btn"
                          onClick={() => copyToClipboard(step, 'Verification step copied!')}
                          title="Copy this step"
                        >
                          <Copy size={12} />
                        </button>
                      </li>
                    ))}
                  </ol>
                </div>
              )}

              {suggestion.recommendedActions?.length > 0 && (
                <div className="section">
                  <div className="section-header">
                    <h3><Zap size={16} /> Recommended Actions</h3>
                    <button 
                      className="export-btn"
                      onClick={exportAsMarkdown}
                      title="Export as Markdown"
                    >
                      <Download size={14} /> Export MD
                    </button>
                  </div>
                  <p className="actions-note">Remediation steps to resolve the issue:</p>
                  <ol className="steps-list">
                    {suggestion.recommendedActions.map((action, i) => (
                      <li key={i} className="step-item">
                        <ReactMarkdown 
                          components={markdownRenderers}
                          children={action}
                        />
                        <button 
                          className="copy-step-btn"
                          onClick={() => copyToClipboard(action, 'Action copied!')}
                          title="Copy this action"
                        >
                          <Copy size={12} />
                        </button>
                      </li>
                    ))}
                  </ol>
                </div>
              )}

              <div className="export-section">
                <button className="btn-secondary" onClick={exportAsMarkdown}>
                  <Download size={14} /> Export as Markdown
                </button>
                <button className="btn-secondary" onClick={exportAsJSON}>
                  <FileText size={14} /> Export as JSON
                </button>
              </div>
            </div>
          )}

          {!loading && !error && !suggestion && (
            <div className="empty-state">
              <Lightbulb size={48} style={{ opacity: 0.3 }} />
              <p>No analysis available for this anomaly.</p>
            </div>
          )}
        </div>

        <div className="modal-footer">
          <button className="btn-secondary" onClick={onClose}>Close</button>
          {suggestion && (
            <>
              <button className="btn-secondary" onClick={exportAsMarkdown}>
                <Download size={14} /> Export MD
              </button>
              <button className="btn-primary" onClick={() => copyToClipboard(JSON.stringify(suggestion, null, 2), 'Full JSON copied!')}>
                <Copy size={14} /> Copy JSON
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default AIAnalysisModal;