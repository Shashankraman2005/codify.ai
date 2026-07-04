import { useEffect, useState, useRef } from 'react';
import { api } from './services/api';
import { connectWebSocket } from './services/websocket';
import { Login } from './components/Login';
import { LogsModal } from './components/LogsModal';
import { ToastContainer } from './components/Toast';
import { ConfirmDialog } from './components/ConfirmDialog';
import { useToast } from './hooks/useToast';
import { Client } from '@stomp/stompjs';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend
} from 'recharts';

// ── Relative-time helper ─────────────────────────────────────────────────────
function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const s = Math.floor(diff / 1000);
  if (s < 5) return 'just now';
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return new Date(iso).toLocaleDateString();
}

// ── Event category config ────────────────────────────────────────────────────
const EVENT_CONFIG: Record<string, { icon: string; color: string }> = {
  JOB:      { icon: '⚡', color: 'var(--color-primary)' },
  QUEUE:    { icon: '📥', color: 'var(--color-secondary)' },
  WORKER:   { icon: '💻', color: 'var(--color-success)' },
  SCHEDULE: { icon: '⏱️', color: 'var(--color-warning)' },
  SYSTEM:   { icon: '🔧', color: 'var(--text-muted)' },
};

function getEventConfig(type: string) {
  for (const key of Object.keys(EVENT_CONFIG)) {
    if (type?.toUpperCase().includes(key)) return EVENT_CONFIG[key];
  }
  return EVENT_CONFIG.SYSTEM;
}

// ── Status badge helper ──────────────────────────────────────────────────────
function StatusBadge({ status }: { status: string }) {
  const cls = `badge badge-${status.toLowerCase().replace('_', '-')}`;
  return <span className={cls}>{status}</span>;
}

// ── Spinner ──────────────────────────────────────────────────────────────────
function Spinner({ size = 16 }: { size?: number }) {
  return (
    <span
      style={{
        display: 'inline-block',
        width: size,
        height: size,
        border: `2px solid rgba(255,255,255,0.2)`,
        borderTopColor: 'var(--color-primary)',
        borderRadius: '50%',
        animation: 'spin 0.7s linear infinite',
        flexShrink: 0,
      }}
    />
  );
}

// ── Empty State ───────────────────────────────────────────────────────────────
function EmptyState({ icon, title, subtitle }: { icon: string; title: string; subtitle?: string }) {
  return (
    <div style={emptyStateStyle}>
      <div style={{ fontSize: 48 }}>{icon}</div>
      <h3 style={{ marginTop: 8 }}>{title}</h3>
      {subtitle && <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>{subtitle}</p>}
    </div>
  );
}

const emptyStateStyle: React.CSSProperties = {
  textAlign: 'center',
  padding: '48px 24px',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  gap: 8,
};

