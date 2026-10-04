declare module 'react-chessground' {
  import { Component, CSSProperties } from 'react';

  type Square = string; // e.g., 'e4', 'a1', etc.
  type Color = 'white' | 'black';

  interface HighlightConfig {
    lastMove?: boolean;
    check?: boolean;
    [key: string]: any;
  }

  interface AnimationConfig {
    enabled?: boolean;
    duration?: number;
    [key: string]: any;
  }

  interface MovableConfig {
    free?: boolean;
    color?: Color | 'both';
    dests?: Map<Square, Square[]> | { [key: string]: Square[] };
    showDests?: boolean;
    [key: string]: any;
  }

  interface PremovableConfig {
    enabled?: boolean;
    showDests?: boolean;
    [key: string]: any;
  }

  interface PredroppableConfig {
    enabled?: boolean;
    [key: string]: any;
  }

  interface DraggableConfig {
    enabled?: boolean;
    distance?: number;
    autoDistance?: boolean;
    [key: string]: any;
  }

  interface SelectableConfig {
    enabled?: boolean;
    [key: string]: any;
  }

  interface DrawShape {
    orig: Square;
    dest?: Square;
    brush?: string;
    label?: { text: string };
    modifiers?: {
      hilite?: boolean;
      lineWidth?: number;
      [key: string]: any;
    };
    piece?: {
      role: 'king' | 'queen' | 'rook' | 'bishop' | 'knight' | 'pawn';
      color: Color;
      scale?: number;
    };
    customSvg?: string;
  }

  interface DrawableConfig {
    enabled?: boolean;
    visible?: boolean;
    defaultSnapToValidMove?: boolean;
    eraseOnClick?: boolean;
    shapes?: DrawShape[]; // user-drawn shapes
    autoShapes?: DrawShape[]; // automatically generated shapes
    brushes?: { [name: string]: { color: string; opacity: number; lineWidth: number } };
    pieces?: {
      baseUrl?: string;
    };
    onChange?: (shapes: DrawShape[]) => void;
    [key: string]: any;
  }

  interface ItemsConfig {
    [key: string]: any;
  }

  interface ChessgroundProps {
    // Board dimensions
    width?: number | string;
    height?: number | string;
    style?: CSSProperties;

    // Board state
    fen?: string;
    orientation?: Color;
    turnColor?: Color;
    check?: Square | boolean;
    lastMove?: [Square, Square] | Square[] | null;
    selected?: Square;

    // Display options
    coordinates?: boolean;
    viewOnly?: boolean;
    autoCastle?: boolean;
    disableContextMenu?: boolean;
    resizable?: boolean;
    addPieceZIndex?: boolean;

    // Configuration objects
    highlight?: HighlightConfig;
    animation?: AnimationConfig;
    movable?: MovableConfig;
    premovable?: PremovableConfig;
    predroppable?: PredroppableConfig;
    draggable?: DraggableConfig;
    selectable?: SelectableConfig;
    drawable?: DrawableConfig;
    items?: ItemsConfig;

    // Event handlers
    onChange?: () => void;
    onMove?: (from: Square, to: Square) => void;
    onDropNewPiece?: (piece: string, key: Square) => void;
    onSelect?: (key: Square) => void;

    // Additional props (for extensibility)
    [key: string]: any;
  }

  /** The bit of chessground's own API used through the component */
  interface ChessgroundApi {
    state: { selected?: Square };
    selectSquare(key: Square | null): void;
  }

  export default class Chessground extends Component<ChessgroundProps> {
    /** Chessground itself, once the component is mounted */
    cg?: ChessgroundApi;
  }
}
