import { SOURCE_QUALITIES } from '../utils/source';
import type { SourceInfo, SourceService } from '../utils/source';
import { dateTextError, formatDateText, parseDateText } from '../utils/date';
import { DateField } from './DateField';
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
  publication: string;
  date: string;
  version: string;
  quality: string;
};

const part = (value: number | undefined) => (value ? String(value) : '');

function toForm(s: SourceInfo): Form {
  return {
    title: s.title,
    publisher: s.publisher ?? '',
    publication: formatDateText(s.publication),
    date: formatDateText(s.date),
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
  for (const field of ['publication', 'date'] as const) {
    const error = dateTextError(form[field]);
    if (error) errors[field] = error;
  }
  number('version', 1, 32767);
  return errors;
}

function fromForm(form: Form, base: SourceInfo, id: number | null): SourceInfo {
  const n = (value: string) => (value.trim() ? parseInt(value, 10) : undefined);
  return {
    id,
    title: form.title.trim(),
    publisher: form.publisher.trim() || undefined,
    publication: parseDateText(form.publication) ?? undefined,
    date: parseDateText(form.date) ?? undefined,
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
    renderFields={({ form, set, setValue, readOnly, errors, input }) => {
      const numberProps = { inputMode: 'numeric' as const };
      return (
        <>
          <fieldset className="game-info-row">
            {input('title', 'Title', { first: true })}
            {input('publisher', 'Publisher')}
          </fieldset>

          <fieldset className="game-info-row">
            <legend>Edition</legend>
            <DateField
              value={form.publication}
              onChange={(value) => setValue('publication', value)}
              label="Publication"
              error={errors.publication}
              readOnly={readOnly}
            />
            <DateField
              value={form.date}
              onChange={(value) => setValue('date', value)}
              label="Date"
              error={errors.date}
              readOnly={readOnly}
            />
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
        </>
      );
    }}
  />
);
