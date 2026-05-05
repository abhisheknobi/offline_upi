import { useState, useEffect } from 'react'
import { fetchPublicKey, submitTransaction, fetchAccounts } from '../utils/api'
import { importRSAPublicKey, buildTransactionPacket } from '../utils/crypto'

const STEPS = [
  { id: 1, label: 'Fetch RSA Public Key', icon: '🔑' },
  { id: 2, label: 'Build Payload JSON', icon: '📝' },
  { id: 3, label: 'AES-256-CBC Encrypt', icon: '🔒' },
  { id: 4, label: 'RSA-OAEP Encrypt Key', icon: '🛡️' },
  { id: 5, label: 'Send via Mesh → Bank', icon: '📡' },
]

export default function SendTransaction() {
  const [accounts, setAccounts] = useState([])
  const [form, setForm] = useState({
    senderUpiId: 'alice@okbank', receiverUpiId: 'bob@ybl',
    amount: '500', pin: '1234', note: 'Payment via Bluetooth Mesh',
  })
  const [step, setStep] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [sending, setSending] = useState(false)

  useEffect(() => { fetchAccounts().then(setAccounts).catch(() => {}) }, [])

  const sleep = (ms) => new Promise(r => setTimeout(r, ms))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSending(true); setResult(null); setError(null); setStep(0)
    try {
      setStep(1); await sleep(400)
      const keyData = await fetchPublicKey()
      const rsaPublicKey = await importRSAPublicKey(keyData.publicKey)
      setStep(2); await sleep(300)
      setStep(3); await sleep(500)
      setStep(4); await sleep(400)
      const packet = await buildTransactionPacket({
        senderUpiId: form.senderUpiId, receiverUpiId: form.receiverUpiId,
        amountInPaise: Math.round(parseFloat(form.amount) * 100),
        pin: form.pin, note: form.note, rsaPublicKey,
      })
      setStep(5); await sleep(300)
      const res = await submitTransaction(packet)
      setStep(6)
      setResult(res)
    } catch (err) {
      setError(err.message || 'Failed to send transaction')
    } finally { setSending(false) }
  }

  const update = (f) => (e) => setForm({ ...form, [f]: e.target.value })
  const inputCls = 'w-full bg-white/5 border border-white/10 rounded-xl px-4 py-2.5 text-white focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-colors'

  return (
    <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
      <div className="lg:col-span-3 space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Send Transaction</h1>
          <p className="text-gray-400">Create an encrypted UPI payment through the Bluetooth Mesh network.</p>
        </div>
        <form onSubmit={handleSubmit} className="glass rounded-2xl p-6 space-y-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-gray-400 text-sm font-medium mb-1.5">Sender UPI</label>
              <select value={form.senderUpiId} onChange={update('senderUpiId')} className={inputCls}>
                {accounts.map(a => <option key={a.upiId} value={a.upiId} className="bg-gray-900">{a.upiId} — {a.ownerName}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-gray-400 text-sm font-medium mb-1.5">Receiver UPI</label>
              <select value={form.receiverUpiId} onChange={update('receiverUpiId')} className={inputCls}>
                {accounts.map(a => <option key={a.upiId} value={a.upiId} className="bg-gray-900">{a.upiId} — {a.ownerName}</option>)}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-gray-400 text-sm font-medium mb-1.5">Amount (₹)</label>
              <input type="number" step="0.01" min="0.01" value={form.amount} onChange={update('amount')} className={inputCls} placeholder="500.00" />
            </div>
            <div>
              <label className="block text-gray-400 text-sm font-medium mb-1.5">PIN</label>
              <input type="password" maxLength={4} value={form.pin} onChange={update('pin')} className={`${inputCls} tracking-[0.3em]`} placeholder="••••" />
            </div>
          </div>
          <div>
            <label className="block text-gray-400 text-sm font-medium mb-1.5">Note</label>
            <input type="text" value={form.note} onChange={update('note')} className={inputCls} placeholder="Coffee payment" />
          </div>
          <button type="submit" disabled={sending}
            className="w-full py-3 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-500 hover:to-violet-500 disabled:opacity-50 text-white rounded-xl font-semibold text-sm transition-all duration-300 shadow-lg shadow-blue-500/20">
            {sending ? 'Encrypting & Sending...' : '🔒 Encrypt & Send via Mesh'}
          </button>
        </form>
        {result && (
          <div className={`rounded-2xl p-6 border ${result.status === 'SUCCESS' ? 'bg-emerald-500/10 border-emerald-500/30' : 'bg-red-500/10 border-red-500/30'}`}>
            <div className="flex items-center gap-3 mb-3">
              <span className="text-2xl">{result.status === 'SUCCESS' ? '✅' : '❌'}</span>
              <h3 className="text-lg font-bold text-white">{result.status}</h3>
            </div>
            <p className="text-gray-300 text-sm mb-2">{result.message}</p>
            {result.referenceNumber && <p className="text-gray-400 text-xs font-mono">Ref: {result.referenceNumber}</p>}
            {result.senderBalanceInPaise != null && <p className="text-gray-400 text-xs mt-1">New Balance: ₹{(result.senderBalanceInPaise / 100).toFixed(2)}</p>}
          </div>
        )}
        {error && <div className="rounded-2xl p-6 bg-red-500/10 border border-red-500/30"><p className="text-red-400">❌ {error}</p></div>}
      </div>
      <div className="lg:col-span-2">
        <h2 className="text-lg font-semibold text-white mb-4">Encryption Pipeline</h2>
        <div className="glass rounded-2xl p-6 space-y-4">
          {STEPS.map(s => {
            const isActive = step === s.id, isDone = step > s.id
            return (
              <div key={s.id} className={`flex items-center gap-4 p-3 rounded-xl transition-all duration-500 ${isActive ? 'bg-blue-500/15 border border-blue-500/30' : isDone ? 'bg-emerald-500/10 border border-emerald-500/20' : 'border border-transparent opacity-50'}`}>
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center text-sm font-bold shrink-0 ${isDone ? 'bg-emerald-500/20 text-emerald-400' : isActive ? 'bg-blue-500/20 text-blue-400 animate-pulse' : 'bg-white/5 text-gray-500'}`}>
                  {isDone ? '✓' : s.id}
                </div>
                <div>
                  <p className={`text-sm font-medium ${isDone || isActive ? 'text-white' : 'text-gray-500'}`}>{s.icon} {s.label}</p>
                  {isActive && <p className="text-xs text-blue-400 mt-0.5">Processing...</p>}
                </div>
              </div>
            )
          })}
        </div>
        <div className="glass rounded-2xl p-6 mt-4">
          <h3 className="text-sm font-semibold text-white mb-3">🔐 Security Info</h3>
          <div className="space-y-2 text-xs text-gray-400">
            <p>• Payload encrypted with <span className="text-blue-400">AES-256-CBC</span></p>
            <p>• AES key encrypted with <span className="text-violet-400">RSA-2048-OAEP</span></p>
            <p>• PIN hashed with <span className="text-emerald-400">SHA-256</span></p>
            <p>• TTL set to 3 hours for replay prevention</p>
            <p>• Transaction ID (UUID) for idempotency</p>
          </div>
        </div>
      </div>
    </div>
  )
}
