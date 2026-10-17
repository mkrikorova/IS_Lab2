import { useCallback, useEffect, useState } from 'react';
import { api, formatDate, WEAPON_LABELS } from '../api.js';
import { useChanges } from '../live.js';
import Field from '../components/Field.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import Modal from '../components/Modal.jsx';
import { NUM_MAX, parseInteger, TEXT_MAX, validateChapter } from '../validation.js';
import { canManageChapter, requestLogin, useUser } from '../auth.js';

/** Специальные операции — каждая вызывает функцию БД через бэкенд. */
export default function OperationsPage() {
  return (
    <section>
      <div className="page-head">
        <h1>Специальные операции</h1>
      </div>
      <div className="ops">
        <HeartSum />
        <GroupByWeapon />
        <NameContains />
        <CreateChapter />
        <DisbandChapter />
      </div>
    </section>
  );
}

function Card({ n, title, fn, children }) {
  return (
    <article className="card">
      <header>
        <span className="card-n">{n}</span>
        <div>
          <h2>{title}</h2>
          <code className="fn">{fn}</code>
        </div>
      </header>
      {children}
    </article>
  );
}

function HeartSum() {
  const [sum, setSum] = useState(null);
  const [error, setError] = useState(null);
  const run = useCallback(() => {
    api.ops.heartSum().then((d) => { setSum(d.sum); setError(null); }).catch((e) => setError(e.message));
  }, []);
  useEffect(run, [run]);
  useChanges((m) => m.entity === 'spaceMarine' || m.action === 'RECONNECT' ? run() : null);
  return (
    <Card n="1" title="Сумма heartCount" fn="sm_sum_heart_count()">
      <ErrorBanner>{error}</ErrorBanner>
      <div className="big">{sum ?? '…'}</div>
      <p className="muted">Сумма по всем объектам, пустые значения не учитываются. Обновляется автоматически.</p>
    </Card>
  );
}

