import React, { useState } from 'react';
import { api } from '../services/api';

interface LoginProps {
  onLoginSuccess: () => void;
}

export const Login: React.FC<LoginProps> = ({ onLoginSuccess }) => {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      if (isRegister) {
        await api.register({ username, password, email });
        setIsRegister(false);
        setError('Registration successful! Please login.');
      } else {
        await api.login({ username, password });
        onLoginSuccess();
      }
    } catch (err: any) {
      setError(err.message || 'An error occurred. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <div className="glass-card fade-in" style={styles.card}>
        <div style={styles.logoContainer}>
          <div style={styles.logoBadge}>⚙️</div>
          <h2 style={styles.title}>JOBSEEK</h2>
          <p style={styles.subtitle}>Distributed Background Execution Platform</p>
        </div>

        <div style={styles.tabs}>
          <button
            onClick={() => { setIsRegister(false); setError(''); }}
            style={{
              ...styles.tab,
              color: !isRegister ? 'var(--color-primary)' : 'var(--text-secondary)',
              borderBottom: !isRegister ? '2px solid var(--color-primary)' : 'none'
            }}
          >
            Sign In
          </button>
          <button
            onClick={() => { setIsRegister(true); setError(''); }}
            style={{
              ...styles.tab,
              color: isRegister ? 'var(--color-primary)' : 'var(--text-secondary)',
              borderBottom: isRegister ? '2px solid var(--color-primary)' : 'none'
            }}
          >
            Register
          </button>
        </div>

        {error && (
          <div style={{
            ...styles.errorAlert,
            backgroundColor: error.includes('successful') ? 'var(--color-success-glow)' : 'var(--color-danger-glow)',
            borderColor: error.includes('successful') ? 'var(--color-success)' : 'var(--color-danger)',
            color: error.includes('successful') ? 'var(--color-success)' : 'var(--color-danger)'
          }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.formGroup}>
            <label style={styles.label}>Username</label>
            <input
              type="text"
              className="glass-input"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              placeholder="Enter your username"
            />
          </div>

          {isRegister && (
            <div style={styles.formGroup}>
              <label style={styles.label}>Email Address</label>
              <input
                type="email"
                className="glass-input"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                placeholder="email@example.com"
              />
            </div>
          )}

          <div style={styles.formGroup}>
            <label style={styles.label}>Password</label>
            <input
              type="password"
              className="glass-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            className="btn-primary"
            disabled={loading}
            style={styles.submitBtn}
          >
            {loading ? 'Processing...' : isRegister ? 'Create Account' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: '100vh',
    padding: '20px',
    background: 'radial-gradient(ellipse at bottom, #131b2e 0%, #0b0f19 100%)',
  },
  card: {
    width: '100%',
    maxWidth: '440px',
    padding: '40px',
  },
  logoContainer: {
    textAlign: 'center',
    marginBottom: '32px',
  },
  logoBadge: {
    fontSize: '48px',
    marginBottom: '12px',
  },
  title: {
    fontSize: '24px',
    color: 'var(--text-primary)',
    marginBottom: '6px',
  },
  subtitle: {
    fontSize: '13px',
    color: 'var(--text-secondary)',
  },
  tabs: {
    display: 'flex',
    borderBottom: '1px solid var(--border-color)',
    marginBottom: '24px',
  },
  tab: {
    flex: 1,
    padding: '12px',
    background: 'none',
    border: 'none',
    fontSize: '14px',
    fontWeight: '600',
    cursor: 'pointer',
    outline: 'none',
    transition: 'all 0.2s',
  },
  errorAlert: {
    padding: '12px 16px',
    borderRadius: '8px',
    fontSize: '13px',
    border: '1px solid',
    marginBottom: '20px',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '18px',
  },
  formGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  label: {
    fontSize: '12px',
    fontWeight: '600',
    color: 'var(--text-secondary)',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  submitBtn: {
    marginTop: '12px',
    width: '100%',
  },
};
