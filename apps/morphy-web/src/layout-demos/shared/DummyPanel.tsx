import { type CSSProperties, useEffect, useRef, useState } from 'react'
import { kindInfo, kindOfId } from './panels'

// How many times each panel id has been mounted. Moving a window must not remount it
// (a remount loses state, and for a real board means rebuilding chessground), so every
// panel shows this count: if it goes up after a drag, the framework remounted it.
const mounts = new Map<string, number>()

const MOVES =
  '1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 7. Bb3 d6 8. c3 O-O 9. h3 Nb8 10. d4 Nbd7 11. c4 c6 12. cxb5 axb5 13. Nc3 Bb7 14. Bg5 b4 15. Nb1 h6 16. Bh4 c5 17. dxe5 Nxe4 18. Bxe7 Qxe7 19. exd6 Qf6 20. Nbd2 Nxd6 21. Nc4 Nxc4 22. Bxc4 Nb6 23. Ne5 Rae8 24. Bxf7+ Rxf7 25. Nxf7 Rxe1+ 26. Qxe1 Kxf7 27. Qe3 Qg5 28. Qxg5 hxg5 29. b3 Ke6 30. a3 Kd6'

export function DummyPanel({ id }: { id: string }) {
  const kind = kindOfId(id)
  const info = kindInfo(kind)
  const [mountedAt] = useState(() => Date.now())
  // Counted in the initializer, which runs once per mount (the demos don't use StrictMode)
  const [mountCount] = useState(() => {
    const n = (mounts.get(id) ?? 0) + 1
    mounts.set(id, n)
    return n
  })
  const [clicks, setClicks] = useState(0)
  const [text, setText] = useState('')
  const [size, setSize] = useState({ w: 0, h: 0 })
  const [now, setNow] = useState(() => Date.now())
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const ro = new ResizeObserver(([entry]) => {
      const r = entry.contentRect
      setSize({ w: Math.round(r.width), h: Math.round(r.height) })
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  return (
    <div ref={ref} className="dummy-panel" style={{ '--kind': info.color } as CSSProperties}>
      <div className="dummy-head">
        <span className="dummy-chip">{info.label}</span>
        <code>{id}</code>
        <span className="dummy-size">
          {size.w}×{size.h}
        </span>
      </div>
      <div className="dummy-state">
        <span className={mountCount > 1 ? 'remounted' : ''} title="Times this panel id has been mounted">
          mounts: {mountCount}
        </span>
        <span title="Time since this instance was mounted">age: {Math.round((now - mountedAt) / 1000)}s</span>
        <button onClick={() => setClicks((c) => c + 1)}>clicks: {clicks}</button>
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="local state…" />
      </div>
      <div className="dummy-body">
        <KindBody kind={kind} />
      </div>
    </div>
  )
}

function KindBody({ kind }: { kind: string }) {
  switch (kind) {
    case 'board':
      return (
        <div className="dummy-board-wrap">
          <div className="dummy-board">
            {Array.from({ length: 64 }, (_, i) => (
              <div key={i} className={(Math.floor(i / 8) + i) % 2 ? 'dark' : 'light'} />
            ))}
          </div>
        </div>
      )
    case 'notation':
      return <p className="dummy-text">{MOVES}</p>
    case 'gamelist':
      return (
        <table className="dummy-table">
          <tbody>
            {Array.from({ length: 40 }, (_, i) => (
              <tr key={i}>
                <td>{i + 1}</td>
                <td>Player {String.fromCharCode(65 + (i % 26))}</td>
                <td>Player {String.fromCharCode(90 - (i % 26))}</td>
                <td>{['1-0', '0-1', '½-½'][i % 3]}</td>
                <td>{1990 + (i % 35)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )
    case 'engine':
      return (
        <pre className="dummy-text">
          {Array.from({ length: 12 }, (_, i) => `depth ${18 + i}  +0.${30 + i}  e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6`).join('\n')}
        </pre>
      )
    default:
      return (
        <table className="dummy-table">
          <tbody>
            {['e4', 'd4', 'Nf3', 'c4', 'g3', 'b3', 'f4'].map((m, i) => (
              <tr key={m}>
                <td>{m}</td>
                <td>{Math.round(48000 / (i + 1))}</td>
                <td>{54 - i}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      )
  }
}
