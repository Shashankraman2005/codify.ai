const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const getHeaders = () => {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
  };
};

const handleResponse = async (response: Response) => {
  if (!response.ok) {
    let errMsg = `Request failed with status ${response.status}`;
    try {
      const data = await response.json();
      errMsg = data.message || data.error || errMsg;
    } catch {
      // ignore
    }
    throw new Error(errMsg);
  }
  if (response.status === 240 || response.status === 204) {
    return null;
  }
  return response.json();
};

export const api = {
  // --- Auth ---
  login: async (loginData: any) => {
    const res = await fetch(`${BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(loginData),
    });
    const data = await handleResponse(res);
    if (data && data.token) {
      localStorage.setItem('token', data.token);
      localStorage.setItem('username', data.username);
    }
    return data;
  },

  register: async (registerData: any) => {
    const res = await fetch(`${BASE_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(registerData),
    });
    return handleResponse(res);
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
  },

  isAuthenticated: () => {
    return !!localStorage.getItem('token');
  },

  getUsername: () => {
    return localStorage.getItem('username') || '';
  },

  // --- Orgs & Projects ---
  getOrganizations: async () => {
    const res = await fetch(`${BASE_URL}/organizations`, { headers: getHeaders() });
    return handleResponse(res);
  },

  createOrganization: async (name: string) => {
    const res = await fetch(`${BASE_URL}/organizations`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ name }),
    });
    return handleResponse(res);
  },

  getProjects: async (orgId: number) => {
    const res = await fetch(`${BASE_URL}/organizations/${orgId}/projects`, { headers: getHeaders() });
    return handleResponse(res);
  },

  createProject: async (orgId: number, name: string) => {
    const res = await fetch(`${BASE_URL}/organizations/${orgId}/projects`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ name }),
    });
    return handleResponse(res);
  },

  // --- Queues ---
  getQueues: async (projectId: number) => {
    const res = await fetch(`${BASE_URL}/projects/${projectId}/queues`, { headers: getHeaders() });
    return handleResponse(res);
  },

  createQueue: async (projectId: number, queueData: any) => {
    const res = await fetch(`${BASE_URL}/projects/${projectId}/queues`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(queueData),
    });
    return handleResponse(res);
  },

  updateQueue: async (queueId: number, queueData: any) => {
    const res = await fetch(`${BASE_URL}/queues/${queueId}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(queueData),
    });
    return handleResponse(res);
  },

  pauseQueue: async (queueId: number) => {
    const res = await fetch(`${BASE_URL}/queues/${queueId}/pause`, {
      method: 'POST',
      headers: getHeaders(),
    });
    return handleResponse(res);
  },

  resumeQueue: async (queueId: number) => {
    const res = await fetch(`${BASE_URL}/queues/${queueId}/resume`, {
      method: 'POST',
      headers: getHeaders(),
    });
    return handleResponse(res);
  },

  // --- Retry Policies ---
  getRetryPolicies: async () => {
    const res = await fetch(`${BASE_URL}/retry-policies`, { headers: getHeaders() });
    return handleResponse(res);
  },

  createRetryPolicy: async (policyData: any) => {
    const res = await fetch(`${BASE_URL}/retry-policies`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(policyData),
    });
    return handleResponse(res);
  },

  // --- Schedules (Quartz) ---
  getScheduledJobs: async (projectId: number) => {
    const res = await fetch(`${BASE_URL}/projects/${projectId}/schedules`, { headers: getHeaders() });
    return handleResponse(res);
  },

  createScheduledJob: async (projectId: number, data: any) => {
    const res = await fetch(`${BASE_URL}/projects/${projectId}/schedules`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse(res);
  },

  deleteScheduledJob: async (id: number) => {
    const res = await fetch(`${BASE_URL}/schedules/${id}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    if (!res.ok) {
      throw new Error('Failed to delete schedule');
    }
  },

  pauseSchedule: async (id: number) => {
    const res = await fetch(`${BASE_URL}/schedules/${id}/pause`, {
      method: 'PUT',
      headers: getHeaders(),
    });
    return handleResponse(res);
  },

  resumeSchedule: async (id: number) => {
    const res = await fetch(`${BASE_URL}/schedules/${id}/resume`, {
      method: 'PUT',
      headers: getHeaders(),
    });
    return handleResponse(res);
  },

  // --- Jobs ---
  createJob: async (jobData: any) => {
    const res = await fetch(`${BASE_URL}/jobs`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(jobData),
    });
    return handleResponse(res);
  },

  getJobById: async (jobId: number) => {
    const res = await fetch(`${BASE_URL}/jobs/${jobId}`, { headers: getHeaders() });
    return handleResponse(res);
  },

  getJobLogs: async (jobId: number) => {
    const res = await fetch(`${BASE_URL}/jobs/${jobId}/logs`, { headers: getHeaders() });
    return handleResponse(res);
  },

  cancelJob: async (jobId: number) => {
    const res = await fetch(`${BASE_URL}/jobs/${jobId}/cancel`, {
      method: 'POST',
      headers: getHeaders(),
    });
    return handleResponse(res);
  },

  retryJob: async (jobId: number) => {
    const res = await fetch(`${BASE_URL}/jobs/${jobId}/retry`, {
      method: 'POST',
      headers: getHeaders(),
    });
    return handleResponse(res);
  },

  getJobs: async (filters: any) => {
    const params = new URLSearchParams();
    if (filters.queueId) params.append('queueId', filters.queueId);
    if (filters.status) params.append('status', filters.status);
    if (filters.type) params.append('type', filters.type);
    if (filters.startDate) params.append('startDate', filters.startDate);
    if (filters.endDate) params.append('endDate', filters.endDate);
    params.append('page', filters.page || 0);
    params.append('size', filters.size || 20);

    const res = await fetch(`${BASE_URL}/jobs?${params.toString()}`, { headers: getHeaders() });
    return handleResponse(res);
  },

  // --- Workers & Dashboard Stats ---
  getWorkers: async () => {
    const res = await fetch(`${BASE_URL}/workers`, { headers: getHeaders() });
    return handleResponse(res);
  },

  getDashboardStats: async (projectId: number) => {
    const res = await fetch(`${BASE_URL}/dashboard/stats?projectId=${projectId}`, { headers: getHeaders() });
    return handleResponse(res);
  },
};
