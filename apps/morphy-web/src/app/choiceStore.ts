// A question with a few answers, asked in a dialog, one at a time

export interface Choice<T extends string> {
  id: T;
  label: string;
  /** The answer Enter gives, drawn as the main button */
  primary?: boolean;
}

export interface ChoiceQuestion {
  title: string;
  message: string;
  choices: Choice<string>[];
  answer: (id: string) => void;
}

let questions: readonly ChoiceQuestion[] = [];
const listeners = new Set<() => void>();

function set(next: readonly ChoiceQuestion[]) {
  questions = next;
  listeners.forEach((l) => l());
}

export function subscribeChoices(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getChoices(): readonly ChoiceQuestion[] {
  return questions;
}

/** Asks a question; the answer is the id of the choice made. */
export function askChoice<T extends string>(title: string, message: string, choices: Choice<T>[]): Promise<T> {
  return new Promise((resolve) => {
    const question: ChoiceQuestion = {
      title,
      message,
      choices,
      answer: (id) => {
        set(questions.filter((q) => q !== question));
        resolve(id as T);
      },
    };
    set([...questions, question]);
  });
}
