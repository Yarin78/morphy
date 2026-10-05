import { useEffect, useRef, useState } from 'react';
import { validateGameInfo } from '../utils/gameInfo';
import type { GameInfo, GameInfoErrors, GameInfoServices, GameInfoTextField } from '../utils/gameInfo';
import type { PlayerInfo } from '../utils/player';
import { sameEloType } from '../utils/eloType';
import type { EloTypeInfo } from '../utils/eloType';
import { newTournament } from '../utils/tournament';
import type { TournamentInfo } from '../utils/tournament';
import { DateField } from './DateField';
import { PlayerField } from './PlayerField';
import { RatingField } from './RatingField';
import { ResultField } from './ResultField';
import type { SourceInfo } from '../utils/source';
import { SourceDialog } from './SourceDialog';
import { SourceField } from './SourceField';
import type { TeamColor, TeamInfo } from '../utils/team';
import type { GameTagInfo } from '../utils/gameTag';
import { GameTagDialog } from './GameTagDialog';
import { GameTagField } from './GameTagField';
import { TeamDialog } from './TeamDialog';
import { TimeControlField } from './TimeControlField';
import { TeamField } from './TeamField';
import { TournamentDialog } from './TournamentDialog';
import { TournamentField } from './TournamentField';
import './GameInfoDialog.css';

interface GameInfoDialogProps {
  initial: GameInfo;
  /** Finds existing players and tournaments. */
  services?: GameInfoServices;
  onSave: (info: GameInfo) => void;
  onCancel: () => void;
}

type InputProps = React.InputHTMLAttributes<HTMLInputElement> & { ref?: React.Ref<HTMLInputElement> };

/** Opens the details of an entity the game refers to; disabled when there's none. */
const DetailsButton: React.FC<{ onClick: () => void; disabled: boolean }> = ({ onClick, disabled }) => (
  <button type="button" className="details-button" onClick={onClick} disabled={disabled}>
    Details…
  </button>
);

/** The fields with problems that only "More info" shows. */
const MORE_INFO_FIELDS = ['whiteFideId', 'blackFideId', 'subRound', 'board'] as const;

/** The GameInfo fields that are an entity with details of its own. */
type EntityField = 'tournament' | 'source' | 'whiteTeam' | 'blackTeam' | 'gameTag';

/**
 * Replaces an existing entity the dialog started with by the entity as it's saved now, with its
 * number of games, unless another one has been picked by then.
 */
function useCurrentEntity<F extends EntityField>(
  service: { get(id: number): Promise<NonNullable<GameInfo[F]>> } | undefined,
  id: number | null | undefined,
  field: F,
  setInfo: React.Dispatch<React.SetStateAction<GameInfo>>
) {
  useEffect(() => {
    if (!service || id == null) return;
    let cancelled = false;
    service
      .get(id)
      .then((current) => {
        if (!cancelled) setInfo((i) => (i[field]?.id === current.id ? { ...i, [field]: current } : i));
      })
      .catch((err) => console.error(`Failed to load the ${field}:`, err));
    return () => {
      cancelled = true;
    };
  }, [service, id, field, setInfo]);
}

