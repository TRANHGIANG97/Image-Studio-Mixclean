import { editorEn, type EditorI18nKey } from './en';
import { editorVi } from './vi';

export type EditorLocale = 'en' | 'vi';

const catalogs: Record<EditorLocale, Record<EditorI18nKey, string>> = {
  en: editorEn,
  vi: editorVi,
};

let currentLocale: EditorLocale = 'vi';

export function setEditorLocale(locale: EditorLocale): void {
  currentLocale = locale;
}

export function getEditorLocale(): EditorLocale {
  return currentLocale;
}

export function t(key: EditorI18nKey): string {
  return catalogs[currentLocale][key] ?? editorEn[key] ?? key;
}

export { editorEn, editorVi };
export type { EditorI18nKey };
