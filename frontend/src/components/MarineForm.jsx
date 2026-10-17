import { useCallback, useEffect, useMemo, useState } from 'react';
import Modal from './Modal.jsx';
import Field from './Field.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import { api, WEAPONS, WEAPON_LABELS } from '../api.js';
import { useChanges } from '../live.js';
import { canManageChapter, isAdmin, useUser } from '../auth.js';
import {
  checkInt, checkLong, isBlank, NUM_MAX, parseDecimal, parseInteger, TEXT_MAX, validateChapter, validateCoordinates,
} from '../validation.js';

function initialState(m) {
  if (!m) {
    return {
      name: '', health: '', heartCount: '', height: '', meleeWeapon: '',
      coordMode: 'new', coordinatesId: '', x: '', y: '',
      chapterMode: 'existing', chapterId: '', chapterName: '', marinesCount: '',
    };
  }
  return {
    name: m.name,
    health: String(m.health),
    heartCount: m.heartCount == null ? '' : String(m.heartCount),
    height: String(m.height),
    meleeWeapon: m.meleeWeapon || '',
    coordMode: 'existing', coordinatesId: String(m.coordinates.id), x: '', y: '',
    chapterMode: 'existing', chapterId: String(m.chapter.id), chapterName: '', marinesCount: '',
  };
}

function validate(f) {
  const err = {};
  if (isBlank(f.name)) err.name = 'Имя не может быть пустым';

  const eh = checkLong(f.health, 'Здоровье');
  if (eh) err.health = eh;
  else if (parseInteger(f.health) <= 0) err.health = 'Здоровье должно быть больше 0';

  if (!isBlank(f.heartCount)) {
    const n = parseInteger(f.heartCount);
    if (!Number.isInteger(n)) err.heartCount = 'Нужно целое число';
    else if (n <= 0 || n > 3) err.heartCount = 'Количество сердец — от 1 до 3 (или пусто)';
  }

  const eht = checkInt(f.height, 'Рост');
  if (eht) err.height = eht;

  if (f.coordMode === 'existing') {
    if (!f.coordinatesId) err.coordinatesId = 'Выберите координаты';
  } else {
    const c = validateCoordinates(f.x, f.y);
    if (c.x) err['coordinates.x'] = c.x;
    if (c.y) err['coordinates.y'] = c.y;
  }

  if (f.chapterMode === 'existing') {
    if (!f.chapterId) err.chapterId = 'Выберите орден';
  } else {
    const c = validateChapter(f.chapterName, f.marinesCount);
    if (c.name) err['chapter.name'] = c.name;
    if (c.marinesCount) err['chapter.marinesCount'] = c.marinesCount;
  }
  return err;
}

function toRequest(f) {
  const body = {
    name: f.name,
    health: parseInteger(f.health),
    heartCount: isBlank(f.heartCount) ? null : parseInteger(f.heartCount),
    height: parseInteger(f.height),
    meleeWeapon: f.meleeWeapon || null,
  };
  if (f.coordMode === 'existing') body.coordinatesId = Number(f.coordinatesId);
  else body.coordinates = { x: parseInteger(f.x), y: parseDecimal(f.y) };
  if (f.chapterMode === 'existing') body.chapterId = Number(f.chapterId);
  else body.chapter = { name: f.chapterName, marinesCount: parseInteger(f.marinesCount) };
  return body;
}

// поле формы -> ключи ошибок (клиентские и с сервера)
const ERROR_KEYS = {
  x: ['coordinates.x'],
  y: ['coordinates.y'],
  chapterName: ['chapter.name'],
  marinesCount: ['chapter.marinesCount'],
};

