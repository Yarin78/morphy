import { describe, expect, it } from 'vitest';
import { gameTagFromTags, gameTagLanguage, gameTagLanguages, gameTagTitle, gameTagToTags } from './gameTag';
import type { GameTagInfo } from './gameTag';
import { sourceFromTags, sourceToTags } from './source';
import type { SourceInfo } from './source';
import { teamFromTags, teamToTags } from './team';
import type { TeamInfo } from './team';
import { tournamentFromTags, tournamentToTags } from './tournament';
import type { TournamentInfo } from './tournament';

/** Reads tags as a Chess instance has them: '' for a tag that isn't there. */
const reader = (tags: Record<string, string>) => (name: string) => tags[name] ?? '';

describe('entities as tags', () => {
  it('round-trip a tournament with all its details', () => {
    const tournament: TournamentInfo = {
      id: 7,
      title: 'World-ch Carlsen-Anand',
      startDate: { year: 2013, month: 11, day: 9 },
      endDate: { year: 2013, month: 11, day: 0 },
      place: 'Chennai',
      nation: 'IND',
      type: 'match',
      timeControl: 'normal',
      rounds: 12,
      category: 22,
      complete: true,
      teamTournament: undefined,
    };
    expect(tournamentFromTags(reader(tournamentToTags(tournament)))).toEqual(tournament);
  });

  it('round-trip a source, a team and a game tag', () => {
    const source: SourceInfo = {
      id: null,
      title: 'CBM 158',
      publisher: 'ChessBase',
      publication: { year: 2014, month: 1, day: 16 },
      date: undefined,
      version: 1,
      quality: 'HIGH',
    };
    expect(sourceFromTags(reader(sourceToTags(source)))).toEqual(source);

    const team: TeamInfo = { id: null, title: 'Norway', number: 1, season: true, year: 2024, nation: 'NOR' };
    expect(teamFromTags('white', reader(teamToTags('white', team)))).toEqual(team);
    // Each player's team has its own tags
    expect(teamFromTags('black', reader(teamToTags('white', team)))).toBeNull();

    const gameTag: GameTagInfo = { id: 3, titles: { GER: 'Eröffnungsfalle', POR: 'Armadilha de abertura' } };
    expect(gameTagFromTags(reader(gameTagToTags(gameTag)))).toEqual(gameTag);
  });

  it('read none when the tags have no entity', () => {
    expect(tournamentFromTags(reader({}))).toBeNull();
    expect(sourceFromTags(reader({}))).toBeNull();
    expect(teamFromTags('white', reader({}))).toBeNull();
    expect(gameTagFromTags(reader({}))).toBeNull();
    // PGN's placeholder for an unknown value
    expect(tournamentFromTags(reader({ Event: '?' }))).toBeNull();
  });
});

describe('game tags', () => {
  it('are shown by their English title, or else the first other one', () => {
    const both: GameTagInfo = { id: null, titles: { FRA: 'Tactique', ENG: 'Tactics' } };
    expect(gameTagLanguage(both)).toBe('ENG');
    expect(gameTagTitle(both)).toBe('Tactics');
    const german: GameTagInfo = { id: null, titles: { POR: 'Armadilha', GER: 'Falle' } };
    expect(gameTagTitle(german)).toBe('Falle');
    expect(gameTagTitle({ id: 0, titles: {} })).toBe('');
  });

  it('have the languages of the format', () => {
    expect(gameTagLanguages('cbh')).toContain('SLO');
    expect(gameTagLanguages('cbh')).not.toContain('POR');
    expect(gameTagLanguages('2cbh')).toContain('POR');
    expect(gameTagLanguages('2cbh')).not.toContain('SLO');
  });
});
