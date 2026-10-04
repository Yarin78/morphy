import { useEffect, useSyncExternalStore } from 'react';
import { TbAlertCircle } from 'react-icons/tb';
import { getChoices, subscribeChoices } from './choiceStore';

/** The question asked with askChoice, the oldest first. */
export function ChoiceDialog() {
  const question = useSyncExternalStore(subscribeChoices, getChoices)[0];

  useEffect(() => {
    if (!question) return;
    const primary = question.choices.find((c) => c.primary);
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Enter' || !primary) return;
      e.preventDefault();
      e.stopPropagation();
      question.answer(primary.id);
    };
    // Before the other handlers, so the key doesn't reach the board behind
    document.addEventListener('keydown', onKey, { capture: true });
    return () => document.removeEventListener('keydown', onKey, { capture: true });
  }, [question]);

  if (!question) return null;
  return (
    <div className="dialog-backdrop">
      <div className="dialog unsaved-dialog" role="alertdialog" aria-label={question.title}>
        <div className="error-dialog-head">
          <TbAlertCircle className="unsaved-dialog-icon" />
          <h2>{question.title}</h2>
        </div>
        <p className="error-dialog-message">{question.message}</p>
        <div className="dialog-buttons">
          {question.choices.map((c) => (
            <button key={c.id} className={c.primary ? '' : 'dialog-secondary'} onClick={() => question.answer(c.id)}>
              {c.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
