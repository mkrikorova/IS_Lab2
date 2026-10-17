import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { api, WEAPON_LABELS } from '../api.js';
import { useChanges } from '../live.js';
import ErrorBanner from '../components/ErrorBanner.jsx';
import MarineView from '../components/MarineView.jsx';
import MarineForm from '../components/MarineForm.jsx';
import {
  chapterColor, cluster, fitView, formatTick, marinesWord, niceStep, toScreen, toWorld,
  visibleBounds, Y_MIN,
} from './mapMath.js';

const S_MIN = 1e-13; // самый мелкий масштаб (огромные координаты Long)
const S_MAX = 400; // 1 единица координат = 400 точек — дальше приближать бессмысленно

const clampS = (s) => Math.min(S_MAX, Math.max(S_MIN, s));

/** Масштабирование вокруг точки экрана (px, py): она остаётся на месте. */
function zoomAt(view, factor, px = view.w / 2, py = view.h / 2) {
  const w = toWorld(view, px, py);
  const s = clampS(view.s * factor);
  return { ...view, s, cx: w.x - (px - view.w / 2) / s, cy: w.y + (py - view.h / 2) / s };
}

/** Высота карты: пропорционально ширине, но не выше окна. */
function mapHeight(width) {
  const byWindow = window.innerHeight - 260;
  return Math.round(Math.max(320, Math.min(width * 0.62, byWindow, 680)));
}

