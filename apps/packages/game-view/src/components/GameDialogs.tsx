import { GameInfoDialog } from './GameInfoDialog';
import { lineStart } from '../utils/variationChoice';
import { ANNOTATION_DIALOGS } from './useGameView';
import type { GameViewState } from './useGameView';

/** The dialogs of a game being viewed, while one is open: Edit Game Info, or one for a move. */
export function GameDialogs({ view }: { view: GameViewState }) {
  const {
    gameInfoServices,
    annotationDialog,
    handleAnnotationDialogSave,
    handleAnnotationDialogCancel,
    editingGameInfo,
    handleGameInfoSave,
    handleGameInfoCancel,
  } = view;

  return (
    <>
      {annotationDialog &&
        (() => {
          const Dialog = ANNOTATION_DIALOGS[annotationDialog.kind];
          return (
            <Dialog
              moveName={lineStart(annotationDialog.move, 1)}
              annotations={annotationDialog.move.annotations}
              onSave={handleAnnotationDialogSave}
              onCancel={handleAnnotationDialogCancel}
            />
          );
        })()}
      {editingGameInfo && (
        <GameInfoDialog
          initial={editingGameInfo}
          services={gameInfoServices}
          onSave={handleGameInfoSave}
          onCancel={handleGameInfoCancel}
        />
      )}
    </>
  );
}
