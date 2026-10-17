// Текущий пользователь на всё приложение (как live.js — простой общий store).
// Токен хранится в localStorage, чтобы вход переживал перезагрузку страницы.
// Права здесь только для удобства интерфейса — настоящая проверка на сервере.
import { useEffect, useState } from 'react';

const KEY = 'spacemarine.auth';
const listeners = new Set();
const loginRequests = new Set();

function read() {
  try {
    const raw = localStorage.getItem(KEY);
    const v = raw ? JSON.parse(raw) : null;
    return v && v.token && v.user ? v : null;
  } catch {
    return null;
  }
}

let state = read(); // { token, user: { id, login, role } } | null

function publish() {
  listeners.forEach((l) => l(state));
}

export function getToken() {
  return state?.token || null;
}

export function setAuth(auth) {
  state = auth;
  try {
    localStorage.setItem(KEY, JSON.stringify(auth));
  } catch {
    /* приватный режим — живём без сохранения */
  }
  publish();
}

export function clearAuth() {
  if (!state) return;
  state = null;
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
  publish();
}

/** Текущий пользователь или null; компонент перерисуется при входе/выходе. */
export function useUser() {
  const [s, setS] = useState(state);
  useEffect(() => {
    listeners.add(setS);
    setS(state);
    return () => listeners.delete(setS);
  }, []);
  return s?.user || null;
}

/** Открыть окно входа из любого места (App подписан на запрос). */
export function requestLogin() {
  loginRequests.forEach((l) => l());
}

export function onLoginRequest(cb) {
  loginRequests.add(cb);
  return () => loginRequests.delete(cb);
}

export const isAdmin = (user) => user?.role === 'ADMIN';

/** Командующий ордена или администратор. Десантником управляет тот же, кто его орденом. */
export function canManageChapter(user, chapter) {
  return Boolean(user && chapter && (isAdmin(user) || chapter.ownerId === user.id));
}

export const ROLE_LABELS = { USER: 'командующий', ADMIN: 'главнокомандующий' };
