import { useEffect, useState } from 'react';
import { connectWebSocket } from '../services/websocket';
import { Client } from '@stomp/stompjs';

export const useWebSocketSubscriptions = () => {
  const [client, setClient] = useState<Client | null>(null);

  useEffect(() => {
    const stompClient = connectWebSocket({
      onJobUpdate: (job) => {
        window.dispatchEvent(new CustomEvent('ws:jobUpdate', { detail: job }));
      },
      onQueueUpdate: (queue) => {
        window.dispatchEvent(new CustomEvent('ws:queueUpdate', { detail: queue }));
      },
      onWorkerUpdate: (worker) => {
        window.dispatchEvent(new CustomEvent('ws:workerUpdate', { detail: worker }));
      },
      onScheduleUpdate: (schedule) => {
        window.dispatchEvent(new CustomEvent('ws:scheduleUpdate', { detail: schedule }));
      },
      onDashboardUpdate: (stats) => {
        window.dispatchEvent(new CustomEvent('ws:dashboardUpdate', { detail: stats }));
      },
      onEventUpdate: (event) => {
        window.dispatchEvent(new CustomEvent('ws:eventUpdate', { detail: event }));
      }
    });

    setClient(stompClient);

    return () => {
      stompClient.deactivate();
    };
  }, []);

  return client;
};
