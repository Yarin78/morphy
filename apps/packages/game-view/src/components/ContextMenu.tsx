import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import './ContextMenu.css';

/** An entry of a context menu: something to do, a submenu, or a line between groups. */
export type ContextMenuItem =
  | {
      label: string;
      /** Shown before the label, like the symbol of a NAG. */
      symbol?: string;
      disabled?: boolean;
      onSelect?: () => void;
      /** Entries opening to the right while the entry is hovered over. */
      submenu?: readonly ContextMenuItem[];
    }
  | 'separator';

interface ContextMenuProps {
  /** Where it opens, in the coordinates of the window. */
  x: number;
  y: number;
  items: readonly ContextMenuItem[];
  onClose: () => void;
}

/** The entries of a menu, or of one of its submenus. */
const MenuList: React.FC<{ items: readonly ContextMenuItem[]; onClose: () => void; className: string }> = ({
  items,
  onClose,
  className,
}) => {
  const [open, setOpen] = useState<number | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const [flip, setFlip] = useState({ left: false, up: false });

  // A submenu that would go off the window opens to the left, or upwards, instead
  useLayoutEffect(() => {
    if (!className.includes('submenu') || !listRef.current) return;
    const rect = listRef.current.getBoundingClientRect();
    setFlip({ left: rect.right > window.innerWidth, up: rect.bottom > window.innerHeight });
  }, [className]);

  return (
    <div
      ref={listRef}
      className={`${className}${flip.left ? ' flip-left' : ''}${flip.up ? ' flip-up' : ''}`}
      role="menu"
    >
      {items.map((item, i) => {
        if (item === 'separator') return <div key={i} className="context-menu-separator" role="separator" />;
        const hasSubmenu = !!item.submenu?.length;
        return (
          <div
            key={i}
            className={`context-menu-item${item.disabled ? ' disabled' : ''}${open === i ? ' open' : ''}`}
            role="menuitem"
            aria-disabled={item.disabled || undefined}
            aria-haspopup={hasSubmenu || undefined}
            onMouseEnter={() => setOpen(hasSubmenu && !item.disabled ? i : null)}
            onClick={() => {
              if (item.disabled || hasSubmenu) return;
              item.onSelect?.();
              onClose();
            }}
          >
            {item.symbol !== undefined && <span className="context-menu-symbol">{item.symbol}</span>}
            <span className="context-menu-label">{item.label}</span>
            {hasSubmenu && <span className="context-menu-arrow">▸</span>}
            {hasSubmenu && open === i && (
              <MenuList items={item.submenu!} onClose={onClose} className="context-menu submenu" />
            )}
          </div>
        );
      })}
    </div>
  );
};

/**
 * A context menu where the mouse was clicked, with submenus opening to the right. A click outside
 * it or Escape closes it.
 */
export const ContextMenu: React.FC<ContextMenuProps> = ({ x, y, items, onClose }) => {
  const menuRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ x, y });

  // Kept inside the window
  useLayoutEffect(() => {
    const rect = menuRef.current?.getBoundingClientRect();
    if (!rect) return;
    setPosition({
      x: Math.max(0, Math.min(x, window.innerWidth - rect.width)),
      y: Math.max(0, Math.min(y, window.innerHeight - rect.height)),
    });
  }, [x, y]);

  useEffect(() => {
    const handleMouseDown = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) onClose();
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopImmediatePropagation();
        onClose();
      }
    };
    window.addEventListener('mousedown', handleMouseDown, true);
    window.addEventListener('keydown', handleKeyDown, true);
    window.addEventListener('blur', onClose);
    return () => {
      window.removeEventListener('mousedown', handleMouseDown, true);
      window.removeEventListener('keydown', handleKeyDown, true);
      window.removeEventListener('blur', onClose);
    };
  }, [onClose]);

  return (
    <div
      ref={menuRef}
      className="context-menu-root"
      style={{ left: position.x, top: position.y }}
      onContextMenu={(e) => e.preventDefault()}
    >
      <MenuList items={items} onClose={onClose} className="context-menu" />
    </div>
  );
};
