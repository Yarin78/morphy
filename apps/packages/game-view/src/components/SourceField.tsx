import { sourceSubtitle } from '../utils/source';
import type { SourceInfo, SourceService } from '../utils/source';
import { EntityField } from './EntityField';

interface SourceFieldProps {
  value: SourceInfo | null;
  onChange: (source: SourceInfo | null) => void;
  service?: SourceService;
}

/** The source's title, with suggestions of existing sources; see EntityField. */
export const SourceField: React.FC<SourceFieldProps> = ({ value, onChange, service }) => (
  <EntityField<SourceInfo>
    value={value}
    onChange={onChange}
    search={service?.search}
    create={(title) => ({ id: null, title })}
    hasDetails={hasDetails}
    subtitle={sourceSubtitle}
    what="source"
    label="Title"
    ariaLabel="Source title"
    className="game-info-field-source"
  />
);

function hasDetails(s: SourceInfo): boolean {
  return Boolean(s.publisher || s.publication || s.date || s.version || s.quality);
}
