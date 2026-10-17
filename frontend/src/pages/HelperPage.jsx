import { useCallback, useEffect, useState } from 'react';
import Modal from '../components/Modal.jsx';
import Field from '../components/Field.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import ReassignDialog from '../components/ReassignDialog.jsx';
import { useChanges } from '../live.js';
import { NUM_MAX, TEXT_MAX } from '../validation.js';
import { requestLogin, useUser } from '../auth.js';

/**
 * Общая страница для вспомогательных объектов (ордены, координаты):
 * список, создание, изменение, удаление с перепривязкой связанных десантников.
 *
 * config: {
 *   entity, title, newLabel, columns: [{title, render, num}],
 *   fields: [{key, label, hint, inputMode}], empty: {}, fromItem(item) -> form,
 *   validate(form) -> errors, toBody(form), label(item),
 *   api: {list, create, update, remove(id, reassignTo)},
 *   canEdit(user, item) — можно ли менять/удалять объект (проверка дублируется на сервере),
 *   reassignTargets(user, items) — куда разрешено перепривязать десантников,
 *   note — пояснение о правах под заголовком
 * }
 */
export default function HelperPage({ config }) {
  const [items, setItems] = useState([]);
  const [error, setError] = useState(null);
  const [dialog, setDialog] = useState(null);
  const user = useUser();

  const load = useCallback(() => {
    config.api.list().then((d) => { setItems(d); setError(null); }).catch((e) => setError(e.message));
  }, [config]);

  useEffect(load, [load]);
  useChanges((m) => {
    if (m.entity === config.entity || m.action === 'RECONNECT') load();
  });

  async function startDelete(item) {
    try {
      await config.api.remove(item.id);
      load();
    } catch (e) {
      if (e.status === 409) {
        // есть связанные десантники — спрашиваем, куда их перевести
        setDialog({ type: 'reassign', item, text: e.message });
      } else {
        setError(e.message);
      }
    }
  }

  return (
    <section>
      <div className="page-head">
        <h1>{config.title}</h1>
        {user
          ? <button className="btn btn-primary" onClick={() => setDialog({ type: 'form' })}>+ {config.newLabel}</button>
          : <button className="btn" onClick={requestLogin}>Войти, чтобы добавлять</button>}
      </div>
      {config.note && <p className="muted page-note">{config.note}</p>}
      <ErrorBanner>{error}</ErrorBanner>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th className="num">ID</th>
              {config.columns.map((c) => <th key={c.title} className={c.num ? 'num' : ''}>{c.title}</th>)}
              <th />
            </tr>
          </thead>
          <tbody>
            {items.length === 0 && <tr><td colSpan={config.columns.length + 2} className="empty">Пусто</td></tr>}
            {items.map((it) => (
              <tr key={it.id}>
                <td className="num">{it.id}</td>
                {config.columns.map((c) => <td key={c.title} className={c.num ? 'num' : ''}>{c.render(it)}</td>)}
                <td className="actions">
                  {config.canEdit(user, it) && (
                    <>
                      <button className="btn btn-sm" onClick={() => setDialog({ type: 'form', item: it })}>Изменить</button>
                      <button className="btn btn-sm btn-danger-ghost" onClick={() => startDelete(it)}>Удалить</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {dialog?.type === 'form' && (
        <HelperForm config={config} item={dialog.item} onClose={() => setDialog(null)} onSaved={load} />
      )}
      {dialog?.type === 'reassign' && (
        <ReassignDialog
          title={`Удаление: ${config.label(dialog.item)}`}
          text={dialog.text}
          options={config.reassignTargets(user, items)
            .filter((i) => i.id !== dialog.item.id)
            .map((i) => ({ id: i.id, label: config.label(i) }))}
          onConfirm={async (target) => { await config.api.remove(dialog.item.id, target); load(); }}
          onClose={() => setDialog(null)}
        />
      )}
    </section>
  );
}

function HelperForm({ config, item, onClose, onSaved }) {
  const [form, setForm] = useState(() => (item ? config.fromItem(item) : config.empty));
  const [errors, setErrors] = useState({});
  const [general, setGeneral] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    const err = config.validate(form);
    setErrors(err);
    setGeneral(null);
    if (Object.keys(err).length) return;
    setBusy(true);
    try {
      const body = config.toBody(form);
      if (item) await config.api.update(item.id, body);
      else await config.api.create(body);
      onSaved();
      onClose();
    } catch (ex) {
      const fe = ex.fieldErrors ? ex.fieldErrors() : {};
      setErrors(fe);
      setGeneral(Object.keys(fe).length ? ex.message : ex.generalText ? ex.generalText() : ex.message);
      setBusy(false);
    }
  }

  return (
    <Modal
      title={item ? `Изменение: ${config.label(item)}` : config.newLabel}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose}>Отмена</button>
          <button className="btn btn-primary" type="submit" form="helper-form" disabled={busy}>
            {item ? 'Сохранить' : 'Создать'}
          </button>
        </>
      }
    >
      <ErrorBanner>{general}</ErrorBanner>
      <form id="helper-form" onSubmit={submit} noValidate>
        {config.fields.map((f, i) => (
          <Field key={f.key} label={f.label} hint={f.hint} error={errors[f.key]}>
            <input
              autoFocus={i === 0}
              inputMode={f.inputMode}
              maxLength={f.inputMode ? NUM_MAX : TEXT_MAX}
              value={form[f.key]}
              onChange={(e) => {
                setForm({ ...form, [f.key]: e.target.value });
                setErrors({ ...errors, [f.key]: undefined });
              }}
            />
          </Field>
        ))}
      </form>
    </Modal>
  );
}
