import type { EntityType } from './entityConfig';

export interface SavedSearch {
  id: string;
  name: string;
  savedAt: number;
  entityType: EntityType;
  selectedDb: string;
  filter: string;
  offset: number;
  limit: number;
  sortBy: string;
  order: 'asc' | 'desc';
  resultFilter: string;
  dateFrom: string;
  dateTo: string;
  ecoCode: string;
  ratingMin: string;
  ratingMax: string;
  ratingMode: string;
  includeMoves: boolean;
}
