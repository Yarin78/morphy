import { useEffect, useRef, useState } from 'react';

interface IconSelectProps<T> {
  options: T[];
  value: T | undefined;
  onChange: (option: T) => void;
  optionKey: (option: T) => string;
  /** An option as shown in the list and on the button, typically an icon and a name. */
  renderOption: (option: T) => React.ReactNode;
  /** What the button shows when nothing is chosen. */
  placeholder: React.ReactNode;
  /** What the field is, for screen readers: "Nation". */
  label: string;
  /** With it, the list has a search field, and shows the options it matches. */
  matches?: (option: T, query: string) => boolean;
  disabled?: boolean;
}

/**
 * A dropdown whose options can have icons, which a select's can't: a button showing the chosen
 * option, opening a list of them, with a search field when there are many.
 */
export function IconSelect<T>({
  options,
  value,
  onChange,
  optionKey,
  renderOption,
  placeholder,
  label,
  matches,
  disabled,
}: IconSelectProps<T>) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlighted, setHighlighted] = useState(0);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const shown = matches && query.trim() ? options.filter((o) => matches(o, query.trim())) : options;

  const openList = () => {
    setQuery('');
    const index = value === undefined ? 0 : options.findIndex((o) => optionKey(o) === optionKey(value));
    setHighlighted(Math.max(0, index));
    setOpen(true);
  };

  const close = () => {
    setOpen(false);
    buttonRef.current?.focus();
  };

  const choose = (option: T | undefined) => {
    if (option !== undefined) onChange(option);
    close();
  };

  useEffect(() => {
    if (!open) return;
    (searchRef.current ?? listRef.current)?.focus();
    const handleMouseDown = (e: MouseEvent) => {
      if (!wrapperRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handleMouseDown);
    return () => document.removeEventListener('mousedown', handleMouseDown);
  }, [open]);

  useEffect(() => {
    listRef.current?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' });
  }, [highlighted, open]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      setHighlighted((i) => Math.min(i + 1, shown.length - 1));
    } else if (e.key === 'ArrowUp') {
      setHighlighted((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      choose(shown[highlighted]);
    } else if (e.key === 'Escape') {
      close();
    } else {
      return;
    }
    // Handled here: not by the popover or dialog around this, nor by the page
    e.preventDefault();
    e.stopPropagation();
  };

  return (
    <div className="icon-select" ref={wrapperRef}>
      <button
        type="button"
        ref={buttonRef}
        className="icon-select-button"
        onClick={() => (open ? close() : openList())}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={label}
      >
        <span className="icon-select-value">{value === undefined ? placeholder : renderOption(value)}</span>
      </button>
      {open && (
        <div className="icon-select-dropdown" onKeyDown={handleKeyDown}>
          {matches && (
            <input
              ref={searchRef}
              type="text"
              className="icon-select-search"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setHighlighted(0);
              }}
              placeholder="Search"
              aria-label={`Search ${label.toLowerCase()}`}
              autoComplete="off"
              data-1p-ignore
              data-lpignore="true"
            />
          )}
          <ul className="icon-select-list" role="listbox" aria-label={label} ref={listRef} tabIndex={-1}>
            {shown.map((option, i) => (
              <li
                key={optionKey(option)}
                role="option"
                aria-selected={i === highlighted}
                className={i === highlighted ? 'highlighted' : undefined}
                // mousedown, not click, so the search field keeps its focus until then
                onMouseDown={(e) => {
                  e.preventDefault();
                  choose(option);
                }}
                onMouseEnter={() => setHighlighted(i)}
              >
                {renderOption(option)}
              </li>
            ))}
            {shown.length === 0 && <li className="icon-select-none">No match</li>}
          </ul>
        </div>
      )}
    </div>
  );
}
