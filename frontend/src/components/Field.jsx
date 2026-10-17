/** Поле формы с подписью и сообщением об ошибке под ним. */
export default function Field({ label, error, hint, children }) {
  return (
    <label className={'field' + (error ? ' field-error' : '')}>
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-msg">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}
