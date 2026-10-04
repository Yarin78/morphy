// The dummy window kinds every demo can create. The real app would map each kind to a
// real component (board, notation, ...); here they are all DummyPanel.

export type PanelKind = 'board' | 'notation' | 'gamelist' | 'engine' | 'tree'

export interface KindInfo {
  kind: PanelKind
  label: string
  /** A single glyph, for icon-only tabs and the collapsed navigator */
  icon: string
  color: string
}

export const KINDS: KindInfo[] = [
  { kind: 'board', label: 'Board', icon: '♞', color: '#b58863' },
  { kind: 'notation', label: 'Notation', icon: '≡', color: '#4f7cac' },
  { kind: 'gamelist', label: 'Game list', icon: '▤', color: '#5a9367' },
  { kind: 'engine', label: 'Engine', icon: '⚙', color: '#a0526d' },
  { kind: 'tree', label: 'Opening tree', icon: '⑂', color: '#8a6fb0' },
]

export function kindInfo(kind: PanelKind): KindInfo {
  return KINDS.find((k) => k.kind === kind) ?? KINDS[0]
}

/** A fresh, layout-unique panel id that also encodes the kind, e.g. `board-k3f9`. */
export function newPanelId(kind: PanelKind): string {
  return `${kind}-${Math.random().toString(36).slice(2, 6)}`
}

/** The kind encoded in a panel id from {@link newPanelId}. */
export function kindOfId(id: string): PanelKind {
  const prefix = id.split('-')[0]
  return (KINDS.find((k) => k.kind === prefix)?.kind ?? 'board') as PanelKind
}

export function titleOf(id: string): string {
  return `${kindInfo(kindOfId(id)).label} ${id.split('-')[1] ?? ''}`.trim()
}

/** Saved layouts live in localStorage, one key per framework. */
export function loadSaved<T>(key: string): T | undefined {
  try {
    const raw = localStorage.getItem(`layout-demo:${key}`)
    return raw ? (JSON.parse(raw) as T) : undefined
  } catch {
    return undefined
  }
}

export function save(key: string, value: unknown) {
  try {
    localStorage.setItem(`layout-demo:${key}`, JSON.stringify(value))
  } catch {
    // ignore, persisting is a convenience
  }
}

export function clearSaved(key: string) {
  try {
    localStorage.removeItem(`layout-demo:${key}`)
  } catch {
    // ignore
  }
}
