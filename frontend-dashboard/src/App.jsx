import { useState, useEffect } from 'react';
import axios from 'axios';
import LiveMetricsPanel from './LiveMetricsPanel';
import LogSearchConsole from './LogSearchConsole';
import IntegrationHub from './IntegrationHub';
import MetricsDashboard from './MetricsDashboard';
import { LayoutDashboard, CodeSquare, LogOut, Briefcase, Plus, ShieldCheck, FolderKey, TrendingUp } from 'lucide-react';

function App() {
  const [token, setToken] = useState(localStorage.getItem('token') || '');
  const [email, setEmail] = useState(localStorage.getItem('email') || '');
  const [currentView, setCurrentView] = useState(token ? 'projects' : 'login');

  // Auth Form State
  const [authEmail, setAuthEmail] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authError, setAuthError] = useState('');
  const [authLoading, setAuthLoading] = useState(false);

  // Projects State
  const [projects, setProjects] = useState([]);
  const [selectedProject, setSelectedProject] = useState(null);
  const [newProjectName, setNewProjectName] = useState('');
  const [isCreatingProject, setIsCreatingProject] = useState(false);

  // Layout Tab State
  const [dashTab, setDashTab] = useState('dashboard');

  useEffect(() => {
    if (token) {
      fetchProjects();
    }
  }, [token]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const urlToken = params.get('token');
    const urlEmail = params.get('email');
    if (urlToken && urlEmail) {
      saveSession(urlToken, urlEmail);
      window.history.replaceState({}, document.title, window.location.pathname);
    }
  }, []);

  const fetchProjects = async () => {
    try {
      const response = await axios.get('http://localhost:8091/api/projects', {
        headers: { Authorization: `Bearer ${token}` }
      });

      const payload = response.data;
      const normalizedProjects = Array.isArray(payload)
        ? payload
        : Array.isArray(payload?.projects)
        ? payload.projects
        : [];

      if (!Array.isArray(payload) && !Array.isArray(payload?.projects)) {
        console.warn('Unexpected /api/projects response shape:', payload);
      }

      setProjects(normalizedProjects);
    } catch (err) {
      console.error('Failed to fetch projects', err);
      if (err.response && err.response.status === 401) {
        handleLogout();
      }
    }
  };

  const handleAuth = async (isRegister) => {
    setAuthError('');
    setAuthLoading(true);
    const url = isRegister ? 'http://localhost:8091/api/auth/register' : 'http://localhost:8091/api/auth/login';
    try {
      const response = await axios.post(url, {
        email: authEmail,
        password: authPassword
      });

      if (isRegister) {
        const loginResp = await axios.post('http://localhost:8091/api/auth/login', {
          email: authEmail,
          password: authPassword
        });
        saveSession(loginResp.data.token, authEmail);
      } else {
        saveSession(response.data.token, authEmail);
      }
    } catch (err) {
      console.error(err);
      setAuthError(err.response?.data?.error || 'Authentication failed. Please try again.');
    } finally {
      setAuthLoading(false);
    }
  };

  const saveSession = (jwtToken, userEmail) => {
    localStorage.setItem('token', jwtToken);
    localStorage.setItem('email', userEmail);
    setToken(jwtToken);
    setEmail(userEmail);
    setCurrentView('projects');
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('email');
    setToken('');
    setEmail('');
    setSelectedProject(null);
    setCurrentView('login');
  };

  const handleCreateProject = async (e) => {
    e.preventDefault();
    if (!newProjectName.trim()) return;

    try {
      await axios.post(
        'http://localhost:8091/api/projects',
        { name: newProjectName },
        { headers: { Authorization: `Bearer ${token}` } }
      );
      setNewProjectName('');
      setIsCreatingProject(false);
      fetchProjects();
    } catch (err) {
      console.error('Failed to create project', err);
      alert('Error creating project. Check if your connection is valid.');
    }
  };

  const selectProject = (project) => {
    setSelectedProject(project);
    setCurrentView('dashboard');
  };

  if (currentView === 'login' || currentView === 'signup') {
    const isSignup = currentView === 'signup';
    return (
      <div className="app-container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
        <div className="card" style={{ maxWidth: '400px', width: '100%', padding: '2rem' }}>
          <div className="card-title" style={{ justifyContent: 'center', fontSize: '1.5rem', marginBottom: '1.5rem' }}>
            <FolderKey size={28} />
            {isSignup ? 'Create Account' : 'Sign In'}
          </div>

          {authError && <p className="error-message" style={{ textAlign: 'center', marginBottom: '1rem' }}>{authError}</p>}

          <form onSubmit={(e) => { e.preventDefault(); handleAuth(isSignup); }}>
            <div style={{ marginBottom: '1rem' }}>
              <label style={{ display: 'block', marginBottom: '0.25rem', color: 'var(--text-muted)' }}>Email</label>
              <input
                type="email"
                className="search-input"
                style={{ width: '100%', padding: '0.75rem' }}
                value={authEmail}
                onChange={(e) => setAuthEmail(e.target.value)}
                required
              />
            </div>
            <div style={{ marginBottom: '1.5rem' }}>
              <label style={{ display: 'block', marginBottom: '0.25rem', color: 'var(--text-muted)' }}>Password</label>
              <input
                type="password"
                className="search-input"
                style={{ width: '100%', padding: '0.75rem' }}
                value={authPassword}
                onChange={(e) => setAuthPassword(e.target.value)}
                required
              />
            </div>

            <button
              type="submit"
              className="nav-item active"
              style={{ width: '100%', padding: '0.75rem', justifyContent: 'center', cursor: 'pointer' }}
              disabled={authLoading}
            >
              {authLoading ? 'Please wait...' : isSignup ? 'Sign Up' : 'Sign In'}
            </button>
          </form>

          <div style={{ display: 'flex', alignItems: 'center', margin: '1.5rem 0', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            <div style={{ flex: 1, height: '1px', backgroundColor: 'var(--border)' }}></div>
            <span style={{ padding: '0 0.5rem' }}>OR</span>
            <div style={{ flex: 1, height: '1px', backgroundColor: 'var(--border)' }}></div>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', flexDirection: 'column' }}>
            <button
              onClick={() => window.location.href = 'http://localhost:8091/oauth2/authorization/google'}
              className="nav-item"
              style={{ width: '100%', padding: '0.75rem', justifyContent: 'center', cursor: 'pointer', border: '1px solid var(--border)' }}
            >
              Continue with Google
            </button>
            <button
              onClick={() => window.location.href = 'http://localhost:8091/oauth2/authorization/github'}
              className="nav-item"
              style={{ width: '100%', padding: '0.75rem', justifyContent: 'center', cursor: 'pointer', border: '1px solid var(--border)' }}
            >
              Continue with GitHub
            </button>
          </div>

          <div style={{ textAlign: 'center', marginTop: '1.5rem', fontSize: '0.875rem' }}>
            {isSignup ? (
              <span style={{ color: 'var(--text-muted)' }}>
                Already have an account? <a href="#" style={{ color: 'var(--primary)' }} onClick={() => setCurrentView('login')}>Sign In</a>
              </span>
            ) : (
              <span style={{ color: 'var(--text-muted)' }}>
                Don't have an account? <a href="#" style={{ color: 'var(--primary)' }} onClick={() => setCurrentView('signup')}>Sign Up</a>
              </span>
            )}
          </div>
        </div>
      </div>
    );
  }

  if (currentView === 'projects') {
    const safeProjects = Array.isArray(projects) ? projects : [];

    return (
      <div className="app-container">
        <header className="header">
          <div className="header-brand">
            <h1>Observability Hub</h1>
            <div className="header-status">
              <ShieldCheck size={14} style={{ color: 'var(--success)' }} />
              Logged in as {email}
            </div>
          </div>
          <nav className="top-nav">
            <button className="nav-item" onClick={handleLogout}>
              <LogOut size={16} />
              Logout
            </button>
          </nav>
        </header>

        <div style={{ padding: '2rem', maxWidth: '1000px', margin: '0 auto' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
            <h2>Your Projects</h2>
            <button
              className="nav-item active"
              onClick={() => setIsCreatingProject(!isCreatingProject)}
              style={{ cursor: 'pointer' }}
            >
              <Plus size={16} />
              New Project
            </button>
          </div>

          {isCreatingProject && (
            <div className="card" style={{ marginBottom: '2rem', padding: '1.5rem' }}>
              <form onSubmit={handleCreateProject} style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end' }}>
                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', marginBottom: '0.25rem', color: 'var(--text-muted)' }}>Project Name</label>
                  <input
                    type="text"
                    className="search-input"
                    style={{ width: '100%', padding: '0.75rem' }}
                    placeholder="e.g. Workflow Engine, Payment API"
                    value={newProjectName}
                    onChange={(e) => setNewProjectName(e.target.value)}
                    required
                  />
                </div>
                <button type="submit" className="nav-item active" style={{ padding: '0.75rem 1.5rem', cursor: 'pointer' }}>Create</button>
                <button type="button" className="nav-item" onClick={() => setIsCreatingProject(false)} style={{ padding: '0.75rem 1.5rem', cursor: 'pointer' }}>Cancel</button>
              </form>
            </div>
          )}

          {safeProjects.length === 0 ? (
            <div className="card" style={{ padding: '4rem', textAlign: 'center', color: 'var(--text-muted)' }}>
              <Briefcase size={48} style={{ margin: '0 auto 1rem', display: 'block', opacity: 0.5 }} />
              <p style={{ fontSize: '1.1rem', marginBottom: '1rem' }}>No projects configured yet.</p>
              <p style={{ fontSize: '0.9rem' }}>Create a project above to generate an API key and start ingesting logs.</p>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '1.5rem' }}>
              {safeProjects.map((proj) => (
                <div
                  key={proj.id}
                  className="card"
                  style={{ padding: '1.5rem', cursor: 'pointer', transition: 'border-color 0.2s', border: '1px solid var(--border)' }}
                  onClick={() => selectProject(proj)}
                  onMouseEnter={(e) => e.currentTarget.style.borderColor = 'var(--primary)'}
                  onMouseLeave={(e) => e.currentTarget.style.borderColor = 'var(--border)'}
                >
                  <h3 style={{ fontSize: '1.25rem', marginBottom: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Briefcase size={18} style={{ color: 'var(--primary)' }} />
                    {proj.name}
                  </h3>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontFamily: 'monospace', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    API Key: {proj.apiKey}
                  </p>
                  <div style={{ marginTop: '1rem', fontSize: '0.875rem', color: 'var(--primary)', textAlign: 'right' }}>
                    Open Dashboard →
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="app-container">
      <header className="header">
        <div className="header-brand">
          <h1 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Briefcase size={22} style={{ color: 'var(--primary)' }} />
            {selectedProject?.name ?? 'Project'}
          </h1>
          <div className="header-status">
            <span style={{ cursor: 'pointer', color: 'var(--primary)' }} onClick={() => setCurrentView('projects')}>
              ← Switch Project
            </span>
          </div>
        </div>

        <nav className="top-nav">
          <button
            className={`nav-item ${dashTab === 'dashboard' ? 'active' : ''}`}
            onClick={() => setDashTab('dashboard')}
          >
            <LayoutDashboard size={16} />
            Dashboard
          </button>
          <button
            className={`nav-item ${dashTab === 'integrations' ? 'active' : ''}`}
            onClick={() => setDashTab('integrations')}
          >
            <CodeSquare size={16} />
            Integrations Hub
          </button>
          <button
            className={`nav-item ${dashTab === 'metrics' ? 'active' : ''}`}
            onClick={() => setDashTab('metrics')}
          >
            <TrendingUp size={16} />
            Metrics
          </button>
          <button className="nav-item" onClick={handleLogout}>
            <LogOut size={16} />
            Logout
          </button>
        </nav>
      </header>

      {dashTab === 'dashboard' ? (
        <div className="dashboard-grid">
          <LiveMetricsPanel projectId={selectedProject?.id} />
          <LogSearchConsole projectId={selectedProject?.id} />
        </div>
      ) : dashTab === 'metrics' ? (
        <MetricsDashboard projectId={selectedProject?.id} token={token} />
      ) : (
        <IntegrationHub project={selectedProject} />
      )}
    </div>
  );
}

export default App;