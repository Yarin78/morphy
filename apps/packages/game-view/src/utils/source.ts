import { dateTag, parseDateTag } from './date';
import type { DateParts } from './date';

/**
 * The source of a game, such as the database or book it's from, as edited in the Edit Game Info
 * dialog. With an id, it's an existing source in the database, shared with every other game from
 * it; without one, it's a new source, created (or matched to an identical one) when the game is
 * saved.
 */
export interface SourceInfo {
  id: number | null;
  title: string;
  publisher?: string;
  /** When it was published. */
  publication?: DateParts;
  /** The source's other date. */
  date?: DateParts;
  version?: number;
  /** One of SOURCE_QUALITIES. */
  quality?: string;
  /** The number of games of an existing source. */
  gameCount?: number;
}

/** How the Edit Game Info dialog finds and changes existing sources. */
export interface SourceService {
  /** Sources whose title starts with the text, those with the most games first. */
  search(text: string): Promise<SourceInfo[]>;
  get(id: number): Promise<SourceInfo>;
  /** Changes an existing source, for every game from it, and returns it as saved. */
  update(source: SourceInfo): Promise<SourceInfo>;
}

export const SOURCE_QUALITIES: { value: string; label: string }[] = [
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
];

/**
 * The PGN tags holding the source on the Chess instance, only understood by whoever saves the game.
 * Source is the publisher, as in ChessBase's PGN.
 */
export const SOURCE_TAGS = {
  id: 'SourceId',
  title: 'SourceTitle',
  publisher: 'Source',
  publication: 'SourcePublication',
  date: 'SourceDate',
  version: 'SourceVersion',
  quality: 'SourceQuality',
} as const;


/** The tags for a source: every tag in SOURCE_TAGS, '' for those not set. */
export function sourceToTags(s: SourceInfo | null): Record<string, string> {
  return {
    [SOURCE_TAGS.id]: s?.id != null ? String(s.id) : '',
    [SOURCE_TAGS.title]: s?.title ?? '',
    [SOURCE_TAGS.publisher]: s?.publisher ?? '',
    [SOURCE_TAGS.publication]: dateTag(s?.publication),
    [SOURCE_TAGS.date]: dateTag(s?.date),
    [SOURCE_TAGS.version]: s?.version ? String(s.version) : '',
    [SOURCE_TAGS.quality]: s?.quality ?? '',
  };
}

/** Reads a source from its tags; null if there is none. */
export function sourceFromTags(tag: (name: string) => string): SourceInfo | null {
  const text = (name: string) => {
    const value = tag(name).trim();
    return value && !/^\?+$/.test(value) ? value : undefined;
  };
  const id = text(SOURCE_TAGS.id);
  const title = text(SOURCE_TAGS.title) ?? '';
  const publisher = text(SOURCE_TAGS.publisher);
  if (id === undefined && !title && !publisher) return null;
  const version = text(SOURCE_TAGS.version);
  return {
    id: id !== undefined && /^\d+$/.test(id) ? +id : null,
    title,
    publisher,
    publication: parseDateTag(tag(SOURCE_TAGS.publication)),
    date: parseDateTag(tag(SOURCE_TAGS.date)),
    version: version && /^\d+$/.test(version) && +version > 0 ? +version : undefined,
    quality: text(SOURCE_TAGS.quality),
  };
}

/** What tells sources with the same title apart: "ChessBase · 1999". */
export function sourceSubtitle(s: SourceInfo): string {
  return [s.publisher, s.publication?.year || undefined].filter(Boolean).join(' · ');
}
