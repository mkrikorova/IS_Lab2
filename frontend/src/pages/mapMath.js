// Геометрия карты: перевод мировых координат десантников в точки на экране,
// подбор шага сетки, объединение близких фигурок в группы.

// view = { cx, cy, s, w, h }: центр в координатах мира, пикселей на единицу, размер области в пикселях
export const PAD = 50;
export const Y_MIN = -833; // y должен быть больше -833 — ниже рисуем штриховку

/** Красивый шаг сетки: 1, 2, 5 × 10^k, чтобы на экране было ~targetLines линий. */
export function niceStep(span, targetLines = 10) {
  const raw = span / targetLines;
  if (!(raw > 0) || !Number.isFinite(raw)) return 1;
  const p = Math.pow(10, Math.floor(Math.log10(raw)));
  const m = raw / p;
  return (m < 1.5 ? 1 : m < 3.5 ? 2 : m < 7.5 ? 5 : 10) * p;
}

/** Подпись деления: дробные шаги — с нужным числом знаков, огромные — коротко. */
export function formatTick(v, step) {
  if (Math.abs(v) >= 1e6) {
    return new Intl.NumberFormat('ru-RU', { notation: 'compact', maximumFractionDigits: 2 }).format(v);
  }
  const digits = Math.max(0, -Math.floor(Math.log10(step)));
  return v.toFixed(Math.min(digits, 6)).replace('.', ',');
}

/** Вид, при котором видны все точки области размером w × h пикселей. */
export function fitView(points, w, h) {
  if (points.length === 0) return { cx: 0, cy: 0, s: 40, w, h };
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  for (const p of points) {
    minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
    minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
  }
  // если все в одной точке или на одной линии — показываем хотя бы 10 единиц
  const dx = Math.max(maxX - minX, 10);
  const dy = Math.max(maxY - minY, 10);
  const s = Math.min((w - 2 * PAD) / dx, (h - 2 * PAD) / dy);
  return { cx: (minX + maxX) / 2, cy: (minY + maxY) / 2, s, w, h };
}

export function toScreen(view, x, y) {
  return {
    px: view.w / 2 + (x - view.cx) * view.s,
    py: view.h / 2 - (y - view.cy) * view.s, // ось Y направлена вверх
  };
}

export function toWorld(view, px, py) {
  return {
    x: view.cx + (px - view.w / 2) / view.s,
    y: view.cy - (py - view.h / 2) / view.s,
  };
}

export function visibleBounds(view) {
  const a = toWorld(view, 0, view.h);
  const b = toWorld(view, view.w, 0);
  return { minX: a.x, minY: a.y, maxX: b.x, maxY: b.y };
}

/**
 * Группы фигурок. Десантники с одинаковыми координатами всегда в одной группе;
 * разные координаты тоже объединяются, если на экране они ближе radius пикселей
 * (иначе фигурки налезут друг на друга). Группа рисуется одной фигуркой со счётчиком.
 */
export function cluster(marines, view, radius = 26) {
  const byPoint = new Map();
  for (const m of marines) {
    const key = m.coordinates.x + ';' + m.coordinates.y;
    if (!byPoint.has(key)) byPoint.set(key, { x: m.coordinates.x, y: m.coordinates.y, marines: [] });
    byPoint.get(key).marines.push(m);
  }
  const groups = [];
  for (const pt of byPoint.values()) {
    const { px, py } = toScreen(view, pt.x, pt.y);
    const near = groups.find((g) => Math.hypot(g.px - px, g.py - py) < radius);
    if (near) {
      near.points.push(pt);
      near.marines.push(...pt.marines);
    } else {
      groups.push({ px, py, points: [pt], marines: [...pt.marines] });
    }
  }
  for (const g of groups) {
    g.key = g.marines.map((m) => m.id).sort((a, b) => a - b).join(',');
    g.samePoint = g.points.length === 1;
  }
  return groups;
}

/** 1 десантник, 2 десантника, 5 десантников */
export function marinesWord(n) {
  const a = n % 10, b = n % 100;
  if (a === 1 && b !== 11) return `${n} десантник`;
  if (a >= 2 && a <= 4 && (b < 12 || b > 14)) return `${n} десантника`;
  return `${n} десантников`;
}

/** Цвет ордена — стабильный по id. */
const PALETTE = ['#c0533a', '#3a78c0', '#c9a227', '#6b4fb3', '#2f9e6e', '#b8467f', '#4a9bb0', '#8a6d3b'];
export function chapterColor(id) {
  return PALETTE[(id - 1 + PALETTE.length * 1000) % PALETTE.length];
}
