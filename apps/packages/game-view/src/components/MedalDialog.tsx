import { useState } from 'react';
import type { Annotation } from '../model/annotations';
import { findAnnotation } from '../model/annotations';
import { withAnnotation } from '../model/specialAnnotations';
import { MEDAL_NAMES, medalColors, medalName } from '../utils/medals';
import { AnnotationDialog } from './AnnotationDialog';

interface MedalDialogProps {
  moveName: string;
  annotations: readonly Annotation[];
  onSave: (annotations: Annotation[]) => void;
  onCancel: () => void;
}

/** A dialog to give a move its medals, the reasons it's of interest: any number of them. */
export const MedalDialog: React.FC<MedalDialogProps> = ({ moveName, annotations, onSave, onCancel }) => {
  const existing = findAnnotation(annotations, 'medals');
  const [medals, setMedals] = useState<ReadonlySet<string>>(new Set(existing?.medals ?? []));

  const toggle = (medal: string) => {
    const next = new Set(medals);
    if (next.has(medal)) next.delete(medal);
    else next.add(medal);
    setMedals(next);
  };

  // In the order ChessBase shows them; none takes the annotation away
  const save = (chosen: ReadonlySet<string>) => {
    const list = MEDAL_NAMES.filter((m) => chosen.has(m));
    onSave(withAnnotation(annotations, 'medals', list.length > 0 ? { type: 'medals', medals: list } : null));
  };

  return (
    <AnnotationDialog
      title={`Medals: ${moveName}`}
      onSubmit={() => save(medals)}
      onCancel={onCancel}
      onRemove={existing ? () => save(new Set()) : undefined}
    >
      <div className="medal-dialog-list">
        {MEDAL_NAMES.map((medal) => {
          const [top, bottom] = medalColors(medal);
          return (
            <label key={medal} className="annotation-dialog-check">
              <input type="checkbox" checked={medals.has(medal)} onChange={() => toggle(medal)} />
              <span className="medal-dialog-swatch" style={{ background: `linear-gradient(to bottom, ${top}, ${bottom})` }} />
              {medalName(medal)}
            </label>
          );
        })}
      </div>
    </AnnotationDialog>
  );
};
