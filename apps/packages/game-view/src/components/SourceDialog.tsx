import { SOURCE_QUALITIES } from '../utils/source';
import type { SourceInfo, SourceService } from '../utils/source';
import type { DateParts } from '../utils/tournament';
import { EntityDetailsDialog } from './EntityDetailsDialog';

interface SourceDialogProps {
  source: SourceInfo;
  service?: SourceService;
  /** Called with the source the game should now be from. */
  onApply: (source: SourceInfo) => void;
  onClose: () => void;
}

type Form = {
  title: string;
  publisher: string;
  publicationYear: string;
  publicationMonth: string;
  publicationDay: string;
  dateYear: string;
  dateMonth: string;
  dateDay: string;
  version: string;
  quality: string;
};

const part = (value: number | undefined) => (value ? String(value) : '');

function toForm(s: SourceInfo): Form {
  return {
    title: s.title,
    publisher: s.publisher ?? '',
    publicationYear: part(s.publication?.year),
    publicationMonth: part(s.publication?.month),
    publicationDay: part(s.publication?.day),
    dateYear: part(s.date?.year),
    dateMonth: part(s.date?.month),
    dateDay: part(s.date?.day),
    version: part(s.version),
    quality: s.quality ?? '',
  };
}

function validate(form: Form): Partial<Record<keyof Form, string>> {
  const errors: Partial<Record<keyof Form, string>> = {};
  const number = (field: keyof Form, min: number, max: number) => {
    const value = form[field].trim();
    if (value && !(/^\d+$/.test(value) && +value >= min && +value <= max)) {
      errors[field] = `Must be ${min}–${max}`;
    }
  };
  if (!form.title.trim()) errors.title = 'Needs a title';
  number('publicationYear', 1, 9999);
  number('publicationMonth', 1, 12);
  number('publicationDay', 1, 31);
  number('dateYear', 1, 9999);
  number('dateMonth', 1, 12);
  number('dateDay', 1, 31);
  number('version', 1, 32767);
  return errors;
}

function fromForm(form: Form, base: SourceInfo, id: number | null): SourceInfo {
  const n = (value: string) => (value.trim() ? parseInt(value, 10) : undefined);
  const date = (y: string, m: string, d: string): DateParts | undefined =>
    n(y) || n(m) || n(d) ? { year: n(y) ?? 0, month: n(m) ?? 0, day: n(d) ?? 0 } : undefined;
  return {
    id,
    title: form.title.trim(),
    publisher: form.publisher.trim() || undefined,
    publication: date(form.publicationYear, form.publicationMonth, form.publicationDay),
    date: date(form.dateYear, form.dateMonth, form.dateDay),
    version: n(form.version),
    quality: form.quality || undefined,
    gameCount: id != null ? base.gameCount : undefined,
  };
}

/** The details of the game's source; see EntityDetailsDialog. */
export const SourceDialog: React.FC<SourceDialogProps> = ({ source, service, onApply, onClose }) => (
  <EntityDetailsDialog<SourceInfo, Form>
    entity={source}
    what="source"
    update={service?.update}
    toForm={toForm}
    validate={validate}
    fromForm={fromForm}
    onApply={onApply}
    onClose={onClose}
    renderFields={({ form, set, readOnly, input }) => {
      const numberProps = { inputMode: 'numeric' as const };
      return (
        <>
          <fieldset className="game-info-row">
            {input('title', 'Title', { first: true })}
            {input('publisher', 'Publisher')}
          </fieldset>

          <div className="source-dialog-dates">
            <fieldset className="game-info-row">
              <legend>Publication</legend>
              {input('publicationYear', 'Year', { ...numberProps, placeholder: 'yyyy' })}
              {input('publicationMonth', 'Month', { ...numberProps, placeholder: 'mm' })}
              {input('publicationDay', 'Day', { ...numberProps, placeholder: 'dd' })}
            </fieldset>
            <fieldset className="game-info-row">
              <legend>Date</legend>
              {input('dateYear', 'Year', { ...numberProps, placeholder: 'yyyy' })}
              {input('dateMonth', 'Month', { ...numberProps, placeholder: 'mm' })}
              {input('dateDay', 'Day', { ...numberProps, placeholder: 'dd' })}
            </fieldset>
            <fieldset className="game-info-row">
              <legend>Edition</legend>
              {input('version', 'Version', numberProps)}
              <label className="game-info-field">
                <span className="game-info-label">Quality</span>
                <select value={form.quality} onChange={set('quality')} disabled={readOnly}>
                  <option value=""></option>
                  {SOURCE_QUALITIES.map((q) => (
                    <option key={q.value} value={q.value}>
                      {q.label}
                    </option>
                  ))}
                </select>
              </label>
            </fieldset>
          </div>
        </>
      );
    }}
  />
);
