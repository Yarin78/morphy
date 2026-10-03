import { useState } from 'react';
import { ContextMenu } from './ContextMenu';
import type { ContextMenuItem } from './ContextMenu';
import { COMMENT_LANGUAGES, languageName } from '../model/languages';

interface LanguageSelectorProps {
  /** The language whose comments are shown and written, or null for all. */
  language: string | null;
  /** The languages the game has comments in. */
  gameLanguages: readonly string[];
  onSelect: (language: string | null) => void;
  disabled?: boolean;
}

/**
 * A pill for the bar below the notation with the language comments are shown and written in, or
 * "All" for the comments in every language, and for writing comments in no particular language.
 * Clicking it opens a menu of every language, those the game has no comments in faded.
 */
export const LanguageSelector: React.FC<LanguageSelectorProps> = ({ language, gameLanguages, onSelect, disabled }) => {
  const [menu, setMenu] = useState<{ x: number; y: number } | null>(null);

  const languages = [...COMMENT_LANGUAGES, ...gameLanguages.filter((l) => !COMMENT_LANGUAGES.includes(l))];
  const items: ContextMenuItem[] = [
    { label: 'All', onSelect: () => onSelect(null) },
    'separator',
    ...languages.map((code) => ({
      label: languageName(code),
      faded: !gameLanguages.includes(code),
      onSelect: () => onSelect(code),
    })),
  ];

  return (
    <div className="notation-bar-group notation-bar-languages">
      <button
        type="button"
        className="language-pill"
        title={
          disabled
            ? 'The language can’t be changed while editing a comment'
            : 'The language of the comments shown and written'
        }
        disabled={disabled}
        aria-haspopup="menu"
        // Keep the focus where it is, so the arrow keys still go through the moves
        onMouseDown={(e) => e.preventDefault()}
        onClick={(e) => {
          const rect = e.currentTarget.getBoundingClientRect();
          setMenu({ x: rect.left, y: rect.top - 4 });
        }}
      >
        {language ? languageName(language) : 'All'} ▾
      </button>
      {menu && <ContextMenu x={menu.x} y={menu.y} items={items} onClose={() => setMenu(null)} above />}
    </div>
  );
};
