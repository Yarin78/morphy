import { useEffect, useMemo, useState } from 'react'
import {
  type DockviewApi,
  type DockviewGroupPanel,
  DockviewReact,
  type DockviewTheme,
  type EdgeGroupPosition,
  type IDockviewHeaderActionsProps,
  type IDockviewPanelHeaderProps,
  type IDockviewPanelProps,
  type SerializedDockview,
  themeAbyss,
  themeDark,
  themeLight,
  themeLightSpaced,
  themeVisualStudio,
} from 'dockview-react'
import 'dockview-react/dist/styles/dockview.css'
import './dockview-demo.css'
import { DemoShell } from '../shared/DemoShell'
import { useJsonDialog } from '../shared/useJsonDialog'
import { DummyPanel } from '../shared/DummyPanel'
import { KindPicker } from '../shared/KindPicker'
import type { Menu, MenuEntry } from '../shared/MenuBar'
import { clearSaved, KINDS, kindInfo, kindOfId, loadSaved, newPanelId, type PanelKind, save, titleOf } from '../shared/panels'
import { Navigator, type NavigatorState } from './Navigator'

const STORAGE = 'dockview-v2'
const NAV_STORAGE = 'dockview-navigator'
const THEMES: DockviewTheme[] = [themeLight, themeLightSpaced, themeVisualStudio, themeDark, themeAbyss]

type Direction = 'right' | 'below' | 'within'

const components = {
  dummy: (props: IDockviewPanelProps) => <DummyPanel id={props.api.id} />,
}

// Tabs show the window kind's icon; in the left/right edge groups, whose tab strips run
// vertically, only the icon, so a collapsed edge group is a strip of icons
function IconTab({ api }: IDockviewPanelHeaderProps) {
  const [, setVersion] = useState(0)
  useEffect(() => {
    const bump = () => setVersion((v) => v + 1)
    const subs = [api.onDidGroupChange(bump), api.onDidLocationChange(bump), api.onDidTitleChange(bump)]
    return () => subs.forEach((s) => s.dispose())
  }, [api])
  const info = kindInfo(kindOfId(api.id))
  const location = api.group.api.location
  const iconOnly = location.type === 'edge' && (location.position === 'left' || location.position === 'right')
  return (
    <div className="demo-tab" title={api.title}>
      <span className="demo-tab-icon" style={{ background: info.color }}>
        {info.icon}
      </span>
      {!iconOnly && <span>{api.title}</span>}
      {!iconOnly && (
        <button
          className="demo-tab-close"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={(e) => {
            e.stopPropagation()
            api.close()
          }}
        >
          ✕
        </button>
      )}
    </div>
  )
}

/** The edge group at a position, created (with these options) if there is none. */
function edgeGroup(
  api: DockviewApi,
  position: EdgeGroupPosition,
  options: { initialSize: number; collapsed?: boolean } = { initialSize: 220 },
): DockviewGroupPanel {
  const find = () => api.groups.find((g) => g.api.location.type === 'edge' && g.api.location.position === position)
  const existing = find()
  if (existing) return existing
  api.addEdgeGroup(position, { id: `edge-${position}`, ...options })
  return find()!
}

function addPanel(api: DockviewApi, kind: PanelKind, group: DockviewGroupPanel | undefined, direction: Direction) {
  const id = newPanelId(kind)
  api.addPanel({
    id,
    title: titleOf(id),
    component: 'dummy',
    position: group ? { referenceGroup: group, direction } : undefined,
  })
}

// The buttons on the right of every group's tab strip
function HeaderActions({ containerApi, group, api }: IDockviewHeaderActionsProps) {
  const location = api.location
  if (location.type === 'edge') {
    const collapsed = api.isCollapsed()
    const vertical = location.position === 'left' || location.position === 'right'
    return (
      <div className={`tool-bar${vertical ? ' vertical' : ''}`}>
        {!collapsed && <KindPicker icon="+" title="New tab here" onPick={(k) => addPanel(containerApi, k, group, 'within')} />}
        <button
          className="tool-button"
          title={collapsed ? 'Expand' : 'Collapse'}
          onClick={() => (collapsed ? api.expand() : api.collapse())}
        >
          {collapsed ? '▴' : '▾'}
        </button>
      </div>
    )
  }
  const floating = location.type === 'floating'
  return (
    <div className="tool-bar">
      <KindPicker icon="+" title="New tab here" onPick={(k) => addPanel(containerApi, k, group, 'within')} />
      <KindPicker icon="◫" title="Split right" onPick={(k) => addPanel(containerApi, k, group, 'right')} />
      <KindPicker icon="⊟" title="Split below" onPick={(k) => addPanel(containerApi, k, group, 'below')} />
      {!floating && (
        <button
          className="tool-button"
          title="Float this group"
          onClick={() => containerApi.addFloatingGroup(group, { x: 60, y: 60, width: 420, height: 320 })}
        >
          ⧉
        </button>
      )}
      <button
        className="tool-button"
        title={api.isMaximized() ? 'Restore' : 'Maximize'}
        onClick={() => (api.isMaximized() ? api.exitMaximized() : api.maximize())}
      >
        {api.isMaximized() ? '⤡' : '⤢'}
      </button>
    </div>
  )
}

