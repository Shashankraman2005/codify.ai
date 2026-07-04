import React, { useEffect } from 'react';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  id: string;
  type: ToastType;
  message: string;
}

interface ToastItemProps {
  toast: Toast;
  onRemove: (id: string) => void;
}

const ICONS: Record<ToastType, string> = {
  success: '✅',
  error: '❌',
  info: 'ℹ️',
  warning: '⚠️',
};

const COLORS: Record<ToastType, { bg: string; border: string; text: string }> = {
  success: {
    bg: 'rgba(16, 185, 129, 0.12)',
    border: 'rgba(16, 185, 129, 0.4)',
    text: '#34d399',
  },
  error: {
    bg: 'rgba(239, 68, 68, 0.12)',
    border: 'rgba(239, 68, 68, 0.4)',
    text: '#f87171',
  },
  info: {
    bg: 'rgba(99, 102, 241, 0.12)',
    border: 'rgba(99, 102, 241, 0.4)',
    text: '#818cf8',
  },
  warning: {
    bg: 'rgba(245, 158, 11, 0.12)',
    border: 'rgba(245, 158, 11, 0.4)',
    text: '#fbbf24',
  },
};

const ToastItem: React.FC<ToastItemProps> = ({ toast, onRemove }) => {
  const colors = COLORS[toast.type];

  useEffect(() => {
    const timer = setTimeout(() => onRemove(toast.id), 4000);
    return () => clearTimeout(timer);
  }, [toast.id, onRemove]);

  return (
    <div
      className="toast-item"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        padding: '14px 18px',
        borderRadius: '10px',
        backgroundColor: colors.bg,
        border: `1px solid ${colors.border}`,
        backdropFilter: 'blur(12px)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
        minWidth: '280px',
        maxWidth: '400px',
        cursor: 'pointer',
      }}
      onClick={() => onRemove(toast.id)}
    >
      <span style={{ fontSize: '18px', flexShrink: 0 }}>{ICONS[toast.type]}</span>
      <span style={{ fontSize: '14px', color: '#f3f4f6', flex: 1, lineHeight: 1.4 }}>
        {toast.message}
      </span>
      <span style={{ fontSize: '16px', color: 'rgba(255,255,255,0.3)', flexShrink: 0 }}>×</span>
    </div>
  );
};

interface ToastContainerProps {
  toasts: Toast[];
  onRemove: (id: string) => void;
}

export const ToastContainer: React.FC<ToastContainerProps> = ({ toasts, onRemove }) => {
  if (toasts.length === 0) return null;

  return (
    <div
      style={{
        position: 'fixed',
        top: '80px',
        right: '24px',
        zIndex: 9999,
        display: 'flex',
        flexDirection: 'column',
        gap: '10px',
        pointerEvents: 'none',
      }}
    >
      {toasts.map(toast => (
        <div key={toast.id} style={{ pointerEvents: 'all' }}>
          <ToastItem toast={toast} onRemove={onRemove} />
        </div>
      ))}
    </div>
  );
};
