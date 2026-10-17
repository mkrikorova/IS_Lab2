import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import MarinesPage from './pages/MarinesPage.jsx';
import ChaptersPage from './pages/ChaptersPage.jsx';
import CoordinatesPage from './pages/CoordinatesPage.jsx';
import OperationsPage from './pages/OperationsPage.jsx';
import MapPage from './pages/MapPage.jsx';
import ImportPage from './pages/ImportPage.jsx';
import LoginDialog from './components/LoginDialog.jsx';
import { useLiveStatus } from './live.js';
import { api } from './api.js';
import { clearAuth, getToken, onLoginRequest, ROLE_LABELS, useUser } from './auth.js';

const STATUS_TEXT = { online: 'онлайн', offline: 'нет связи', connecting: 'подключение' };

export default function App() {
  const status = useLiveStatus();
  const user = useUser();
  const [loginOpen, setLoginOpen] = useState(false);

  // токен из прошлого визита мог истечь — проверяем один раз при запуске
  useEffect(() => {
    if (getToken()) api.auth.me().catch(() => {});
  }, []);

  useEffect(() => onLoginRequest(() => setLoginOpen(true)), []);

  async function logout() {
    try {
      await api.auth.logout();
    } catch {
      /* сессия могла уже истечь */
    }
    clearAuth();
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">SpaceMarine</div>
        <nav className="menu">
          <NavLink to="/marines">Десантники</NavLink>
          <NavLink to="/chapters">Ордены</NavLink>
          <NavLink to="/coordinates">Координаты</NavLink>
          <NavLink to="/operations">Спецоперации</NavLink>
          <NavLink to="/map">Карта</NavLink>
          <NavLink to="/import">Импорт</NavLink>
        </nav>
        <div className={'live live-' + status} title="Соединение для обновлений в реальном времени">
          <span className="dot" /> {STATUS_TEXT[status]}
        </div>
        <div className="account">
          {user ? (
            <>
              <span className="who" title={ROLE_LABELS[user.role]}>
                <b>{user.login}</b>
                <span className={'role role-' + user.role.toLowerCase()}>{ROLE_LABELS[user.role]}</span>
              </span>
              <button className="btn btn-sm btn-bar" onClick={logout}>Выйти</button>
            </>
          ) : (
            <button className="btn btn-sm btn-bar" onClick={() => setLoginOpen(true)}>Войти</button>
          )}
        </div>
      </header>
      <main className="content">
        <Routes>
          <Route path="/marines" element={<MarinesPage />} />
          <Route path="/chapters" element={<ChaptersPage />} />
          <Route path="/coordinates" element={<CoordinatesPage />} />
          <Route path="/operations" element={<OperationsPage />} />
          <Route path="/map" element={<MapPage />} />
          <Route path="/import" element={<ImportPage />} />
          <Route path="*" element={<Navigate to="/marines" replace />} />
        </Routes>
      </main>
      {loginOpen && <LoginDialog onClose={() => setLoginOpen(false)} />}
    </div>
  );
}
