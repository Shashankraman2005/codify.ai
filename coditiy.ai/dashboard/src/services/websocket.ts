import { Client } from '@stomp/stompjs';

export interface StompCallbacks {
  onJobUpdate: (job: any) => void;
  onQueueUpdate: (queue: any) => void;
  onWorkerUpdate: (worker: any) => void;
  onScheduleUpdate: (schedule: any) => void;
  onDashboardUpdate: (stats: any) => void;
  onEventUpdate: (event: any) => void;
}

export const connectWebSocket = (callbacks: StompCallbacks): Client => {
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const brokerURL = import.meta.env.VITE_WS_URL || `${wsProtocol}//localhost:8080/ws`;

  console.log(`Connecting to WebSocket STOMP broker at: ${brokerURL}`);

  const client = new Client({
    brokerURL,
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  });

  client.onConnect = (frame) => {
    console.log('Connected to WebSocket STOMP: ', frame);

    client.subscribe(`/topic/jobs`, (message) => {
      try { callbacks.onJobUpdate(JSON.parse(message.body)); } catch (e) {}
    });

    client.subscribe(`/topic/queues`, (message) => {
      try { callbacks.onQueueUpdate(JSON.parse(message.body)); } catch (e) {}
    });

    client.subscribe(`/topic/workers`, (message) => {
      try { callbacks.onWorkerUpdate(JSON.parse(message.body)); } catch (e) {}
    });

    client.subscribe(`/topic/schedules`, (message) => {
      try { callbacks.onScheduleUpdate(JSON.parse(message.body)); } catch (e) {}
    });

    client.subscribe(`/topic/dashboard`, (message) => {
      try { callbacks.onDashboardUpdate(JSON.parse(message.body)); } catch (e) {}
    });

    client.subscribe(`/topic/events`, (message) => {
      try { callbacks.onEventUpdate(JSON.parse(message.body)); } catch (e) {}
    });
  };

  client.onStompError = (frame) => {
    console.error('STOMP Broker reported error: ', frame.headers['message']);
  };

  client.onWebSocketClose = () => {
    console.log('WebSocket connection closed. Attempting reconnect...');
  };

  client.activate();
  return client;
};
