import { NATIONS, nationInfo } from '../utils/nations';
import type { NationInfo } from '../utils/nations';
import { NationFlag } from './EloTypeIcons';
import { IconSelect } from './IconSelect';

interface NationSelectProps {
  /** An IOC code, e.g. 'NOR'; '' for none. */
  value: string;
  onChange: (ioc: string) => void;
  /** Whether "None" can be chosen; otherwise, without a nation, it asks for one. */
  optional?: boolean;
  disabled?: boolean;
}

/** Stands for no nation in the list. */
const NO_NATION: NationInfo = { ioc: '', name: 'None' };

function nationMatches(nation: NationInfo, query: string): boolean {
  const q = query.toLowerCase();
  return nation.name.toLowerCase().includes(q) || nation.ioc.toLowerCase().startsWith(q);
}

/** A nation picked by its flag and name from all of them, with a search field. */
export const NationSelect: React.FC<NationSelectProps> = ({ value, onChange, optional, disabled }) => (
  <IconSelect<NationInfo>
    options={optional ? [NO_NATION, ...NATIONS] : NATIONS}
    value={nationInfo(value) ?? (optional ? NO_NATION : undefined)}
    onChange={(nation) => onChange(nation.ioc)}
    optionKey={(nation) => nation.ioc || 'none'}
    renderOption={(nation) => (
      <>
        {nation.ioc && <NationFlag nation={nation.ioc} />}
        <span>{nation.name}</span>
      </>
    )}
    placeholder={
      optional ? (
        <span className="icon-select-placeholder">None</span>
      ) : (
        <>
          <NationFlag nation={undefined} />
          <span className="icon-select-placeholder">Choose a nation</span>
        </>
      )
    }
    label="Nation"
    matches={nationMatches}
    disabled={disabled}
  />
);
