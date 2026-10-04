// Panes the board document can show that aren't built yet

function Placeholder({ title, text }: { title: string; text: string }) {
  return (
    <div className="pane-placeholder">
      <h2>{title}</h2>
      <p>{text}</p>
    </div>
  );
}

export function TreePane() {
  return (
    <Placeholder title="Opening tree" text="The moves played from this position in a database, with their results, will appear here." />
  );
}
