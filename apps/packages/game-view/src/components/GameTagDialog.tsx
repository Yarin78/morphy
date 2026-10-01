import { Fragment } from 'react';
import { GAME_TAG_LANGUAGES, gameTagLanguageName, gameTagTitleLanguages } from '../utils/gameTag';
import type { GameTagInfo, GameTagLanguage, GameTagService } from '../utils/gameTag';
import { NationFlag } from './EloTypeIcons';
import { EntityDetailsDialog } from './EntityDetailsDialog';

interface GameTagDialogProps {
  gameTag: GameTagInfo;
  service?: GameTagService;
  /** Called with the game tag the game should now have. */
  onApply: (gameTag: GameTagInfo) => void;
  onClose: () => void;
}

type Form = Record<GameTagLanguage, string>;

function toForm(t: GameTagInfo): Form {
  return Object.fromEntries(GAME_TAG_LANGUAGES.map(({ code }) => [code, t.titles[code] ?? ''])) as Form;
}

function fromForm(form: Form, base: GameTagInfo, id: number | null): GameTagInfo {
  const titles: GameTagInfo['titles'] = {};
  for (const { code } of GAME_TAG_LANGUAGES) {
    const title = form[code].trim();
    if (title) titles[code] = title;
  }
  return { id, titles, gameCount: id != null ? base.gameCount : undefined };
}

/** The details of the game's tag: its title in each language; see EntityDetailsDialog. */
export const GameTagDialog: React.FC<GameTagDialogProps> = ({ gameTag, service, onApply, onClose }) => {
  // The languages the database offers, and any other the tag has a title in
  const offered = service?.languages ?? GAME_TAG_LANGUAGES.map((l) => l.code);
  const languages = GAME_TAG_LANGUAGES.map((l) => l.code).filter(
    (code) => offered.includes(code) || gameTagTitleLanguages(gameTag).includes(code)
  );
  const validate = (form: Form) =>
    languages.some((code) => form[code].trim())
      ? {}
      : ({ [languages[0]]: 'Needs a title in some language' } as Partial<Record<GameTagLanguage, string>>);
  return (
    <EntityDetailsDialog<GameTagInfo, Form>
      entity={gameTag}
      what="game tag"
      name="game-tag"
      update={service?.update}
      toForm={toForm}
      validate={validate}
      fromForm={fromForm}
      onApply={onApply}
      onClose={onClose}
      renderFields={({ input }) => (
        <fieldset className="game-tag-titles">
          <legend>Titles</legend>
          {languages.map((code, i) => (
            <Fragment key={code}>
              {input(
                code,
                <>
                  <NationFlag nation={code} label={gameTagLanguageName(code)} />
                  {gameTagLanguageName(code)}
                </>,
                { first: i === 0 }
              )}
            </Fragment>
          ))}
        </fieldset>
      )}
    />
  );
};
