import { type PointerEvent as ReactPointerEvent, useEffect, useState } from 'react'
import type { DockviewApi, IDockviewPanel } from 'dockview-react'
import { KindPicker } from '../shared/KindPicker'
import { kindInfo, kindOfId, type PanelKind } from '../shared/panels'

// The left-hand navigator: an app-owned sidebar next to Dockview (not a Dockview
// panel), listing the open windows by section. It can be collapsed to an icon strip,
// and resized by dragging its right edge; dragging it narrow enough snaps it to icons.

export const COLLAPSED_WIDTH = 44
const MIN_EXPANDED = 150
const MAX_EXPANDED = 420
const SNAP_BELOW = 110

const SECTIONS: { title: string; kinds: PanelKind[] }[] = [
  { title: 'Games', kinds: ['board', 'notation'] },
  { title: 'Databases', kinds: ['gamelist'] },
  { title: 'Analysis', kinds: ['engine', 'tree'] },
]

export interface NavigatorState {
  collapsed: boolean
  width: number
}

export function Navigator({
  api,
  state,
  onChange,
  onAdd,
}: {
  api: DockviewApi | null
  state: NavigatorState
  onChange: (state: NavigatorState) => void
  onAdd: (kind: PanelKind) => void
}) {
  const [, setVersion] = useState(0)
  const [dragWidth, setDragWidth] = useState<number | null>(null)

  // Re-render whenever the set of windows, the active one or the layout changes
  useEffect(() => {
    if (!api) return
    const bump = () => setVersion((v) => v + 1)
    const subs = [
      api.onDidAddPanel(bump),
      api.onDidRemovePanel(bump),
      api.onDidActivePanelChange(bump),
      api.onDidLayoutChange(bump),
    ]
    return () => subs.forEach((s) => s.dispose())
  }, [api])

  const collapsed = dragWidth === null ? state.collapsed : dragWidth < SNAP_BELOW
  const width = collapsed ? COLLAPSED_WIDTH : (dragWidth ?? state.width)
  const panels = api?.panels ?? []
  const active = api?.activePanel

  const reveal = (panel: IDockviewPanel) => {
    const group = panel.group
    if (group.api.location.type === 'edge' && group.api.isCollapsed()) group.api.expand()
    panel.api.setActive()
  }

  const onResizeStart = (e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault()
    const handle = e.currentTarget
    handle.setPointerCapture(e.pointerId)
    const startX = e.clientX
    const startWidth = width
    let current = startWidth
    const move = (ev: PointerEvent) => {
      current = Math.min(MAX_EXPANDED, Math.max(COLLAPSED_WIDTH, startWidth + ev.clientX - startX))
      setDragWidth(current)
    }
    const up = () => {
      handle.removeEventListener('pointermove', move)
      handle.removeEventListener('pointerup', up)
      setDragWidth(null)
      if (current < SNAP_BELOW) onChange({ ...state, collapsed: true })
      else onChange({ collapsed: false, width: Math.max(MIN_EXPANDED, current) })
    }
    handle.addEventListener('pointermove', move)
    handle.addEventListener('pointerup', up)
  }

  return (
    <aside className={`navigator${collapsed ? ' collapsed' : ''}`} style={{ width }}>
      <div className="nav-head">
        {!collapsed && <span className="nav-title">Open windows</span>}
        {!collapsed && <KindPicker icon="+" title="New window" onPick={onAdd} />}
        <button
          className="tool-button"
          title={collapsed ? 'Expand navigator (Alt+B)' : 'Collapse to icons (Alt+B)'}
          onClick={() => onChange({ ...state, collapsed: !state.collapsed })}
        >
          {collapsed ? '»' : '«'}
        </button>
      </div>
      <div className="nav-body">
        {SECTIONS.map((section) => {
          const items = panels.filter((p) => section.kinds.includes(kindOfId(p.id)))
          return (
            <div key={section.title} className="nav-section">
              {collapsed ? <div className="nav-sep" /> : <div className="nav-section-title">{section.title}</div>}
              {items.length === 0 && !collapsed && <div className="nav-empty">none open</div>}
              {items.map((p) => {
                const info = kindInfo(kindOfId(p.id))
                const where = p.group.api.location.type
                return (
                  <div
                    key={p.id}
                    className={`nav-item${p === active ? ' active' : ''}`}
                    title={collapsed ? `${p.title} (${where})` : undefined}
                    onClick={() => reveal(p)}
                  >
                    <span className="nav-icon" style={{ background: info.color }}>
                      {info.icon}
                    </span>
                    {!collapsed && (
                      <>
                        <span className="nav-label">{p.title}</span>
                        {where !== 'grid' && <span className="nav-where">{where}</span>}
                        <button
                          className="nav-close"
                          title="Close"
                          onClick={(e) => {
                            e.stopPropagation()
                            p.api.close()
                          }}
                        >
                          ✕
                        </button>
                      </>
                    )}
                  </div>
                )
              })}
            </div>
          )
        })}
      </div>
      <div className="nav-resize" onPointerDown={onResizeStart} title="Drag to resize; narrow it to collapse to icons" />
    </aside>
  )
}
