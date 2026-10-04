import { GameNotation } from './GameNotation';
import { GameHeader } from './GameHeader';
import { NagBar } from './NagBar';
import { EvalGraph } from './EvalGraph';
import { LanguageSelector } from './LanguageSelector';
import { VariationChooser } from './VariationChooser';
import { ContextMenu } from './ContextMenu';
import type { MoveNode } from '../model/GameTree';
import type { GameViewState } from './useGameView';
import './NotationBar.css';
import './GameView.css';

/**
 * The notation of a game being viewed: its header, the moves with their annotations, the
 * evaluation graph and the bar to annotate the current move with.
 */
export function GameNotationPanel({ view }: { view: GameViewState }) {
  const {
    selectedGame,
    game,
    version,
    isEditMode,
    onQuotationClick,
    openGameInfo,
    languages,
    shownLanguages,
    language,
    handleLanguageSelect,
    handleMoveClick,
    handleMoveContextMenu,
    editingComment,
    handleCommentEdit,
    handleCommentEditDone,
    handleNotationReady,
    moveMenu,
    moveMenuItems,
    handleMoveMenuClose,
    choosingMove,
    handleMoveChosen,
    handleMoveChoiceCancel,
    evaluationBars,
    seekToMove,
    notationRef,
    handleNagToggle,
    barActionGroups,
  } = view;

  return (
    <div className="notation-area">
      {selectedGame && (
        <>
          <GameHeader game={game} onClick={openGameInfo} />
          <div className="notation-divider"></div>
          <GameNotation
            game={game}
            version={version}
            languages={shownLanguages}
            onMoveClick={handleMoveClick}
            onMoveContextMenu={isEditMode ? handleMoveContextMenu : undefined}
            editing={editingComment}
            onCommentDoubleClick={isEditMode ? handleCommentEdit : undefined}
            onCommentEditDone={handleCommentEditDone}
            onNotationReady={handleNotationReady}
            containerRef={notationRef}
            onQuotationClick={onQuotationClick}
          />
          {moveMenu && moveMenuItems.length > 0 && (
            <ContextMenu x={moveMenu.x} y={moveMenu.y} items={moveMenuItems} onClose={handleMoveMenuClose} />
          )}
          {choosingMove && (
            <VariationChooser moves={choosingMove} onChoose={handleMoveChosen} onCancel={handleMoveChoiceCancel} />
          )}
          {evaluationBars && (
            <EvalGraph
              bars={evaluationBars}
              current={game.currentNode()}
              onSelect={(node) => seekToMove('san' in node ? (node as MoveNode) : null)}
            />
          )}
          {(isEditMode || languages.length > 0) && (
            <div className="notation-bar">
              {isEditMode && (
                <NagBar
                  annotations={game.currentMove()?.annotations ?? null}
                  onToggle={handleNagToggle}
                  actionGroups={barActionGroups}
                />
              )}
              <LanguageSelector
                language={language}
                gameLanguages={languages}
                onSelect={handleLanguageSelect}
                disabled={!!editingComment}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
