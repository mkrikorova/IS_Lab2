import { useCallback, useEffect, useRef, useState } from 'react';
import { api, formatDate } from '../api.js';
import { useChanges } from '../live.js';
import { isAdmin, requestLogin, useUser } from '../auth.js';
import ErrorBanner from '../components/ErrorBanner.jsx';
import Pagination from '../components/Pagination.jsx';

const MAX_BYTES = 5 * 1024 * 1024;

const SAMPLE = [
  {
    name: 'Titus',
    coordinates: { x: 10, y: 20.5 },
    chapter: { name: 'Ultramarines', marinesCount: 700 },
    health: 100, heartCount: 2, height: 190, meleeWeapon: 'POWER_SWORD',
  },
  {
    name: 'Leandros',
    coordinates: { x: 10, y: 20.5 },
    chapter: { name: 'Ultramarines', marinesCount: 700 },
    health: 80, heartCount: null, height: 185, meleeWeapon: null,
  },
];

const STATUS = {
  SUCCESS: { text: 'успешно', cls: 'st-ok' },
  FAILED: { text: 'ошибка', cls: 'st-fail' },
  IN_PROGRESS: { text: 'выполняется', cls: 'st-run' },
};

function downloadSample() {
  const blob = new Blob([JSON.stringify(SAMPLE, null, 2) + '\n'], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'spacemarine-import-example.json';
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

export default function ImportPage() {
  const user = useUser();
  return (
    <section>
      <div className="page-head">
        <h1>Импорт</h1>
      </div>
      {!user ? (
        <div className="card">
          <div className="login-hint">
            <span className="muted">Импорт и его история доступны после входа.</span>
            <button className="btn btn-sm" onClick={requestLogin}>Войти</button>
          </div>
        </div>
      ) : (
        <>
          <Upload />
          <History admin={isAdmin(user)} />
        </>
      )}
    </section>
  );
}

function Upload() {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null); // {ok, title, lines}
  const input = useRef(null);

  function pick(e) {
    const f = e.target.files?.[0] || null;
    setResult(null);
    if (f && f.size > MAX_BYTES) {
      setResult({ ok: false, title: 'Файл больше 5 МБ', lines: [] });
      setFile(null);
      e.target.value = '';
      return;
    }
    setFile(f);
  }

  async function upload(e) {
    e.preventDefault();
    if (!file) return;
    setBusy(true);
    setResult(null);
    try {
      const r = await api.imports.upload(file);
      setResult({
        ok: true,
        title: `Импорт номер ${r.operationId} выполнен: добавлено ${r.added}` + (r.skipped ? `, пропущено ${r.skipped}` : ''),
        lines: r.skippedDetails || [],
      });
      setFile(null);
      if (input.current) input.current.value = '';
    } catch (ex) {
      setResult({ ok: false, title: ex.message, lines: ex.details || [] });
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="card import-card">
      <div className="import-grid">
        <form onSubmit={upload} className="import-form">
          <h2>Загрузить файл</h2>
          <p className="muted">
            JSON-массив десантников, до 5 МБ. Все записи сохраняются в одной транзакции:
            если хотя бы одна запись с ошибкой, не создаётся ни один объект.
          </p>
          <div className="inline">
            <input ref={input} type="file" accept=".json,application/json" onChange={pick} aria-label="Файл импорта" />
            <button className="btn btn-primary" type="submit" disabled={!file || busy}>
              {busy ? 'Загрузка…' : 'Импортировать'}
            </button>
          </div>
          {result && (
            <div className={'banner ' + (result.ok ? 'banner-ok' : 'banner-error')} role={result.ok ? 'status' : 'alert'}>
              <b>{result.title}</b>
              {result.lines.length > 0 && (
                <ul className="report">
                  {result.lines.map((l, i) => <li key={i}>{l}</li>)}
                </ul>
              )}
            </div>
          )}
        </form>
        <div className="import-help">
          <h2>Формат</h2>
          <pre className="code">{`[
  {
    "name": "Titus",
    "coordinates": { "x": 10, "y": 20.5 },
    "chapter": { "name": "Ultramarines",
                 "marinesCount": 700 },
    "health": 100,
    "heartCount": 2,
    "height": 190,
    "meleeWeapon": "POWER_SWORD"
  }
]`}</pre>
          <ul className="muted rules">
            <li>heartCount — от 1 до 3 или null; meleeWeapon — CHAIN_SWORD, POWER_SWORD, CHAIN_AXE, POWER_FIST или null.</li>
            <li>Координаты с теми же (x, y) и ваш орден с тем же названием (регистр не важен) используются повторно.</li>
            <li>Орден другого командующего или с другой численностью — ошибка, импорт отменяется.</li>
            <li>Полный дубль вашего десантника пропускается.</li>
          </ul>
          <button className="btn btn-sm" type="button" onClick={downloadSample}>Скачать пример</button>
        </div>
      </div>
    </article>
  );
}

function History({ admin }) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    api.imports.page({ page, size }).then((d) => { setData(d); setError(null); }).catch((e) => setError(e.message));
  }, [page, size]);

  useEffect(load, [load]);
  useChanges((m) => (m.entity === 'import' || m.action === 'RECONNECT' ? load() : null));

  return (
    <>
      <div className="page-head history-head">
        <h2>История импорта</h2>
        <span className="muted">{admin ? 'все операции (вы администратор)' : 'ваши операции'}</span>
      </div>
      <ErrorBanner>{error}</ErrorBanner>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th className="num">ID</th>
              <th>Статус</th>
              <th>Пользователь</th>
              <th className="num">Добавлено</th>
              <th className="num">Пропущено</th>
              <th>Файл</th>
              <th>Начало</th>
              <th>Окончание</th>
              <th>Ошибка</th>
            </tr>
          </thead>
          <tbody>
            {data.content.length === 0 && (
              <tr><td colSpan={9} className="empty">Операций импорта пока нет</td></tr>
            )}
            {data.content.map((o) => (
              <tr key={o.id}>
                <td className="num">{o.id}</td>
                <td><span className={'status ' + STATUS[o.status].cls}>{STATUS[o.status].text}</span></td>
                <td>{o.userLogin}</td>
                <td className="num">{o.status === 'SUCCESS' ? o.addedCount : '—'}</td>
                <td className="num">{o.status === 'SUCCESS' ? o.skippedCount : '—'}</td>
                <td className="file-cell" title={o.fileName || ''}>{o.fileName || '—'}</td>
                <td className="nowrap">{formatDate(o.startedAt)}</td>
                <td className="nowrap">{o.finishedAt ? formatDate(o.finishedAt) : '—'}</td>
                <td className="error-cell" title={o.errorMessage || ''}>{o.errorMessage || ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination
        page={page}
        size={size}
        totalPages={data.totalPages}
        totalElements={data.totalElements}
        onPage={setPage}
        onSize={(s) => { setSize(s); setPage(0); }}
      />
    </>
  );
}