/** Окно создания (marine == null) или изменения десантника. */
export default function MarineForm({ marine, onClose, onSaved }) {
  const editing = Boolean(marine);
  const [f, setF] = useState(() => initialState(marine));
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const [busy, setBusy] = useState(false);
  const [deleted, setDeleted] = useState(false);
  const [allChapters, setChapters] = useState(null); // null — ещё не загружены
  const [coords, setCoords] = useState(null);
  const user = useUser();
  const admin = isAdmin(user);
  // записать десантника можно только в свой орден (администратор — в любой)
  const chapters = useMemo(
    () => allChapters && allChapters.filter((c) => canManageChapter(user, c)),
    [allChapters, user],
  );

  const loadLists = useCallback(() => {
    api.chapters.list().then(setChapters).catch(() => {});
    api.coordinates.list().then(setCoords).catch(() => {});
  }, []);

  useEffect(() => {
    loadLists();
  }, [loadLists]);

  // Пока окно открыто, другие пользователи могут менять данные
  useChanges((m) => {
    if (m.entity === 'chapter' || m.entity === 'coordinates' || m.action === 'RECONNECT') loadLists();
    if (editing && m.entity === 'spaceMarine' && m.action === 'DELETED' && m.id === marine.id) setDeleted(true);
  });

  // Орденов в системе ещё нет — выбирать не из чего, сразу режим «новый»
  useEffect(() => {
    if (!editing && chapters && chapters.length === 0 && f.chapterMode === 'existing') {
      setF((s) => ({ ...s, chapterMode: 'new' }));
    }
  }, [chapters, editing, f.chapterMode]);

  const set = (key) => (e) => {
    const value = e.target.value;
    setF((s) => ({ ...s, [key]: value }));
    // убираем ошибку поля, как только его начали исправлять
    const keys = ERROR_KEYS[key] || [key];
    setErrors((er) => {
      if (!keys.some((k) => er[k])) return er;
      const next = { ...er };
      keys.forEach((k) => delete next[k]);
      return next;
    });
  };

  async function submit(e) {
    e.preventDefault();
    const err = validate(f);
    setErrors(err);
    setGeneral(null);
    if (Object.keys(err).length) return;
    setBusy(true);
    try {
      const body = toRequest(f);
      const saved = editing ? await api.marines.update(marine.id, body) : await api.marines.create(body);
      onSaved?.(saved);
      onClose();
    } catch (ex) {
      const fe = ex.fieldErrors ? ex.fieldErrors() : {};
      setErrors(fe);
      setGeneral(Object.keys(fe).length ? ex.message : ex.generalText ? ex.generalText() : ex.message);
    } finally {
      setBusy(false);
    }
  }

  const e = (k) => errors[k];

  return (
    <Modal
      wide
      title={editing ? `Изменение десантника номер ${marine.id}` : 'Новый десантник'}
      onClose={onClose}
      footer={
        <>
          <button className="btn" type="button" onClick={onClose}>Отмена</button>
          <button className="btn btn-primary" type="submit" form="marine-form" disabled={busy || deleted}>
            {busy ? 'Сохранение…' : editing ? 'Сохранить' : 'Создать'}
          </button>
        </>
      }
    >
      {deleted && <ErrorBanner>Этот объект только что удалил другой пользователь.</ErrorBanner>}
      <ErrorBanner>{general}</ErrorBanner>

      <form id="marine-form" onSubmit={submit} noValidate>
        <fieldset>
          <legend>Основное</legend>
          <div className="grid-2">
            <Field label="Имя *" error={e('name')}>
              <input value={f.name} onChange={set('name')} maxLength={TEXT_MAX} autoFocus />
            </Field>
            <Field label="Оружие ближнего боя" error={e('meleeWeapon')}>
              <select value={f.meleeWeapon} onChange={set('meleeWeapon')}>
                <option value="">— нет —</option>
                {WEAPONS.map((w) => <option key={w} value={w}>{WEAPON_LABELS[w]} ({w})</option>)}
              </select>
            </Field>
            <Field label="Здоровье *" error={e('health')} hint="целое, больше 0">
              <input inputMode="numeric" maxLength={NUM_MAX} value={f.health} onChange={set('health')} />
            </Field>
            <Field label="Количество сердец" error={e('heartCount')} hint="1–3 или пусто">
              <input inputMode="numeric" maxLength={NUM_MAX} value={f.heartCount} onChange={set('heartCount')} />
            </Field>
            <Field label="Рост *" error={e('height')} hint="целое число">
              <input inputMode="numeric" maxLength={NUM_MAX} value={f.height} onChange={set('height')} />
            </Field>
          </div>
        </fieldset>

        <fieldset>
          <legend>Координаты</legend>
          <div className="seg" role="radiogroup">
            <label><input type="radio" checked={f.coordMode === 'existing'} onChange={() => setF({ ...f, coordMode: 'existing' })} disabled={!coords?.length} /> существующие</label>
            <label><input type="radio" checked={f.coordMode === 'new'} onChange={() => setF({ ...f, coordMode: 'new' })} /> новые</label>
          </div>
          {f.coordMode === 'existing' ? (
            <Field label="Координаты *" error={e('coordinatesId')}>
              <select value={f.coordinatesId} onChange={set('coordinatesId')}>
                <option value="">— выберите —</option>
                {(coords || []).map((c) => <option key={c.id} value={c.id}>x = {c.x}, y = {c.y}</option>)}
              </select>
            </Field>
          ) : (
            <div className="grid-2">
              <Field label="x *" error={e('coordinates.x')} hint="целое; если точка уже есть — будет выбрана она">
                <input inputMode="numeric" maxLength={NUM_MAX} value={f.x} onChange={set('x')} />
              </Field>
              <Field label="y *" error={e('coordinates.y')} hint="больше -833">
                <input inputMode="decimal" maxLength={NUM_MAX} value={f.y} onChange={set('y')} />
              </Field>
            </div>
          )}
        </fieldset>

        <fieldset>
          <legend>Орден</legend>
          <div className="seg" role="radiogroup">
            <label><input type="radio" checked={f.chapterMode === 'existing'} onChange={() => setF({ ...f, chapterMode: 'existing' })} disabled={!chapters?.length} /> существующий</label>
            <label><input type="radio" checked={f.chapterMode === 'new'} onChange={() => setF({ ...f, chapterMode: 'new' })} /> новый</label>
          </div>
          {f.chapterMode === 'existing' ? (
            <Field label="Орден *" error={e('chapterId')} hint={admin ? undefined : 'только ваши ордены'}>
              <select value={f.chapterId} onChange={set('chapterId')}>
                <option value="">— выберите —</option>
                {(chapters || []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.marinesCount}){admin && c.ownerId !== user.id ? ` — командир ${c.ownerLogin}` : ''}
                  </option>
                ))}
              </select>
            </Field>
          ) : (
            <div className="grid-2">
              <Field label="Название *" error={e('chapter.name')} hint="если такой ваш орден уже есть — будет выбран он">
                <input value={f.chapterName} onChange={set('chapterName')} maxLength={TEXT_MAX} />
              </Field>
              <Field label="Численность *" error={e('chapter.marinesCount')} hint="1–1000">
                <input inputMode="numeric" maxLength={NUM_MAX} value={f.marinesCount} onChange={set('marinesCount')} />
              </Field>
            </div>
          )}
        </fieldset>
      </form>
    </Modal>
  );
}
