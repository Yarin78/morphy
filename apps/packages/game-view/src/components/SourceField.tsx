import { sourceSubtitle } from '../utils/source';
import type { SourceInfo, SourceService } from '../utils/source';
import { EntityCombobox, gameCountText } from './EntityCombobox';

interface SourceFieldProps {
  value: SourceInfo | null;
  onChange: (source: SourceInfo | null) => void;
  service?: SourceService;
}

/**
 * The source's title, with suggestions of existing sources to pick from while typing. A badge marks
 * a new source, one that saving the game will create; editing the title of an existing one makes it
 * a new source, since the existing one belongs to other games too.
 */
export const SourceField: React.FC<SourceFieldProps> = ({ value, onChange, service }) => {
  const handleTextChange = (title: string) => {
    if (!value || value.id != null) {
      // A source created now, without any existing one's details
      onChange(title ? { id: null, title } : null);
    } else {
      // A new source keeps the details entered for it
      onChange(title || hasDetails(value) ? { ...value, title } : null);
    }
  };

  return (
    <div className="game-info-field game-info-field-source">
      <span className="game-info-label">
        Title
        {service && value && value.id == null && <span className="entity-badge entity-badge-new">New</span>}
      </span>
      <EntityCombobox<SourceInfo>
        text={value?.title ?? ''}
        onTextChange={handleTextChange}
        search={service?.search}
        onChoose={onChange}
        optionKey={(s) => s.id ?? s.title}
        optionTitle={(s) => s.title}
        optionSubtitle={(s) => [sourceSubtitle(s), gameCountText(s.gameCount)].filter(Boolean).join(' · ')}
        newWhat="source"
        inputProps={{ 'aria-label': 'Source title' }}
      />
    </div>
  );
};

function hasDetails(s: SourceInfo): boolean {
  return Boolean(s.publisher || s.publication || s.date || s.version || s.quality);
}
