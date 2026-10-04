import { useDocuments } from '../documentsStore';

export function HomePane() {
  const { dispatch } = useDocuments();
  return (
    <div className="pane-page">
      <figure className="home-portrait">
        <img src="/morphy-portrait.jpg" alt="Paul Morphy, seated by a chess board" />
        <figcaption>Paul Morphy (1837–1884), photographed in 1859</figcaption>
      </figure>
      <h1>Morphy</h1>
      <p className="pane-lead">Browse, search and edit chess databases.</p>
      <p>
        Morphy reads and writes ChessBase databases, both the classic format (<code>.cbh</code>) and the newer one
        (<code>.2cbh</code>), as well as plain PGN files. The databases it serves are configured in morphy-service.
      </p>
      <h2>Getting started</h2>
      <ul>
        <li>
          <a href="#" onClick={(e) => (e.preventDefault(), dispatch({ type: 'openSingleton', kind: 'databases' }))}>
            All Databases
          </a>{' '}
          lists the configured databases. Open one to search its games, players, tournaments and more.
        </li>
        <li>Click a game in a database to open it on a board, where you can play through it, annotate it and save it.</li>
        <li>
          <a href="#" onClick={(e) => (e.preventDefault(), dispatch({ type: 'openBoard' }))}>
            New Board
          </a>{' '}
          starts an empty board.
        </li>
      </ul>
      <h2>Working with documents</h2>
      <p>
        Every database and board you open is a document in the navigator on the left. Each document has its own
        arrangement of windows, kept when you switch between documents and when you reload the page. Collapse the
        navigator to icons with « or Alt+B.
      </p>
    </div>
  );
}
