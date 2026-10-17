import { useCallback, useEffect, useState } from 'react';
import { api, formatDate, WEAPONS, WEAPON_LABELS } from '../api.js';
import { useChanges } from '../live.js';
import Pagination from '../components/Pagination.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import MarineForm from '../components/MarineForm.jsx';
import MarineView from '../components/MarineView.jsx';
import Modal from '../components/Modal.jsx';
import { canManageChapter, requestLogin, useUser } from '../auth.js';

// key — параметр sort на сервере; null — колонка без сортировки
const COLUMNS = [
  { key: 'id', title: 'ID', num: true },
  { key: 'name', title: 'Имя' },
  { key: 'coordinatesX', title: 'X', num: true },
  { key: 'coordinatesY', title: 'Y', num: true },
  { key: 'creationDate', title: 'Создан' },
  { key: 'chapterName', title: 'Орден' },
  { key: null, title: 'Числ. ордена', num: true },
  { key: 'ownerLogin', title: 'Командир' },
  { key: 'health', title: 'Здоровье', num: true },
  { key: 'heartCount', title: 'Сердец', num: true },
  { key: 'height', title: 'Рост', num: true },
  { key: 'meleeWeapon', title: 'Оружие' },
];

const EMPTY_FILTER = { name: '', chapterName: '', ownerLogin: '', meleeWeapon: '' };

