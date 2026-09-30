import { useEffect, useRef, useState } from 'react';
import {
  LINE_EVALUATIONS,
  LINE_RESULT,
  RESULTS,
  lineEvaluationSymbol,
  validateGameInfo,
} from '../utils/gameInfo';
import type { GameInfo, GameInfoErrors, GameInfoServices, GameInfoTextField } from '../utils/gameInfo';
import type { PlayerInfo } from '../utils/player';
import { sameEloType } from '../utils/eloType';
import type { EloTypeInfo } from '../utils/eloType';
import { newTournament } from '../utils/tournament';
import type { TournamentInfo } from '../utils/tournament';
import { DateField } from './DateField';
import { PlayerField } from './PlayerField';
import { RatingField } from './RatingField';
import type { SourceInfo } from '../utils/source';
import { SourceDialog } from './SourceDialog';
import { SourceField } from './SourceField';
import type { TeamColor, TeamInfo } from '../utils/team';
import { TeamDialog } from './TeamDialog';
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

export const GameInfoDialog: React.FC<GameInfoDialogProps> = ({ initial, services, onSave, onCancel }) => {
  const tournamentService = services?.tournaments;
  const sourceService = services?.sources;
  const [info, setInfo] = useState<GameInfo>(initial);
  const [errors, setErrors] = useState<GameInfoErrors>({});
  // Whose details are open in a dialog on top of this one
  const [detailsOpen, setDetailsOpen] = useState<'tournament' | 'source' | TeamColor | null>(null);
  const teamService = services?.teams;
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

  // An existing tournament's details, as saved now rather than when the game was loaded, and its
  // number of games
  const initialTournamentId = initial.tournament?.id;
  useEffect(() => {
    if (!tournamentService || initialTournamentId == null) return;
    let cancelled = false;
    tournamentService
      .get(initialTournamentId)
      .then((current) => {
        if (cancelled) return;
        setInfo((i) => (i.tournament?.id === current.id ? { ...i, tournament: current } : i));
      })
      .catch((err) => console.error('Failed to load the tournament:', err));
    return () => {
      cancelled = true;
    };
  }, [tournamentService, initialTournamentId]);

  // The same for an existing source
  const initialSourceId = initial.source?.id;
  useEffect(() => {
    if (!sourceService || initialSourceId == null) return;
    let cancelled = false;
    sourceService
      .get(initialSourceId)
      .then((current) => {
        if (cancelled) return;
        setInfo((i) => (i.source?.id === current.id ? { ...i, source: current } : i));
      })
      .catch((err) => console.error('Failed to load the source:', err));
    return () => {
      cancelled = true;
    };
  }, [sourceService, initialSourceId]);

  const setSource = (source: SourceInfo | null) => setInfo((i) => ({ ...i, source }));

  // The same for the players' existing teams
  const initialWhiteTeamId = initial.whiteTeam?.id;
  const initialBlackTeamId = initial.blackTeam?.id;
  useEffect(() => {
    if (!teamService) return;
    let cancelled = false;
    for (const [color, id] of [['white', initialWhiteTeamId], ['black', initialBlackTeamId]] as const) {
      if (id == null) continue;
      teamService
        .get(id)
        .then((current) => {
          if (cancelled) return;
          setInfo((i) => (i[`${color}Team`]?.id === current.id ? { ...i, [`${color}Team`]: current } : i));
        })
        .catch((err) => console.error('Failed to load the team:', err));
    }
    return () => {
      cancelled = true;
    };
  }, [teamService, initialWhiteTeamId, initialBlackTeamId]);

  const setTeam = (color: TeamColor) => (team: TeamInfo | null) => setInfo((i) => ({ ...i, [`${color}Team`]: team }));

  const setTournament = (tournament: TournamentInfo | null) => setInfo((i) => ({ ...i, tournament }));
  const setPlayer = (color: 'white' | 'black') => (player: PlayerInfo) => setInfo((i) => ({ ...i, [color]: player }));

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
      title={existingTournament ? "The existing tournament's; see Details" : undefined}
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

  // Keep an evaluation the list doesn't offer selectable, rather than silently dropping it.
  const evaluations =
    !initial.lineEvaluation || LINE_EVALUATIONS.some((e) => e.value === initial.lineEvaluation)
      ? LINE_EVALUATIONS
      : [...LINE_EVALUATIONS, { value: initial.lineEvaluation, symbol: lineEvaluationSymbol(initial.lineEvaluation) }];

  return (
    <>
    <div className="game-info-overlay" onMouseDown={onCancel}>
      <form
        className="game-info-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="game-info-title"
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
        noValidate
      >
        <h3 id="game-info-title">Edit Game Info</h3>

        <fieldset className="game-info-players">
          <legend>Players</legend>
          <span />
          <span className="game-info-label">Name</span>
          <span className="game-info-label">Rating</span>

          <span className="game-info-row-label">White</span>
          <PlayerField
            value={info.white}
            onChange={setPlayer('white')}
            service={services?.players}
            label="White player"
            inputRef={firstFieldRef}
          />
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

        <fieldset className="game-info-tournament">
          <legend>Tournament</legend>
          <TournamentField value={info.tournament} onChange={setTournament} service={tournamentService} />
          {field('round', 'Round', numberProps)}
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
          <button
            type="button"
            className="tournament-details-button"
            onClick={() => setDetailsOpen('tournament')}
            disabled={!info.tournament}
          >
            Details…
          </button>
        </fieldset>

        <div className="game-info-result-row">
          <fieldset className="game-info-row">
            <legend>Result</legend>
            <label className="game-info-field">
              <span className="game-info-label">Result</span>
              <select value={info.result} onChange={set('result')}>
                {RESULTS.map((r) => (
                  <option key={r.value} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </select>
            </label>
            {info.result === LINE_RESULT && (
              <label className="game-info-field">
                <span className="game-info-label">Evaluation</span>
                <select value={info.lineEvaluation} onChange={set('lineEvaluation')}>
                  <option value=""></option>
                  {evaluations.map((e) => (
                    <option key={e.value} value={e.value}>
                      {e.symbol}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </fieldset>

          <fieldset className="game-info-row">
            <legend>Date</legend>
            <DateField
              value={info.date}
              onChange={(date) => setInfo((i) => ({ ...i, date }))}
              label="Date"
              error={errors.date}
              className="game-info-field-date"
            />
          </fieldset>

          <fieldset className="game-info-row game-info-opening">
            <legend>Opening</legend>
            {field('eco', 'ECO', { placeholder: 'A00', maxLength: 6 })}
            {field('opening', 'Name')}
          </fieldset>
        </div>

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
            <button
              type="button"
              className="tournament-details-button"
              onClick={() => setDetailsOpen('source')}
              disabled={!info.source}
            >
              Details…
            </button>
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
              <button
                type="button"
                className="tournament-details-button"
                onClick={() => setDetailsOpen(color)}
                disabled={!info[`${color}Team`]}
              >
                Details…
              </button>
            </div>
          ))}
        </fieldset>

        <div className="game-info-buttons">
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
