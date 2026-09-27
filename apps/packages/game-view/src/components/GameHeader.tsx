import { Chess } from '@jackstenglein/chess';
import './GameHeader.css';

interface GameHeaderProps {
  chess: Chess;
}

export const GameHeader: React.FC<GameHeaderProps> = ({ chess }) => {
  // Get headers from chess instance (loaded from PGN)
  const headers = chess.header() || {};

  // Helper function to filter out empty, "?", or "???" values
  const getValue = (value: string | undefined): string => {
    if (!value || value === '?' || value === '???' || value.trim() === '') return '';
    return value;
  };

  // Extract information from PGN headers
  // Use getRawValue() to get string values instead of parsed types
  const white = getValue(headers.getRawValue('White'));
  const black = getValue(headers.getRawValue('Black'));
  const whiteElo = getValue(headers.getRawValue('WhiteElo') || headers.getRawValue('WhiteRating'));
  const blackElo = getValue(headers.getRawValue('BlackElo') || headers.getRawValue('BlackRating'));
  const result = getValue(headers.getRawValue('Result'));
  const date = getValue(headers.getRawValue('Date'));
  const event = getValue(headers.getRawValue('Event'));
  const site = getValue(headers.getRawValue('Site'));
  const round = getValue(headers.getRawValue('Round'));
  const eco = getValue(headers.getRawValue('ECO'));
  const annotator = getValue(headers.getRawValue('Annotator'));

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
        return '';
      default:
        return result;
    }
  };

  // Don't render if no basic game information is available
  if (!white && !black && !result) {
    return null;
  }

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

  // Build second line: eco event site (round) date
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

  return (
    <div className="game-header">
      <div className="game-header-line">{firstLineParts}</div>
      {secondLineParts.length > 0 && (
        <div className="game-header-line">{secondLineParts}</div>
      )}
    </div>
  );
};
