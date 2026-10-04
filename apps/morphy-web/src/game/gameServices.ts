import { gameTagLanguages } from 'game-view';
import type {
  GameInfoServices,
  GameTagInfo,
  GameTagLanguage,
  GameTagService,
  PlayerService,
  SourceInfo,
  SourceService,
  TeamInfo,
  TeamService,
  TournamentInfo,
  TournamentService,
} from 'game-view';
import { fetchEntity, search, updateEntity } from '../api/client';
import type { EntityPath } from '../api/client';
import {
  gameTagDto,
  gameTagInfo,
  sourceDto,
  sourceInfo,
  teamDto,
  teamInfo,
  tournamentDto,
  tournamentInfo,
} from './gameDtoAdapter';
import type {
  AnnotatorDto,
  EntitySearchResponse,
  GameDto,
  GameSearchResponse,
  GameTagDto,
  PlayerDto,
  SourceDto,
  TeamDto,
  TournamentDto,
} from '../api/types';

// The server side of a game shown in GameView: the entities the Edit Game Info dialog picks
// from and changes, and finding the games a quotation refers to

/** A quoted filter value matches names and titles that start with the whole text. */
function prefixFilter(text: string): string {
  return `"${text.replace(/"/g, '')}"`;
}

/** Entities of a database whose names or titles start with the text, those with the most games first. */
async function searchEntities<T>(databaseId: string, path: EntityPath, text: string, sortBy = '-count'): Promise<T[]> {
  const response = await search<EntitySearchResponse<T>>(databaseId, path, {
    filter: prefixFilter(text),
    limit: 20,
    sortBy,
  });
  return response.items;
}

/** Existing players of a database, for the Edit Game Info dialog. */
function playerService(databaseId: string, fideIds: boolean): PlayerService {
  return {
    fideIds,
    async search(text) {
      return (await searchEntities<PlayerDto>(databaseId, 'players', text)).map((p) => ({
        id: p.id,
        name: p.firstName ? `${p.lastName ?? ''}, ${p.firstName}` : p.lastName ?? '',
        gameCount: p.gameCount,
        fideId: p.fideId,
      }));
    },
  };
}

/**
 * Gives the game's existing players the FIDE ids the game was saved with: a FIDE id belongs to the
 * player, so the game itself can't change it. Returns whether any player was changed.
 */
export async function saveFideIds(databaseId: string, wanted: GameDto, saved: GameDto): Promise<boolean> {
  let changed = false;
  for (const [want, have] of [
    [wanted.whitePlayer, saved.whitePlayer],
    [wanted.blackPlayer, saved.blackPlayer],
  ]) {
    if (have?.id == null || (want?.fideId ?? null) === (have.fideId ?? null)) continue;
    // The whole entity is replaced, so start from it as saved; 0 is no FIDE id
    const current = await fetchEntity<PlayerDto>(databaseId, 'players', have.id);
    await updateEntity(databaseId, 'players', { ...current, fideId: want?.fideId ?? 0 });
    changed = true;
  }
  return changed;
}

/** Existing annotators of a database, for the Edit Game Info dialog. */
function annotatorService(databaseId: string): PlayerService {
  return {
    async search(text) {
      return (await searchEntities<AnnotatorDto>(databaseId, 'annotators', text)).map((a) => ({
        id: a.id,
        name: a.name ?? '',
        gameCount: a.gameCount,
      }));
    },
  };
}

/**
 * Existing entities of one kind in a database, for the Edit Game Info dialog: found, fetched and
 * changed as the dialog has them, converted to and from their DTOs.
 */
function entityService<D extends { id: number | null }, I extends { id: number | null }>(
  databaseId: string,
  path: EntityPath,
  toInfo: (dto: D) => I,
  toDto: (info: I) => Partial<D>,
  sortBy?: string
) {
  return {
    async search(text: string) {
      return (await searchEntities<D>(databaseId, path, text, sortBy)).map(toInfo);
    },
    async get(id: number) {
      return toInfo(await fetchEntity<D>(databaseId, path, id));
    },
    async update(info: I) {
      // The whole entity is replaced, so start from it as saved, keeping the fields not edited here
      const current = await fetchEntity<D>(databaseId, path, info.id!);
      return toInfo(await updateEntity<D>(databaseId, path, { ...current, ...toDto(info) }));
    },
  };
}

function sourceService(databaseId: string): SourceService {
  return entityService<SourceDto, SourceInfo>(databaseId, 'sources', (dto) => sourceInfo(dto)!, sourceDto);
}

function teamService(databaseId: string): TeamService {
  return entityService<TeamDto, TeamInfo>(databaseId, 'teams', (dto) => teamInfo(dto)!, teamDto);
}

function tournamentService(databaseId: string): TournamentService {
  // The latest first, as an older one of the same name is rarely meant
  return entityService<TournamentDto, TournamentInfo>(
    databaseId,
    'tournaments',
    (dto) => tournamentInfo(dto)!,
    tournamentDto,
    '-startDate'
  );
}

function gameTagService(databaseId: string, languages: GameTagLanguage[]): GameTagService {
  // The empty placeholder tag has no titles
  const info = (dto: GameTagDto): GameTagInfo => gameTagInfo(dto) ?? { id: dto.id, titles: {}, gameCount: dto.gameCount };
  return { languages, ...entityService<GameTagDto, GameTagInfo>(databaseId, 'gametags', info, gameTagDto) };
}

/** A name compared loosely: in lower case, with only its letters and digits. */
function looseName(name: string | undefined): string {
  return (name ?? '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
}

/**
 * The id of the game in a database with these players, as a quotation names them, or null if there
 * is none. The search is on the first word of a name, and the names are then compared in full.
 */
export async function findGameByPlayers(
  databaseId: string,
  white: string | undefined,
  black: string | undefined
): Promise<number | null> {
  const [name, position] = black?.trim() ? [black, 'black'] : [white ?? '', 'white'];
  const word = name.trim().split(/[\s,]+/)[0];
  if (!word) return null;
  const response = await search<GameSearchResponse>(databaseId, 'games', {
    filter: `player.name:${word},position=${position}`,
    limit: 1000,
  });
  const playerName = (p: GameDto['whitePlayer']) => [p?.lastName, p?.firstName].filter((n) => n).join(',');
  const match = response.games.find(
    (g) =>
      looseName(playerName(g.whitePlayer)) === looseName(white) &&
      looseName(playerName(g.blackPlayer)) === looseName(black)
  );
  return match?.id ?? null;
}

/** The format of a database, from its path, which decides the languages a game tag can have titles in. */
export function databaseFormat(path: string | undefined): 'cbh' | '2cbh' | undefined {
  return path?.endsWith('.2cbh') ? '2cbh' : path?.endsWith('.cbh') ? 'cbh' : undefined;
}

/** The services the Edit Game Info dialog uses for a game of a database. */
export function gameInfoServices(databaseId: string, format: 'cbh' | '2cbh' | undefined): GameInfoServices {
  return {
    players: playerService(databaseId, format === '2cbh'),
    annotators: annotatorService(databaseId),
    tournaments: tournamentService(databaseId),
    sources: sourceService(databaseId),
    teams: teamService(databaseId),
    gameTags: gameTagService(databaseId, gameTagLanguages(format)),
  };
}
