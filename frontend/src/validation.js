// Клиентская проверка — те же правила, что в БД и на сервере.
// Сервер всё равно проверяет повторно; это только чтобы подсказать сразу.

/** Максимальная длина числовых полей ввода */
export const NUM_MAX = 15;
/** Максимальная длина строк — как VARCHAR(255) в БД */
export const TEXT_MAX = 255;

const INT = /^-?\d+$/;
const INT_MIN = -2147483648;
const INT_MAX = 2147483647;

export const isBlank = (v) => v === undefined || v === null || String(v).trim() === '';

export function parseInteger(v) {
  return INT.test(String(v).trim()) ? Number(v) : NaN;
}

export function parseDecimal(v) {
  const s = String(v).trim().replace(',', '.');
  return s === '' ? NaN : Number(s);
}

export function checkLong(v, label) {
  if (isBlank(v)) return `${label}: обязательное поле`;
  if (!INT.test(String(v).trim())) return `${label}: нужно целое число`;
  if (!Number.isSafeInteger(parseInteger(v))) return `${label}: число слишком большое`;
  return null;
}

export function checkInt(v, label) {
  const e = checkLong(v, label);
  if (e) return e;
  const n = parseInteger(v);
  if (n < INT_MIN || n > INT_MAX) return `${label}: число слишком большое`;
  return null;
}

/** Ошибки для координат: { x, y } */
export function validateCoordinates(x, y) {
  const err = {};
  const ex = checkLong(x, 'x');
  if (ex) err.x = ex;
  if (isBlank(y)) err.y = 'y: обязательное поле';
  else {
    const n = parseDecimal(y);
    if (!Number.isFinite(n)) err.y = 'y: нужно число';
    else if (n <= -833) err.y = 'y должен быть больше -833';
  }
  return err;
}

/** Ошибки для ордена: { name, marinesCount } */
export function validateChapter(name, marinesCount) {
  const err = {};
  if (isBlank(name)) err.name = 'Имя ордена не может быть пустым';
  const e = checkLong(marinesCount, 'Численность');
  if (e) err.marinesCount = e;
  else {
    const n = parseInteger(marinesCount);
    if (n <= 0) err.marinesCount = 'Численность должна быть больше 0';
    else if (n > 1000) err.marinesCount = 'Численность не может быть больше 1000';
  }
  return err;
}
