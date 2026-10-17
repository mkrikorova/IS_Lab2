// Одно WebSocket-соединение на всё приложение.
// Компоненты подписываются через useChanges(cb) и получают сообщения
// {entity, action, id}. После переподключения приходит {action: 'RECONNECT'}:
// пока связи не было, могли пропустить изменения — надо перезагрузить данные.
import { useEffect, useRef, useState } from 'react';
import { ROOT } from './api.js';

const listeners = new Set();
const statusListeners = new Set();
let status = 'connecting';
let wasConnected = false;

function setStatus(s) {
  status = s;
  statusListeners.forEach((l) => l(s));
}

function emit(msg) {
  listeners.forEach((l) => l(msg));
}

function connect() {
  const url = new URL('ws/changes', ROOT);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  const ws = new WebSocket(url);
  ws.onopen = () => {
    setStatus('online');
    if (wasConnected) emit({ entity: '*', action: 'RECONNECT', id: null });
    wasConnected = true;
  };
  ws.onmessage = (e) => {
    try {
      emit(JSON.parse(e.data));
    } catch {
      /* игнорируем мусор */
    }
  };
  ws.onclose = () => {
    setStatus('offline');
    setTimeout(connect, 3000);
  };
}

connect();

export function useChanges(callback) {
  const ref = useRef(callback);
  ref.current = callback;
  useEffect(() => {
    const l = (m) => ref.current(m);
    listeners.add(l);
    return () => listeners.delete(l);
  }, []);
}

export function useLiveStatus() {
  const [s, setS] = useState(status);
  useEffect(() => {
    statusListeners.add(setS);
    setS(status); // статус мог смениться до подписки
    return () => statusListeners.delete(setS);
  }, []);
  return s;
}
