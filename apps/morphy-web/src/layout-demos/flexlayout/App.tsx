import { type MouseEvent, type ReactNode, useMemo } from 'react'
import {
  Actions,
  BorderNode,
  ContextMenuBuilder,
  DockLocation,
  type IJsonModel,
  type IJsonTabNode,
  Layout,
  Model,
  showPopupMenu,
  TabGroupNode,
  TabNode,
  TabSetNode,
  useUndo,
} from 'flexlayout-react'
import 'flexlayout-react/style/alpha_light.css'
import { DemoShell } from '../shared/DemoShell'
import { useJsonDialog } from '../shared/useJsonDialog'
import { DummyPanel } from '../shared/DummyPanel'
import { KindPicker } from '../shared/KindPicker'
import type { Menu, MenuEntry } from '../shared/MenuBar'
import { clearSaved, KINDS, kindOfId, loadSaved, newPanelId, type PanelKind, save, titleOf } from '../shared/panels'

const STORAGE = 'flexlayout'

function tab(kind: PanelKind): IJsonTabNode {
  const id = newPanelId(kind)
  return { type: 'tab', id, name: titleOf(id), component: 'dummy' }
}

function defaultJson(): IJsonModel {
  return {
    global: {
      tabEnableFloat: true,
      tabEnablePopout: true,
      tabEnableRename: true,
      tabSetEnableMaximize: true,
      tabSetEnableDeleteWhenEmpty: true,
    },
    borders: [
      { type: 'border', location: 'left', size: 320, children: [tab('gamelist'), tab('tree')] },
      { type: 'border', location: 'bottom', size: 200, children: [tab('engine')] },
    ],
    layout: {
      type: 'row',
      children: [
        { type: 'tabset', weight: 55, children: [tab('board')] },
        {
          type: 'row',
          weight: 45,
          children: [
            { type: 'tabset', weight: 60, children: [tab('notation')] },
            { type: 'tabset', weight: 40, children: [tab('engine'), tab('tree')] },
          ],
        },
      ],
    },
  }
}

function initialModel(): Model {
  const saved = loadSaved<IJsonModel>(STORAGE)
  try {
    if (saved) return Model.fromJson(saved)
  } catch {
    clearSaved(STORAGE)
  }
  return Model.fromJson(defaultJson())
}

const factory = (node: TabNode) => <DummyPanel id={node.getId()} />

export default function App() {
  const { model, setModel, undo, redo, canUndo, canRedo } = useUndo(initialModel)
  const [jsonDialog, showJson] = useJsonDialog()

  const menus = useMemo<Menu[]>(() => {
    const m = () => model!
    const activeTabset = () => m().getActiveTabset()
    const addTo = (kind: PanelKind, location: DockLocation) => {
      const target = activeTabset() ?? m().getRootRow()
      if (target) m().doAction(Actions.addNode(tab(kind), target.getId(), location, -1))
    }
    const kindItems = (location: DockLocation, shortcutPrefix?: string): MenuEntry[] =>
      KINDS.map((k, i) => ({
        label: k.label,
        shortcut: shortcutPrefix ? `${shortcutPrefix}+${i + 1}` : undefined,
        action: () => addTo(k.kind, location),
      }))
    const selected = () => activeTabset()?.getSelectedNode()
    return [
      {
        title: 'Edit',
        items: [
          { label: 'Undo layout change', shortcut: 'Alt+Z', disabled: !canUndo, action: undo },
          { label: 'Redo layout change', shortcut: 'Alt+Shift+Z', disabled: !canRedo, action: redo },
        ],
      },
      {
        title: 'Layout',
        items: [
          { label: 'Show JSON', action: () => showJson(m().toJson()) },
          { label: 'Reset to default', action: () => setModel(Model.fromJson(defaultJson())) },
        ],
      },
      {
        title: 'Window',
        items: [
          { label: 'New tab in active tabset', submenu: kindItems(DockLocation.CENTER, 'Alt') },
          { label: 'Split active right', submenu: kindItems(DockLocation.RIGHT, 'Alt+Shift') },
          { label: 'Split active below', submenu: kindItems(DockLocation.BOTTOM) },
          'separator',
          {
            label: 'Float active tab',
            action: () => {
              const t = selected()
              if (t) m().doAction(Actions.popoutTab(t.getId(), 'float'))
            },
          },
          {
            label: 'Pop out active tab to a browser window',
            action: () => {
              const t = selected()
              if (t) m().doAction(Actions.popoutTab(t.getId(), 'window'))
            },
          },
          {
            label: 'Maximize / restore active tabset',
            shortcut: 'Alt+M',
            action: () => {
              const ts = activeTabset()
              if (ts) m().doAction(Actions.maximizeToggle(ts.getId()))
            },
          },
          {
            label: 'Close active tab',
            shortcut: 'Alt+W',
            action: () => {
              const t = selected()
              if (t) m().doAction(Actions.deleteTab(t.getId()))
            },
          },
        ],
      },
    ]
  }, [model, canUndo, canRedo, undo, redo, setModel, showJson])

  if (!model) return null

  const onRenderTabSet = (node: TabSetNode | BorderNode, values: { buttons: ReactNode[] }) => {
    if (!(node instanceof TabSetNode)) return
    const add = (kind: PanelKind, location: DockLocation) =>
      model.doAction(Actions.addNode(tab(kind), node.getId(), location, -1))
    values.buttons.unshift(
      <KindPicker key="add" icon="+" title="New tab here" onPick={(k) => add(k, DockLocation.CENTER)} />,
      <KindPicker key="right" icon="◫" title="Split right" onPick={(k) => add(k, DockLocation.RIGHT)} />,
      <KindPicker key="below" icon="⊟" title="Split below" onPick={(k) => add(k, DockLocation.BOTTOM)} />,
    )
  }

  const onContextMenu = (node: TabNode | TabSetNode | BorderNode | TabGroupNode, event: MouseEvent) => {
    event.preventDefault()
    event.stopPropagation()
    const container = node.getLayoutRef()
    if (!container) return
    const builder = new ContextMenuBuilder(node).addStandard()
    if (node instanceof TabNode) {
      builder.addDivider().addCustom({
        key: 'duplicate',
        label: 'Duplicate to the right',
        onSelect: () => model.doAction(Actions.addNode(tab(kindOfId(node.getId())), node.getParent()!.getId(), DockLocation.RIGHT, -1)),
      })
    }
    showPopupMenu({ anchor: { x: event.clientX, y: event.clientY }, items: builder.build(), onClose: () => {}, container })
  }

  return (
    <DemoShell
      current="FlexLayout"
      menus={menus}
      dialog={jsonDialog}
      hint={
        <>
          Drag tabs or tabset headers onto any edge or centre, including the window edges. Left and bottom are collapsible
          border docks. Right-click a tab or tabset for the built-in context menu (float, pop out, rename, …). Double-click a
          header to maximize. Alt+Z undoes layout changes. The layout is saved to localStorage.
        </>
      }
    >
      <Layout
        model={model}
        factory={factory}
        onRenderTabSet={onRenderTabSet}
        onContextMenu={onContextMenu}
        onModelChange={(mdl) => save(STORAGE, mdl.toJson())}
      />
    </DemoShell>
  )
}
