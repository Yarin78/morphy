import type { ReactNode } from 'react'
import { type Menu, MenuBar } from './MenuBar'
import './demo.css'

const DEMOS = [
  { href: '/layout-dockview.html', label: 'Dockview' },
  { href: '/layout-flexlayout.html', label: 'FlexLayout' },
  { href: '/layout-mosaic.html', label: 'react-mosaic' },
  { href: '/layout-rcdock.html', label: 'rc-dock' },
]

/** The frame shared by every layout demo: menu bar, demo switcher, the layout, a hint line. */
export function DemoShell({
  current,
  menus,
  hint,
  dialog,
  children,
}: {
  current: string
  menus: Menu[]
  hint: ReactNode
  dialog?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="demo-app">
      <header className="demo-top">
        <span className="demo-brand">Morphy</span>
        <MenuBar menus={menus} />
        <nav className="demo-switch">
          {DEMOS.map((d) => (
            <a key={d.href} href={d.href} className={d.label === current ? 'current' : ''}>
              {d.label}
            </a>
          ))}
        </nav>
      </header>
      <main className="demo-main">{children}</main>
      <footer className="demo-hint">{hint}</footer>
      {dialog}
    </div>
  )
}