export default function MapPage() {
  const [marines, setMarines] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(null);
  const [chapterId, setChapterId] = useState('');
  const [view, setView] = useState(null);
  const [hover, setHover] = useState(null); // группа под курсором — быстрый просмотр
  const [pinned, setPinned] = useState(null); // { ids, x, y } — закреплённый список по клику
  const [dialog, setDialog] = useState(null);
  const [size, setSize] = useState(null); // { w, h } области карты в пикселях
  const wrapRef = useRef(null);
  const svgRef = useRef(null);
  const drag = useRef(null);

  const load = useCallback(() => {
    api.marines.all().then((d) => { setMarines(d); setLoaded(true); setError(null); }).catch((e) => setError(e.message));
  }, []);
  useEffect(load, [load]);
  // любые изменения у других пользователей — перечитываем, вид карты не сбрасываем
  useChanges(load);

  const shown = useMemo(
    () => (chapterId ? marines.filter((m) => String(m.chapter.id) === chapterId) : marines),
    [marines, chapterId]
  );
  const points = useMemo(() => shown.map((m) => m.coordinates), [shown]);

  // первый показ и смена фильтра — вписываем всех в экран
  const fitted = useRef(null);
  useEffect(() => {
    if (!loaded || !size || fitted.current === chapterId) return;
    setView(fitView(points, size.w, size.h));
    fitted.current = chapterId;
  }, [loaded, size, points, chapterId]);

  // размер контейнера: при изменении окна сохраняем центр и масштаб, меняем только w/h
  useLayoutEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const update = () => {
      const w = Math.round(el.getBoundingClientRect().width);
      const h = mapHeight(w);
      setSize((old) => (old && old.w === w && old.h === h ? old : { w, h }));
      // масштаб меняем вместе с размером, чтобы в кадре оставалась та же область
      setView((v) => (v ? { ...v, w, h, s: v.s * Math.min(w / v.w, h / v.h) } : v));
    };
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    window.addEventListener('resize', update);
    return () => { ro.disconnect(); window.removeEventListener('resize', update); };
  }, [view === null]); // контейнер меняется после загрузки — подписываемся заново

  const chapters = useMemo(() => {
    const map = new Map();
    marines.forEach((m) => map.set(m.chapter.id, m.chapter));
    return [...map.values()].sort((a, b) => a.id - b.id);
  }, [marines]);

  const toLocal = useCallback((clientX, clientY) => {
    const r = svgRef.current.getBoundingClientRect();
    return { px: clientX - r.left, py: clientY - r.top };
  }, []);

  // колесо мыши — масштаб вокруг курсора (passive: false, чтобы не прокручивалась страница)
  useEffect(() => {
    const el = svgRef.current;
    if (!el) return;
    const onWheel = (e) => {
      e.preventDefault();
      const { px, py } = toLocal(e.clientX, e.clientY);
      setView((v) => zoomAt(v, Math.exp(-e.deltaY * 0.0015), px, py));
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [toLocal, view === null]);

  // Esc закрывает закреплённый список (если не открыто окно)
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && !dialog && setPinned(null);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [dialog]);

  const groups = useMemo(() => (view ? cluster(shown, view) : []), [shown, view]);

  // ---------- перетаскивание карты ----------
  function onPointerDown(e) {
    if (e.button !== 0) return;
    drag.current = { x: e.clientX, y: e.clientY, view, moved: false };
    e.currentTarget.setPointerCapture(e.pointerId);
  }
  function onPointerMove(e) {
    const d = drag.current;
    if (!d) return;
    const dx = e.clientX - d.x;
    const dy = e.clientY - d.y;
    if (Math.abs(dx) + Math.abs(dy) > 3) d.moved = true;
    if (d.moved) setView({ ...d.view, cx: d.view.cx - dx / d.view.s, cy: d.view.cy + dy / d.view.s });
  }
  function onPointerUp() {
    const d = drag.current;
    drag.current = null;
    if (d && !d.moved) setPinned(null); // простой клик по пустому месту — закрыть список
  }

  function pin(g) {
    const w = toWorld(view, g.px, g.py);
    setPinned({ ids: g.marines.map((m) => m.id), x: w.x, y: w.y });
    setHover(null);
  }

  if (!view) {
    return (
      <section>
        <div className="page-head"><h1>Карта</h1></div>
        <ErrorBanner>{error}</ErrorBanner>
        <div ref={wrapRef} className="map-wrap map-loading muted">Загрузка…</div>
      </section>
    );
  }
  const W = view.w, H = view.h;

  // ---------- сетка ----------
  const b = visibleBounds(view);
  const step = niceStep(b.maxX - b.minX);
  const xs = [], ys = [];
  for (let x = Math.ceil(b.minX / step) * step; x <= b.maxX && xs.length < 200; x += step) xs.push(x);
  for (let y = Math.ceil(b.minY / step) * step; y <= b.maxY && ys.length < 200; y += step) ys.push(y);
  const origin = toScreen(view, 0, 0);
  const forbiddenTop = toScreen(view, 0, Y_MIN).py;

  // всплывающий список: закреплённый важнее наведённого
  const popup = pinned
    ? (() => {
        const list = shown.filter((m) => pinned.ids.includes(m.id));
        if (list.length === 0) return null;
        const { px, py } = toScreen(view, pinned.x, pinned.y);
        return { list, px, py, pinned: true };
      })()
    : hover
      ? { list: hover.marines, px: hover.px, py: hover.py, pinned: false }
      : null;

  return (
    <section>
      <div className="page-head">
        <h1>Карта</h1>
        <div className="map-tools">
          <select value={chapterId} onChange={(e) => { setChapterId(e.target.value); setPinned(null); }} aria-label="Фильтр по ордену">
            <option value="">Все ордены</option>
            {chapters.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <button className="btn" onClick={() => setView((v) => zoomAt(v, 1 / 1.6))} aria-label="Отдалить">−</button>
          <button className="btn" onClick={() => setView((v) => zoomAt(v, 1.6))} aria-label="Приблизить">+</button>
          <button className="btn" onClick={() => setView(fitView(points, W, H))}>Показать всех</button>
        </div>
      </div>
      <ErrorBanner>{error}</ErrorBanner>

      <div className="map-info muted">
        <span>На карте: {shown.length}</span>
        <span>Шаг сетки: {formatTick(step, step)}</span>
        <span>Колесо — масштаб, перетаскивание — сдвиг, клик по фигурке — список</span>
      </div>

      <div className="map-wrap" ref={wrapRef}>
        <svg
          ref={svgRef}
          className="map"
          width={W}
          height={H}
          viewBox={`0 0 ${W} ${H}`}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          role="img"
          aria-label="Карта расположения десантников"
        >
          <defs>
            <pattern id="hatch" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <line x1="0" y1="0" x2="0" y2="8" className="hatch-line" />
            </pattern>
          </defs>

          <rect className="map-bg" x="0" y="0" width={W} height={H} />

          {/* недопустимая область y ≤ -833 */}
          {forbiddenTop < H && (
            <g>
              <rect x="0" y={Math.max(0, forbiddenTop)} width={W} height={H - Math.max(0, forbiddenTop)} fill="url(#hatch)" />
              {forbiddenTop > 0 && <line className="forbidden-line" x1="0" y1={forbiddenTop} x2={W} y2={forbiddenTop} />}
              {forbiddenTop > 24 && <text className="map-forbidden" x={W - 12} y={forbiddenTop - 6} textAnchor="end">ниже y = −833 — недопустимые координаты</text>}
            </g>
          )}

          {xs.map((x) => { const { px } = toScreen(view, x, 0); return <line key={'x' + x} className="grid" x1={px} y1="0" x2={px} y2={H} />; })}
          {ys.map((y) => { const { py } = toScreen(view, 0, y); return <line key={'y' + y} className="grid" x1="0" y1={py} x2={W} y2={py} />; })}
          {origin.px >= 0 && origin.px <= W && <line className="axis" x1={origin.px} y1="0" x2={origin.px} y2={H} />}
          {origin.py >= 0 && origin.py <= H && <line className="axis" x1="0" y1={origin.py} x2={W} y2={origin.py} />}

          {xs.map((x) => { const { px } = toScreen(view, x, 0); return px > 30 && px < W - 50 && <text key={'tx' + x} className="tick" x={px} y={H - 6} textAnchor="middle">{formatTick(x, step)}</text>; })}
          {ys.map((y) => { const { py } = toScreen(view, 0, y); return py > 32 && py < H - 20 && <text key={'ty' + y} className="tick" x="6" y={py - 4}>{formatTick(y, step)}</text>; })}
          {(() => {
            const ax = origin.px > 40 && origin.px < W - 40 ? origin.px + 8 : 10;
            const ay = origin.py > 30 && origin.py < H - 40 ? origin.py - 8 : H - 24;
            return (
              <>
                <text className="axis-label" x={W - 10} y={ay} textAnchor="end">X →</text>
                <text className="axis-label" x={ax} y="18">↑ Y</text>
              </>
            );
          })()}

          {groups.map((g) => {
            const ids = new Set(g.marines.map((m) => m.chapter.id));
            const color = ids.size === 1 ? chapterColor(g.marines[0].chapter.id) : '#8a8a8a';
            const active = (pinned && g.key === pinned.ids.slice().sort((a, b) => a - b).join(',')) || hover?.key === g.key;
            const label = g.marines.length === 1
              ? `${g.marines[0].name}, x = ${g.marines[0].coordinates.x}, y = ${g.marines[0].coordinates.y}`
              : marinesWord(g.marines.length);
            return (
              <g
                key={g.key}
                className={'marker' + (active ? ' marker-active' : '')}
                transform={`translate(${g.px} ${g.py})`}
                tabIndex={0}
                role="button"
                aria-label={label}
                onPointerDown={(e) => e.stopPropagation()}
                onPointerEnter={() => !pinned && setHover(g)}
                onPointerLeave={() => setHover(null)}
                onClick={() => pin(g)}
                onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), pin(g))}
              >
                <ellipse className="marker-ring" cx="0" cy="0" rx="17" ry="7" />
                <ellipse cx="0" cy="0" rx="13" ry="5" fill={color} />
                <path className="marker-body" d="M-7 -2 L-8 -15 Q-8 -20 -3 -21 L3 -21 Q8 -20 8 -15 L7 -2 Z" />
                <circle className="marker-body" cx="0" cy="-27" r="6.5" />
                <rect x="-4.5" y="-29" width="9" height="3" rx="1" fill={color} />
                {/* разные координаты в одной группе — пунктирная подставка */}
                {!g.samePoint && <ellipse cx="0" cy="0" rx="17" ry="7" className="marker-mixed" />}
                {g.marines.length > 1 && (
                  <g transform="translate(11 -30)">
                    <circle r="9" className="badge-bg" />
                    <text className="badge-text" textAnchor="middle" dy="3.5">{g.marines.length > 99 ? '99+' : g.marines.length}</text>
                  </g>
                )}
              </g>
            );
          })}
        </svg>

        {shown.length === 0 && <div className="map-empty muted">Нет десантников для отображения</div>}

        {popup && (
          <MapPopup
            popup={popup}
            width={W}
            height={H}
            onOpen={(id) => setDialog({ type: 'view', id })}
            onClose={() => setPinned(null)}
            onZoom={(list) => { setView(fitView(list.map((m) => m.coordinates), W, H)); setPinned(null); }}
          />
        )}
      </div>

      {chapters.length > 0 && (
        <div className="map-legend">
          {chapters.map((c) => (
            <span key={c.id}><i style={{ background: chapterColor(c.id) }} />{c.name}</span>
          ))}
          <span><i style={{ background: '#8a8a8a' }} />несколько орденов</span>
          <span><i className="legend-dashed" />разные координаты рядом</span>
        </div>
      )}

      {dialog?.type === 'view' && (
        <MarineView id={dialog.id} onClose={() => setDialog(null)} onEdit={(m) => setDialog({ type: 'edit', marine: m })} />
      )}
      {dialog?.type === 'edit' && (
        <MarineForm marine={dialog.marine} onClose={() => setDialog(null)} onSaved={load} />
      )}
    </section>
  );
}

