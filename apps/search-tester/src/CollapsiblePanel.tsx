interface CollapsiblePanelProps {
  title: string;
  onToggle: (visible: boolean) => void;
  hideLabel?: string;
  panelClass?: string;
  children: React.ReactNode;
}

export function CollapsiblePanel({
  title,
  onToggle,
  hideLabel = 'Hide',
  panelClass = '',
  children,
}: CollapsiblePanelProps) {
  return (
    <section className={`panel ${panelClass}`.trim()}>
      <h2>
        {title}
        <button
          className="panel-toggle"
          onClick={() => onToggle(false)}
          aria-label={`${hideLabel} pane`}
        >
          −
        </button>
      </h2>
      {children}
    </section>
  );
}

interface ShowPanelButtonProps {
  label: string;
  onClick: () => void;
}

export function ShowPanelButton({ label, onClick }: ShowPanelButtonProps) {
  return (
    <button type="button" className="show-panel-btn" onClick={onClick}>
      {label}
    </button>
  );
}
