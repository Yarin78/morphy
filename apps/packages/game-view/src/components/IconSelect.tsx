import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

interface IconSelectProps<T> {
  options: T[];
  value: T | undefined;
  onChange: (option: T) => void;
  optionKey: (option: T) => string;
  /** An option as shown in the list and on the button, typically an icon and a name. */
  renderOption: (option: T) => React.ReactNode;
  /** The chosen option as the button shows it, if not as in the list: just an icon, say. */
  renderValue?: (option: T) => React.ReactNode;
  /** What the button shows when nothing is chosen. */
  placeholder: React.ReactNode;
  /** What the field is, for screen readers: "Nation". */
  label: string;
  /** With it, the list has a search field, and shows the options it matches. */
  matches?: (option: T, query: string) => boolean;
  disabled?: boolean;
  /** Also the tooltip of the button. */
  title?: string;
  /** On the field; its list, which is elsewhere on the page, gets it with "-dropdown" after. */
  className?: string;
  /** Which edge of the button the list lines up with, if it fits: its start, or its end. */
  align?: 'start' | 'end';
}

/** Between the list and the edges of the window. */
const MARGIN = 8;

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
  renderValue = renderOption,
  placeholder,
  label,
  matches,
  disabled,
  title,
  className,
  align = 'start',
}: IconSelectProps<T>) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlighted, setHighlighted] = useState(0);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<React.CSSProperties | null>(null);

  const shown = matches && query.trim() ? options.filter((o) => matches(o, query.trim())) : options;

  const openList = () => {
    setPosition(null);
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
      const target = e.target as Node;
      if (!wrapperRef.current?.contains(target) && !dropdownRef.current?.contains(target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleMouseDown);
    return () => document.removeEventListener('mousedown', handleMouseDown);
  }, [open]);

  // The list is drawn above everything, so that a dialog it's in doesn't cut it off: below the
  // button, or above it if there's more room there, and leftwards if it doesn't fit to the right
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const button = buttonRef.current?.getBoundingClientRect();
      const dropdown = dropdownRef.current;
      const list = listRef.current;
      if (!button || !dropdown || !list) return;
      const below = window.innerHeight - button.bottom - MARGIN;
      const above = button.top - MARGIN;
      // As tall as it would be with room for all of the list it shows, whatever its height now
      const listMax = parseFloat(getComputedStyle(list).maxHeight) || Infinity;
      const natural = dropdown.offsetHeight - list.offsetHeight + Math.min(list.scrollHeight, listMax);
      const height = Math.min(natural, Math.max(below, above));
      const width = dropdown.offsetWidth;
      const up = height > below && above > below;
      const fitsStart = button.left + width <= window.innerWidth - MARGIN;
      const left = align === 'start' && fitsStart ? button.left : button.right - width;
      setPosition({
        top: up ? button.top - 2 - height : button.bottom + 2,
        left: Math.max(MARGIN, left),
        minWidth: button.width,
        maxHeight: height,
      });
    };
    place();
    // Scrolling the dialog moves the button; scrolling the list itself doesn't
    const handleScroll = (e: Event) => {
      if (!dropdownRef.current?.contains(e.target as Node)) place();
    };
    window.addEventListener('resize', place);
    window.addEventListener('scroll', handleScroll, true);
    return () => {
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', handleScroll, true);
    };
  }, [open, shown.length, align]);

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
    <div className={`icon-select${className ? ` ${className}` : ''}`} ref={wrapperRef}>
      <button
        type="button"
        ref={buttonRef}
        className="icon-select-button"
        onClick={() => (open ? close() : openList())}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={label}
        title={title}
      >
        <span className="icon-select-value">{value === undefined ? placeholder : renderValue(value)}</span>
      </button>
      {open &&
        createPortal(
          <div
            className={`icon-select-dropdown${className ? ` ${className}-dropdown` : ''}`}
            ref={dropdownRef}
            // Measured where it is first, then placed; invisible until then, but not hidden, so it
            // can have the focus
            style={position ?? { top: 0, left: 0, opacity: 0, pointerEvents: 'none' }}
            onKeyDown={handleKeyDown}
          >
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
          </div>,
          document.body
        )}
    </div>
  );
}
