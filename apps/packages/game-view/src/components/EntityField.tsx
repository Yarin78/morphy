import type { SharedEntity } from './EntityDetailsDialog';
import { EntityCombobox, gameCountText } from './EntityCombobox';

/** An entity the game refers to that's named by a title, like a tournament or a source. */
export interface TitledEntity extends SharedEntity {
  title: string;
}

interface EntityFieldProps<E extends TitledEntity> {
  value: E | null;
  onChange: (entity: E | null) => void;
  /** Existing ones whose titles start with the text; without it, the field is plain text. */
  search?: (text: string) => Promise<E[]>;
  /** A new one with a title, and none of an existing one's details. */
  create: (title: string) => E;
  /** Whether a new one has details besides its title, which it keeps without a title. */
  hasDetails: (entity: E) => boolean;
  /** What tells existing ones with the same title apart. */
  subtitle: (entity: E) => string;
  /** What it is, in lowercase: "tournament". */
  what: string;
  /** Shown above the field, if anything. */
  label?: string;
  ariaLabel: string;
  placeholder?: string;
  className: string;
}

/**
 * An entity's title, with suggestions of existing ones to pick from while typing; empty for none. A
 * badge marks a new one, which saving the game will create; editing the title of an existing one
 * makes it a new one, since the existing one belongs to other games too.
 */
export function EntityField<E extends TitledEntity>({
  value,
  onChange,
  search,
  create,
  hasDetails,
  subtitle,
  what,
  label,
  ariaLabel,
  placeholder,
  className,
}: EntityFieldProps<E>) {
  const handleTextChange = (title: string) => {
    if (!value || value.id != null) {
      // A new one, without any existing one's details
      onChange(title ? create(title) : null);
    } else {
      // A new one keeps the details entered for it
      onChange(title || hasDetails(value) ? { ...value, title } : null);
    }
  };
  const isNew = Boolean(search) && value !== null && value.id == null;
  return (
    <div className={`game-info-field ${className}`}>
      {label && <span className="game-info-label">{label}</span>}
      <div className={`entity-input${isNew ? ' with-badge' : ''}`}>
        <EntityCombobox<E>
          text={value?.title ?? ''}
          onTextChange={handleTextChange}
          search={search}
          onChoose={onChange}
          optionKey={(e) => e.id ?? e.title}
          optionTitle={(e) => e.title}
          optionSubtitle={(e) => [subtitle(e), gameCountText(e.gameCount)].filter(Boolean).join(' · ')}
          newWhat={what}
          inputProps={{ 'aria-label': ariaLabel, placeholder }}
        />
        {isNew && <span className="entity-badge entity-badge-new entity-badge-inline">New</span>}
      </div>
    </div>
  );
}