function GroupByWeapon() {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  const run = useCallback(() => {
    api.ops.groupByWeapon().then((d) => { setRows(d); setError(null); }).catch((e) => setError(e.message));
  }, []);
  useEffect(run, [run]);
  useChanges((m) => m.entity === 'spaceMarine' || m.action === 'RECONNECT' ? run() : null);
  return (
    <Card n="2" title="Группировка по meleeWeapon" fn="sm_group_by_melee_weapon()">
      <ErrorBanner>{error}</ErrorBanner>
      {rows && rows.length === 0 && <p className="muted">Объектов нет</p>}
      {rows && rows.length > 0 && (
        <table className="compact">
          <thead><tr><th>Оружие</th><th className="num">Количество</th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.meleeWeapon ?? 'null'}>
                <td>{r.meleeWeapon ? `${WEAPON_LABELS[r.meleeWeapon]} (${r.meleeWeapon})` : 'без оружия'}</td>
                <td className="num">{r.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}

function NameContains() {
  const [q, setQ] = useState('');
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);

  async function run(e) {
    e.preventDefault();
    if (q === '') {
      setError('Введите подстроку');
      return;
    }
    try {
      setRows(await api.ops.nameContains(q));
      setError(null);
    } catch (ex) {
      setError(ex.message);
    }
  }

  return (
    <Card n="3" title="Поиск по подстроке в name" fn="sm_find_by_name_substring(text)">
      <form className="inline" onSubmit={run}>
        <input maxLength={TEXT_MAX} value={q} onChange={(e) => setQ(e.target.value)} placeholder="например, an" aria-label="Подстрока" />
        <button className="btn" type="submit">Найти</button>
      </form>
      <ErrorBanner>{error}</ErrorBanner>
      {rows && rows.length === 0 && <p className="muted">Ничего не найдено</p>}
      {rows && rows.length > 0 && (
        <table className="compact">
          <thead><tr><th className="num">ID</th><th>Имя</th><th>Орден</th><th>Создан</th></tr></thead>
          <tbody>
            {rows.map((m) => (
              <tr key={m.id}><td className="num">{m.id}</td><td>{m.name}</td><td>{m.chapter.name}</td><td className="nowrap">{formatDate(m.creationDate)}</td></tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}

function LoginHint({ text }) {
  return (
    <div className="login-hint">
      <span className="muted">{text}</span>
      <button className="btn btn-sm" onClick={requestLogin}>Войти</button>
    </div>
  );
}

function CreateChapter() {
  const user = useUser();
  const [form, setForm] = useState({ name: '', marinesCount: '' });
  const [errors, setErrors] = useState({});
  const [result, setResult] = useState(null);
  const [general, setGeneral] = useState(null);

  async function run(e) {
    e.preventDefault();
    const err = validateChapter(form.name, form.marinesCount);
    setErrors(err);
    setGeneral(null);
    setResult(null);
    if (Object.keys(err).length) return;
    try {
      const c = await api.ops.createChapter({ name: form.name, marinesCount: parseInteger(form.marinesCount) });
      setResult(`Создан орден номер ${c.id} «${c.name}»`);
      setForm({ name: '', marinesCount: '' });
    } catch (ex) {
      const fe = ex.fieldErrors ? ex.fieldErrors() : {};
      setErrors(fe);
      setGeneral(ex.generalText ? ex.generalText() : ex.message);
    }
  }

  if (!user) {
    return (
      <Card n="4" title="Создать новый орден" fn="sm_create_chapter(text, bigint, integer)">
        <LoginHint text="Создавать ордены могут только вошедшие пользователи." />
      </Card>
    );
  }

  return (
    <Card n="4" title="Создать новый орден" fn="sm_create_chapter(text, bigint, integer)">
      <p className="muted">Командующим нового ордена станете вы.</p>
      <form onSubmit={run} noValidate>
        <div className="grid-2">
          <Field label="Название" error={errors.name} hint="уникально, регистр не важен">
            <input maxLength={TEXT_MAX} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>
          <Field label="Численность" error={errors.marinesCount} hint="1–1000">
            <input inputMode="numeric" maxLength={NUM_MAX} value={form.marinesCount} onChange={(e) => setForm({ ...form, marinesCount: e.target.value })} />
          </Field>
        </div>
        <button className="btn btn-primary" type="submit">Создать</button>
      </form>
      <ErrorBanner>{general}</ErrorBanner>
      {result && <div className="banner banner-ok">{result}</div>}
    </Card>
  );
}

function DisbandChapter() {
  const user = useUser();
  const [allChapters, setChapters] = useState([]);
  // распустить можно только свой орден (администратор — любой)
  const chapters = allChapters.filter((c) => canManageChapter(user, c));
  const [id, setId] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    api.chapters.list().then(setChapters).catch(() => {});
  }, []);
  useEffect(load, [load]);
  useChanges((m) => (m.entity === 'chapter' || m.action === 'RECONNECT' ? load() : null));

  // выбранный орден удалили (или пользователь вышел) — сбрасываем выбор
  const selectedGone = Boolean(id) && !chapters.some((c) => String(c.id) === id);
  useEffect(() => {
    if (selectedGone) setId('');
  }, [selectedGone]);

  const chapter = chapters.find((c) => String(c.id) === id);

  async function run() {
    setConfirm(false);
    try {
      const r = await api.ops.disbandChapter(Number(id));
      setResult(`Орден «${chapter?.name}» распущен, удалено десантников: ${r.deletedMarines}`);
      setError(null);
      setId('');
    } catch (ex) {
      setError(ex.message);
      setResult(null);
    }
  }

  return (
    <Card n="5" title="Распустить орден" fn="sm_disband_chapter(integer)">
      <p className="muted">Орден удаляется вместе со всеми его десантниками. Распустить можно только свой орден (администратор — любой).</p>
      {!user && <LoginHint text="Войдите, чтобы распускать свои ордены." />}
      {user && chapters.length === 0 && <p className="muted">У вас нет орденов.</p>}
      {user && chapters.length > 0 && (
      <div className="inline">
        <select value={id} onChange={(e) => setId(e.target.value)} aria-label="Орден">
          <option value="">— выберите орден —</option>
          {chapters.map((c) => (
            <option key={c.id} value={c.id}>{c.name}{c.ownerId !== user.id ? ` — командир ${c.ownerLogin}` : ''}</option>
          ))}
        </select>
        <button className="btn btn-danger" disabled={!id} onClick={() => setConfirm(true)}>Распустить</button>
      </div>
      )}
      <ErrorBanner>{error}</ErrorBanner>
      {result && <div className="banner banner-ok">{result}</div>}
      {confirm && (
        <Modal
          title="Роспуск ордена"
          onClose={() => setConfirm(false)}
          footer={
            <>
              <button className="btn" onClick={() => setConfirm(false)}>Отмена</button>
              <button className="btn btn-danger" onClick={run}>Распустить</button>
            </>
          }
        >
          <p>Распустить орден <b>«{chapter?.name}»</b>? Все его десантники будут удалены.</p>
        </Modal>
      )}
    </Card>
  );
}
