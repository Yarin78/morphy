import { useRef } from 'react';
import { IoCalendarOutline } from 'react-icons/io5';
import { parseDateText } from '../utils/dateText';

interface DateFieldProps {
  /** The date as typed: yyyy-mm-dd, yyyy-mm or yyyy; see dateText. */
  value: string;
  onChange: (value: string) => void;
  label: string;
  error?: string;
  readOnly?: boolean;
  className?: string;
  inputRef?: React.Ref<HTMLInputElement>;
}

/**
 * A date typed as yyyy-mm-dd, where the day, or the month and day, can be left out when they're not
 * known. A button opens the browser's date picker, for picking an exact date.
 */
export const DateField: React.FC<DateFieldProps> = ({
  value,
  onChange,
  label,
  error,
  readOnly,
  className,
  inputRef,
}) => {
  const pickerRef = useRef<HTMLInputElement>(null);

  const openPicker = () => {
    const picker = pickerRef.current;
    if (!picker) return;
    // Starting from the date typed, when it's an exact one
    const date = parseDateText(value);
    picker.value = date && date.month && date.day && date.year >= 1000 ? value.trim() : '';
    try {
      picker.showPicker();
    } catch {
      picker.focus();
    }
  };

  return (
    <label className={`game-info-field date-field${className ? ` ${className}` : ''}`}>
      <span className="game-info-label">{label}</span>
      <span className={`date-field-input${error ? ' invalid' : ''}`}>
        <input
          type="text"
          ref={inputRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          readOnly={readOnly}
          placeholder="yyyy-mm-dd"
          inputMode="numeric"
          aria-invalid={error ? true : undefined}
          autoComplete="off"
          data-1p-ignore
          data-lpignore="true"
        />
        <button
          type="button"
          className="date-field-picker-button"
          onClick={openPicker}
          disabled={readOnly}
          aria-label={`Pick ${label.toLowerCase()} from a calendar`}
          title="Pick a date"
        >
          <IoCalendarOutline />
        </button>
        {/* Not shown: only there for its date picker, which fills in the date above */}
        <input
          type="date"
          ref={pickerRef}
          className="date-field-picker"
          tabIndex={-1}
          aria-hidden="true"
          onChange={(e) => e.target.value && onChange(e.target.value)}
        />
      </span>
      {error && <span className="game-info-error">{error}</span>}
    </label>
  );
};