export default function MarinesPage() {
  const [draft, setDraft] = useState(EMPTY_FILTER); // то, что набрано в полях
  const [filter, setFilter] = useState(EMPTY_FILTER); // применённый фильтр
  const [sort, setSort] = useState({ key: 'id', order: 'asc' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [error, setError] = useState(null);
  const [dialog, setDialog] = useState(null); // {type: 'create'|'view'|'edit'|'delete', ...}
  const [flash, setFlash] = useState(false);
  const user = useUser();

  const load = useCallback(() => {
    api.marines
      .page({ page, size, ...filter, sort: sort.key, order: sort.order })
      .then((d) => {
        // после удаления страница могла опустеть — уходим на последнюю существующую
        if (d.content.length === 0 && page > 0 && d.totalPages > 0) {
          setPage(d.totalPages - 1);
          return;
        }
        setData(d);
        setError(null);
      })
      .catch((e) => setError(e.message));
  }, [page, size, filter, sort]);

  useEffect(load, [load]);

  // Изменения от других пользователей: перечитываем текущую страницу
  useChanges(() => {
    load();
    setFlash(true);
    setTimeout(() => setFlash(false), 700);
  });

  function toggleSort(key) {
    if (!key) return;
    setSort((s) => (s.key === key ? { key, order: s.order === 'asc' ? 'desc' : 'asc' } : { key, order: 'asc' }));
    setPage(0);
  }

  function applyFilter(e) {
    e.preventDefault();
    setFilter(draft);
    setPage(0);
  }

  function resetFilter() {
    setDraft(EMPTY_FILTER);
    setFilter(EMPTY_FILTER);
    setPage(0);
  }

  const filtered = Object.values(filter).some(Boolean);

  return (
    <section>
      <div className="page-head">
        <h1>Космодесантники</h1>
        {user
          ? <button className="btn btn-primary" onClick={() => setDialog({ type: 'create' })}>+ Новый десантник</button>
          : <button className="btn" onClick={requestLogin}>Войти, чтобы добавлять</button>}
      </div>

      <form className="filters" onSubmit={applyFilter}>
        <label>
          Имя
          <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="точное совпадение" maxLength={255} />
        </label>
        <label>
          Орден
          <input value={draft.chapterName} onChange={(e) => setDraft({ ...draft, chapterName: e.target.value })} placeholder="точное совпадение" maxLength={255} />
        </label>
        <label>
          Командир
          <input value={draft.ownerLogin} onChange={(e) => setDraft({ ...draft, ownerLogin: e.target.value })} placeholder="логин" maxLength={50} />
        </label>
        <label>
          Оружие
          <select value={draft.meleeWeapon} onChange={(e) => setDraft({ ...draft, meleeWeapon: e.target.value })}>
            <option value="">любое</option>
            {WEAPONS.map((w) => <option key={w} value={w}>{WEAPON_LABELS[w]}</option>)}
          </select>
        </label>
        <button className="btn" type="submit">Применить</button>
        {filtered && <button className="btn btn-ghost" type="button" onClick={resetFilter}>Сбросить</button>}
      </form>

      <ErrorBanner>{error}</ErrorBanner>

      <div className={'table-wrap' + (flash ? ' flash' : '')}>
        <table>
          <thead>
            <tr>
              {COLUMNS.map((c) => (
                <th
                  key={c.title}
                  className={(c.num ? 'num ' : '') + (c.key ? 'sortable' : '')}
                  onClick={() => toggleSort(c.key)}
                  aria-sort={sort.key === c.key ? (sort.order === 'asc' ? 'ascending' : 'descending') : undefined}
                >
                  {c.title}
                  {sort.key === c.key && <span className="arrow">{sort.order === 'asc' ? '▲' : '▼'}</span>}
                </th>
              ))}
              <th />
            </tr>
          </thead>
          <tbody>
            {data.content.length === 0 && (
              <tr><td colSpan={COLUMNS.length + 1} className="empty">{filtered ? 'Ничего не найдено' : 'Пока нет ни одного десантника'}</td></tr>
            )}
            {data.content.map((m) => {
              const mine = canManageChapter(user, m.chapter);
              return (
              <tr key={m.id} className={user && m.chapter.ownerId === user.id ? 'mine' : undefined} onDoubleClick={() => setDialog({ type: 'view', id: m.id })}>
                <td className="num">{m.id}</td>
                <td>{m.name}</td>
                <td className="num">{m.coordinates.x}</td>
                <td className="num">{m.coordinates.y}</td>
                <td className="nowrap">{formatDate(m.creationDate)}</td>
                <td>{m.chapter.name}</td>
                <td className="num">{m.chapter.marinesCount}</td>
                <td>{m.chapter.ownerLogin}</td>
                <td className="num">{m.health}</td>
                <td className="num">{m.heartCount ?? '—'}</td>
                <td className="num">{m.height}</td>
                <td>{m.meleeWeapon ? WEAPON_LABELS[m.meleeWeapon] : '—'}</td>
                <td className="actions">
                  <button className="btn btn-sm" onClick={() => setDialog({ type: 'view', id: m.id })}>Открыть</button>
                  {mine && (
                    <>
                      <button className="btn btn-sm" onClick={() => setDialog({ type: 'edit', marine: m })}>Изменить</button>
                      <button className="btn btn-sm btn-danger-ghost" onClick={() => setDialog({ type: 'delete', marine: m })}>Удалить</button>
                    </>
                  )}
                </td>
              </tr>
              );
            })}
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

      {dialog?.type === 'create' && <MarineForm onClose={() => setDialog(null)} onSaved={load} />}
      {dialog?.type === 'edit' && <MarineForm marine={dialog.marine} onClose={() => setDialog(null)} onSaved={load} />}
      {dialog?.type === 'view' && (
        <MarineView id={dialog.id} onClose={() => setDialog(null)} onEdit={(m) => setDialog({ type: 'edit', marine: m })} />
      )}
      {dialog?.type === 'delete' && (
        <DeleteMarine marine={dialog.marine} onClose={() => setDialog(null)} onDone={load} />
      )}
    </section>
  );
}

function DeleteMarine({ marine, onClose, onDone }) {
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function remove() {
    setBusy(true);
    try {
      await api.marines.remove(marine.id);
      onDone();
      onClose();
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }

  return (
    <Modal
      title="Удаление десантника"
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Отмена</button>
          <button className="btn btn-danger" onClick={remove} disabled={busy}>Удалить</button>
        </>
      }
    >
      <ErrorBanner>{error}</ErrorBanner>
      <p>Удалить десантника <b>номер {marine.id} «{marine.name}»</b>? Его орден и координаты останутся.</p>
    </Modal>
  );
}
