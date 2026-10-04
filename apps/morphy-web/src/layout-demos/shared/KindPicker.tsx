import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { KINDS, type PanelKind } from './panels'

/**
 * A small toolbar button that drops down the list of window kinds, for the "+" (new
 * tab) and split buttons in the window headers. The list is portalled to the body of
 * the button's own document, so it is not clipped by the header and also works in a
 * popped-out browser window.
 */
export function KindPicker({ icon, title, onPick }: { icon: string; title: string; onPick: (kind: PanelKind) => void }) {
  const [anchor, setAnchor] = useState<{ rect: DOMRect; body: HTMLElement } | null>(null)
  const button = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!anchor) return
    const doc = anchor.body.ownerDocument
    const onDown = (e: PointerEvent) => {
      if (!list.current?.contains(e.target as Node) && !button.current?.contains(e.target as Node)) setAnchor(null)
    }
    doc.addEventListener('pointerdown', onDown)
    return () => doc.removeEventListener('pointerdown', onDown)
  }, [anchor])

  return (
    <>
      <button
        ref={button}
        className="tool-button"
        title={title}
        // Keep the header's own drag/activate handlers from seeing this click
        onPointerDown={(e) => e.stopPropagation()}
        onMouseDown={(e) => e.stopPropagation()}
        onClick={(e) => {
          e.stopPropagation()
          setAnchor(anchor ? null : { rect: e.currentTarget.getBoundingClientRect(), body: e.currentTarget.ownerDocument.body })
        }}
      >
        {icon}
      </button>
      {anchor &&
        createPortal(
          <div
            ref={list}
            className="menu-list kind-picker"
            style={{ position: 'fixed', top: anchor.rect.bottom + 2, left: Math.max(4, anchor.rect.right - 160) }}
          >
            <div className="kind-picker-title">{title}</div>
            {KINDS.map((k) => (
              <div key={k.kind} className="menu-row">
                <button
                  onClick={() => {
                    setAnchor(null)
                    onPick(k.kind)
                  }}
                >
                  <span className="menu-check" style={{ color: k.color }}>
                    ■
                  </span>
                  <span className="menu-label">{k.label}</span>
                </button>
              </div>
            ))}
          </div>,
          anchor.body,
        )}
    </>
  )
}
