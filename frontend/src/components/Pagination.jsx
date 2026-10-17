export default function Pagination({ page, totalPages, totalElements, size, onPage, onSize }) {
  const last = Math.max(totalPages - 1, 0);
  return (
    <div className="pager">
      <span className="muted">Всего: {totalElements}</span>
      <div className="pager-nav">
        <button className="btn btn-sm" onClick={() => onPage(0)} disabled={page <= 0} aria-label="Первая страница">«</button>
        <button className="btn btn-sm" onClick={() => onPage(page - 1)} disabled={page <= 0}>Назад</button>
        <span className="pager-pos">стр. {totalPages === 0 ? 0 : page + 1} из {totalPages}</span>
        <button className="btn btn-sm" onClick={() => onPage(page + 1)} disabled={page >= last}>Вперёд</button>
        <button className="btn btn-sm" onClick={() => onPage(last)} disabled={page >= last} aria-label="Последняя страница">»</button>
      </div>
      <label className="pager-size">
        по
        <select value={size} onChange={(e) => onSize(Number(e.target.value))}>
          {[5, 10, 20, 50].map((n) => <option key={n} value={n}>{n}</option>)}
        </select>
      </label>
    </div>
  );
}
