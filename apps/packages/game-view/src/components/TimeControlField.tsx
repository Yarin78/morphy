import { IoTimeOutline } from 'react-icons/io5';
import { TIME_CONTROL_PRESETS } from '../utils/timeControl';
import { IconSelect } from './IconSelect';

interface TimeControlFieldProps {
  /** The time control as typed; see formatTimeControl. */
  value: string;
  onChange: (value: string) => void;
  error?: string;
}

type Preset = (typeof TIME_CONTROL_PRESETS)[number];

/**
 * A time control typed as in the PGN TimeControl tag but with hours, minutes and seconds, like
 * "40/90m+30s:30m+30s"; a button picks one of the common ones instead.
 */
export const TimeControlField: React.FC<TimeControlFieldProps> = ({ value, onChange, error }) => (
  <div className="game-info-field game-info-field-time-control">
    <span className="game-info-label">Time control</span>
    <span className={`date-field-input time-control-input${error ? ' invalid' : ''}`}>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-label="Time control"
        aria-invalid={error ? true : undefined}
        autoComplete="off"
        data-1p-ignore
        data-lpignore="true"
      />
      <IconSelect<Preset>
        className="time-control-presets"
        align="end"
        options={TIME_CONTROL_PRESETS}
        value={undefined}
        onChange={(preset) => onChange(preset.text)}
        optionKey={(preset) => preset.text}
        renderOption={(preset) => (
          <>
            <span className="time-control-preset-text">{preset.text}</span>
            <span className="time-control-preset-name">{preset.name}</span>
          </>
        )}
        placeholder={<IoTimeOutline />}
        label="Common time controls"
        title="Pick a common time control"
      />
    </span>
    {error && <span className="game-info-error">{error}</span>}
  </div>
);
