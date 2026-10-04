import { useEffect, useMemo, useState } from 'react'
import DockLayout, { type DockContext, type DropDirection, type LayoutBase, type LayoutData, type PanelData, type TabData } from 'rc-dock'
import 'rc-dock/dist/rc-dock.css'
import { DemoShell } from '../shared/DemoShell'
import { useJsonDialog } from '../shared/useJsonDialog'
import { DummyPanel } from '../shared/DummyPanel'
import { KindPicker } from '../shared/KindPicker'
import type { Menu, MenuEntry } from '../shared/MenuBar'
import { clearSaved, KINDS, loadSaved, newPanelId, type PanelKind, save, titleOf } from '../shared/panels'

const STORAGE = 'rcdock'

function makeTab(id: string): TabData {
  // cached keeps the content mounted while the tab is hidden behind another tab
  return { id, title: titleOf(id), content: <DummyPanel id={id} />, closable: true, cached: true, group: 'win' }
}

const newTab = (kind: PanelKind) => makeTab(newPanelId(kind))

function defaultLayout(): LayoutData {
  return {
    dockbox: {
      mode: 'horizontal',
      children: [
        { size: 550, tabs: [newTab('board')] },
        {
          size: 450,
          mode: 'vertical',
          children: [{ tabs: [newTab('notation')] }, { tabs: [newTab('engine'), newTab('tree')] }],
        },
      ],
    },
    floatbox: {
      mode: 'float',
      children: [{ tabs: [newTab('gamelist')], x: 120, y: 120, w: 420, h: 280, z: 1 }],
    },
  }
}

function PanelButtons({ panel, context }: { panel: PanelData; context: DockContext }) {
  const add = (kind: PanelKind, direction: DropDirection) => context.dockMove(newTab(kind), panel, direction)
  const floating = panel.parent?.mode === 'float'
  return (
    <div className="tool-bar">
      <KindPicker icon="+" title="New tab here" onPick={(k) => add(k, 'middle')} />
      {!floating && <KindPicker icon="◫" title="Split right" onPick={(k) => add(k, 'right')} />}
      {!floating && <KindPicker icon="⊟" title="Split below" onPick={(k) => add(k, 'bottom')} />}
    </div>
  )
}

const groups = {
  win: {
    floatable: true,
    maximizable: true,
    newWindow: true,
    panelExtra: (panel: PanelData, context: DockContext) => <PanelButtons panel={panel} context={context} />,
  },
}

export default function App() {
  const [dock, setDock] = useState<DockLayout | null>(null)
  // rc-dock has no active-panel API; track the last tab the user touched
  const [activeTabId, setActiveTabId] = useState<string | undefined>(undefined)
  const [jsonDialog, showJson] = useJsonDialog()
  const [initial] = useState(defaultLayout)

  useEffect(() => {
    const saved = loadSaved<LayoutBase>(STORAGE)
    if (!dock || !saved) return
    try {
      dock.loadLayout(saved)
    } catch {
      clearSaved(STORAGE)
    }
  }, [dock])

  const menus = useMemo<Menu[]>(() => {
    const d = () => dock!
    const activePanel = () => {
      const t = activeTabId && (d().find(activeTabId) as TabData | undefined)
      return t ? t.parent : undefined
    }
    const kindItems = (direction: DropDirection, shortcutPrefix?: string): MenuEntry[] =>
      KINDS.map((k, i) => ({
        label: k.label,
        shortcut: shortcutPrefix ? `${shortcutPrefix}+${i + 1}` : undefined,
        action: () => {
          const tab = newTab(k.kind)
          const panel = activePanel()
          if (panel) d().dockMove(tab, panel, direction)
          else d().dockMove(tab, d().getLayout().dockbox, direction === 'middle' ? 'right' : direction)
          setActiveTabId(tab.id)
        },
      }))
    return [
      {
        title: 'Layout',
        items: [
          { label: 'Show JSON', action: () => showJson(d().saveLayout()) },
          { label: 'Reset to default', action: () => d().loadLayout(defaultLayout()) },
        ],
      },
      {
        title: 'Window',
        items: [
          { label: 'New tab in active panel', submenu: kindItems('middle', 'Alt') },
          { label: 'Split active right', submenu: kindItems('right', 'Alt+Shift') },
          { label: 'Split active below', submenu: kindItems('bottom') },
          {
            label: 'New floating window',
            submenu: KINDS.map((k) => ({
              label: k.label,
              action: () => d().dockMove(newTab(k.kind), null, 'float', { left: 100, top: 100, width: 400, height: 300 }),
            })),
          },
          'separator',
          {
            label: 'Float active panel',
            action: () => {
              const p = activePanel()
              if (p) d().dockMove(p, null, 'float')
            },
          },
          {
            label: 'Pop out active panel to a browser window',
            action: () => {
              const p = activePanel()
              if (p) d().dockMove(p, null, 'new-window')
            },
          },
          {
            label: 'Maximize / restore active panel',
            shortcut: 'Alt+M',
            action: () => {
              const p = activePanel()
              if (p) d().dockMove(p, null, 'maximize')
            },
          },
          {
            label: 'Close active tab',
            shortcut: 'Alt+W',
            action: () => {
              const t = activeTabId && (d().find(activeTabId) as TabData | undefined)
              if (t) d().dockMove(t, null, 'remove')
            },
          },
        ],
      },
    ]
  }, [dock, activeTabId, showJson])

  return (
    <DemoShell
      current="rc-dock"
      menus={menus}
      dialog={jsonDialog}
      hint={
        <>
          Drag tabs or a panel's empty header area onto any edge or centre; drop outside a target to float. Floating
          windows overlap and can be docked back. Panel buttons: + new tab, ◫/⊟ split, then the built-in maximize and
          new-window (pop-out). No built-in context menu. The layout is saved to localStorage.
        </>
      }
    >
      <DockLayout
        ref={setDock}
        defaultLayout={initial}
        groups={groups}
        loadTab={(tab) => makeTab(tab.id!)}
        onLayoutChange={(_layout, currentTabId) => {
          if (currentTabId) setActiveTabId(currentTabId)
          // The layout passed in holds live React elements; save the serialisable form once applied
          setTimeout(() => dock && save(STORAGE, dock.saveLayout()))
        }}
        style={{ position: 'absolute', inset: 0 }}
      />
    </DemoShell>
  )
}
