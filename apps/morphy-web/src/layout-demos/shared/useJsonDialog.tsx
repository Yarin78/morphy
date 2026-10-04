import { type ReactNode, useState } from 'react'

/** A modal showing the serialised layout. Returns the element to render and a function to open it. */
export function useJsonDialog(): [ReactNode, (value: unknown) => void] {
  const [json, setJson] = useState<string | null>(null)
  const element =
    json === null ? null : (
      <div className="json-backdrop" onClick={() => setJson(null)}>
        <div className="json-dialog" onClick={(e) => e.stopPropagation()}>
          <div className="json-title">
            Serialised layout ({json.length} chars)
            <button onClick={() => setJson(null)}>Close</button>
          </div>
          <pre>{json}</pre>
        </div>
      </div>
    )
  return [element, (value) => setJson(JSON.stringify(value, null, 2))]
}
