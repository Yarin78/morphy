import { useEffect } from 'react';
import { useBoardSettings } from './boardSettings';

/** The settings of how boards look; they apply to every board, as soon as they're changed. */
export function BoardSettingsDialog({ onClose }: { onClose: () => void }) {
  const [settings, setSettings] = useBoardSettings();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" role="dialog" aria-label="Board settings" onClick={(e) => e.stopPropagation()}>
        <h2>Board settings</h2>
        <label className="dialog-check">
          <input
            type="checkbox"
            checked={settings.coordinates}
            onChange={(e) => setSettings({ ...settings, coordinates: e.target.checked })}
          />
          Show coordinates
        </label>
        <label className="dialog-check">
          <input
            type="checkbox"
            checked={settings.animation}
            onChange={(e) => setSettings({ ...settings, animation: e.target.checked })}
          />
          Animate moves
        </label>
        <p className="dialog-note">These apply to every board.</p>
        <div className="dialog-buttons">
          <button onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}
