import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import './EntityCombobox.css';

interface EntityComboboxProps<T> {
  text: string;
  /** Called as the text is typed. */
  onTextChange: (text: string) => void;
  /** Existing entities matching the typed text; without it, the field is plain text. */
  search?: (query: string) => Promise<T[]>;
  /** Called when an existing entity is picked from the suggestions. */
  onChoose: (option: T) => void;
  optionKey: (option: T) => string | number;
  /** The first line of a suggestion, and the smaller second line. */
  optionTitle: (option: T) => string;
  optionSubtitle: (option: T) => string;
  /** Something shown before a suggestion's title: a flag, say. */
  optionIcon?: (option: T) => React.ReactNode;
  /**
   * What the last suggestion, which keeps the typed text as a new entity, calls it: "tournament".
   * Without it there's no such suggestion, and none is highlighted until moved to with the arrows,
   * so Enter keeps the text typed.
   */
  newWhat?: string;
  inputProps?: React.InputHTMLAttributes<HTMLInputElement> & { ref?: React.Ref<HTMLInputElement> };
  /** A class of the list of suggestions besides its own, to style it where it's used */
  listClassName?: string;
  /** The narrowest the list is, if wider than the field */
  minListWidth?: number;
}

const SEARCH_DELAY_MS = 200;
const MIN_LIST_WIDTH = 440;

/**
 * A text field suggesting existing entities (players, tournaments, ...) to pick from while typing,
 * with a last suggestion to keep the typed text for a new one if newWhat is given.
 */
export function EntityCombobox<T>({
  text,
  onTextChange,
  search,
  onChoose,
  optionKey,
  optionTitle,
  optionSubtitle,
  optionIcon,
  newWhat,
  inputProps,
  listClassName,
  minListWidth = MIN_LIST_WIDTH,
}: EntityComboboxProps<T>) {
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<T[]>([]);
  // With nothing new to suggest, nothing is highlighted at first
  const firstHighlighted = newWhat ? 0 : -1;
  const [highlighted, setHighlighted] = useState(firstHighlighted);
  const listRef = useRef<HTMLUListElement>(null);
  const comboboxRef = useRef<HTMLDivElement>(null);
  const [listPosition, setListPosition] = useState<React.CSSProperties | null>(null);

  const query = text.trim();
  useEffect(() => {
    if (!search || !open || !query) return;
    let cancelled = false;
    const timer = setTimeout(() => {
      search(query)
        .then((found) => {
          if (cancelled) return;
          setResults(found);
          setHighlighted(firstHighlighted);
        })
        .catch((err) => console.error('The search for suggestions failed:', err));
    }, SEARCH_DELAY_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [search, open, query, firstHighlighted]);

  const options: (T | 'new')[] = query ? (newWhat ? [...results, 'new'] : results) : [];
  const showList = Boolean(search) && open && options.length > 0;

  useEffect(() => {
    listRef.current?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' });
  }, [highlighted]);

  // The list is put on the page, not in the dialog, which would cut it off; it follows the input
  useLayoutEffect(() => {
    if (!showList) return;
    const place = () => {
      const rect = comboboxRef.current?.getBoundingClientRect();
      if (!rect) return;
      const width = Math.min(Math.max(rect.width, minListWidth), window.innerWidth - 16);
      setListPosition({
        top: rect.bottom + 2,
        left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
        width,
        maxHeight: Math.max(120, window.innerHeight - rect.bottom - 16),
      });
    };
    place();
    window.addEventListener('resize', place);
    window.addEventListener('scroll', place, true);
    return () => {
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', place, true);
    };
  }, [showList, minListWidth]);

  const choose = (option: T | 'new') => {
    setOpen(false);
    if (option !== 'new') onChoose(option);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!search) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) setOpen(true);
      else setHighlighted((i) => Math.min(i + 1, options.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlighted((i) => Math.max(i - 1, firstHighlighted));
    } else if (e.key === 'Enter' && showList && highlighted < 0) {
      // Nothing highlighted: the text typed is kept, and the form submitted
      setOpen(false);
    } else if (e.key === 'Enter' && showList) {
      // Choose the suggestion rather than submitting the form
      e.preventDefault();
      choose(options[Math.min(highlighted, options.length - 1)]);
    } else if (e.key === 'Escape' && showList) {
      // Close the suggestions, not the dialog
      e.preventDefault();
      e.stopPropagation();
      setOpen(false);
    }
  };

  return (
    <div className="entity-combobox" ref={comboboxRef}>
      <input
        type="text"
        value={text}
        onChange={(e) => {
          setOpen(true);
          onTextChange(e.target.value);
        }}
        onKeyDown={handleKeyDown}
        onBlur={() => setOpen(false)}
        role={search ? 'combobox' : undefined}
        aria-expanded={search ? showList : undefined}
        aria-autocomplete={search ? 'list' : undefined}
        autoComplete="off"
        data-1p-ignore
        data-lpignore="true"
        {...inputProps}
      />
      {showList &&
        listPosition &&
        createPortal(
          <ul
            className={`entity-options${listClassName ? ` ${listClassName}` : ''}`}
            role="listbox"
            ref={listRef}
            style={listPosition}
          >
            {options.map((option, i) => (
              <li
                key={option === 'new' ? 'new' : optionKey(option)}
                role="option"
                aria-selected={i === highlighted}
                className={i === highlighted ? 'highlighted' : undefined}
                // mousedown, not click: the input's blur would close the list first
                onMouseDown={(e) => {
                  e.preventDefault();
                  choose(option);
                }}
                onMouseEnter={() => setHighlighted(i)}
              >
                {option === 'new' ? (
                  <span className="entity-option-new">
                    + New {newWhat} “{query}”
                  </span>
                ) : (
                  <>
                    <span className="entity-option-title">
                      {optionIcon?.(option)}
                      {optionTitle(option)}
                    </span>
                    {optionSubtitle(option) && (
                      <span className="entity-option-subtitle">{optionSubtitle(option)}</span>
                    )}
                  </>
                )}
              </li>
            ))}
          </ul>,
          document.body
        )}
    </div>
  );
}

/** "12 games", for a suggestion's second line. */
export function gameCountText(count: number | undefined): string | undefined {
  return count == null ? undefined : `${count} ${count === 1 ? 'game' : 'games'}`;
}
