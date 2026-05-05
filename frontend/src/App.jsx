import { Routes, Route } from 'react-router-dom'
import Navbar from './components/Navbar'
import Dashboard from './pages/Dashboard'
import SendTransaction from './pages/SendTransaction'
import TransactionHistory from './pages/TransactionHistory'
import MeshSimulator from './pages/MeshSimulator'

export default function App() {
  return (
    <div className="min-h-screen bg-navy-950">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/send" element={<SendTransaction />} />
          <Route path="/history" element={<TransactionHistory />} />
          <Route path="/simulator" element={<MeshSimulator />} />
        </Routes>
      </main>
    </div>
  )
}
