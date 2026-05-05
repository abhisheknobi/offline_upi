import { useState, useEffect } from 'react'
import { fetchHealth, fetchStats, fetchAccounts, fetchTransactions } from '../utils/api'

export default function Dashboard() {
  const [health, setHealth] = useState(null)
  const [stats, setStats] = useState(null)
  const [accounts, setAccounts] = useState([])
  const [transactions, setTransactions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadData = async () => {
    try {
      const [h, s, a, t] = await Promise.all([
        fetchHealth(), fetchStats(), fetchAccounts(), fetchTransactions()
      ])
      setHealth(h)
      setStats(s)
      setAccounts(a)
      setTransactions(t)
      setError(null)
    } catch (e) {
      setError('Cannot reach bank server. Make sure it is running on port 8080.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
    const interval = setInterval(loadData, 5000)
    return () => clearInterval(interval)
  }, [])

  if (loading) return <LoadingSkeleton />
  if (error) return <ErrorState message={error} onRetry={loadData} />

  return (
    <div className="space-y-8">
      {/* Hero */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-blue-600/20 via-violet-600/10 to-navy-900 p-8 border border-white/5">
        <div className="absolute top-0 right-0 w-64 h-64 bg-blue-500/10 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2" />
        <div className="relative">
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-2">
            Bank Server Dashboard
          </h1>
          <p className="text-gray-400 max-w-2xl">
            Real-time monitoring of the Offline UPI Bluetooth Mesh Payment System.
            Hybrid AES-256 + RSA-2048 encryption with PostgreSQL-backed idempotency.
          </p>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Server Status"
          value={health?.status || 'DOWN'}
          icon="🟢"
          color="emerald"
          subtitle="All systems operational"
        />
        <StatCard
          label="Uptime"
          value={formatUptime(health?.uptime)}
          icon="⏱️"
          color="blue"
          subtitle="Since last restart"
        />
        <StatCard
          label="Transactions"
          value={stats?.processedTransactions || 0}
          icon="📦"
          color="violet"
          subtitle="Total processed"
        />
        <StatCard
          label="Accounts"
          value={stats?.totalAccounts || 0}
          icon="👥"
          color="amber"
          subtitle="Registered users"
        />
      </div>

      {/* Accounts */}
      <section>
        <h2 className="text-xl font-semibold text-white mb-4 flex items-center gap-2">
          <span className="text-2xl">🏦</span> Account Balances
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {accounts.map(acc => (
            <div key={acc.upiId} className="glass rounded-2xl p-5 glass-hover transition-all duration-300 hover:scale-[1.02] cursor-default">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-violet-500 flex items-center justify-center text-white font-bold text-sm">
                  {acc.ownerName.charAt(0)}
                </div>
                <div>
                  <p className="text-white font-medium text-sm">{acc.ownerName}</p>
                  <p className="text-gray-500 text-xs font-mono">{acc.upiId}</p>
                </div>
              </div>
              <p className="text-2xl font-bold text-white animate-count-up">
                {acc.balanceFormatted}
              </p>
              <p className="text-gray-500 text-xs mt-1">
                {(acc.balanceInPaise).toLocaleString()} paise
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* Recent Transactions */}
      <section>
        <h2 className="text-xl font-semibold text-white mb-4 flex items-center gap-2">
          <span className="text-2xl">📋</span> Recent Transactions
        </h2>
        {transactions.length === 0 ? (
          <div className="glass rounded-2xl p-12 text-center">
            <p className="text-4xl mb-4">📭</p>
            <p className="text-gray-400">No transactions yet. Send one from the Send page!</p>
          </div>
        ) : (
          <div className="glass rounded-2xl overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-white/5 text-gray-400 text-xs uppercase tracking-wider">
                    <th className="text-left p-4">Transaction ID</th>
                    <th className="text-left p-4">From → To</th>
                    <th className="text-left p-4">Amount</th>
                    <th className="text-left p-4">Status</th>
                    <th className="text-left p-4">Time</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.slice(0, 10).map(tx => (
                    <tr key={tx.transactionId} className="border-b border-white/5 hover:bg-white/5 transition-colors">
                      <td className="p-4 font-mono text-xs text-gray-300">
                        {tx.transactionId?.substring(0, 8)}...
                      </td>
                      <td className="p-4 text-gray-300">
                        {tx.senderUpiId || '—'} → {tx.receiverUpiId || '—'}
                      </td>
                      <td className="p-4 text-white font-medium">
                        {tx.amountInPaise ? `₹${(tx.amountInPaise / 100).toFixed(2)}` : '—'}
                      </td>
                      <td className="p-4">
                        <StatusBadge status={tx.status} />
                      </td>
                      <td className="p-4 text-gray-400 text-xs">
                        {tx.processedAt ? new Date(tx.processedAt).toLocaleString() : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}

function StatCard({ label, value, icon, color, subtitle }) {
  const colorMap = {
    emerald: 'from-emerald-500/20 to-emerald-900/10 border-emerald-500/20',
    blue: 'from-blue-500/20 to-blue-900/10 border-blue-500/20',
    violet: 'from-violet-500/20 to-violet-900/10 border-violet-500/20',
    amber: 'from-amber-500/20 to-amber-900/10 border-amber-500/20',
  }
  return (
    <div className={`rounded-2xl p-5 bg-gradient-to-br ${colorMap[color]} border transition-all duration-300 hover:scale-[1.02]`}>
      <div className="flex items-center justify-between mb-3">
        <span className="text-gray-400 text-sm font-medium">{label}</span>
        <span className="text-xl">{icon}</span>
      </div>
      <p className="text-2xl font-bold text-white animate-count-up">{value}</p>
      <p className="text-gray-500 text-xs mt-1">{subtitle}</p>
    </div>
  )
}

function StatusBadge({ status }) {
  const config = {
    SUCCESS: { bg: 'bg-emerald-500/20 text-emerald-400', dot: 'bg-emerald-400' },
    DUPLICATE: { bg: 'bg-amber-500/20 text-amber-400', dot: 'bg-amber-400' },
    EXPIRED: { bg: 'bg-red-500/20 text-red-400', dot: 'bg-red-400' },
    INSUFFICIENT_FUNDS: { bg: 'bg-red-500/20 text-red-400', dot: 'bg-red-400' },
    INVALID_PIN: { bg: 'bg-red-500/20 text-red-400', dot: 'bg-red-400' },
    ACCOUNT_NOT_FOUND: { bg: 'bg-gray-500/20 text-gray-400', dot: 'bg-gray-400' },
    DECRYPTION_FAILED: { bg: 'bg-red-500/20 text-red-400', dot: 'bg-red-400' },
    PROCESSING: { bg: 'bg-blue-500/20 text-blue-400', dot: 'bg-blue-400' },
  }
  const c = config[status] || config.PROCESSING
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${c.bg}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${c.dot}`} />
      {status}
    </span>
  )
}

function LoadingSkeleton() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="h-40 bg-white/5 rounded-3xl" />
      <div className="grid grid-cols-4 gap-4">
        {[1,2,3,4].map(i => <div key={i} className="h-28 bg-white/5 rounded-2xl" />)}
      </div>
      <div className="h-60 bg-white/5 rounded-2xl" />
    </div>
  )
}

function ErrorState({ message, onRetry }) {
  return (
    <div className="glass rounded-3xl p-12 text-center max-w-lg mx-auto mt-20">
      <p className="text-5xl mb-4">⚠️</p>
      <h2 className="text-xl font-bold text-white mb-2">Connection Error</h2>
      <p className="text-gray-400 mb-6">{message}</p>
      <button onClick={onRetry} className="px-6 py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-medium transition-colors">
        Retry
      </button>
    </div>
  )
}

function formatUptime(duration) {
  if (!duration) return '—'
  const match = duration.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?/)
  if (!match) return duration
  const h = match[1] || '0'
  const m = match[2] || '0'
  const s = Math.floor(parseFloat(match[3] || '0'))
  return `${h}h ${m}m ${s}s`
}
