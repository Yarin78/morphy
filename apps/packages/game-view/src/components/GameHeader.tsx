import type { GameTree } from '../model/GameTree';
import { LINE_EVALUATION_TAG, lineEvaluationSymbol } from '../utils/gameInfo';
import './GameHeader.css';

interface GameHeaderProps {
  game: GameTree;
  /** When provided, the whole header is clickable, e.g. to edit the game info. */
  onClick?: () => void;
}

export const GameHeader: React.FC<GameHeaderProps> = ({ game, onClick }) => {
  // Helper function to filter out empty values and PGN placeholders like "?" or "????.??.??"
  const getValue = (value: string | undefined): string => {
    if (!value || /^[?.\s]*$/.test(value)) return '';
    return value;
  };

  // Extract information from the PGN tags
  const white = getValue(game.getTag('White'));
  const black = getValue(game.getTag('Black'));
  const whiteElo = getValue(game.getTag('WhiteElo') || game.getTag('WhiteRating'));
  const blackElo = getValue(game.getTag('BlackElo') || game.getTag('BlackRating'));
  const result = getValue(game.getTag('Result'));
  const date = getValue(game.getTag('Date'));
  const event = getValue(game.getTag('Event'));
  const site = getValue(game.getTag('Site'));
  // "round.subround.board", leaving out whichever parts aren't set. The Round tag already holds
  // "round" or "round.subround".
  const round = [...getValue(game.getTag('Round')).split('.'), getValue(game.getTag('Board'))]
    .map((part) => part.trim())
    .filter((part) => part && !/^[?0]+$/.test(part))
    .join('.');
  const eco = getValue(game.getTag('ECO'));
  const annotator = getValue(game.getTag('Annotator'));
  const lineEvaluation = getValue(game.getTag(LINE_EVALUATION_TAG));

  // Format result for display
  const formatResult = (result: string) => {
    switch (result) {
      case '1-0':
        return '1-0';
      case '0-1':
        return '0-1';
      case '1/2-1/2':
        return '½-½';
      case '*':
        return lineEvaluation ? lineEvaluationSymbol(lineEvaluation) : '';
      default:
        return result;
    }
  };

  // Build first line: white player rating - black player rating result
  const firstLineParts: React.ReactNode[] = [];
  if (white) {
    firstLineParts.push(<strong key="white">{white}</strong>);
    if (whiteElo) {
      firstLineParts.push(<span key="whiteElo"> {whiteElo}</span>);
    }
  }
  if (white && black) {
    firstLineParts.push(<span key="dash"> - </span>);
  }
  if (black) {
    firstLineParts.push(<strong key="black">{black}</strong>);
    if (blackElo) {
      firstLineParts.push(<span key="blackElo"> {blackElo}</span>);
    }
  }
  const formattedResult = formatResult(result);
  if (formattedResult) {
    firstLineParts.push(<strong key="result"> {formattedResult}</strong>);
  }

  // Build second line: eco event site (round.subround.board) date
  const secondLineParts: React.ReactNode[] = [];
  if (eco) {
    secondLineParts.push(<strong key="eco">{eco}</strong>);
  }
  if (event) {
    if (secondLineParts.length > 0) secondLineParts.push(<span key="event-space"> </span>);
    secondLineParts.push(<span key="event">{event}</span>);
  }
  if (site) {
    if (secondLineParts.length > 0) secondLineParts.push(<span key="site-space"> </span>);
    secondLineParts.push(<span key="site">{site}</span>);
  }
  if (round) {
    if (secondLineParts.length > 0) secondLineParts.push(<span key="round-space"> </span>);
    secondLineParts.push(<span key="round">({round})</span>);
  }
  if (date) {
    if (secondLineParts.length > 0) secondLineParts.push(<span key="date-space"> </span>);
    secondLineParts.push(<span key="date">{date}</span>);
  }
  if (annotator) {
    if (secondLineParts.length > 0) secondLineParts.push(<span key="annotator-space"> </span>);
    secondLineParts.push(<span key="annotator" className="annotator">[{annotator}]</span>);
  }

  // Without any game information there's nothing to show, except something to click on
  const isEmpty = firstLineParts.length === 0 && secondLineParts.length === 0;
  if (isEmpty && !onClick) {
    return null;
  }

  const clickableProps = onClick
    ? {
        role: 'button',
        tabIndex: 0,
        title: 'Edit game info',
        onClick,
        onKeyDown: (e: React.KeyboardEvent) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        },
      }
    : {};

  return (
    <div className={`game-header${onClick ? ' game-header-clickable' : ''}`} {...clickableProps}>
      {isEmpty ? (
        <div className="game-header-line game-header-placeholder">Click to add game info</div>
      ) : (
        <>
          <div className="game-header-line">{firstLineParts}</div>
          {secondLineParts.length > 0 && (
            <div className="game-header-line">{secondLineParts}</div>
          )}
        </>
      )}
    </div>
  );
};