function defaultLayout(api: DockviewApi) {
  const board = newPanelId('board')
  api.addPanel({ id: board, title: titleOf(board), component: 'dummy' })
  const notation = newPanelId('notation')
  api.addPanel({ id: notation, title: titleOf(notation), component: 'dummy', position: { referencePanel: board, direction: 'right' } })
  const engine = newPanelId('engine')
  api.addPanel({ id: engine, title: titleOf(engine), component: 'dummy', position: { referencePanel: notation, direction: 'below' } })
  const tree = newPanelId('tree')
  api.addPanel({ id: tree, title: titleOf(tree), component: 'dummy', position: { referencePanel: engine, direction: 'within' } })
  // Collapsible edge groups: the game list at the bottom (open), analysis on the right (collapsed to icons)
  const bottom = edgeGroup(api, 'bottom', { initialSize: 220 })
  const list = newPanelId('gamelist')
  api.addPanel({ id: list, title: titleOf(list), component: 'dummy', position: { referenceGroup: bottom } })
  const right = edgeGroup(api, 'right', { initialSize: 360, collapsed: true })
  for (const kind of ['tree', 'engine'] as const) {
    const id = newPanelId(kind)
    api.addPanel({ id, title: titleOf(id), component: 'dummy', position: { referenceGroup: right }, inactive: true })
  }
  api.getPanel(board)?.api.setActive()
}

