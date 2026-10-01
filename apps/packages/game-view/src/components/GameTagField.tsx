import { useState } from 'react';
import {
  GAME_TAG_LANGUAGES,
  gameTagLanguage,
  gameTagLanguageName,
  gameTagTitle,
  gameTagTitleLanguages,
} from '../utils/gameTag';
import type { GameTagInfo, GameTagLanguage, GameTagService } from '../utils/gameTag';
import { NationFlag } from './EloTypeIcons';
import { EntityCombobox, gameCountText } from './EntityCombobox';
import { IconSelect } from './IconSelect';

interface GameTagFieldProps {
  value: GameTagInfo | null;
  onChange: (gameTag: GameTagInfo | null) => void;
  service?: GameTagService;
}

/**
 * The game's tag, by its title in one language, with suggestions of existing tags to pick from
 * while typing; empty for no tag. The flag at the start of the field is that language. For a new
 * tag with a title in one language, picking another moves the title to it; otherwise it shows the
 * tag's title in that language, if it has one. A badge marks a new tag, one that saving the game
 * will create; editing the title of an existing one makes it a new tag, with only the title edited,
 * since the existing one belongs to other games too.
 */
export const GameTagField: React.FC<GameTagFieldProps> = ({ value, onChange, service }) => {
  // The language picked to show the tag in; without one, the tag's own (English if it has it)
  const [picked, setPicked] = useState<GameTagLanguage | null>(null);
  // A different tag is shown in its own language again
  const [shownId, setShownId] = useState(value?.id);
  if (value?.id !== shownId) {
    setShownId(value?.id);
    if (value?.id != null) setPicked(null);
  }
  const language = picked ?? (value && gameTagLanguage(value)) ?? 'ENG';
  const offered = service?.languages ?? GAME_TAG_LANGUAGES.map((l) => l.code);
  const languages = offered.includes(language) ? offered : [...offered, language];
  const isNew = value !== null && value.id == null;
  const titled = value ? gameTagTitleLanguages(value) : [];

  const handleTextChange = (title: string) => {
    setPicked(language);
    if (!value || value.id != null) {
      // A tag created now, in the language of the title edited
      onChange(title ? { id: null, titles: { [language]: title } } : null);
    } else {
      const titles = { ...value.titles, [language]: title };
      if (!title) delete titles[language];
      onChange(Object.keys(titles).length > 0 ? { id: null, titles } : null);
    }
  };

  const handleLanguageChange = (code: GameTagLanguage) => {
    setPicked(code);
    // A new tag in one language is moved to the other one
    if (value && isNew && titled.length === 1) onChange({ id: null, titles: { [code]: gameTagTitle(value) } });
  };

  const search = service
    ? async (text: string) => (await service.search(text)).filter((t) => gameTagTitle(t))
    : undefined;

  const languageName = gameTagLanguageName(language);
  return (
    <div className={`game-info-field game-info-field-game-tag${isNew && service ? ' with-badge' : ''}`}>
      <div className="rating-input game-tag-input">
        <IconSelect<GameTagLanguage>
          className="game-tag-language"
          options={languages}
          value={language}
          onChange={handleLanguageChange}
          optionKey={(code) => code}
          renderOption={(code) => (
            <>
              <NationFlag nation={code} label={gameTagLanguageName(code)} />
              <span className={value && !isNew && !titled.includes(code) ? 'game-tag-untitled' : undefined}>
                {gameTagLanguageName(code)}
              </span>
            </>
          )}
          renderValue={(code) => <NationFlag nation={code} label={gameTagLanguageName(code)} />}
          placeholder={null}
          label="Language of the game tag"
          title={`In ${languageName}`}
        />
        <EntityCombobox<GameTagInfo>
          text={value ? (value.titles[language] ?? '') : ''}
          onTextChange={handleTextChange}
          search={search}
          onChoose={onChange}
          optionKey={(t) => t.id ?? gameTagTitle(t)}
          optionTitle={gameTagTitle}
          optionSubtitle={(t) =>
            [gameTagTitleLanguages(t).map(gameTagLanguageName).join(', '), gameCountText(t.gameCount)]
              .filter(Boolean)
              .join(' · ')
          }
          newWhat="game tag"
          inputProps={{
            'aria-label': 'Game tag',
            placeholder: value ? `No ${languageName} title` : 'No game tag',
          }}
        />
      </div>
      {isNew && service && <span className="entity-badge entity-badge-new entity-badge-inline">New</span>}
    </div>
  );
};
