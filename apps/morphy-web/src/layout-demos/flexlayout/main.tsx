import { createRoot } from 'react-dom/client'
import '../../index.css'
import App from './App.tsx'

// No StrictMode: its double mount would make every panel's "mounts" counter start at 2
createRoot(document.getElementById('root')!).render(<App />)
