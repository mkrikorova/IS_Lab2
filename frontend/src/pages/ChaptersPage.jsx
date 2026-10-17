import HelperPage from './HelperPage.jsx';
import { api } from '../api.js';
import { parseInteger, validateChapter } from '../validation.js';
import { canManageChapter } from '../auth.js';

const config = {
  entity: 'chapter',
  title: 'Ордены',
  newLabel: 'Новый орден',
  columns: [
    { title: 'Название', render: (c) => c.name },
    { title: 'Численность', render: (c) => c.marinesCount, num: true },
    { title: 'Командир', render: (c) => c.ownerLogin },
  ],
  fields: [
    { key: 'name', label: 'Название *', hint: 'уникально, регистр не важен' },
    { key: 'marinesCount', label: 'Численность *', hint: '1–1000', inputMode: 'numeric' },
  ],
  empty: { name: '', marinesCount: '' },
  fromItem: (c) => ({ name: c.name, marinesCount: String(c.marinesCount) }),
  validate: (f) => validateChapter(f.name, f.marinesCount),
  toBody: (f) => ({ name: f.name, marinesCount: parseInteger(f.marinesCount) }),
  label: (c) => `#${c.id} ${c.name}`,
  api: api.chapters,
  canEdit: canManageChapter,
  // командующий переводит бойцов только в свой орден, администратор — в любой
  reassignTargets: (user, items) => items.filter((c) => canManageChapter(user, c)),
  note: 'Новый орден записывается на вас. Название ордена уникально без учёта регистра. Изменять и удалять орден может его командующий или администратор.',
};

export default function ChaptersPage() {
  return <HelperPage config={config} />;
}
