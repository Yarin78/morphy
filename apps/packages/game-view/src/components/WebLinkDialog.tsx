import { useState } from 'react';
import type { Annotation } from '../model/annotations';
import { findAnnotation } from '../model/annotations';
import { withAnnotation } from '../model/specialAnnotations';
import { AnnotationDialog } from './AnnotationDialog';

interface WebLinkDialogProps {
  moveName: string;
  annotations: readonly Annotation[];
  onSave: (annotations: Annotation[]) => void;
  onCancel: () => void;
}

/** Whether text is a web address to link to: http or https, with a host. */
function isUrl(text: string): boolean {
  try {
    const url = new URL(text);
    return (url.protocol === 'http:' || url.protocol === 'https:') && !!url.hostname;
  } catch {
    return false;
  }
}

/**
 * A dialog to give a move a link to a web page: its address and the text shown for it, by default
 * the address. A move with several links has the first edited here.
 */
export const WebLinkDialog: React.FC<WebLinkDialogProps> = ({ moveName, annotations, onSave, onCancel }) => {
  const existing = findAnnotation(annotations, 'webLink');
  const [url, setUrl] = useState(existing?.url ?? '');
  const [text, setText] = useState(existing?.text ?? '');
  const urlError = url.trim() && !isUrl(url.trim()) ? 'Like https://example.com/page' : undefined;

  return (
    <AnnotationDialog
      title={`Web Link: ${moveName}`}
      onSubmit={() =>
        onSave(
          withAnnotation(
            annotations,
            'webLink',
            url.trim() ? { type: 'webLink', url: url.trim(), text: text.trim() || url.trim() } : null
          )
        )
      }
      onCancel={onCancel}
      onRemove={existing ? () => onSave(withAnnotation(annotations, 'webLink', null)) : undefined}
      invalid={!!urlError}
    >
      <div className="web-link-dialog-fields">
        <label className="game-info-field">
          <span className="game-info-label">Address</span>
          <input
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://"
            aria-invalid={urlError ? true : undefined}
            autoComplete="off"
            data-1p-ignore
            autoFocus
          />
          {urlError && <span className="game-info-error">{urlError}</span>}
        </label>
        <label className="game-info-field">
          <span className="game-info-label">Text</span>
          <input
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={url.trim() || 'The address'}
            autoComplete="off"
            data-1p-ignore
          />
        </label>
      </div>
    </AnnotationDialog>
  );
};
