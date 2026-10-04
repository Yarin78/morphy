import { useEffect, useMemo, useState } from 'react'
import {
  createBalancedTreeFromLeaves,
  getLeaves,
  getNodeAtPath,
  getParentPath,
  isTabsNode,
  Mosaic,
  type MosaicDirection,
  type MosaicNode,
  type MosaicPath,
  MosaicWindow,
  useMosaic,
} from 'react-mosaic-component'
import 'react-mosaic-component/react-mosaic-component.css'
import { DemoShell } from '../shared/DemoShell'
import { useJsonDialog } from '../shared/useJsonDialog'
import { DummyPanel } from '../shared/DummyPanel'
import { KindPicker } from '../shared/KindPicker'
import type { Menu } from '../shared/MenuBar'
import { KINDS, loadSaved, newPanelId, type PanelKind, save, titleOf } from '../shared/panels'

const STORAGE = 'mosaic'

function defaultTree(): MosaicNode<string> {
  return {
    type: 'split',
    direction: 'row',
    splitPercentages: [55, 45],
    children: [
      newPanelId('board'),
      {
        type: 'split',
        direction: 'column',
        children: [
          newPanelId('notation'),
          { type: 'tabs', tabs: [newPanelId('engine'), newPanelId('tree')], activeTabIndex: 0 },
          newPanelId('gamelist'),
        ],
      },
    ],
  }
}

// Splits this window in two, with a new window of the picked kind on the right or below.
// A window that is a tab in a tab group splits the whole group (groups can't hold splits).
function SplitPicker({ path, direction }: { path: MosaicPath; direction: MosaicDirection }) {
  const { mosaicActions } = useMosaic<string>()
  const split = (kind: PanelKind) => {
    const root = mosaicActions.getRoot()
    const parentPath = getParentPath(path)
    const target = path.length > 0 && isTabsNode(getNodeAtPath(root, parentPath)) ? parentPath : path
    const existing = getNodeAtPath(root, target)
    if (existing === null) return
    mosaicActions.replaceWith(target, { type: 'split', direction, children: [existing, newPanelId(kind)] })
  }
  return (
    <KindPicker
      icon={direction === 'row' ? '◫' : '⊟'}
      title={direction === 'row' ? 'Split right' : 'Split below'}
      onPick={split}
    />
  )
}

// The library's own ExpandButton/RemoveButton draw Blueprint icon-font glyphs, which are
// invisible without Blueprint's CSS
function WindowButton({ path, action }: { path: MosaicPath; action: 'expand' | 'remove' }) {
  const { mosaicActions } = useMosaic<string>()
  return (
    <button
      className="tool-button"
      title={action === 'expand' ? 'Expand' : 'Close'}
      onClick={() => (action === 'expand' ? mosaicActions.expand(path, 80) : mosaicActions.remove(path))}
    >
      {action === 'expand' ? '⤢' : '✕'}
    </button>
  )
}

export default function App() {
  const [tree, setTree] = useState<MosaicNode<string> | null>(() => loadSaved(STORAGE) ?? defaultTree())
  const [jsonDialog, showJson] = useJsonDialog()
  useEffect(() => save(STORAGE, tree), [tree])

  const menus = useMemo<Menu[]>(
    () => [
      {
        title: 'Layout',
        items: [
          { label: 'Show JSON', action: () => showJson(tree) },
          { label: 'Reset to default', action: () => setTree(defaultTree()) },
          {
            label: 'Rebalance (drops tab groups)',
            action: () => setTree((t) => createBalancedTreeFromLeaves(getLeaves(t))),
          },
          { label: 'Clear all', action: () => setTree(null) },
        ],
      },
      {
        title: 'Window',
        items: [
          {
            // react-mosaic has no notion of an active window, so new windows go at the edge
            label: 'New window at the right edge',
            submenu: KINDS.map((k, i) => ({
              label: k.label,
              shortcut: `Alt+${i + 1}`,
              action: () =>
                setTree((t) => (t === null ? newPanelId(k.kind) : { type: 'split', direction: 'row', splitPercentages: [70, 30], children: [t, newPanelId(k.kind)] })),
            })),
          },
          {
            label: 'New window at the bottom edge',
            submenu: KINDS.map((k) => ({
              label: k.label,
              action: () =>
                setTree((t) => (t === null ? newPanelId(k.kind) : { type: 'split', direction: 'column', splitPercentages: [70, 30], children: [t, newPanelId(k.kind)] })),
            })),
          },
        ],
      },
    ],
    [tree, showJson],
  )

  return (
    <DemoShell
      current="react-mosaic"
      menus={menus}
      dialog={jsonDialog}
      hint={
        <>
          A pure tiling manager: drag a window by its title bar onto another window's edge (or the outer edges) to split.
          Title bar buttons: ◫/⊟ split, expand, remove. The Engine/Tree group shows tabs (+ adds a tab). No floating,
          no popouts, no context menu, no active-window concept. The layout is saved to localStorage.
        </>
      }
    >
      <Mosaic<string>
        value={tree}
        onChange={setTree}
        createNode={() => newPanelId(KINDS[Math.floor(Math.random() * KINDS.length)].kind)}
        renderTabTitle={({ tabKey }) => titleOf(tabKey)}
        renderTile={(id, path) => (
          <MosaicWindow<string>
            path={path}
            title={titleOf(id)}
            toolbarControls={[
              <SplitPicker key="r" path={path} direction="row" />,
              <SplitPicker key="c" path={path} direction="column" />,
              <WindowButton key="e" path={path} action="expand" />,
              <WindowButton key="x" path={path} action="remove" />,
            ]}
          >
            <DummyPanel id={id} />
          </MosaicWindow>
        )}
        zeroStateView={<div style={{ padding: 20 }}>Empty layout. Use the Window menu to add a window.</div>}
      />
    </DemoShell>
  )
}
