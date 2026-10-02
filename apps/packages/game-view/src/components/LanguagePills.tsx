import { languageName } from '../model/languages';

interface LanguagePillsProps {
  /** The languages the game's comments are in. */
  languages: readonly string[];
  /** The languages whose comments are shown. */
  shown: readonly string[];
  /** Shows the comments in a language, or hides them if they're shown. */
  onToggle: (language: string) => void;
}

/**
 * A pill for each language the game's comments are in, for the bar below the notation, showing
 * or hiding the comments in it. Comments in no particular language are always shown.
 */
export const LanguagePills: React.FC<LanguagePillsProps> = ({ languages, shown, onToggle }) => (
  <div className="notation-bar-group notation-bar-languages" role="group" aria-label="Comment languages">
    {languages.map((language) => {
      const active = shown.includes(language);
      return (
        <button
          key={language}
          type="button"
          className={`language-pill${active ? ' active' : ''}`}
          title={`${active ? 'Hide' : 'Show'} the comments in ${languageName(language)}`}
          aria-pressed={active}
          // Keep the focus where it is, so the arrow keys still go through the moves
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => onToggle(language)}
        >
          {languageName(language)}
        </button>
      );
    })}
  </div>
);
