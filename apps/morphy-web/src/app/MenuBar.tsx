import { type ReactNode, useEffect, useRef, useState } from 'react';
import { MAC, shortcutLabel } from './shortcuts';

// A desktop-style menu bar: click a title to open its menu, hover to move between open menus,
// with submenus, check marks and keyboard shortcuts.

export type MenuEntry =
  | 'separator'
  | {
      label: string;
      icon?: ReactNode;
      action?: () => void;
      submenu?: MenuEntry[];
      /**
       * The keys that run the item, while the menu bar is enabled, e.g. 'Cmd+S' or 'Alt+E':
       * modifiers in the order Ctrl, Alt, Shift, Cmd, then the key.
       */
      shortcut?: string;
      /** Keys shown for an item whose keys are handled elsewhere, e.g. by the board */
      hint?: string;
      checked?: boolean;
      disabled?: boolean;
    };

export interface Menu {
  title: string;
  items: MenuEntry[];
}

/** The shortcut a key press makes, in the form of MenuEntry.shortcut. Cmd is Ctrl off a Mac. */
function shortcutOf(e: KeyboardEvent): string {
  const parts = [];
  if (e.ctrlKey && MAC) parts.push('Ctrl');
  if (e.altKey) parts.push('Alt');
  if (e.shiftKey) parts.push('Shift');
  if (MAC ? e.metaKey : e.ctrlKey) parts.push('Cmd');
  // With Alt held, a Mac reports a composed character in e.key; e.code names the key itself
  const key = e.code.startsWith('Key') ? e.code.slice(3) : e.code.startsWith('Digit') ? e.code.slice(5) : e.key;
  parts.push(key.length === 1 ? key.toUpperCase() : key);
  return parts.join('+');
}

function findShortcut(items: MenuEntry[], pressed: string): Exclude<MenuEntry, 'separator'> | undefined {
  for (const item of items) {
    if (item === 'separator') continue;
    if (item.shortcut === pressed) return item;
    if (item.submenu) {
      const found = findShortcut(item.submenu, pressed);
      if (found) return found;
    }
  }
  return undefined;
}

/** The menus; their shortcuts work only while enabled, as several menu bars can be mounted. */
export function MenuBar({ menus, enabled }: { menus: Menu[]; enabled: boolean }) {
  const [open, setOpen] = useState<number | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open === null) return;
    const onDown = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(null);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(null);
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  useEffect(() => {
    if (!enabled) return;
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) return;
      const item = findShortcut(
        menus.flatMap((m) => m.items),
        shortcutOf(e)
      );
      if (item?.action && !item.disabled) {
        e.preventDefault();
        item.action();
        setOpen(null);
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [menus, enabled]);

  return (
    <div className="menubar" ref={ref}>
      {menus.map((menu, i) => (
        <div key={menu.title} className="menubar-item">
          <button
            className={open === i ? 'open' : ''}
            onClick={() => setOpen(open === i ? null : i)}
            onPointerEnter={() => open !== null && setOpen(i)}
          >
            {menu.title}
          </button>
          {open === i && <MenuList items={menu.items} close={() => setOpen(null)} />}
        </div>
      ))}
    </div>
  );
}

function MenuList({ items, close, nested }: { items: MenuEntry[]; close: () => void; nested?: boolean }) {
  const [sub, setSub] = useState<number | null>(null);
  return (
    <div className={`menu-list${nested ? ' nested' : ''}`} role="menu">
      {items.map((item, i) =>
        item === 'separator' ? (
          <div key={i} className="menu-sep" />
        ) : (
          <div key={i} className="menu-row" onPointerEnter={() => setSub(item.submenu ? i : null)}>
            <button
              role="menuitem"
              disabled={item.disabled}
              onClick={() => {
                if (item.submenu) return setSub(i);
                item.action?.();
                close();
              }}
            >
              <span className="menu-check">{item.checked ? '✓' : item.icon}</span>
              <span className="menu-label">{item.label}</span>
              <span className="menu-shortcut">
                {item.submenu ? '▸' : item.shortcut ? shortcutLabel(item.shortcut) : (item.hint ?? '')}
              </span>
            </button>
            {item.submenu && sub === i && <MenuList items={item.submenu} close={close} nested />}
          </div>
        )
      )}
    </div>
  );
}
