import { describe, expect, it } from 'vitest';
import { EMPTY_ENTITY_FORM, EMPTY_GAME_FORM, entityQuery, gameQuery, isValidDate, quote } from './queries';

describe('gameQuery', () => {
  it('is empty for an empty form', () => {
    expect(gameQuery(EMPTY_GAME_FORM)).toBe('');
  });

  it('finds one player in either colour', () => {
    expect(gameQuery({ ...EMPTY_GAME_FORM, white: 'Kasparov' })).toBe('player:Kasparov');
    expect(gameQuery({ ...EMPTY_GAME_FORM, black: 'Carlsen, M' })).toBe('player:"Carlsen, M"');
  });

  it('finds two players in either colour', () => {
    expect(gameQuery({ ...EMPTY_GAME_FORM, white: 'Kasparov', black: 'Karpov' })).toBe(
      'player.name:Kasparov|Karpov,position=both'
    );
  });

  it('finds the players by colour', () => {
    expect(gameQuery({ ...EMPTY_GAME_FORM, eitherColour: false, white: 'Kasparov', black: 'Karpov' })).toBe(
      'white:Kasparov black:Karpov'
    );
    expect(gameQuery({ ...EMPTY_GAME_FORM, eitherColour: false, black: 'Karpov' })).toBe('black:Karpov');
  });

  it('makes date ranges, open at either end', () => {
    expect(gameQuery({ ...EMPTY_GAME_FORM, dateFrom: '1990', dateTo: '2000' })).toBe('date:1990..2000');
    expect(gameQuery({ ...EMPTY_GAME_FORM, dateFrom: '1990' })).toBe('date:1990..');
    expect(gameQuery({ ...EMPTY_GAME_FORM, dateTo: '1990-05' })).toBe('date:..1990-05');
    expect(gameQuery({ ...EMPTY_GAME_FORM, dateFrom: '1985', dateTo: '1985' })).toBe('date:1985');
  });

  it('leaves out an invalid date', () => {
    expect(gameQuery({ ...EMPTY_GAME_FORM, dateFrom: '85', dateTo: '2000' })).toBe('date:..2000');
  });

  it('picks time controls, but none when all are picked', () => {
    expect(gameQuery({ ...EMPTY_GAME_FORM, timeControls: ['blitz', 'rapid'] })).toBe('tournament.time:rapid|blitz');
    expect(gameQuery({ ...EMPTY_GAME_FORM, timeControls: ['normal', 'rapid', 'blitz'] })).toBe('');
  });

  it('adds the advanced filters', () => {
    expect(
      gameQuery({
        ...EMPTY_GAME_FORM,
        result: '1-0',
        eco: 'b9*',
        ratingMin: '2600',
        ratingMode: 'both',
        tournament: 'Wijk aan Zee',
        entity: { field: 'playerid', id: 24, label: 'Player: Kasparov' },
      })
    ).toBe('result:1-0 eco:B9* rating:2600..,mode=both tournament:"Wijk aan Zee" playerid:24');
  });
});

describe('entityQuery', () => {
  it('searches the main field with a bare value', () => {
    expect(entityQuery('players', { ...EMPTY_ENTITY_FORM, name: 'Kasp' })).toBe('Kasp');
    expect(entityQuery('players', EMPTY_ENTITY_FORM)).toBe('');
  });

  it('has more fields for tournaments', () => {
    expect(
      entityQuery('tournaments', {
        name: 'World',
        place: 'New York',
        dateFrom: '1990',
        dateTo: '',
        timeControls: ['rapid'],
      })
    ).toBe('World place:"New York" date:1990.. time:rapid');
  });
});

describe('helpers', () => {
  it('quotes values with spaces or commas', () => {
    expect(quote(' Kasparov ')).toBe('Kasparov');
    expect(quote('Carlsen, Magnus')).toBe('"Carlsen, Magnus"');
  });

  it('accepts partial dates', () => {
    expect(isValidDate('1990')).toBe(true);
    expect(isValidDate('1990-5')).toBe(true);
    expect(isValidDate('1990-05-17')).toBe(true);
    expect(isValidDate('90')).toBe(false);
  });
});
