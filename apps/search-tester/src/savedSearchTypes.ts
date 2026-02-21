import type { EntityType } from './entityConfig';

export interface SavedSearch {
  id: string;
  name: string;
  savedAt: number;
  entityType: EntityType;
  selectedDb: string;
  filter: string;
  sortBy: string;
  order: 'asc' | 'desc';
  includeMoves: boolean;
}
