import { useCallback, useEffect, useState } from 'react';
import Modal from './Modal.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import { api, formatDate, WEAPON_LABELS } from '../api.js';
import { useChanges } from '../live.js';
import { canManageChapter, useUser } from '../auth.js';

/** Окно «информация об объекте» — вместе со связанными координатами и орденом. */
export default function MarineView({ id, onClose, onEdit }) {
  const [m, setM] = useState(null);
  const [error, setError] = useState(null);
  const user = useUser();

  const load = useCallback(() => {
    api.marines.get(id).then((d) => { setM(d); setError(null); }).catch((e) => setError(e.message));
  }, [id]);

  useEffect(load, [load]);

  useChanges((msg) => {
    if (msg.entity === 'spaceMarine' && msg.action === 'DELETED' && msg.id === id) {
      setError('Объект удалён другим пользователем');
      setM(null);
    } else {
      load(); // изменился сам объект, его орден или координаты
    }
  });

  return (
    <Modal
      title={`Десантник номер ${id}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Закрыть</button>
          {m && canManageChapter(user, m.chapter) && (
            <button className="btn btn-primary" onClick={() => onEdit(m)}>Изменить</button>
          )}
        </>
      }
    >
      <ErrorBanner>{error}</ErrorBanner>
      {m && (
        <>
          <dl className="props">
            <dt>Имя</dt><dd>{m.name}</dd>
            <dt>Создан</dt><dd>{formatDate(m.creationDate)}</dd>
            <dt>Здоровье</dt><dd>{m.health}</dd>
            <dt>Сердец</dt><dd>{m.heartCount ?? '—'}</dd>
            <dt>Рост</dt><dd>{m.height}</dd>
            <dt>Оружие</dt><dd>{m.meleeWeapon ? `${WEAPON_LABELS[m.meleeWeapon]} (${m.meleeWeapon})` : '—'}</dd>
          </dl>
          <h3 className="sub">Координаты номер {m.coordinates.id}</h3>
          <dl className="props">
            <dt>x</dt><dd>{m.coordinates.x}</dd>
            <dt>y</dt><dd>{m.coordinates.y}</dd>
          </dl>
          <h3 className="sub">Орден номер {m.chapter.id}</h3>
          <dl className="props">
            <dt>Название</dt><dd>{m.chapter.name}</dd>
            <dt>Численность</dt><dd>{m.chapter.marinesCount}</dd>
            <dt>Командир</dt><dd>{m.chapter.ownerLogin}</dd>
          </dl>
        </>
      )}
    </Modal>
  );
}