export const GameInfoDialog: React.FC<GameInfoDialogProps> = ({ initial, services, onSave, onCancel }) => {
  const tournamentService = services?.tournaments;
  const sourceService = services?.sources;
  const [info, setInfo] = useState<GameInfo>(initial);
  const [errors, setErrors] = useState<GameInfoErrors>({});
  // At first only the fields most often filled in; "More info" shows them all
  const [expanded, setExpanded] = useState(false);
  // Whose details are open in a dialog on top of this one
  const [detailsOpen, setDetailsOpen] = useState<'tournament' | 'source' | TeamColor | 'gameTag' | null>(null);
  const teamService = services?.teams;
  const gameTagService = services?.gameTags;
  const firstFieldRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    firstFieldRef.current?.focus();
  }, []);

  useEffect(() => {
    // The details dialog on top handles Esc itself
    if (detailsOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onCancel, detailsOpen]);

  // The existing entities the game refers to, as saved now rather than when the game was loaded,
  // with their numbers of games
  useCurrentEntity(tournamentService, initial.tournament?.id, 'tournament', setInfo);
  useCurrentEntity(sourceService, initial.source?.id, 'source', setInfo);
  useCurrentEntity(teamService, initial.whiteTeam?.id, 'whiteTeam', setInfo);
  useCurrentEntity(teamService, initial.blackTeam?.id, 'blackTeam', setInfo);
  useCurrentEntity(gameTagService, initial.gameTag?.id, 'gameTag', setInfo);

  const setSource = (source: SourceInfo | null) => setInfo((i) => ({ ...i, source }));
  const setGameTag = (gameTag: GameTagInfo | null) => setInfo((i) => ({ ...i, gameTag }));

  const setTeam = (color: TeamColor) => (team: TeamInfo | null) => setInfo((i) => ({ ...i, [`${color}Team`]: team }));

  const setTournament = (tournament: TournamentInfo | null) => setInfo((i) => ({ ...i, tournament }));
  const setPlayer = (color: 'white' | 'black') => (player: PlayerInfo) =>
    setInfo((i) => {
      // A player picked has the FIDE id it has. A name typed over an existing player's is someone
      // else, without its FIDE id; a new player's name being typed keeps the one typed for it.
      const picked = player.id != null && player.id !== i[color].id;
      const replaced = player.id == null && i[color].id != null;
      const fideId = picked ? (player.fideId ? String(player.fideId) : '') : replaced ? '' : i[`${color}FideId`];
      return { ...i, [color]: player, [`${color}FideId`]: fideId };
    });
  const fideIds = Boolean(services?.players?.fideIds) && expanded;
  const fideIdField = (color: 'white' | 'black') => (
    <label className="game-info-field game-info-field-fide-id">
      {input(`${color}FideId`, {
        'aria-label': `${color === 'white' ? 'White' : 'Black'} FIDE ID`,
        inputMode: 'numeric',
        maxLength: 10,
        title: "Belongs to the player: changing it changes it for the player's other games too",
      })}
    </label>
  );

  // Both ratings are usually of the same type, so a change to one changes both, unless asked not to
  const [differentEloTypes, setDifferentEloTypes] = useState(
    () => !sameEloType(initial.whiteEloType, initial.blackEloType)
  );
  const setEloType = (color: 'white' | 'black') => (type: EloTypeInfo | null) =>
    setInfo((i) =>
      differentEloTypes
        ? { ...i, [`${color}EloType`]: type }
        : { ...i, whiteEloType: type, blackEloType: type }
    );
  const setDifferent = (color: 'white' | 'black') => (different: boolean) => {
    setDifferentEloTypes(different);
    if (!different) {
      // Back to one type for both: this one
      setInfo((i) => ({ ...i, whiteEloType: i[`${color}EloType`], blackEloType: i[`${color}EloType`] }));
    }
  };

  // The site and year are the tournament's place and start year: only a new tournament's can be
  // changed here
  const existingTournament = info.tournament?.id != null;
  const changeTournament = (change: (t: TournamentInfo) => TournamentInfo) =>
    setInfo((i) => (i.tournament?.id != null ? i : { ...i, tournament: change(i.tournament ?? newTournament('')) }));
  const setSite = (e: React.ChangeEvent<HTMLInputElement>) => {
    const place = e.target.value;
    changeTournament((t) => ({ ...t, place: place || undefined }));
  };
  const setYear = (e: React.ChangeEvent<HTMLInputElement>) => {
    const year = e.target.value.replace(/\D/g, '').slice(0, 4);
    // Only the year is known now: a month and day kept from another year would be wrong
    changeTournament((t) => ({ ...t, startDate: year ? { year: parseInt(year, 10), month: 0, day: 0 } : undefined }));
  };
  const tournamentInput = (props: React.InputHTMLAttributes<HTMLInputElement>) => (
    <input
      type="text"
      readOnly={existingTournament}
      title={existingTournament ? "The existing event's; see Details" : undefined}
      autoComplete="off"
      data-1p-ignore
      data-lpignore="true"
      {...props}
    />
  );

  const set = (field: GameInfoTextField) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setInfo((current) => ({ ...current, [field]: e.target.value }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const found = validateGameInfo(info);
    setErrors(found);
    // A problem with a field that isn't shown shows them all
    if (MORE_INFO_FIELDS.some((f) => found[f])) setExpanded(true);
    if (Object.keys(found).length === 0) onSave(info);
  };

  const input = (name: GameInfoTextField, props: InputProps = {}) => (
    <>
      <input
        type="text"
        value={info[name]}
        onChange={set(name)}
        aria-invalid={errors[name] ? true : undefined}
        // Not a login form: keep browsers and password managers from offering to fill it in
        autoComplete="off"
        data-1p-ignore
        data-lpignore="true"
        {...props}
      />
      {errors[name] && <span className="game-info-error">{errors[name]}</span>}
    </>
  );

  /** An input with its label above it. */
  const field = (name: GameInfoTextField, label: string, props: InputProps = {}) => (
    <label className={`game-info-field game-info-field-${name}`}>
      <span className="game-info-label">{label}</span>
      {input(name, props)}
    </label>
  );

  const numberProps = { inputMode: 'numeric' as const };

  return (
    <>
    <div className="game-info-overlay" onMouseDown={onCancel}>
      <form
        className={`game-info-dialog game-info-main${expanded ? ' expanded' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="game-info-title"
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
        noValidate
      >
        <h3 id="game-info-title">Edit Game Info</h3>

        <fieldset className={`game-info-players${fideIds ? ' with-fide-ids' : ''}${expanded ? '' : ' compact'}`}>
          <legend>Players</legend>
          <span />
          <span className="game-info-label">Name</span>
          {fideIds && <span className="game-info-label">FIDE ID</span>}
          <span className="game-info-label">Rating</span>

          <span className="game-info-row-label">White</span>
          <PlayerField
            value={info.white}
            onChange={setPlayer('white')}
            service={services?.players}
            label="White player"
            inputRef={firstFieldRef}
          />
          {fideIds && fideIdField('white')}
          <RatingField
            elo={info.whiteElo}
            onEloChange={(whiteElo) => setInfo((i) => ({ ...i, whiteElo }))}
            type={info.whiteEloType}
            onTypeChange={setEloType('white')}
            player="White"
            error={errors.whiteElo}
            differentTypes={differentEloTypes}
            onDifferentTypesChange={setDifferent('white')}
          />

          <span className="game-info-row-label">Black</span>
          <PlayerField value={info.black} onChange={setPlayer('black')} service={services?.players} label="Black player" />
          {fideIds && fideIdField('black')}
          <RatingField
            elo={info.blackElo}
            onEloChange={(blackElo) => setInfo((i) => ({ ...i, blackElo }))}
            type={info.blackEloType}
            onTypeChange={setEloType('black')}
            player="Black"
            error={errors.blackElo}
            differentTypes={differentEloTypes}
            onDifferentTypesChange={setDifferent('black')}
          />
        </fieldset>

        <fieldset className={`game-info-tournament${expanded ? '' : ' compact'}`}>
          <legend>Event</legend>
          <TournamentField value={info.tournament} onChange={setTournament} service={tournamentService} />
          {field('round', 'Round', numberProps)}
          {expanded && (
            <>
              {field('subRound', 'Sub-round', numberProps)}
              {field('board', 'Board', numberProps)}

              <label className="game-info-field">
                <span className="game-info-label">Site</span>
                {tournamentInput({ value: info.tournament?.place ?? '', onChange: setSite })}
              </label>
              <label className="game-info-field">
                <span className="game-info-label">Year</span>
                {tournamentInput({
                  value: info.tournament?.startDate?.year ? String(info.tournament.startDate.year) : '',
                  onChange: setYear,
                  inputMode: 'numeric',
                  placeholder: 'yyyy',
                })}
              </label>
              <DetailsButton onClick={() => setDetailsOpen('tournament')} disabled={!info.tournament} />
            </>
          )}
        </fieldset>

        <fieldset className="game-info-row game-info-game">
          <legend>Game Info</legend>
          <ResultField
            result={info.result}
            lineEvaluation={info.lineEvaluation}
            onChange={(result, lineEvaluation) => setInfo((i) => ({ ...i, result, lineEvaluation }))}
            initialLineEvaluation={initial.lineEvaluation}
          />
          <DateField
            value={info.date}
            onChange={(date) => setInfo((i) => ({ ...i, date }))}
            label="Date"
            error={errors.date}
            className="game-info-field-date"
          />
          <TimeControlField
            value={info.timeControl}
            onChange={(timeControl) => setInfo((i) => ({ ...i, timeControl }))}
            error={errors.timeControl}
          />
          <span className="game-info-break" />
          {field('eco', 'ECO', { placeholder: 'A00', maxLength: 6 })}
          {field('opening', 'Opening')}
        </fieldset>

        {expanded && (
          <>
            <div className="game-info-pair game-info-annotation">
              <fieldset className="game-info-row">
                <legend>Annotator</legend>
                <div className="game-info-field game-info-field-annotator">
                  <span className="game-info-label">Name</span>
                  <PlayerField
                    value={info.annotator}
                    onChange={(annotator) => setInfo((i) => ({ ...i, annotator }))}
                    service={services?.annotators}
                    label="Annotator"
                    newWhat="annotator"
                  />
                </div>
              </fieldset>

              <fieldset className="game-info-row game-info-source">
                <legend>Source</legend>
                <SourceField value={info.source} onChange={setSource} service={sourceService} />
                <DetailsButton onClick={() => setDetailsOpen('source')} disabled={!info.source} />
              </fieldset>
            </div>

            <fieldset className="game-info-teams">
              <legend>Teams</legend>
              {(['white', 'black'] as const).map((color) => (
                <div key={color} className="game-info-team-row">
                  <span className="game-info-row-label">{color === 'white' ? 'White' : 'Black'}</span>
                  <TeamField
                    value={info[`${color}Team`]}
                    onChange={setTeam(color)}
                    service={teamService}
                    label={`${color === 'white' ? 'White' : 'Black'} team`}
                  />
                  <DetailsButton onClick={() => setDetailsOpen(color)} disabled={!info[`${color}Team`]} />
                </div>
              ))}
            </fieldset>

            <fieldset className="game-info-row game-info-game-tag">
              <legend>Game Tag</legend>
              <GameTagField value={info.gameTag} onChange={setGameTag} service={gameTagService} />
              <DetailsButton onClick={() => setDetailsOpen('gameTag')} disabled={!info.gameTag} />
            </fieldset>
          </>
        )}

        <div className="game-info-buttons">
          <button
            type="button"
            className="game-info-more"
            onClick={() => setExpanded((e) => !e)}
            aria-expanded={expanded}
          >
            {expanded ? 'Less info' : 'More info'}
          </button>
          <button type="button" className="game-info-cancel" onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" className="game-info-ok">
            OK
          </button>
        </div>
      </form>
    </div>
    {detailsOpen === 'tournament' && info.tournament && (
      <TournamentDialog
        tournament={info.tournament}
        service={tournamentService}
        onApply={setTournament}
        onClose={() => setDetailsOpen(null)}
      />
    )}
    {(detailsOpen === 'white' || detailsOpen === 'black') && info[`${detailsOpen}Team`] && (
      <TeamDialog
        team={info[`${detailsOpen}Team`]!}
        service={teamService}
        onApply={setTeam(detailsOpen)}
        onClose={() => setDetailsOpen(null)}
      />
    )}
    {detailsOpen === 'gameTag' && info.gameTag && (
      <GameTagDialog
        gameTag={info.gameTag}
        service={gameTagService}
        onApply={setGameTag}
        onClose={() => setDetailsOpen(null)}
      />
    )}
    {detailsOpen === 'source' && info.source && (
      <SourceDialog
        source={info.source}
        service={sourceService}
        onApply={setSource}
        onClose={() => setDetailsOpen(null)}
      />
    )}
    </>
  );
};
