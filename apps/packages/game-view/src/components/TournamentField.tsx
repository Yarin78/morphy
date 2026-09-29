import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { newTournament, tournamentSubtitle } from '../utils/tournament';
import type { TournamentInfo, TournamentService } from '../utils/tournament';

interface TournamentFieldProps {
  value: TournamentInfo | null;
  onChange: (tournament: TournamentInfo | null) => void;
  service?: TournamentService;
}

const SEARCH_DELAY_MS = 200;
const MIN_LIST_WIDTH = 440;

/**
 * The tournament's name, with suggestions of existing tournaments to pick from while typing. A
 * badge marks a new tournament, one that saving the game will create; editing the name of an
 * existing one makes it a new tournament, since the existing one belongs to other games too.
 */
export const TournamentField: React.FC<TournamentFieldProps> = ({ value, onChange, service }) => {
  const text = value?.title ?? '';
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<TournamentInfo[]>([]);
  const [highlighted, setHighlighted] = useState(0);
  const listRef = useRef<HTMLUListElement>(null);
  const comboboxRef = useRef<HTMLDivElement>(null);
  const [listPosition, setListPosition] = useState<React.CSSProperties | null>(null);

  const query = text.trim();
  useEffect(() => {
    if (!service || !open || !query) return;
    let cancelled = false;
    const timer = setTimeout(() => {
      service
        .search(query)
        .then((found) => {
          if (cancelled) return;
          setResults(found);
          setHighlighted(0);
        })
        .catch((err) => console.error('Tournament search failed:', err));
    }, SEARCH_DELAY_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [service, open, query]);

  // The last option always creates a new tournament with the typed name
  const options: (TournamentInfo | 'new')[] = query ? [...(results ?? []), 'new'] : [];
  const showList = Boolean(service) && open && options.length > 0;

  useEffect(() => {
    listRef.current?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' });
  }, [highlighted]);

  // The list is put on the page, not in the dialog, which would cut it off; it follows the input
  useLayoutEffect(() => {
    if (!showList) return;
    const place = () => {
      const rect = comboboxRef.current?.getBoundingClientRect();
      if (!rect) return;
      const width = Math.min(Math.max(rect.width, MIN_LIST_WIDTH), window.innerWidth - 16);
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
  }, [showList]);

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const title = e.target.value;
    setOpen(true);
    if (!value || value.id != null) {
      // A tournament created now, without any existing one's details
      onChange(title ? newTournament(title) : null);
    } else {
      // A new tournament keeps the details entered for it
      onChange(title || hasDetails(value) ? { ...value, title } : null);
    }
  };

  const choose = (option: TournamentInfo | 'new') => {
    setOpen(false);
    if (option !== 'new') onChange(option);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!service) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) setOpen(true);
      else setHighlighted((i) => Math.min(i + 1, options.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlighted((i) => Math.max(i - 1, 0));
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
    <div className="game-info-field game-info-field-tournament">
      <span className="game-info-label">
        Name
        {service && value && value.id == null && <span className="tournament-badge tournament-badge-new">New</span>}
      </span>
      <div className="tournament-name">
        <div className="tournament-combobox" ref={comboboxRef}>
          <input
            type="text"
            value={text}
            onChange={handleInput}
            onKeyDown={handleKeyDown}
            onBlur={() => setOpen(false)}
            role={service ? 'combobox' : undefined}
            aria-expanded={service ? showList : undefined}
            aria-autocomplete={service ? 'list' : undefined}
            aria-label="Tournament name"
            autoComplete="off"
            data-1p-ignore
            data-lpignore="true"
          />
          {showList && listPosition && createPortal(
            <ul className="tournament-options" role="listbox" ref={listRef} style={listPosition}>
              {options.map((option, i) => (
                <li
                  key={option === 'new' ? 'new' : option.id}
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
                    <span className="tournament-option-new">+ New tournament “{query}”</span>
                  ) : (
                    <>
                      <span className="tournament-option-title">{option.title}</span>
                      <span className="tournament-option-subtitle">
                        {[
                          tournamentSubtitle(option),
                          option.gameCount != null && `${option.gameCount} ${option.gameCount === 1 ? 'game' : 'games'}`,
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </span>
                    </>
                  )}
                </li>
              ))}
            </ul>,
            document.body
          )}
        </div>
      </div>
    </div>
  );
};

function hasDetails(t: TournamentInfo): boolean {
  return Boolean(
    t.startDate || t.endDate || t.place || t.nation || t.type || t.timeControl || t.rounds || t.category ||
      t.complete || t.teamTournament
  );
}
