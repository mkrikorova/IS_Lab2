import { useState } from 'react';
import Modal from './Modal.jsx';
import Field from './Field.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import { api } from '../api.js';
import { setAuth } from '../auth.js';

const LOGIN_RE = /^[A-Za-z0-9_.-]{3,50}$/;

function validate(mode, f) {
  const err = {};
  if (mode === 'register') {
    if (!LOGIN_RE.test(f.login)) err.login = 'От 3 до 50 символов: латинские буквы, цифры, точка, дефис, подчёркивание';
    if (f.password.length < 4 || f.password.length > 100) err.password = 'Пароль: от 4 до 100 символов';
    else if (f.password !== f.repeat) err.repeat = 'Пароли не совпадают';
  } else {
    if (!f.login.trim()) err.login = 'Введите логин';
    if (!f.password) err.password = 'Введите пароль';
  }
  return err;
}

/** Вход и регистрация в одном окне. Новый пользователь получает роль «командующий». */
export default function LoginDialog({ onClose }) {
  const [mode, setMode] = useState('login');
  const [f, setF] = useState({ login: '', password: '', repeat: '' });
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const [busy, setBusy] = useState(false);

  const set = (key) => (e) => {
    setF({ ...f, [key]: e.target.value });
    setErrors({ ...errors, [key]: undefined });
  };

  function switchMode(m) {
    setMode(m);
    setErrors({});
    setGeneral(null);
  }

  async function submit(e) {
    e.preventDefault();
    const err = validate(mode, f);
    setErrors(err);
    setGeneral(null);
    if (Object.keys(err).length) return;
    setBusy(true);
    try {
      const res = mode === 'login'
        ? await api.auth.login(f.login.trim(), f.password)
        : await api.auth.register(f.login, f.password);
      setAuth(res);
      onClose();
    } catch (ex) {
      const fe = ex.fieldErrors ? ex.fieldErrors() : {};
      setErrors(fe);
      setGeneral(Object.keys(fe).length ? null : ex.generalText ? ex.generalText() : ex.message);
      setBusy(false);
    }
  }

  return (
    <Modal
      title={mode === 'login' ? 'Вход' : 'Регистрация'}
      onClose={onClose}
      footer={
        <>
          <button className="btn" type="button" onClick={onClose}>Отмена</button>
          <button className="btn btn-primary" type="submit" form="login-form" disabled={busy}>
            {mode === 'login' ? 'Войти' : 'Зарегистрироваться'}
          </button>
        </>
      }
    >
      <div className="tabs" role="tablist">
        <button type="button" role="tab" aria-selected={mode === 'login'} className={mode === 'login' ? 'tab active' : 'tab'} onClick={() => switchMode('login')}>Вход</button>
        <button type="button" role="tab" aria-selected={mode === 'register'} className={mode === 'register' ? 'tab active' : 'tab'} onClick={() => switchMode('register')}>Регистрация</button>
      </div>
      <ErrorBanner>{general}</ErrorBanner>
      <form id="login-form" onSubmit={submit} noValidate>
        <Field label="Логин" error={errors.login}>
          <input autoFocus autoComplete="username" maxLength={50} value={f.login} onChange={set('login')} />
        </Field>
        <Field label="Пароль" error={errors.password}>
          <input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} maxLength={100} value={f.password} onChange={set('password')} />
        </Field>
        {mode === 'register' && (
          <Field label="Пароль ещё раз" error={errors.repeat}>
            <input type="password" autoComplete="new-password" maxLength={100} value={f.repeat} onChange={set('repeat')} />
          </Field>
        )}
      </form>
      <p className="muted small">
        {mode === 'login'
          ? 'Смотреть данные можно без входа. Чтобы создавать и изменять объекты, войдите.'
          : 'Новый пользователь становится командующим: управляет своими орденами и их десантниками.'}
      </p>
    </Modal>
  );
}