// ── Main App ─────────────────────────────────────────────────────────────────
export default function App() {
  const { toasts, removeToast, toast } = useToast();

  const [isAuthenticated, setIsAuthenticated] = useState(api.isAuthenticated());
  const [username, setUsername] = useState(api.getUsername());

  // Workspace
  const [orgs, setOrgs] = useState<any[]>([]);
  const [selectedOrgId, setSelectedOrgId] = useState<number | null>(null);
  const [projects, setProjects] = useState<any[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [queues, setQueues] = useState<any[]>([]);
  const [selectedQueueId, setSelectedQueueId] = useState<number | null>(null);
  const [retryPolicies, setRetryPolicies] = useState<any[]>([]);
  const [workers, setWorkers] = useState<any[]>([]);

  // Stats & Scheduled Jobs
  const [stats, setStats] = useState<any>(null);
  const [scheduledJobs, setScheduledJobs] = useState<any[]>([]);

  // Jobs
  const [jobs, setJobs] = useState<any[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [filterStatus, setFilterStatus] = useState('');
  const [filterType, setFilterType] = useState('');

  // Log modal
  const [activeLogJobId, setActiveLogJobId] = useState<number | null>(null);

  // Live events
  const [events, setEvents] = useState<any[]>([]);

  // Tab
  const [activeTab, setActiveTab] = useState<string>('overview');

  // Loading states
  const [loadingJobs, setLoadingJobs] = useState(false);
  const [loadingWorkers, setLoadingWorkers] = useState(false);
  const [submittingQueue, setSubmittingQueue] = useState(false);
  const [submittingSchedule, setSubmittingSchedule] = useState(false);
  const [submittingJob, setSubmittingJob] = useState(false);
  const [submittingRetryPolicy, setSubmittingRetryPolicy] = useState(false);
  const [deletingScheduleId, setDeletingScheduleId] = useState<number | null>(null);
  const [togglingQueueId, setTogglingQueueId] = useState<number | null>(null);

  // Search / filter
  const [queueSearch, setQueueSearch] = useState('');
  const [scheduleSearch, setScheduleSearch] = useState('');
  const [retrySearch, setRetrySearch] = useState('');

  // Confirm dialog state
  const [confirm, setConfirm] = useState<{
    title: string; message: string; danger?: boolean; onConfirm: () => void;
  } | null>(null);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  // Modals
  const [showNewOrg, setShowNewOrg] = useState(false);
  const [newOrgName, setNewOrgName] = useState('');
  const [showNewProject, setShowNewProject] = useState(false);
  const [newProjectName, setNewProjectName] = useState('');
  const [showNewQueue, setShowNewQueue] = useState(false);
  const [newQueueName, setNewQueueName] = useState('');
  const [newQueuePriority, setNewQueuePriority] = useState(1);
  const [newQueueConcurrency, setNewQueueConcurrency] = useState(5);
  const [newQueuePolicyId, setNewQueuePolicyId] = useState<number | null>(null);

  // Job dispatcher
  const [jobType, setJobType] = useState<'IMMEDIATE' | 'DELAYED' | 'SCHEDULED' | 'BATCH'>('IMMEDIATE');
  const [jobPayload, setJobPayload] = useState('{"task": "process_image"}');
  const [jobPriority, setJobPriority] = useState(1);
  const [jobDelay, setJobDelay] = useState(10);
  const [jobScheduledAt, setJobScheduledAt] = useState('');
  const [batchPayloads, setBatchPayloads] = useState('[{"task": "batch_1"}, {"task": "batch_2"}]');

  // Schedule creator
  const [scheduleName, setScheduleName] = useState('');
  const [scheduleType, setScheduleType] = useState<'ONE_TIME' | 'DELAYED' | 'CRON' | 'FIXED_INTERVAL'>('CRON');
  const [cronExpression, setCronExpression] = useState('0/30 * * * * ?');
  const [intervalSeconds, setIntervalSeconds] = useState(60);
  const [scheduledAtTime, setScheduledAtTime] = useState('');
  const [scheduleQueueId, setScheduleQueueId] = useState<number | null>(null);
  const [schedulePayload, setSchedulePayload] = useState('{"scheduled": true}');

  // Retry policy creator
  const [rpName, setRpName] = useState('');
  const [rpType, setRpType] = useState<'FIXED' | 'LINEAR' | 'EXPONENTIAL'>('FIXED');
  const [rpBaseDelay, setRpBaseDelay] = useState(5);
  const [rpMaxDelay, setRpMaxDelay] = useState(60);
  const [rpMaxAttempts, setRpMaxAttempts] = useState(3);

  const wsClientRef = useRef<Client | null>(null);

  // ── Effects ────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (isAuthenticated) loadInitialData();
  }, [isAuthenticated]);

  useEffect(() => {
    if (selectedOrgId !== null) {
      loadProjects(selectedOrgId);
    } else {
      setProjects([]);
      setSelectedProjectId(null);
    }
  }, [selectedOrgId]);

  useEffect(() => {
    if (selectedProjectId !== null) {
      setSelectedQueueId(null);
      loadProjectDetails(selectedProjectId);
      setupWebSocket();
    } else {
      setQueues([]);
      setStats(null);
      setScheduledJobs([]);
      disconnectWebSocket();
    }
  }, [selectedProjectId]);

  useEffect(() => {
    if (selectedProjectId !== null) loadJobs();
  }, [selectedQueueId, currentPage, filterStatus, filterType, selectedProjectId]);

  // ── Data loaders ──────────────────────────────────────────────────────────
  const loadInitialData = async () => {
    try {
      const [orgsData, rpData] = await Promise.all([
        api.getOrganizations(),
        api.getRetryPolicies(),
      ]);
      setOrgs(orgsData);
      setRetryPolicies(rpData);
      if (orgsData.length > 0) setSelectedOrgId(orgsData[0].id);
      loadWorkers();
    } catch (err) {
      toast.error('Failed to load workspace. Please refresh.');
    }
  };

  const loadProjects = async (orgId: number) => {
    try {
      const projectsData = await api.getProjects(orgId);
      setProjects(projectsData);
      setSelectedProjectId(projectsData.length > 0 ? projectsData[0].id : null);
    } catch (err) {
      toast.error('Failed to load projects.');
    }
  };

  const loadProjectDetails = async (projectId: number) => {
    try {
      const [queuesData, statsData, cronData] = await Promise.all([
        api.getQueues(projectId),
        api.getDashboardStats(projectId),
        api.getScheduledJobs(projectId),
      ]);
      setQueues(queuesData);
      setStats(statsData);
      setScheduledJobs(cronData);
    } catch (err) {
      toast.error('Failed to load project details.');
    }
  };

  const loadJobs = async () => {
    if (selectedProjectId === null) return;
    setLoadingJobs(true);
    try {
      const pageData = await api.getJobs({
        queueId: selectedQueueId || undefined,
        status: filterStatus || undefined,
        type: filterType || undefined,
        page: currentPage,
        size: 10,
      });
      setJobs(pageData.content);
      setTotalPages(pageData.totalPages);
    } catch (err) {
      toast.error('Failed to load jobs.');
    } finally {
      setLoadingJobs(false);
    }
  };

  const loadWorkers = async () => {
    setLoadingWorkers(true);
    try {
      const workersData = await api.getWorkers();
      setWorkers(workersData);
    } catch (err) {
      toast.error('Failed to load worker nodes.');
    } finally {
      setLoadingWorkers(false);
    }
  };

  // ── WebSocket ──────────────────────────────────────────────────────────────
  const setupWebSocket = () => {
    disconnectWebSocket();
    wsClientRef.current = connectWebSocket({
      onJobUpdate: (job) => {
        setJobs(prev => {
          const exists = prev.find(j => j.id === job.id);
          if (exists) return prev.map(j => j.id === job.id ? job : j);
          return [job, ...prev];
        });
      },
      onQueueUpdate: (queue) => {
        setQueues(prev => prev.map(q => q.id === queue.id ? queue : q));
      },
      onWorkerUpdate: (worker) => {
        setWorkers(prev => {
          const exists = prev.find(w => w.workerId === worker.workerId);
          if (exists) return prev.map(w => w.workerId === worker.workerId ? worker : w);
          return [...prev, worker];
        });
      },
      onScheduleUpdate: (schedule) => {
        setScheduledJobs(prev => prev.map(s => s.id === schedule.id ? schedule : s));
      },
      onDashboardUpdate: (newStats) => {
        setStats((prevStats: any) => ({ ...prevStats, ...newStats }));
      },
      onEventUpdate: (event) => {
        setEvents(prev => [event, ...prev].slice(0, 100));
      },
    });
  };

  const disconnectWebSocket = () => {
    if (wsClientRef.current) {
      wsClientRef.current.deactivate();
      wsClientRef.current = null;
    }
  };

  // ── Handlers ───────────────────────────────────────────────────────────────
  const handleCreateOrg = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newOrgName.trim()) return;
    try {
      const org = await api.createOrganization(newOrgName);
      setOrgs(prev => [...prev, org]);
      setSelectedOrgId(org.id);
      setNewOrgName('');
      setShowNewOrg(false);
      toast.success('Organization created successfully!');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to create organization.');
    }
  };

  const handleCreateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedOrgId === null || !newProjectName.trim()) return;
    try {
      const proj = await api.createProject(selectedOrgId, newProjectName);
      setProjects(prev => [...prev, proj]);
      setSelectedProjectId(proj.id);
      setNewProjectName('');
      setShowNewProject(false);
      toast.success('Project created successfully!');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to create project.');
    }
  };

  const handleCreateQueue = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedProjectId === null || !newQueueName.trim()) return;
    setSubmittingQueue(true);
    try {
      const qData = {
        name: newQueueName,
        projectId: selectedProjectId,
        priority: newQueuePriority,
        concurrencyLimit: newQueueConcurrency,
        retryPolicyId: newQueuePolicyId || undefined,
      };
      await api.createQueue(selectedProjectId, qData);
      await loadProjectDetails(selectedProjectId);
      setNewQueueName('');
      setShowNewQueue(false);
      toast.success(`Queue "${newQueueName}" created successfully!`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to create queue.');
    } finally {
      setSubmittingQueue(false);
    }
  };

  const handleCreateRetryPolicy = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rpName.trim()) return;
    setSubmittingRetryPolicy(true);
    try {
      const policy = await api.createRetryPolicy({
        name: rpName,
        strategyType: rpType,
        baseDelaySeconds: rpBaseDelay,
        maxDelaySeconds: rpMaxDelay,
        maxAttempts: rpMaxAttempts,
      });
      setRetryPolicies(prev => [...prev, policy]);
      setRpName('');
      toast.success(`Retry policy "${policy.name}" created!`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to create retry policy.');
    } finally {
      setSubmittingRetryPolicy(false);
    }
  };

  const handleCreateScheduledJob = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedProjectId === null || !scheduleName.trim()) return;
    setSubmittingSchedule(true);
    try {
      const data = {
        name: scheduleName,
        scheduleType,
        cronExpression: scheduleType === 'CRON' ? cronExpression : null,
        intervalSeconds: scheduleType === 'FIXED_INTERVAL' ? intervalSeconds : null,
        scheduledAt: (scheduleType === 'ONE_TIME' || scheduleType === 'DELAYED') && scheduledAtTime
          ? new Date(scheduledAtTime).toISOString() : null,
        payload: schedulePayload,
        queueId: scheduleQueueId || queues[0]?.id,
      };
      await api.createScheduledJob(selectedProjectId, data);
      setScheduleName('');
      const cronData = await api.getScheduledJobs(selectedProjectId);
      setScheduledJobs(cronData);
      toast.success(`Schedule "${data.name}" registered!`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to register schedule.');
    } finally {
      setSubmittingSchedule(false);
    }
  };

  const handlePauseSchedule = async (id: number) => {
    try {
      await api.pauseSchedule(id);
      const cronData = await api.getScheduledJobs(selectedProjectId!);
      setScheduledJobs(cronData);
      toast.success('Schedule paused.');
    } catch (err) {
      toast.error('Failed to pause schedule.');
    }
  };

  const handleResumeSchedule = async (id: number) => {
    try {
      await api.resumeSchedule(id);
      const cronData = await api.getScheduledJobs(selectedProjectId!);
      setScheduledJobs(cronData);
      toast.success('Schedule resumed.');
    } catch (err) {
      toast.error('Failed to resume schedule.');
    }
  };

  const handleDeleteScheduledJob = (id: number, name: string) => {
    setConfirm({
      title: 'Delete Schedule',
      message: `Are you sure you want to permanently delete "${name}"? This action cannot be undone.`,
      danger: true,
      onConfirm: async () => {
        setConfirm(null);
        setDeletingScheduleId(id);
        try {
          await api.deleteScheduledJob(id);
          if (selectedProjectId) {
            const cronData = await api.getScheduledJobs(selectedProjectId);
            setScheduledJobs(cronData);
          }
          toast.success('Schedule deleted.');
        } catch (err) {
          toast.error(err instanceof Error ? err.message : 'Failed to delete schedule.');
        } finally {
          setDeletingScheduleId(null);
        }
      },
    });
  };

  const handleDispatchJob = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedQueueId === null) return;
    setSubmittingJob(true);
    try {
      let payload = jobPayload;
      let childJobs: any[] | undefined;

      if (jobType === 'BATCH') {
        childJobs = JSON.parse(batchPayloads).map((p: any) => ({
          queueId: selectedQueueId,
          type: 'IMMEDIATE',
          payload: JSON.stringify(p),
        }));
        payload = '';
      }

      await api.createJob({
        queueId: selectedQueueId,
        type: jobType === 'BATCH' ? 'BATCH' : jobType,
        payload,
        priority: jobPriority,
        delaySeconds: jobType === 'DELAYED' ? jobDelay : undefined,
        scheduledAt: jobType === 'SCHEDULED' ? jobScheduledAt : undefined,
        childJobs,
      });
      loadJobs();
      if (selectedProjectId) {
        api.getDashboardStats(selectedProjectId).then(setStats);
      }
      toast.success('Job dispatched successfully!');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to dispatch job.');
    } finally {
      setSubmittingJob(false);
    }
  };

  const handleQueuePauseToggle = async (queue: any) => {
    setTogglingQueueId(queue.id);
    try {
      if (queue.isPaused) {
        await api.resumeQueue(queue.id);
        toast.success(`Queue "${queue.name}" resumed.`);
      } else {
        await api.pauseQueue(queue.id);
        toast.success(`Queue "${queue.name}" paused.`);
      }
      if (selectedProjectId) await loadProjectDetails(selectedProjectId);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to toggle queue state.');
    } finally {
      setTogglingQueueId(null);
    }
  };

  const handleCancelJob = async (jobId: number) => {
    try {
      await api.cancelJob(jobId);
      loadJobs();
      toast.success('Job cancelled.');
    } catch (err) {
      toast.error('Failed to cancel job.');
    }
  };

  const handleRetryJob = async (jobId: number) => {
    try {
      await api.retryJob(jobId);
      loadJobs();
      toast.success('Job re-queued for retry.');
    } catch (err) {
      toast.error('Failed to retry job.');
    }
  };

  const handleLogout = () => {
    setShowLogoutConfirm(true);
  };

  const doLogout = () => {
    api.logout();
    setIsAuthenticated(false);
    setUsername('');
    disconnectWebSocket();
    setShowLogoutConfirm(false);
  };

  // ── Filtered data ─────────────────────────────────────────────────────────
  const filteredQueues = queues.filter(q =>
    q.name.toLowerCase().includes(queueSearch.toLowerCase())
  );
  const filteredSchedules = scheduledJobs.filter(s =>
    s.name.toLowerCase().includes(scheduleSearch.toLowerCase())
  );
  const filteredRetryPolicies = retryPolicies.filter(p =>
    p.name.toLowerCase().includes(retrySearch.toLowerCase())
  );

  // ── Auth gate ──────────────────────────────────────────────────────────────
  if (!isAuthenticated) {
    return <Login onLoginSuccess={() => {
      setIsAuthenticated(true);
      setUsername(api.getUsername());
    }} />;
  }

  const selectedQueue = queues.find(q => q.id === selectedQueueId);

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div style={styles.appContainer}>
      {/* Global Toast Notifications */}
      <ToastContainer toasts={toasts} onRemove={removeToast} />

      {/* Confirmation Dialog */}
      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          message={confirm.message}
          danger={confirm.danger}
          confirmLabel="Yes, proceed"
          onConfirm={confirm.onConfirm}
          onCancel={() => setConfirm(null)}
        />
      )}

      {/* Logout Confirmation */}
      {showLogoutConfirm && (
        <ConfirmDialog
          title="Sign Out"
          message="Are you sure you want to sign out of JOBSEEK?"
          confirmLabel="Sign Out"
          onConfirm={doLogout}
          onCancel={() => setShowLogoutConfirm(false)}
        />
      )}

      {/* ── Header ─────────────────────────────────────────────────────── */}
      <header style={styles.header}>
        <div style={styles.headerBrand}>
          <span style={styles.brandIcon}>⚙️</span>
          <span style={styles.brandName}>JOBSEEK</span>
        </div>

        <div style={styles.workspaceSelector}>
          <div style={styles.selectGroup}>
            <label style={styles.selectLabel}>Org:</label>
            <select
              style={styles.select}
              value={selectedOrgId || ''}
              onChange={(e) => setSelectedOrgId(Number(e.target.value))}
            >
              {orgs.map(org => <option key={org.id} value={org.id}>{org.name}</option>)}
            </select>
            <button onClick={() => setShowNewOrg(true)} style={styles.addButton} title="New Organization">+</button>
          </div>

          {selectedOrgId && (
            <div style={styles.selectGroup}>
              <label style={styles.selectLabel}>Project:</label>
              <select
                style={styles.select}
                value={selectedProjectId || ''}
                onChange={(e) => setSelectedProjectId(Number(e.target.value))}
              >
                {projects.map(proj => <option key={proj.id} value={proj.id}>{proj.name}</option>)}
              </select>
              <button onClick={() => setShowNewProject(true)} style={styles.addButton} title="New Project">+</button>
            </div>
          )}
        </div>

        <div style={styles.headerUser}>
          <span style={styles.userName}>🟢 {username}</span>
          <button onClick={handleLogout} className="btn-secondary" style={styles.logoutBtn}>
            Sign Out
          </button>
        </div>
      </header>

      {/* ── Main Layout ────────────────────────────────────────────────── */}
      <div style={styles.mainLayout}>
        {/* Sidebar */}
        <aside style={styles.sidebar}>
          <div style={styles.sidebarSection}>
            <h4 style={styles.sidebarTitle}>Navigation</h4>
            <div style={styles.tabList}>
              {[
                { id: 'overview', icon: '📊', label: 'Dashboard Overview' },
                { id: 'queues',   icon: '📥', label: 'Active Job Queues' },
                { id: 'cron',     icon: '⏱️', label: 'Schedules' },
                { id: 'workers',  icon: '💻', label: 'Cluster Workers' },
                { id: 'retry',    icon: '🔄', label: 'Retry Policies' },
                { id: 'events',   icon: '📡', label: 'Live Event Feed' },
              ].map(tab => (
                <button
                  key={tab.id}
                  onClick={() => {
                    setActiveTab(tab.id);
                    if (tab.id === 'workers') loadWorkers();
                    if (tab.id === 'queues') setSelectedQueueId(null);
                  }}
                  style={{
                    ...styles.tabButton,
                    backgroundColor: activeTab === tab.id ? 'var(--color-primary-glow)' : 'transparent',
                    borderColor: activeTab === tab.id ? 'var(--color-primary)' : 'transparent',
                  }}
                >
                  {tab.icon} {tab.label}
                </button>
              ))}
            </div>
          </div>

          {/* Queue sidebar when on queues tab */}
          {activeTab === 'queues' && (
            <div style={styles.sidebarSection}>
              <div style={styles.sectionHeader}>
                <h4 style={styles.sidebarTitle}>Select Queue</h4>
                <button onClick={() => setShowNewQueue(true)} className="btn-secondary" style={styles.smallBtn}>
                  + Add
                </button>
              </div>
              {/* Queue search */}
              <input
                type="text"
                className="glass-input"
                placeholder="🔍 Search queues..."
                value={queueSearch}
                onChange={e => setQueueSearch(e.target.value)}
                style={{ fontSize: 13, padding: '8px 12px' }}
              />
              <div style={styles.queueList}>
                {filteredQueues.length === 0 && (
                  <p style={styles.emptyText}>
                    {queueSearch ? 'No queues match your search.' : 'No queues created yet.'}
                  </p>
                )}
                {filteredQueues.map(queue => (
                  <button
                    key={queue.id}
                    onClick={() => setSelectedQueueId(queue.id)}
                    style={{
                      ...styles.queueSelector,
                      backgroundColor: selectedQueueId === queue.id ? 'var(--bg-glass-hover)' : 'transparent',
                      borderColor: selectedQueueId === queue.id ? 'var(--color-primary)' : 'var(--border-color)',
                    }}
                  >
                    <div style={styles.queueSelectorTitle}>
                      <span>{queue.name}</span>
                      <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Prio: {queue.priority}</span>
                    </div>
                    <div style={styles.queueSelectorMeta}>
                      <span>Limit: {queue.concurrencyLimit}</span>
                      <span style={{ color: queue.isPaused ? 'var(--color-danger)' : 'var(--color-success)' }}>
                        {queue.isPaused ? 'Paused' : 'Active'}
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          )}
        </aside>

        {/* Content Pane */}
        <main style={styles.content}>

          {/* ── TAB: OVERVIEW ──────────────────────────────────────────── */}
          {activeTab === 'overview' && (
            <div className="fade-in" style={styles.panel}>
              <div style={styles.titleSection}>
                <h2>Workspace Analytics</h2>
                <p style={{ color: 'var(--text-secondary)' }}>Real-time cluster activity for the selected project</p>
              </div>

              {!selectedProjectId ? (
                <EmptyState icon="🏢" title="No project selected" subtitle="Select or create an organization and project to get started." />
              ) : !stats ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, color: 'var(--text-secondary)' }}>
                  <Spinner size={20} /> Loading dashboard metrics...
                </div>
              ) : (
                <>
                  {/* Stats grid */}
                  <div style={styles.statsGrid}>
                    {[
                      { label: 'Queues', value: stats.totalQueues, color: 'var(--text-primary)' },
                      { label: 'Running Jobs', value: stats.runningCount, color: 'var(--color-success)' },
                      { label: 'Jobs In Backlog', value: stats.queuedCount, color: '#818cf8' },
                      { label: 'Completed Today', value: stats.completedCount, color: 'var(--color-success)' },
                      { label: 'Failures (DLQ)', value: stats.failedCount, color: 'var(--color-danger)' },
                      { label: 'Worker Nodes', value: workers.length, color: 'var(--color-secondary)' },
                      { label: 'Active Schedules', value: scheduledJobs.filter(s => s.status === 'ACTIVE').length, color: 'var(--color-warning)' },
                      { label: 'Retry Policies', value: retryPolicies.length, color: 'var(--text-secondary)' },
                    ].map(card => (
                      <div key={card.label} className="glass-card" style={styles.statsCard}>
                        <span style={styles.statsCardLabel}>{card.label}</span>
                        <span style={{ ...styles.statsCardVal, color: card.color }}>{card.value ?? '—'}</span>
                      </div>
                    ))}
                  </div>

                  {/* Throughput chart */}
                  {stats.throughputChart && (
                    <div className="glass-card" style={styles.chartContainer}>
                      <h3 style={styles.cardTitle}>Hourly Execution Throughput (Last 24 Hours)</h3>
                      <div style={{ width: '100%', height: 300 }}>
                        <ResponsiveContainer>
                          <AreaChart data={stats.throughputChart} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                            <defs>
                              <linearGradient id="colorCompleted" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="var(--color-success)" stopOpacity={0.4} />
                                <stop offset="95%" stopColor="var(--color-success)" stopOpacity={0} />
                              </linearGradient>
                              <linearGradient id="colorFailed" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="var(--color-danger)" stopOpacity={0.4} />
                                <stop offset="95%" stopColor="var(--color-danger)" stopOpacity={0} />
                              </linearGradient>
                            </defs>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                            <XAxis dataKey="hour" stroke="var(--text-secondary)" fontSize={11} />
                            <YAxis stroke="var(--text-secondary)" fontSize={11} />
                            <Tooltip contentStyle={{ backgroundColor: 'var(--bg-secondary)', border: '1px solid var(--border-color)', borderRadius: 8 }} />
                            <Legend />
                            <Area name="Successful" type="monotone" dataKey="completed" stroke="var(--color-success)" fillOpacity={1} fill="url(#colorCompleted)" />
                            <Area name="Failed" type="monotone" dataKey="failed" stroke="var(--color-danger)" fillOpacity={1} fill="url(#colorFailed)" />
                          </AreaChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}

                  {/* Queue overview grid */}
                  <div style={styles.overviewSection}>
                    <h3 style={styles.cardTitle}>Queue Overview</h3>
                    {queues.length === 0 ? (
                      <EmptyState icon="📥" title="No queues yet" subtitle='Create a queue from the "Active Job Queues" tab to get started.' />
                    ) : (
                      <div style={styles.queueGrid}>
                        {queues.map(queue => (
                          <div key={queue.id} className="glass-card" style={styles.queueOverviewCard}>
                            <div style={styles.queueOverviewHeader}>
                              <h4>{queue.name}</h4>
                              <button
                                onClick={() => handleQueuePauseToggle(queue)}
                                disabled={togglingQueueId === queue.id}
                                style={{
                                  ...styles.toggleBtn,
                                  backgroundColor: queue.isPaused ? 'var(--color-danger-glow)' : 'var(--color-success-glow)',
                                  borderColor: queue.isPaused ? 'var(--color-danger)' : 'var(--color-success)',
                                  color: queue.isPaused ? 'var(--color-danger)' : 'var(--color-success)',
                                  opacity: togglingQueueId === queue.id ? 0.6 : 1,
                                }}
                              >
                                {togglingQueueId === queue.id ? '...' : queue.isPaused ? '▶ Resume' : '⏸ Pause'}
                              </button>
                            </div>
                            <div style={styles.queueOverviewBody}>
                              <div style={styles.metricRow}><span>Priority:</span><span>{queue.priority}</span></div>
                              <div style={styles.metricRow}><span>Concurrency:</span><span>{queue.concurrencyLimit} threads</span></div>
                              <div style={styles.metricRow}><span>Retry Policy:</span><span>{queue.retryPolicyName || 'None'}</span></div>
                              {queue.stats && (
                                <div style={styles.statsPreview}>
                                  <div style={styles.miniStatsCell}><span style={{ color: 'var(--text-secondary)' }}>Queued</span><strong>{queue.stats.queuedCount}</strong></div>
                                  <div style={styles.miniStatsCell}><span style={{ color: 'var(--color-success)' }}>Running</span><strong>{queue.stats.runningCount}</strong></div>
                                  <div style={styles.miniStatsCell}><span style={{ color: 'var(--color-danger)' }}>Failed</span><strong>{queue.stats.failedCount}</strong></div>
                                  <div style={styles.miniStatsCell}><span>Avg Exec</span><strong>{queue.stats.averageExecutionTimeSeconds?.toFixed(1)}s</strong></div>
                                </div>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          )}

          {/* ── TAB: QUEUES ────────────────────────────────────────────── */}
          {activeTab === 'queues' && (
            <div className="fade-in" style={styles.panel}>
              {!selectedQueue ? (
                <EmptyState
                  icon="📥"
                  title="Select a Queue from the sidebar"
                  subtitle="Configure priorities, dispatch jobs, and inspect logs in real time."
                />
              ) : (
                <div style={styles.queueWorkspace}>
                  <div style={styles.workspaceHeader}>
                    <div>
                      <h2>Queue: {selectedQueue.name}</h2>
                      <p style={{ color: 'var(--text-secondary)' }}>Project: {selectedQueue.projectName}</p>
                    </div>
                    <div style={styles.workspaceHeaderActions}>
                      <button
                        onClick={() => handleQueuePauseToggle(selectedQueue)}
                        disabled={togglingQueueId === selectedQueue.id}
                        className="btn-secondary"
                        style={{
                          borderColor: selectedQueue.isPaused ? 'var(--color-success)' : 'var(--color-danger)',
                          color: selectedQueue.isPaused ? 'var(--color-success)' : 'var(--color-danger)',
                          display: 'flex', alignItems: 'center', gap: 8,
                        }}
                      >
                        {togglingQueueId === selectedQueue.id
                          ? <><Spinner /> Processing...</>
                          : selectedQueue.isPaused ? '▶ Resume Dispatch' : '⏸ Pause Dispatch'}
                      </button>
                    </div>
                  </div>

                  {selectedQueue.stats && (
                    <div style={styles.statsGrid}>
                      {[
                        { label: 'Queued', value: selectedQueue.stats.queuedCount, color: '#818cf8' },
                        { label: 'Running', value: selectedQueue.stats.runningCount, color: 'var(--color-success)' },
                        { label: 'Completed', value: selectedQueue.stats.completedCount, color: 'var(--text-primary)' },
                        { label: 'Failed', value: selectedQueue.stats.failedCount, color: 'var(--color-danger)' },
                        { label: 'Avg Runtime', value: `${selectedQueue.stats.averageExecutionTimeSeconds?.toFixed(2)}s`, color: 'var(--text-secondary)' },
                      ].map(m => (
                        <div key={m.label} className="glass-card" style={styles.miniCard}>
                          <span style={styles.statsCardLabel}>{m.label}</span>
                          <span style={{ ...styles.miniCardVal, color: m.color }}>{m.value}</span>
                        </div>
                      ))}
                    </div>
                  )}

                  <div style={styles.workspaceSplit}>
                    {/* Dispatcher */}
                    <div className="glass-card" style={styles.dispatcherForm}>
                      <h3 style={styles.cardTitle}>⚡ Dispatch New Job</h3>
                      <form onSubmit={handleDispatchJob} style={styles.form}>
                        <div style={styles.formGroup}>
                          <label style={styles.label}>Job Type</label>
                          <select style={styles.select} value={jobType} onChange={(e: any) => setJobType(e.target.value)}>
                            <option value="IMMEDIATE">Immediate</option>
                            <option value="DELAYED">Delayed</option>
                            <option value="SCHEDULED">Scheduled</option>
                            <option value="BATCH">Batch Job</option>
                          </select>
                        </div>
                        {jobType !== 'BATCH' ? (
                          <div style={styles.formGroup}>
                            <label style={styles.label}>Job JSON Payload</label>
                            <textarea
                              className="glass-input"
                              style={{ fontFamily: 'var(--font-mono)', height: 80, resize: 'none' }}
                              value={jobPayload}
                              onChange={(e) => setJobPayload(e.target.value)}
                              required
                            />
                          </div>
                        ) : (
                          <div style={styles.formGroup}>
                            <label style={styles.label}>Batch Children Payloads (JSON Array)</label>
                            <textarea
                              className="glass-input"
                              style={{ fontFamily: 'var(--font-mono)', height: 80, resize: 'none' }}
                              value={batchPayloads}
                              onChange={(e) => setBatchPayloads(e.target.value)}
                              required
                            />
                          </div>
                        )}
                        <div style={styles.formRow}>
                          <div style={{ ...styles.formGroup, flex: 1 }}>
                            <label style={styles.label}>Priority (higher = first)</label>
                            <input type="number" className="glass-input" value={jobPriority} onChange={(e) => setJobPriority(Number(e.target.value))} required />
                          </div>
                          {jobType === 'DELAYED' && (
                            <div style={{ ...styles.formGroup, flex: 1 }}>
                              <label style={styles.label}>Delay (Seconds)</label>
                              <input type="number" className="glass-input" value={jobDelay} onChange={(e) => setJobDelay(Number(e.target.value))} required />
                            </div>
                          )}
                        </div>
                        {jobType === 'SCHEDULED' && (
                          <div style={styles.formGroup}>
                            <label style={styles.label}>Run Time (Local Timezone)</label>
                            <input type="datetime-local" className="glass-input" value={jobScheduledAt} onChange={(e) => setJobScheduledAt(e.target.value)} required />
                          </div>
                        )}
                        <button
                          type="submit"
                          className="btn-primary"
                          disabled={submittingJob}
                          style={{ marginTop: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                        >
                          {submittingJob ? <><Spinner /> Dispatching...</> : 'Launch Job'}
                        </button>
                      </form>
                    </div>

                    {/* Job Explorer */}
                    <div className="glass-card" style={styles.jobExplorer}>
                      <div style={styles.sectionHeader}>
                        <h3 style={styles.cardTitle}>📋 Job Explorer</h3>
                        <div style={styles.filterControls}>
                          <select style={styles.miniSelect} value={filterStatus} onChange={(e) => { setFilterStatus(e.target.value); setCurrentPage(0); }}>
                            <option value="">All Statuses</option>
                            <option value="QUEUED">Queued</option>
                            <option value="SCHEDULED">Scheduled</option>
                            <option value="CLAIMED">Claimed</option>
                            <option value="RUNNING">Running</option>
                            <option value="COMPLETED">Completed</option>
                            <option value="FAILED">Failed</option>
                            <option value="DEAD_LETTER">Dead Letter</option>
                          </select>
                          <select style={styles.miniSelect} value={filterType} onChange={(e) => { setFilterType(e.target.value); setCurrentPage(0); }}>
                            <option value="">All Types</option>
                            <option value="IMMEDIATE">Immediate</option>
                            <option value="DELAYED">Delayed</option>
                            <option value="SCHEDULED">Scheduled</option>
                            <option value="RECURRING">Recurring</option>
                            <option value="BATCH">Batch</option>
                          </select>
                        </div>
                      </div>

                      {loadingJobs ? (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 24, color: 'var(--text-secondary)' }}>
                          <Spinner size={18} /> Loading jobs...
                        </div>
                      ) : (
                        <div style={styles.tableScroll}>
                          <table style={styles.table}>
                            <thead>
                              <tr style={styles.tr}>
                                <th style={styles.th}>ID</th>
                                <th style={styles.th}>Type</th>
                                <th style={styles.th}>Status</th>
                                <th style={styles.th}>Attempts</th>
                                <th style={styles.th}>Scheduled Run</th>
                                <th style={styles.th}>Actions</th>
                              </tr>
                            </thead>
                            <tbody>
                              {jobs.length === 0 && (
                                <tr>
                                  <td colSpan={6} style={styles.tdEmpty}>
                                    No jobs found matching the current filters.
                                  </td>
                                </tr>
                              )}
                              {jobs.map(job => (
                                <tr key={job.id} style={styles.tr}>
                                  <td style={styles.td}>#{job.id}</td>
                                  <td style={styles.td}><span style={styles.jobTypeBadge}>{job.type}</span></td>
                                  <td style={styles.td}><StatusBadge status={job.status} /></td>
                                  <td style={styles.td}>{job.attemptCount}/{job.maxAttempts}</td>
                                  <td style={styles.td}>{new Date(job.scheduledAt).toLocaleString()}</td>
                                  <td style={styles.td}>
                                    <div style={styles.actionRow}>
                                      <button onClick={() => setActiveLogJobId(job.id)} className="btn-secondary" style={styles.actionBtn}>📄 Logs</button>
                                      {(job.status === 'QUEUED' || job.status === 'SCHEDULED' || job.status === 'RUNNING') && (
                                        <button onClick={() => handleCancelJob(job.id)} className="btn-secondary" style={{ ...styles.actionBtn, color: 'var(--color-danger)' }}>✕ Cancel</button>
                                      )}
                                      {(job.status === 'FAILED' || job.status === 'DEAD_LETTER') && (
                                        <button onClick={() => handleRetryJob(job.id)} className="btn-secondary" style={{ ...styles.actionBtn, color: 'var(--color-success)' }}>🔄 Retry</button>
                                      )}
                                    </div>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}

                      {totalPages > 1 && (
                        <div style={styles.pagination}>
                          <button disabled={currentPage === 0} onClick={() => setCurrentPage(p => Math.max(0, p - 1))} className="btn-secondary" style={styles.pageBtn}>◀ Prev</button>
                          <span style={styles.pageIndicator}>Page {currentPage + 1} of {totalPages}</span>
                          <button disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage(p => Math.min(totalPages - 1, p + 1))} className="btn-secondary" style={styles.pageBtn}>Next ▶</button>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ── TAB: SCHEDULES ─────────────────────────────────────────── */}
          {activeTab === 'cron' && (
            <div className="fade-in" style={styles.panel}>
              <div style={styles.titleSection}>
                <h2>Quartz Schedules</h2>
                <p style={{ color: 'var(--text-secondary)' }}>Register schedules that automatically enqueue jobs based on your defined timers.</p>
              </div>

              <div style={styles.workspaceSplit}>
                {/* Form */}
                <div className="glass-card" style={styles.dispatcherForm}>
                  <h3 style={styles.cardTitle}>⏱️ Register Schedule</h3>
                  <form onSubmit={handleCreateScheduledJob} style={styles.form}>
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Schedule Name</label>
                      <input type="text" className="glass-input" placeholder="e.g. daily-cleanup-task" value={scheduleName} onChange={(e) => setScheduleName(e.target.value)} required />
                    </div>
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Schedule Type</label>
                      <select className="glass-input" value={scheduleType} onChange={(e) => setScheduleType(e.target.value as any)}>
                        <option value="CRON">CRON Expression</option>
                        <option value="FIXED_INTERVAL">Fixed Interval</option>
                        <option value="ONE_TIME">One Time</option>
                        <option value="DELAYED">Delayed</option>
                      </select>
                    </div>
                    {scheduleType === 'CRON' && (
                      <div style={styles.formGroup}>
                        <label style={styles.label}>Cron Expression (Quartz Format)</label>
                        <input type="text" className="glass-input" value={cronExpression} onChange={(e) => setCronExpression(e.target.value)} required />
                        <span style={styles.helpText}>Seconds Minutes Hours DayOfMonth Month DayOfWeek [Year]</span>
                      </div>
                    )}
                    {scheduleType === 'FIXED_INTERVAL' && (
                      <div style={styles.formGroup}>
                        <label style={styles.label}>Interval (Seconds)</label>
                        <input type="number" className="glass-input" value={intervalSeconds} onChange={(e) => setIntervalSeconds(Number(e.target.value))} required />
                      </div>
                    )}
                    {(scheduleType === 'ONE_TIME' || scheduleType === 'DELAYED') && (
                      <div style={styles.formGroup}>
                        <label style={styles.label}>Scheduled At</label>
                        <input type="datetime-local" className="glass-input" value={scheduledAtTime} onChange={(e) => setScheduledAtTime(e.target.value)} />
                        <span style={styles.helpText}>Leave blank for immediate one-time execution</span>
                      </div>
                    )}
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Target Queue</label>
                      <select className="glass-input" value={scheduleQueueId || queues[0]?.id || ''} onChange={(e) => setScheduleQueueId(Number(e.target.value))}>
                        {queues.map(q => <option key={q.id} value={q.id}>{q.name}</option>)}
                      </select>
                    </div>
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Template Payload (JSON)</label>
                      <textarea className="glass-input" style={{ fontFamily: 'var(--font-mono)', height: 80, resize: 'none' }} value={schedulePayload} onChange={(e) => setSchedulePayload(e.target.value)} required />
                    </div>
                    <button type="submit" className="btn-primary" disabled={submittingSchedule}
                      style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                      {submittingSchedule ? <><Spinner /> Registering...</> : 'Register Schedule'}
                    </button>
                  </form>
                </div>

                {/* List */}
                <div className="glass-card" style={styles.jobExplorer}>
                  <div style={styles.sectionHeader}>
                    <h3 style={styles.cardTitle}>Registered Schedules</h3>
                    <input
                      type="text"
                      className="glass-input"
                      placeholder="🔍 Search..."
                      value={scheduleSearch}
                      onChange={e => setScheduleSearch(e.target.value)}
                      style={{ fontSize: 12, padding: '6px 10px', width: 160 }}
                    />
                  </div>
                  {filteredSchedules.length === 0 ? (
                    <EmptyState
                      icon="⏱️"
                      title={scheduleSearch ? 'No schedules match your search.' : 'No schedules yet.'}
                      subtitle={!scheduleSearch ? 'Register a schedule to automate job dispatching.' : undefined}
                    />
                  ) : (
                    <div style={styles.tableScroll}>
                      <table style={styles.table}>
                        <thead>
                          <tr style={styles.tr}>
                            <th style={styles.th}>Name / Type</th>
                            <th style={styles.th}>Pattern</th>
                            <th style={styles.th}>Status</th>
                            <th style={styles.th}>Next Fire Time</th>
                            <th style={styles.th}>Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredSchedules.map(job => (
                            <tr key={job.id} style={styles.tr}>
                              <td style={styles.td}>
                                <div style={{ fontWeight: 'bold' }}>{job.name}</div>
                                <div style={{ fontSize: 10, color: 'var(--color-primary)' }}>{job.scheduleType}</div>
                              </td>
                              <td style={{ ...styles.td, fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                                {job.scheduleType === 'CRON' ? job.cronExpression
                                  : job.scheduleType === 'FIXED_INTERVAL' ? `Every ${job.intervalSeconds}s`
                                  : job.scheduledAt ? new Date(job.scheduledAt).toLocaleString() : 'Immediate'}
                              </td>
                              <td style={styles.td}>
                                <span className="badge" style={{
                                  backgroundColor: job.status === 'ACTIVE' ? 'rgba(16,185,129,0.15)'
                                    : job.status === 'PAUSED' ? 'rgba(245,158,11,0.15)'
                                    : 'rgba(107,114,128,0.15)',
                                  color: job.status === 'ACTIVE' ? 'var(--color-success)'
                                    : job.status === 'PAUSED' ? 'var(--color-warning)'
                                    : 'var(--text-muted)',
                                  border: `1px solid ${job.status === 'ACTIVE' ? 'rgba(16,185,129,0.3)'
                                    : job.status === 'PAUSED' ? 'rgba(245,158,11,0.3)'
                                    : 'rgba(107,114,128,0.3)'}`,
                                }}>
                                  {job.status}
                                </span>
                              </td>
                              <td style={styles.td}>
                                {job.nextFireTime ? new Date(job.nextFireTime).toLocaleString()
                                  : job.status === 'COMPLETED' ? 'Completed' : 'N/A'}
                              </td>
                              <td style={styles.td}>
                                <div style={styles.actionRow}>
                                  {job.status === 'ACTIVE' && (
                                    <button onClick={() => handlePauseSchedule(job.id)} className="btn-secondary" style={styles.actionBtn}>⏸ Pause</button>
                                  )}
                                  {job.status === 'PAUSED' && (
                                    <button onClick={() => handleResumeSchedule(job.id)} className="btn-secondary" style={styles.actionBtn}>▶ Resume</button>
                                  )}
                                  <button
                                    onClick={() => handleDeleteScheduledJob(job.id, job.name)}
                                    className="btn-secondary"
                                    disabled={deletingScheduleId === job.id}
                                    style={{ ...styles.actionBtn, color: 'var(--color-danger)', display: 'flex', alignItems: 'center', gap: 4 }}
                                  >
                                    {deletingScheduleId === job.id ? <><Spinner size={12} /> Deleting</> : '✕ Delete'}
                                  </button>
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* ── TAB: WORKERS ───────────────────────────────────────────── */}
          {activeTab === 'workers' && (
            <div className="fade-in" style={styles.panel}>
              <div style={styles.titleSection}>
                <h2>Cluster Worker Nodes</h2>
                <p style={{ color: 'var(--text-secondary)' }}>Live monitoring of distributed job processors and their heartbeats</p>
              </div>

              <div className="glass-card">
                <div style={styles.sectionHeader}>
                  <h3 style={styles.cardTitle}>Active / Inactive Nodes</h3>
                  <button
                    onClick={loadWorkers}
                    disabled={loadingWorkers}
                    className="btn-secondary"
                    style={{ ...styles.headerBtn, display: 'flex', alignItems: 'center', gap: 8 }}
                  >
                    {loadingWorkers ? <><Spinner size={14} /> Refreshing</> : '🔄 Refresh Nodes'}
                  </button>
                </div>

                {loadingWorkers && workers.length === 0 ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 24, color: 'var(--text-secondary)' }}>
                    <Spinner size={18} /> Loading workers...
                  </div>
                ) : workers.length === 0 ? (
                  <EmptyState icon="💻" title="No worker nodes registered" subtitle="Start the worker Docker service to register nodes." />
                ) : (
                  <div style={styles.tableScroll}>
                    <table style={styles.table}>
                      <thead>
                        <tr style={styles.tr}>
                          <th style={styles.th}>Worker Node</th>
                          <th style={styles.th}>Host</th>
                          <th style={styles.th}>Status</th>
                          <th style={styles.th}>Running Jobs</th>
                          <th style={styles.th}>Completed Jobs</th>
                          <th style={styles.th}>Last Heartbeat</th>
                        </tr>
                      </thead>
                      <tbody>
                        {workers.map(worker => {
                          const isActive = worker.status === 'ACTIVE';
                          const heartbeatAge = worker.lastHeartbeatAt
                            ? Math.floor((Date.now() - new Date(worker.lastHeartbeatAt).getTime()) / 1000)
                            : null;
                          const isStale = heartbeatAge !== null && heartbeatAge > 60;
                          return (
                            <tr key={worker.workerId} style={styles.tr}>
                              <td style={{ ...styles.td, fontWeight: 'bold' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                  <span style={{
                                    width: 8, height: 8, borderRadius: '50%',
                                    backgroundColor: isActive && !isStale ? 'var(--color-success)' : 'var(--color-danger)',
                                    display: 'inline-block',
                                    boxShadow: isActive && !isStale ? '0 0 8px var(--color-success)' : 'none',
                                  }} />
                                  💻 {worker.workerId}
                                </div>
                              </td>
                              <td style={styles.td}>{worker.hostname}</td>
                              <td style={styles.td}>
                                <span className="badge" style={{
                                  color: isActive && !isStale ? 'var(--color-success)' : 'var(--color-danger)',
                                  backgroundColor: isActive && !isStale ? 'var(--color-success-glow)' : 'var(--color-danger-glow)',
                                  border: `1px solid ${isActive && !isStale ? 'var(--color-success)' : 'var(--color-danger)'}`,
                                }}>
                                  {isStale ? 'STALE' : worker.status}
                                </span>
                              </td>
                              <td style={{ ...styles.td, color: 'var(--color-success)', fontWeight: 600 }}>
                                {worker.runningJobCount ?? '—'}
                              </td>
                              <td style={{ ...styles.td, color: 'var(--text-secondary)' }}>
                                {worker.completedJobCount ?? '—'}
                              </td>
                              <td style={styles.td}>
                                <div style={{ fontSize: 12 }}>
                                  <div>{worker.lastHeartbeatAt ? relativeTime(worker.lastHeartbeatAt) : 'N/A'}</div>
                                  {isStale && <div style={{ color: 'var(--color-warning)', fontSize: 11 }}>⚠️ No heartbeat {heartbeatAge}s</div>}
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ── TAB: RETRY POLICIES ────────────────────────────────────── */}
          {activeTab === 'retry' && (
            <div className="fade-in" style={styles.panel}>
              <div style={styles.titleSection}>
                <h2>Retry Policies</h2>
                <p style={{ color: 'var(--text-secondary)' }}>Configure FIXED, LINEAR, or EXPONENTIAL delay formulas for failing jobs.</p>
              </div>

              <div style={styles.workspaceSplit}>
                {/* Create form */}
                <div className="glass-card" style={{ ...styles.dispatcherForm, flex: '1 1 320px' }}>
                  <h3 style={styles.cardTitle}>➕ Create Policy</h3>
                  <form onSubmit={handleCreateRetryPolicy} style={styles.form}>
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Policy Name</label>
                      <input type="text" className="glass-input" placeholder="e.g. exponential-backoff-3x" value={rpName} onChange={(e) => setRpName(e.target.value)} required />
                    </div>
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Backoff Strategy</label>
                      <select style={styles.select} value={rpType} onChange={(e: any) => setRpType(e.target.value)}>
                        <option value="FIXED">Fixed (retry every N seconds)</option>
                        <option value="LINEAR">Linear (increases by N each time)</option>
                        <option value="EXPONENTIAL">Exponential (doubles base delay)</option>
                      </select>
                    </div>
                    <div style={styles.formRow}>
                      <div style={{ ...styles.formGroup, flex: 1 }}>
                        <label style={styles.label}>Base Delay (s)</label>
                        <input type="number" className="glass-input" value={rpBaseDelay} onChange={(e) => setRpBaseDelay(Number(e.target.value))} required min={1} />
                      </div>
                      <div style={{ ...styles.formGroup, flex: 1 }}>
                        <label style={styles.label}>Max Delay Cap (s)</label>
                        <input type="number" className="glass-input" value={rpMaxDelay} onChange={(e) => setRpMaxDelay(Number(e.target.value))} required min={1} />
                      </div>
                    </div>
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Max Attempts (DLQ threshold)</label>
                      <input type="number" className="glass-input" value={rpMaxAttempts} onChange={(e) => setRpMaxAttempts(Number(e.target.value))} required min={1} />
                    </div>
                    <button type="submit" className="btn-primary" disabled={submittingRetryPolicy}
                      style={{ marginTop: 12, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                      {submittingRetryPolicy ? <><Spinner /> Creating...</> : 'Create and Register Policy'}
                    </button>
                  </form>
                </div>

                {/* Policies list */}
                <div className="glass-card" style={{ flex: '2 2 500px' }}>
                  <div style={styles.sectionHeader}>
                    <h3 style={styles.cardTitle}>📋 Existing Policies</h3>
                    <input
                      type="text"
                      className="glass-input"
                      placeholder="🔍 Search..."
                      value={retrySearch}
                      onChange={e => setRetrySearch(e.target.value)}
                      style={{ fontSize: 12, padding: '6px 10px', width: 160 }}
                    />
                  </div>
                  {filteredRetryPolicies.length === 0 ? (
                    <EmptyState
                      icon="🔄"
                      title={retrySearch ? 'No policies match your search.' : 'No retry policies yet.'}
                      subtitle={!retrySearch ? 'Create a policy to assign automatic retry behaviour to queues.' : undefined}
                    />
                  ) : (
                    <div style={styles.tableScroll}>
                      <table style={styles.table}>
                        <thead>
                          <tr style={styles.tr}>
                            <th style={styles.th}>Name</th>
                            <th style={styles.th}>Strategy</th>
                            <th style={styles.th}>Base Delay</th>
                            <th style={styles.th}>Max Delay</th>
                            <th style={styles.th}>Max Attempts</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredRetryPolicies.map(policy => (
                            <tr key={policy.id} style={styles.tr}>
                              <td style={{ ...styles.td, fontWeight: 600 }}>{policy.name}</td>
                              <td style={styles.td}>
                                <span className="badge" style={{ backgroundColor: 'rgba(99,102,241,0.15)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.3)' }}>
                                  {policy.strategyType}
                                </span>
                              </td>
                              <td style={styles.td}>{policy.baseDelaySeconds}s</td>
                              <td style={styles.td}>{policy.maxDelaySeconds}s</td>
                              <td style={styles.td}>{policy.maxAttempts}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* ── TAB: LIVE EVENTS ───────────────────────────────────────── */}
          {activeTab === 'events' && (
            <div className="fade-in" style={styles.panel}>
              <div style={styles.titleSection}>
                <h2>Live Event Feed</h2>
                <p style={{ color: 'var(--text-secondary)' }}>Real-time streaming of all cluster events via WebSocket</p>
              </div>

              {events.length === 0 ? (
                <EmptyState icon="📡" title="Listening for events…" subtitle="Events will appear here as soon as jobs, queues, workers, or schedules change." />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {events.map((ev, index) => {
                    const cfg = getEventConfig(ev.type || '');
                    return (
                      <div
                        key={index}
                        className="fade-in"
                        style={{
                          padding: '14px 18px',
                          borderRadius: 10,
                          backgroundColor: 'rgba(255,255,255,0.03)',
                          borderLeft: `4px solid ${cfg.color}`,
                          display: 'flex',
                          gap: 14,
                          alignItems: 'flex-start',
                        }}
                      >
                        <span style={{ fontSize: 20, flexShrink: 0 }}>{cfg.icon}</span>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                            <strong style={{ color: cfg.color, fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                              {ev.type || 'SYSTEM'}
                            </strong>
                            <span style={{ fontSize: 11, color: 'var(--text-muted)', flexShrink: 0 }}>
                              {ev.timestamp ? relativeTime(ev.timestamp) : relativeTime(new Date().toISOString())}
                            </span>
                          </div>
                          <p style={{ fontSize: 13, color: 'var(--text-primary)', marginTop: 4, wordBreak: 'break-word' }}>{ev.message}</p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

        </main>
      </div>

      {/* Logs Modal */}
      {activeLogJobId !== null && (
        <LogsModal jobId={activeLogJobId} onClose={() => setActiveLogJobId(null)} />
      )}

      {/* Modal — New Org */}
      {showNewOrg && (
        <div style={styles.overlay}>
          <div className="glass-card fade-in" style={styles.dialog}>
            <h3>Create New Organization</h3>
            <form onSubmit={handleCreateOrg} style={styles.form}>
              <input type="text" className="glass-input" placeholder="Organization Name" value={newOrgName} onChange={(e) => setNewOrgName(e.target.value)} required autoFocus />
              <div style={styles.dialogActions}>
                <button type="button" onClick={() => setShowNewOrg(false)} className="btn-secondary">Cancel</button>
                <button type="submit" className="btn-primary">Create</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal — New Project */}
      {showNewProject && (
        <div style={styles.overlay}>
          <div className="glass-card fade-in" style={styles.dialog}>
            <h3>Create New Project</h3>
            <form onSubmit={handleCreateProject} style={styles.form}>
              <input type="text" className="glass-input" placeholder="Project Name" value={newProjectName} onChange={(e) => setNewProjectName(e.target.value)} required autoFocus />
              <div style={styles.dialogActions}>
                <button type="button" onClick={() => setShowNewProject(false)} className="btn-secondary">Cancel</button>
                <button type="submit" className="btn-primary">Create</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal — New Queue */}
      {showNewQueue && (
        <div style={styles.overlay}>
          <div className="glass-card fade-in" style={styles.dialog}>
            <h3>Create Job Queue</h3>
            <form onSubmit={handleCreateQueue} style={styles.form}>
              <div style={styles.formGroup}>
                <label style={styles.label}>Queue Name</label>
                <input type="text" className="glass-input" placeholder="e.g. image-processing" value={newQueueName} onChange={(e) => setNewQueueName(e.target.value)} required autoFocus />
              </div>
              <div style={styles.formRow}>
                <div style={{ ...styles.formGroup, flex: 1 }}>
                  <label style={styles.label}>Priority Rank</label>
                  <input type="number" className="glass-input" value={newQueuePriority} onChange={(e) => setNewQueuePriority(Number(e.target.value))} required />
                </div>
                <div style={{ ...styles.formGroup, flex: 1 }}>
                  <label style={styles.label}>Max Concurrency</label>
                  <input type="number" className="glass-input" value={newQueueConcurrency} onChange={(e) => setNewQueueConcurrency(Number(e.target.value))} required min={1} />
                </div>
              </div>
              <div style={styles.formGroup}>
                <label style={styles.label}>Retry Policy</label>
                <select style={styles.select} value={newQueuePolicyId || ''} onChange={(e) => setNewQueuePolicyId(e.target.value ? Number(e.target.value) : null)}>
                  <option value="">None (No retries)</option>
                  {retryPolicies.map(policy => (
                    <option key={policy.id} value={policy.id}>{policy.name} ({policy.strategyType})</option>
                  ))}
                </select>
              </div>
              <div style={styles.dialogActions}>
                <button type="button" onClick={() => setShowNewQueue(false)} className="btn-secondary">Cancel</button>
                <button type="submit" className="btn-primary" disabled={submittingQueue}
                  style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {submittingQueue ? <><Spinner /> Creating...</> : 'Create Queue'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Styles ──────────────────────────────────────────────────────────────────
const styles: Record<string, React.CSSProperties> = {
  appContainer: { display: 'flex', flexDirection: 'column', minHeight: '100vh', backgroundColor: 'var(--bg-primary)', color: 'var(--text-primary)' },
  header: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 24px', borderBottom: '1px solid var(--border-color)', backgroundColor: 'var(--bg-secondary)' },
  headerBrand: { display: 'flex', alignItems: 'center', gap: 8 },
  brandIcon: { fontSize: 24 },
  brandName: { fontSize: 18, fontWeight: 700, fontFamily: 'var(--font-title)' },
  workspaceSelector: { display: 'flex', alignItems: 'center', gap: 24 },
  selectGroup: { display: 'flex', alignItems: 'center', gap: 8 },
  selectLabel: { fontSize: 12, fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase' },
  select: { backgroundColor: '#05070c', border: '1px solid var(--border-color)', color: 'var(--text-primary)', padding: '8px 12px', borderRadius: 6, outline: 'none', fontSize: 14 },
  addButton: { backgroundColor: 'rgba(255,255,255,0.05)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', padding: '6px 10px', borderRadius: 6, cursor: 'pointer', fontWeight: 'bold' },
  headerUser: { display: 'flex', alignItems: 'center', gap: 16 },
  userName: { fontSize: 14, fontWeight: 500 },
  logoutBtn: { padding: '8px 16px', fontSize: 13 },
  mainLayout: { display: 'flex', flex: 1, overflow: 'hidden' },
  sidebar: { width: 280, borderRight: '1px solid var(--border-color)', backgroundColor: 'rgba(19,27,46,0.3)', padding: 24, display: 'flex', flexDirection: 'column', gap: 24, overflowY: 'auto' },
  sidebarSection: { display: 'flex', flexDirection: 'column', gap: 12 },
  sectionHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  sidebarTitle: { fontSize: 12, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' },
  tabList: { display: 'flex', flexDirection: 'column', gap: 8 },
  tabButton: { display: 'flex', alignItems: 'center', padding: '10px 14px', borderRadius: 8, border: '1px solid transparent', color: 'var(--text-primary)', fontSize: 14, fontWeight: 500, cursor: 'pointer', textAlign: 'left', transition: 'all 0.2s' },
  queueList: { display: 'flex', flexDirection: 'column', gap: 10 },
  queueSelector: { display: 'flex', flexDirection: 'column', gap: 6, padding: 12, borderRadius: 10, border: '1px solid', color: 'var(--text-primary)', cursor: 'pointer', textAlign: 'left', transition: 'all 0.2s' },
  queueSelectorTitle: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontWeight: 600, fontSize: 14 },
  queueSelectorMeta: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12, color: 'var(--text-secondary)' },
  content: { flex: 1, padding: 32, overflowY: 'auto' },
  panel: { display: 'flex', flexDirection: 'column', gap: 32 },
  titleSection: { display: 'flex', flexDirection: 'column', gap: 6 },
  statsGrid: { display: 'flex', gap: 20, flexWrap: 'wrap' },
  statsCard: { flex: '1 1 180px', display: 'flex', flexDirection: 'column', gap: 8 },
  statsCardLabel: { fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' },
  statsCardVal: { fontSize: 32, fontWeight: 700, fontFamily: 'var(--font-title)' },
  chartContainer: { display: 'flex', flexDirection: 'column', gap: 16 },
  cardTitle: { fontSize: 16, fontWeight: 600 },
  overviewSection: { display: 'flex', flexDirection: 'column', gap: 16 },
  queueGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 20 },
  queueOverviewCard: { display: 'flex', flexDirection: 'column', gap: 16 },
  queueOverviewHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  queueOverviewBody: { display: 'flex', flexDirection: 'column', gap: 8 },
  metricRow: { display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--text-secondary)' },
  statsPreview: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8, marginTop: 12, borderTop: '1px solid var(--border-color)', paddingTop: 12 },
  miniStatsCell: { display: 'flex', flexDirection: 'column', fontSize: 11 },
  toggleBtn: { padding: '4px 8px', borderRadius: 4, fontSize: 11, fontWeight: 'bold', cursor: 'pointer', border: '1px solid' },
  queueWorkspace: { display: 'flex', flexDirection: 'column', gap: 24 },
  workspaceHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  workspaceHeaderActions: { display: 'flex', gap: 12 },
  miniCard: { flex: '1 1 140px', padding: '16px 20px' },
  miniCardVal: { fontSize: 22, fontWeight: 700, fontFamily: 'var(--font-title)' },
  workspaceSplit: { display: 'flex', gap: 24, alignItems: 'flex-start', flexWrap: 'wrap' },
  dispatcherForm: { flex: '1 1 320px', display: 'flex', flexDirection: 'column', gap: 20 },
  jobExplorer: { flex: '2 2 500px', display: 'flex', flexDirection: 'column', gap: 20 },
  filterControls: { display: 'flex', gap: 10 },
  miniSelect: { backgroundColor: '#05070c', border: '1px solid var(--border-color)', color: 'var(--text-secondary)', padding: '6px 10px', borderRadius: 4, outline: 'none', fontSize: 12 },
  tableScroll: { overflowX: 'auto' },
  table: { width: '100%', borderCollapse: 'collapse', textAlign: 'left' },
  tr: { borderBottom: '1px solid var(--border-color)' },
  th: { padding: '12px 16px', color: 'var(--text-muted)', fontSize: 12, fontWeight: 700, textTransform: 'uppercase' },
  td: { padding: '14px 16px', fontSize: 13, color: 'var(--text-primary)' },
  tdEmpty: { padding: 24, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 },
  jobTypeBadge: { fontFamily: 'var(--font-mono)', fontSize: 11, padding: '2px 6px', borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.05)' },
  actionRow: { display: 'flex', gap: 8, flexWrap: 'wrap' },
  actionBtn: { padding: '4px 8px', fontSize: 11 },
  pagination: { display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 16, marginTop: 12 },
  pageBtn: { padding: '6px 12px', fontSize: 12 },
  pageIndicator: { fontSize: 12, color: 'var(--text-secondary)' },
  helpText: { fontSize: 11, color: 'var(--text-muted)' },
  emptyText: { fontSize: 13, color: 'var(--text-muted)', textAlign: 'center' },
  overlay: { position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(5,7,12,0.8)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000, padding: 24, backdropFilter: 'blur(4px)' },
  dialog: { width: '100%', maxWidth: 480, display: 'flex', flexDirection: 'column', gap: 20 },
  dialogActions: { display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 12 },
  form: { display: 'flex', flexDirection: 'column', gap: 16 },
  formGroup: { display: 'flex', flexDirection: 'column', gap: 6 },
  formRow: { display: 'flex', gap: 16 },
  label: { fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase' },
  smallBtn: { padding: '4px 8px', fontSize: 11 },
  headerBtn: { padding: '8px 14px', fontSize: 12 },
  badge: { display: 'inline-flex', alignItems: 'center', padding: '4px 10px', borderRadius: 9999, fontSize: 12, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' },
  logoBadge: { fontSize: 48 },
};
