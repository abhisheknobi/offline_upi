import { useState, useEffect } from 'react'
import { fetchTransactions } from '../utils/api'

export default function TransactionHistory() {
  const [txns, setTxns] = useState([])
  const [filter, setFilter] = useState('ALL')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchTransactions().then(data => { setTxns(data); setLoading(false) }).catch(() => setLoading(false))
    const id = setInterval(() => fetchTransactions().then(setTxns).catch(() => {}), 5000)
    return () => clearInterval(id)
  }, [])

  const statuses = ['ALL', 'SUCCESS', 'DUPLICATE', 'EXPIRED', 'INSUFFICIENT_FUNDS', 'INVALID_PIN', 'ACCOUNT_NOT_FOUND', 'DECRYPTION_FAILED']
  const filtered = filter === 'ALL' ? txns : txns.filter(t => t.status === filter)

  const statusColor = (s) => {
    const m = { SUCCESS: 'text-emerald-400 bg-emerald-500/20', DUPLICATE: 'text-amber-400 bg-amber-500/20', EXPIRED: 'text-red-400 bg-red-500/20', INSUFFICIENT_FUNDS: 'text-red-400 bg-red-500/20', INVALID_PIN: 'text-red-400 bg-red-500/20', ACCOUNT_NOT_FOUND: 'text-gray-400 bg-gray-500/20', DECRYPTION_FAILED: 'text-red-400 bg-red-500/20', PROCESSING: 'text-blue-400 bg-blue-500/20' }
    return m[s] || m.PROCESSING
  }

  if (loading) return <div className="animate-pulse space-y-4">{[1,2,3].map(i => <div key={i} className="h-20 bg-white/5 rounded-2xl" />)}</div>

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-white mb-2">Transaction History</h1>
        <p className="text-gray-400">All processed transactions with real-time updates every 5s.</p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-2">
        {statuses.map(s => (
          <button key={s} onClick={() => setFilter(s)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${filter === s ? 'bg-blue-600 text-white' : 'bg-white/5 text-gray-400 hover:bg-white/10 hover:text-white'}`}>
            {s.replace(/_/g, ' ')}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <div className="glass rounded-2xl p-12 text-center">
          <p className="text-4xl mb-4">📭</p>
          <p className="text-gray-400">{filter === 'ALL' ? 'No transactions yet.' : `No ${filter} transactions.`}</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map(tx => (
            <div key={tx.transactionId} className="glass rounded-2xl p-5 glass-hover transition-all duration-300">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-3 mb-2">
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${statusColor(tx.status)}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${tx.status === 'SUCCESS' ? 'bg-emerald-400' : tx.status === 'DUPLICATE' ? 'bg-amber-400' : 'bg-red-400'}`} />
                      {tx.status}
                    </span>
                    {tx.referenceNumber && <span className="text-xs text-gray-500 font-mono">{tx.referenceNumber}</span>}
                  </div>
                  <p className="text-sm text-gray-300 truncate">
                    {tx.senderUpiId || '—'} → {tx.receiverUpiId || '—'}
                    {tx.note && <span className="text-gray-500 ml-2">• {tx.note}</span>}
                  </p>
                  <p className="text-xs text-gray-500 font-mono mt-1">ID: {tx.transactionId}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="text-xl font-bold text-white">
                    {tx.amountInPaise ? `₹${(tx.amountInPaise / 100).toFixed(2)}` : '—'}
                  </p>
                  <p className="text-xs text-gray-500">
                    {tx.processedAt ? new Date(tx.processedAt).toLocaleString() : '—'}
                  </p>
                  {tx.hopCount != null && <p className="text-xs text-gray-600">{tx.hopCount} hops</p>}
                </div>
              </div>
              {tx.message && <p className="text-xs text-gray-500 mt-2 pt-2 border-t border-white/5">{tx.message}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
