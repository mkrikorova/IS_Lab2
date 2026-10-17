// Все запросы к бэкенду. Адреса строятся от корня приложения,
// поэтому работают и на Payara (/spacemarine/app/ -> /spacemarine/api), и в dev-режиме.
import { clearAuth, getToken } from './auth.js';

function appRoot() {
  // /spacemarine/app/index.html -> /spacemarine/
  const path = window.location.pathname.replace(/[^/]*$/, '');
  return new URL(path.replace(/app\/$/, ''), window.location.origin);
}

export const ROOT = appRoot();

export class ApiError extends Error {
  constructor(status, message, details) {
    super(message);
    this.status = status;
    this.details = details || [];
  }

  /** "health: health должно быть больше 0" -> { health: "health должно быть больше 0" } */
  fieldErrors() {
    const res = {};
    for (const d of this.details) {
      const i = d.indexOf(': ');
      if (i > 0) res[d.slice(0, i)] = d.slice(i + 2);
    }
    return res;
  }

  /** Ошибки, которые не привязаны к полю формы. */
  generalText() {
    const free = this.details.filter((d) => d.indexOf(': ') <= 0);
    return [this.message, ...free].filter(Boolean).join('. ');
  }
}

async function request(method, path, body) {
  const headers = {};
  const isForm = body instanceof FormData; // файл: Content-Type с boundary поставит браузер
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  const token = getToken();
  if (token) headers.Authorization = 'Bearer ' + token;
  let res;
  try {
    res = await fetch(new URL('api/' + path, ROOT), {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Сервер недоступен');
  }
  // токен истёк или сессию закрыли — считаем, что пользователь вышел
  if (res.status === 401 && token) clearAuth();
  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    /* не JSON — например, страница ошибки Payara */
  }
  if (!res.ok) {
    throw new ApiError(res.status, data?.message || `Ошибка ${res.status}`, data?.details);
  }
  return data;
}

function query(params) {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') q.set(k, v);
  }
  const s = q.toString();
  return s ? '?' + s : '';
}

export const api = {
  auth: {
    login: (login, password) => request('POST', 'auth/login', { login, password }),
    register: (login, password) => request('POST', 'auth/register', { login, password }),
    logout: () => request('POST', 'auth/logout'),
    me: () => request('GET', 'auth/me'),
  },
  imports: {
    upload: (file) => {
      const form = new FormData();
      form.append('file', file);
      return request('POST', 'imports/upload', form);
    },
    page: (params) => request('GET', 'imports' + query(params)),
  },
  marines: {
    page: (params) => request('GET', 'space-marines' + query(params)),
    all: () => request('GET', 'space-marines/all'),
    get: (id) => request('GET', `space-marines/${id}`),
    create: (body) => request('POST', 'space-marines', body),
    update: (id, body) => request('PUT', `space-marines/${id}`, body),
    remove: (id) => request('DELETE', `space-marines/${id}`),
  },
  chapters: {
    list: () => request('GET', 'chapters'),
    create: (body) => request('POST', 'chapters', body),
    update: (id, body) => request('PUT', `chapters/${id}`, body),
    remove: (id, reassignTo) => request('DELETE', `chapters/${id}` + query({ reassignTo })),
  },
  coordinates: {
    list: () => request('GET', 'coordinates'),
    create: (body) => request('POST', 'coordinates', body),
    update: (id, body) => request('PUT', `coordinates/${id}`, body),
    remove: (id, reassignTo) => request('DELETE', `coordinates/${id}` + query({ reassignTo })),
  },
  ops: {
    heartSum: () => request('GET', 'operations/heart-count-sum'),
    groupByWeapon: () => request('GET', 'operations/group-by-melee-weapon'),
    nameContains: (substring) => request('GET', 'operations/name-contains' + query({ substring })),
    createChapter: (body) => request('POST', 'operations/chapters', body),
    disbandChapter: (id) => request('DELETE', `operations/chapters/${id}`),
  },
};

export const WEAPONS = ['CHAIN_SWORD', 'POWER_SWORD', 'CHAIN_AXE', 'POWER_FIST'];

export const WEAPON_LABELS = {
  CHAIN_SWORD: 'Цепной меч',
  POWER_SWORD: 'Силовой меч',
  CHAIN_AXE: 'Цепной топор',
  POWER_FIST: 'Силовой кулак',
};

/** "2026-10-02T18:46:23.1+03:00[Europe/Moscow]" -> "02.10.2026, 18:46:23" */
export function formatDate(value) {
  if (!value) return '';
  const d = new Date(String(value).replace(/\[.*\]$/, ''));
  return isNaN(d) ? String(value) : d.toLocaleString('ru-RU');
}
