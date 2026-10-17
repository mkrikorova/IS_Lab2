import { useState } from 'react';
import Modal from './Modal.jsx';
import Field from './Field.jsx';
import ErrorBanner from './ErrorBanner.jsx';

/**
 * Удаление объекта, с которым связаны десантники:
 * пользователь выбирает, к какому объекту их перепривязать.
 * options: [{ id, label }] — уже без удаляемого объекта.
 */
export default function ReassignDialog({ title, text, options, onConfirm, onClose }) {
  const [target, setTarget] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!target) {
      setError('Выберите объект для перепривязки');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onConfirm(Number(target));
      onClose();
    } catch (e) {
      setError(e.generalText ? e.generalText() : e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Отмена</button>
          <button className="btn btn-danger" onClick={submit} disabled={busy || options.length === 0}>
            Перепривязать и удалить
          </button>
        </>
      }
    >
      <p className="muted">{text}</p>
      <ErrorBanner>{error}</ErrorBanner>
      {options.length === 0 ? (
        <ErrorBanner>Нет других объектов для перепривязки — сначала создайте ещё один.</ErrorBanner>
      ) : (
        <Field label="Перепривязать к">
          <select value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="">— выберите —</option>
            {options.map((o) => (
              <option key={o.id} value={o.id}>{o.label}</option>
            ))}
          </select>
        </Field>
      )}
    </Modal>
  );
}