function MapPopup({ popup, width, height, onOpen, onClose, onZoom }) {
  const { list, pinned } = popup;
  const first = list[0].coordinates;
  const samePoint = list.every((m) => m.coordinates.x === first.x && m.coordinates.y === first.y);
  // справа от фигурки; у правого края — слева
  const left = popup.px;
  const top = popup.py;
  const flip = popup.px > width * 0.6;
  return (
    <div
      className={'map-popup' + (pinned ? ' map-popup-pinned' : '')}
      style={{
        left,
        top: Math.min(Math.max(top, 8), height - 8),
        // в верхней части — вниз от фигурки, в нижней — вверх
        transform: `translate(${flip ? 'calc(-100% - 24px)' : '24px'}, ${top > height * 0.5 ? 'calc(-100% + 16px)' : '-16px'})`,
        maxHeight: Math.max(160, height - 24),
      }}
      role={pinned ? 'dialog' : 'tooltip'}
      onPointerDown={(e) => e.stopPropagation()}
    >
      <div className="map-popup-head">
        <b>{samePoint ? `x = ${first.x}, y = ${first.y}` : `${marinesWord(list.length)} рядом`}</b>
        {pinned && <button className="icon-btn" onClick={onClose} aria-label="Закрыть">×</button>}
      </div>
      {!samePoint && pinned && (
        <button className="btn btn-sm map-zoom" onClick={() => onZoom(list)}>Приблизить</button>
      )}
      <ul>
        {list.slice(0, pinned ? 200 : 6).map((m) => (
          <li key={m.id}>
            {pinned ? (
              <button className="map-item" onClick={() => onOpen(m.id)}>
                <i style={{ background: chapterColor(m.chapter.id) }} />
                <span className="map-item-name">{m.name}</span>
                <span className="muted">{m.id}</span>
                <span className="map-item-meta muted">
                  {m.chapter.name}
                  {m.meleeWeapon ? ' · ' + WEAPON_LABELS[m.meleeWeapon] : ''}
                  {!samePoint ? ` · (${m.coordinates.x}; ${m.coordinates.y})` : ''}
                </span>
              </button>
            ) : (
              <span className="map-item">
                <i style={{ background: chapterColor(m.chapter.id) }} />
                <span className="map-item-name">{m.name}</span>
                <span className="muted">{m.id}</span>
              </span>
            )}
          </li>
        ))}
      </ul>
      {!pinned && (
        <div className="muted map-popup-hint">
          {list.length > 6 ? `и ещё ${list.length - 6}. ` : ''}Нажмите, чтобы выбрать
        </div>
      )}
    </div>
  );
}
