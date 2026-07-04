import React, { useEffect, useState } from 'react';
import { api } from '../services/api';

interface LogsModalProps {
  jobId: number;
  onClose: () => void;
}

export const LogsModal: React.FC<LogsModalProps> = ({ jobId, onClose }) => {
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchLogs = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await api.getJobLogs(jobId);
      setLogs(data);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch logs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [jobId]);

  return (
    <div style={styles.overlay}>
      <div className="glass-card fade-in" style={styles.modal}>
        <div style={styles.header}>
          <div>
            <h3 style={styles.title}>Job Logs</h3>
            <p style={styles.subtitle}>Execution History for Job #{jobId}</p>
          </div>
          <div style={styles.headerActions}>
            <button onClick={fetchLogs} className="btn-secondary" style={styles.headerBtn}>
              🔄 Refresh
            </button>
            <button onClick={onClose} className="btn-secondary" style={{ ...styles.headerBtn, color: 'var(--color-danger)' }}>
              ✕ Close
            </button>
          </div>
        </div>

        <div style={styles.consoleContainer}>
          {loading && <div style={styles.statusText}>Streaming logs...</div>}
          {error && <div style={{ ...styles.statusText, color: 'var(--color-danger)' }}>{error}</div>}
          
          {!loading && !error && logs.length === 0 && (
            <div style={styles.statusText}>No logs recorded for this job execution.</div>
          )}

          {!loading && logs.length > 0 && (
            <pre style={styles.console}>
              {logs.map((log, idx) => (
                <div key={idx} style={styles.logLine}>
                  <span style={styles.logTime}>[{new Date(log.createdAt).toISOString()}]</span>
                  <span style={getLogLevelStyle(log.logLevel)}>[{log.logLevel}]</span>
                  <span style={styles.logMsg}>{log.message}</span>
                </div>
              ))}
            </pre>
          )}
        </div>
      </div>
    </div>
  );
};

const getLogLevelStyle = (level: string): React.CSSProperties => {
  const base = {
    marginRight: '8px',
    fontWeight: 'bold',
  };
  if (level === 'ERROR') return { ...base, color: 'var(--color-danger)' };
  if (level === 'WARN') return { ...base, color: 'var(--color-warning)' };
  return { ...base, color: 'var(--color-success)' };
};

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(5, 7, 12, 0.8)',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 1000,
    padding: '24px',
    backdropFilter: 'blur(4px)',
  },
  modal: {
    width: '100%',
    maxWidth: '850px',
    height: '600px',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderBottom: '1px solid var(--border-color)',
    paddingBottom: '16px',
    marginBottom: '20px',
  },
  title: {
    fontSize: '20px',
    color: 'var(--text-primary)',
  },
  subtitle: {
    fontSize: '12px',
    color: 'var(--text-secondary)',
  },
  headerActions: {
    display: 'flex',
    gap: '12px',
  },
  headerBtn: {
    padding: '8px 16px',
    fontSize: '13px',
  },
  consoleContainer: {
    flex: 1,
    backgroundColor: '#05070c',
    border: '1px solid var(--border-color)',
    borderRadius: '12px',
    padding: '16px',
    overflowY: 'auto',
    display: 'flex',
    flexDirection: 'column',
  },
  statusText: {
    margin: 'auto',
    color: 'var(--text-secondary)',
    fontSize: '14px',
  },
  console: {
    fontFamily: 'var(--font-mono)',
    fontSize: '13px',
    lineHeight: '1.6',
    whiteSpace: 'pre-wrap',
  },
  logLine: {
    display: 'flex',
    marginBottom: '6px',
  },
  logTime: {
    color: '#4b5563',
    marginRight: '8px',
  },
  logMsg: {
    color: '#e5e7eb',
    wordBreak: 'break-all',
  },
};
