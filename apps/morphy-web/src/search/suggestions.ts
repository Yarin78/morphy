import { search } from '../api/client';
import type { EntitySearchResponse, PlayerDto, TournamentDto } from '../api/types';

// The players and events of a database suggested while typing in a game search's fields: those
// whose names or titles start with the text, the players with the most games first and the events
// newest first.

const SUGGESTIONS = 5;

async function startingWith<T>(
  databaseId: string,
  path: 'players' | 'tournaments',
  text: string,
  sortBy: string
): Promise<T[]> {
  // A quoted value matches names and titles that start with the whole text
  const response = await search<EntitySearchResponse<T>>(databaseId, path, {
    filter: `"${text.replace(/"/g, '')}"`,
    limit: SUGGESTIONS,
    sortBy,
  });
  return response.items;
}

export function suggestPlayers(databaseId: string, text: string): Promise<PlayerDto[]> {
  return startingWith<PlayerDto>(databaseId, 'players', text, '-count');
}

export function suggestEvents(databaseId: string, text: string): Promise<TournamentDto[]> {
  return startingWith<TournamentDto>(databaseId, 'tournaments', text, '-startDate');
}

/** A player as the search's field holds them once picked: "Carlsen, Magnus". */
export function playerFullName(p: PlayerDto): string {
  const last = p.lastName ?? '';
  return p.firstName ? `${last}, ${p.firstName}` : last;
}
