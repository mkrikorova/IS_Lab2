import HelperPage from './HelperPage.jsx';
import { api } from '../api.js';
import { parseDecimal, parseInteger, validateCoordinates } from '../validation.js';
import { isAdmin } from '../auth.js';

const config = {
  entity: 'coordinates',
  title: 'Координаты',
  newLabel: 'Новые координаты',
  columns: [
    { title: 'X', render: (c) => c.x, num: true },
    { title: 'Y', render: (c) => c.y, num: true },
  ],
  fields: [
    { key: 'x', label: 'x *', hint: 'целое число', inputMode: 'numeric' },
    { key: 'y', label: 'y *', hint: 'больше -833', inputMode: 'decimal' },
  ],
  empty: { x: '', y: '' },
  fromItem: (c) => ({ x: String(c.x), y: String(c.y) }),
  validate: (f) => validateCoordinates(f.x, f.y),
  toBody: (f) => ({ x: parseInteger(f.x), y: parseDecimal(f.y) }),
  label: (c) => `${c.id} (${c.x}; ${c.y})`,
  api: api.coordinates,
  // точки общие: изменение двигало бы и чужих десантников, поэтому только администратор
  canEdit: (user) => isAdmin(user),
  reassignTargets: (user, items) => items,
  note: 'Координаты — общие точки пространства: пара (x, y) уникальна, десантники в одной точке ссылаются на одну запись. Создавать точки может любой вошедший пользователь, изменять и удалять — только администратор.',
};

export default function CoordinatesPage() {
  return <HelperPage config={config} />;
}