export default function App() {
  const [dockApi, setDockApi] = useState<DockviewApi | null>(null)
  const [theme, setTheme] = useState<DockviewTheme>(themeLight)
  const [locked, setLocked] = useState(false)
  const [jsonDialog, showJson] = useJsonDialog()
  const [nav, setNav] = useState<NavigatorState>(() => loadSaved(NAV_STORAGE) ?? { collapsed: false, width: 220 })
  useEffect(() => save(NAV_STORAGE, nav), [nav])

  const menus = useMemo<Menu[]>(() => {
    const api = () => dockApi!
    const kindItems = (direction: Direction, shortcutPrefix?: string): MenuEntry[] =>
      KINDS.map((k, i) => ({
        label: k.label,
        shortcut: shortcutPrefix ? `${shortcutPrefix}+${i + 1}` : undefined,
        action: () => addPanel(api(), k.kind, api().activeGroup, direction),
      }))
    const toggleEdge = (position: EdgeGroupPosition) => {
      const g = edgeGroup(api(), position)
      if (g.api.isCollapsed()) g.api.expand()
      else g.api.collapse()
    }
    const moveActiveTo = (position: EdgeGroupPosition | 'main') => {
      const panel = api().activePanel
      if (!panel) return
      if (position === 'main') {
        const grid = api().groups.find((g) => g.api.location.type === 'grid')
        panel.api.moveTo(grid ? { group: grid, position: 'right' } : {})
      } else {
        const g = edgeGroup(api(), position)
        panel.api.moveTo({ group: g })
        if (g.api.isCollapsed()) g.api.expand()
      }
    }
    return [
      {
        title: 'Layout',
        items: [
          { label: 'Show JSON', action: () => showJson(api().toJSON()) },
          { label: 'Reset to default', action: () => (api().clear(), defaultLayout(api())) },
          { label: 'Clear all', action: () => api().clear() },
          'separator',
          { label: 'Lock layout (no drag & drop)', checked: locked, action: () => setLocked(!locked) },
        ],
      },
      {
        title: 'Window',
        items: [
          { label: 'New tab in active group', submenu: kindItems('within', 'Alt') },
          { label: 'Split active right', submenu: kindItems('right', 'Alt+Shift') },
          { label: 'Split active below', submenu: kindItems('below') },
          {
            label: 'New floating window',
            submenu: KINDS.map((k) => ({
              label: k.label,
              action: () => {
                const id = newPanelId(k.kind)
                api().addPanel({ id, title: titleOf(id), component: 'dummy', floating: { width: 400, height: 300 } })
              },
            })),
          },
          'separator',
          {
            label: 'Float active group',
            action: () => {
              const g = api().activeGroup
              if (g) api().addFloatingGroup(g)
            },
          },
          {
            label: 'Pop out active group to a browser window',
            action: () => {
              const g = api().activeGroup
              if (g) void api().addPopoutGroup(g)
            },
          },
          {
            label: 'Maximize / restore active group',
            shortcut: 'Alt+M',
            action: () => {
              const g = api().activeGroup
              if (g?.api.isMaximized()) g.api.exitMaximized()
              else g?.api.maximize()
            },
          },
          {
            label: 'Move active window to',
            submenu: [
              { label: 'Main area', action: () => moveActiveTo('main') },
              { label: 'Bottom edge', action: () => moveActiveTo('bottom') },
              { label: 'Right edge', action: () => moveActiveTo('right') },
              { label: 'Left edge', action: () => moveActiveTo('left') },
            ],
          },
          { label: 'Close active tab', shortcut: 'Alt+W', action: () => api().activePanel?.api.close() },
          'separator',
          { label: 'Next tab', shortcut: 'Alt+ArrowRight', action: () => api().moveToNext({ includePanel: true }) },
          { label: 'Previous tab', shortcut: 'Alt+ArrowLeft', action: () => api().moveToPrevious({ includePanel: true }) },
        ],
      },
      {
        title: 'View',
        items: [
          {
            label: 'Navigator as icons only',
            shortcut: 'Alt+B',
            checked: nav.collapsed,
            action: () => setNav({ ...nav, collapsed: !nav.collapsed }),
          },
          { label: 'Collapse / expand bottom edge', shortcut: 'Alt+J', action: () => toggleEdge('bottom') },
          { label: 'Collapse / expand right edge', shortcut: 'Alt+K', action: () => toggleEdge('right') },
          'separator',
          { label: 'Theme', submenu: THEMES.map((t) => ({ label: t.name, checked: t === theme, action: () => setTheme(t) })) },
        ],
      },
    ]
  }, [dockApi, theme, locked, nav, showJson])

  return (
    <DemoShell
      current="Dockview"
      menus={menus}
      dialog={jsonDialog}
      hint={
        <>
          Left: our own navigator (« or Alt+B collapses it to icons; drag its edge to resize). Bottom and right: Dockview
          edge groups; click the active tab (or ▾/▴) to collapse/expand; drag windows in and out. Shift-drag a tab to
          float it. Right-click a tab for the context menu. The layout is saved to localStorage.
        </>
      }
    >
      <div className="dv-demo-row">
        <Navigator
          api={dockApi}
          state={nav}
          onChange={setNav}
          onAdd={(k) => dockApi && addPanel(dockApi, k, dockApi.groups.find((g) => g.api.location.type === 'grid'), 'within')}
        />
        <div className="dv-demo-dock">
          <DockviewReact
            className="dockview-demo"
            theme={theme}
            locked={locked}
            disableDnd={locked}
            components={components}
            defaultTabComponent={IconTab}
            rightHeaderActionsComponent={HeaderActions}
            getTabContextMenuItems={({ panel, api }) => [
              'close',
              'closeOthers',
              'closeAll',
              'separator',
              'maximize',
              'float',
              'popout',
              'separator',
              {
                label: 'Duplicate to the right',
                action: () => addPanel(api, panel.id.split('-')[0] as PanelKind, panel.group, 'right'),
              },
            ]}
            onReady={({ api }) => {
              setDockApi(api)
              const saved = loadSaved<SerializedDockview>(STORAGE)
              try {
                if (saved) api.fromJSON(saved)
                else defaultLayout(api)
              } catch {
                clearSaved(STORAGE)
                api.clear()
                defaultLayout(api)
              }
              api.onDidLayoutChange(() => save(STORAGE, api.toJSON()))
            }}
          />
        </div>
      </div>
    </DemoShell>
  )
}
