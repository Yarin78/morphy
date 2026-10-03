import { useState } from 'react';
import type { Annotation } from '../model/annotations';
import { findAnnotation } from '../model/annotations';
import { withAnnotation } from '../model/specialAnnotations';
import { AnnotationDialog } from './AnnotationDialog';

interface VariationColorDialogProps {
  moveName: string;
  annotations: readonly Annotation[];
  onSave: (annotations: Annotation[]) => void;
  onCancel: () => void;
}

// The color a new variation color starts from
const DEFAULT_COLOR = '#2b5f94';

/**
 * A dialog to give the variation from a move a color: the color, and whether it's for the moves
 * only, not the comments, and for the main line of the variation only, not its sublines.
 */
export const VariationColorDialog: React.FC<VariationColorDialogProps> = ({
  moveName,
  annotations,
  onSave,
  onCancel,
}) => {
  const existing = findAnnotation(annotations, 'variationColor');
  const [color, setColor] = useState(existing?.color ?? DEFAULT_COLOR);
  const [onlyMoves, setOnlyMoves] = useState(existing?.onlyMoves ?? false);
  const [onlyMainline, setOnlyMainline] = useState(existing?.onlyMainline ?? false);

  return (
    <AnnotationDialog
      title={`Variation Colour: ${moveName}`}
      onSubmit={() =>
        onSave(withAnnotation(annotations, 'variationColor', { type: 'variationColor', color, onlyMoves, onlyMainline }))
      }
      onCancel={onCancel}
      onRemove={existing ? () => onSave(withAnnotation(annotations, 'variationColor', null)) : undefined}
    >
      <div className="variation-color-dialog-fields">
        <label className="annotation-dialog-check">
          <input type="color" value={color} onChange={(e) => setColor(e.target.value)} />
          Colour of the variation from this move
        </label>
        <label className="annotation-dialog-check">
          <input type="checkbox" checked={onlyMoves} onChange={(e) => setOnlyMoves(e.target.checked)} />
          Only the moves, not the comments
        </label>
        <label className="annotation-dialog-check">
          <input type="checkbox" checked={onlyMainline} onChange={(e) => setOnlyMainline(e.target.checked)} />
          Only the main line of the variation, not its sublines
        </label>
      </div>
    </AnnotationDialog>
  );
};
