import type { SaveMode } from './unsavedStore';

// Asking what to do with a board's unsaved changes, before it's closed

export type UnsavedChoice = SaveMode | 'discard' | 'cancel';

export interface Question {
  title: string;
  canSave: boolean;
  answer: (choice: UnsavedChoice) => void;
}

let question: Question | null = null;

export function getQuestion(): Question | null {
  return question;
}
const listeners = new Set<() => void>();

function set(next: Question | null) {
  question = next;
  listeners.forEach((l) => l());
}

export function subscribeQuestion(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Asks what to do with the unsaved changes of a board: save them (where it is, or as a new
 * game), throw them away, or not close it after all.
 */
export function askAboutUnsavedChanges(title: string, canSave: boolean): Promise<UnsavedChoice> {
  return new Promise((resolve) => {
    set({
      title,
      canSave,
      answer: (choice) => {
        set(null);
        resolve(choice);
      },
    });
  });
}
