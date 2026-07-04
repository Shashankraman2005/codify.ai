import React from 'react';

interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
  onConfirm,
  onCancel,
}) => {
  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(5, 7, 12, 0.85)',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        zIndex: 2000,
        padding: '24px',
        backdropFilter: 'blur(6px)',
      }}
      onClick={onCancel}
    >
      <div
        className="glass-card fade-in"
        style={{ width: '100%', maxWidth: '420px', display: 'flex', flexDirection: 'column', gap: '20px' }}
        onClick={e => e.stopPropagation()}
      >
        {/* Icon + Title */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <span style={{ fontSize: '28px' }}>{danger ? '⚠️' : '❓'}</span>
          <h3 style={{ fontSize: '17px', fontWeight: 700 }}>{title}</h3>
        </div>

        {/* Message */}
        <p style={{ fontSize: '14px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>{message}</p>

        {/* Actions */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '4px' }}>
          <button className="btn-secondary" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button
            onClick={onConfirm}
            style={{
              background: danger
                ? 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)'
                : 'linear-gradient(135deg, var(--color-primary) 0%, #4f46e5 100%)',
              color: 'white',
              border: 'none',
              padding: '12px 24px',
              borderRadius: '8px',
              fontWeight: 600,
              cursor: 'pointer',
              boxShadow: danger
                ? '0 4px 14px rgba(239,68,68,0.4)'
                : '0 4px 14px rgba(99,102,241,0.4)',
              fontFamily: 'var(--font-title)',
            }}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
};
