import { DatabaseSearch } from '../database/DatabaseSearch';
import './App.css';

function App() {
  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>Search Tester</h1>
          <p className="subtitle">Debug the morphy-service search API</p>
        </div>
      </header>
      <DatabaseSearch />
    </div>
  );
}

export default App;
