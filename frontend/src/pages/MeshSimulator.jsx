import { useState, useRef, useCallback } from 'react'
import { fetchPublicKey, submitTransaction } from '../utils/api'
import { importRSAPublicKey, buildTransactionPacket } from '../utils/crypto'

const NODES = [
  { id: 'alice', label: "Alice's Phone", subtitle: 'No Internet', x: 80, y: 200, emoji: '📱', color: 'from-blue-500 to-blue-600' },
  { id: 'eve', label: 'Stranger A', subtitle: 'No Internet', x: 280, y: 120, emoji: '📱', color: 'from-gray-500 to-gray-600' },
  { id: 'bob', label: 'Stranger B', subtitle: 'No Internet', x: 480, y: 280, emoji: '📱', color: 'from-gray-500 to-gray-600' },
  { id: 'relay', label: 'Relay Node', subtitle: 'HAS Internet 🌐', x: 680, y: 180, emoji: '📡', color: 'from-emerald-500 to-emerald-600' },
  { id: 'bank', label: 'Bank Server', subtitle: 'Spring Boot', x: 880, y: 200, emoji: '🏦', color: 'from-violet-500 to-violet-600' },
]

const EDGES = [
  { from: 'alice', to: 'eve', label: 'BT' },
  { from: 'eve', to: 'bob', label: 'BT' },
  { from: 'bob', to: 'relay', label: 'BT' },
  { from: 'relay', to: 'bank', label: 'HTTPS' },
]

const SCENARIOS = [
  { id: 1, name: 'Normal Transaction', desc: 'Alice → Bob ₹500', icon: '✅', sender: 'alice@okbank', receiver: 'bob@ybl', amount: 500, pin: '1234', note: 'Coffee payment', ttl: 3 },
  { id: 2, name: 'Invalid PIN', desc: 'Wrong PIN rejected', icon: '🔐', sender: 'alice@okbank', receiver: 'merchant@hdfc', amount: 150, pin: '0000', note: 'Groceries', ttl: 3 },
  { id: 3, name: 'Expired TTL', desc: 'Replay attack blocked', icon: '⏰', sender: 'alice@okbank', receiver: 'bob@ybl', amount: 200, pin: '1234', note: 'Old payment', ttl: -1 },
  { id: 4, name: 'Insufficient Funds', desc: 'Balance too low', icon: '💸', sender: 'charlie@sbi', receiver: 'merchant@hdfc', amount: 1000000, pin: '4321', note: 'Big purchase', ttl: 3 },
]

export default function MeshSimulator() {
  const [activeEdge, setActiveEdge] = useState(-1)
  const [activeNode, setActiveNode] = useState('')
  const [running, setRunning] = useState(false)
  const [logs, setLogs] = useState([])
  const [result, setResult] = useState(null)
  const [packetPos, setPacketPos] = useState(null)
  const logsEndRef = useRef(null)

  const addLog = useCallback((msg, type = 'info') => {
    setLogs(prev => [...prev, { msg, type, time: new Date().toLocaleTimeString() }])
    setTimeout(() => logsEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
  }, [])

  const sleep = (ms) => new Promise(r => setTimeout(r, ms))

  const animateEdge = async (edgeIndex) => {
    const edge = EDGES[edgeIndex]
    setActiveEdge(edgeIndex)
    setActiveNode(edge.from)
    await sleep(600)
    setActiveNode(edge.to)
    await sleep(400)
  }

  const runScenario = async (scenario) => {
    if (running) return
    setRunning(true)
    setLogs([])
    setResult(null)
    setActiveEdge(-1)
    setActiveNode('')

    addLog(`🚀 Starting Scenario: ${scenario.name}`, 'header')
    addLog(`${scenario.sender} → ${scenario.receiver} | ₹${scenario.amount}`)
    await sleep(500)

    // Step 1: Fetch public key
    addLog('🔑 Fetching bank RSA-2048 public key...', 'step')
    setActiveNode('bank')
    await sleep(600)
    let rsaKey
    try {
      const keyData = await fetchPublicKey()
      rsaKey = await importRSAPublicKey(keyData.publicKey)
      addLog('✅ RSA public key received', 'success')
    } catch {
      addLog('❌ Failed to fetch public key. Is backend running?', 'error')
      setRunning(false); return
    }

    // Step 2: Encrypt on Alice's device
    addLog('🔒 Encrypting payload on Alice\'s device...', 'step')
    setActiveNode('alice')
    await sleep(400)
    addLog('   AES-256-CBC key generated')
    await sleep(300)
    addLog('   Payload encrypted with AES')
    await sleep(300)
    addLog('   AES key encrypted with RSA-OAEP')
    await sleep(200)
    addLog('✅ Encryption complete', 'success')

    let packet
    try {
      packet = await buildTransactionPacket({
        senderUpiId: scenario.sender,
        receiverUpiId: scenario.receiver,
        amountInPaise: Math.round(scenario.amount * 100),
        pin: scenario.pin,
        note: scenario.note,
        rsaPublicKey: rsaKey,
        ttlHours: scenario.ttl,
      })
    } catch {
      addLog('❌ Encryption failed', 'error')
      setRunning(false); return
    }

    // Step 3: Mesh gossip
    addLog('📡 Broadcasting via Bluetooth Mesh...', 'step')
    for (let i = 0; i < EDGES.length; i++) {
      const e = EDGES[i]
      addLog(`   Hop ${i + 1}: ${getNodeLabel(e.from)} → ${getNodeLabel(e.to)} (${e.label})`)
      await animateEdge(i)
    }
    addLog('✅ Packet reached bank server', 'success')

    // Step 4: Bank processes
    addLog('🏦 Bank processing transaction...', 'step')
    setActiveNode('bank')
    await sleep(400)
    addLog('   Decrypting AES key with RSA private key...')
    await sleep(300)
    addLog('   Decrypting payload with AES-256-CBC...')
    await sleep(300)
    addLog('   Validating TTL, PIN, balance...')
    await sleep(400)

    try {
      const res = await submitTransaction(packet)
      setResult(res)
      if (res.status === 'SUCCESS') {
        addLog(`✅ ${res.status}: ${res.message}`, 'success')
        if (res.referenceNumber) addLog(`   Ref: ${res.referenceNumber}`)
      } else {
        addLog(`❌ ${res.status}: ${res.message}`, 'error')
      }
    } catch {
      addLog('❌ Failed to reach bank server', 'error')
    }

    setActiveEdge(-1)
    setActiveNode('')
    setRunning(false)
    addLog('─── Scenario Complete ───', 'header')
  }

  const getNodeLabel = (id) => NODES.find(n => n.id === id)?.label || id

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-white mb-2">Mesh Network Simulator</h1>
        <p className="text-gray-400">Visualize Bluetooth Mesh gossip protocol with encrypted packet relay.</p>
      </div>

      {/* Mesh Visualization */}
      <div className="glass rounded-2xl p-4 overflow-x-auto">
        <svg viewBox="0 0 960 400" className="w-full min-w-[700px] h-auto">
          <defs>
            <filter id="glow">
              <feGaussianBlur stdDeviation="4" result="blur" />
              <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
            </filter>
          </defs>

          {/* Edges */}
          {EDGES.map((edge, i) => {
            const from = NODES.find(n => n.id === edge.from)
            const to = NODES.find(n => n.id === edge.to)
            const isActive = activeEdge === i
            return (
              <g key={i}>
                <line x1={from.x} y1={from.y} x2={to.x} y2={to.y}
                  stroke={isActive ? '#3b82f6' : '#1e293b'} strokeWidth={isActive ? 3 : 1.5}
                  strokeDasharray={isActive ? '8 4' : 'none'}
                  filter={isActive ? 'url(#glow)' : 'none'}
                  className={isActive ? 'animate-dash' : ''} />
                <text x={(from.x + to.x) / 2} y={(from.y + to.y) / 2 - 10}
                  textAnchor="middle" fill="#64748b" fontSize="11" fontWeight="500">
                  {edge.label}
                </text>
              </g>
            )
          })}

          {/* Nodes */}
          {NODES.map(node => {
            const isActive = activeNode === node.id
            return (
              <g key={node.id} className="transition-transform duration-300" style={{ transform: isActive ? 'scale(1.1)' : 'scale(1)', transformOrigin: `${node.x}px ${node.y}px` }}>
                <circle cx={node.x} cy={node.y} r={isActive ? 38 : 34}
                  fill={isActive ? 'rgba(59,130,246,0.15)' : 'rgba(255,255,255,0.04)'}
                  stroke={isActive ? '#3b82f6' : '#1e293b'} strokeWidth={isActive ? 2 : 1}
                  filter={isActive ? 'url(#glow)' : 'none'} />
                <text x={node.x} y={node.y + 5} textAnchor="middle" fontSize="22">{node.emoji}</text>
                <text x={node.x} y={node.y + 55} textAnchor="middle" fill="#e2e8f0" fontSize="12" fontWeight="600">{node.label}</text>
                <text x={node.x} y={node.y + 70} textAnchor="middle" fill="#64748b" fontSize="10">{node.subtitle}</text>
                {isActive && (
                  <circle cx={node.x} cy={node.y} r={42} fill="none" stroke="#3b82f6" strokeWidth="1" opacity="0.5">
                    <animate attributeName="r" from="38" to="50" dur="1s" repeatCount="indefinite" />
                    <animate attributeName="opacity" from="0.5" to="0" dur="1s" repeatCount="indefinite" />
                  </circle>
                )}
              </g>
            )
          })}
        </svg>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Scenarios */}
        <div>
          <h2 className="text-lg font-semibold text-white mb-3">Run Scenario</h2>
          <div className="space-y-2">
            {SCENARIOS.map(s => (
              <button key={s.id} onClick={() => runScenario(s)} disabled={running}
                className="w-full glass rounded-xl p-4 text-left glass-hover transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed hover:scale-[1.01] active:scale-[0.99]">
                <div className="flex items-center gap-3">
                  <span className="text-2xl">{s.icon}</span>
                  <div>
                    <p className="text-white font-medium text-sm">{s.name}</p>
                    <p className="text-gray-500 text-xs">{s.desc}</p>
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Logs */}
        <div>
          <h2 className="text-lg font-semibold text-white mb-3">Live Log</h2>
          <div className="glass rounded-xl p-4 h-80 overflow-y-auto font-mono text-xs space-y-1">
            {logs.length === 0 && <p className="text-gray-600">Select a scenario to begin...</p>}
            {logs.map((log, i) => (
              <p key={i} className={`${log.type === 'success' ? 'text-emerald-400' : log.type === 'error' ? 'text-red-400' : log.type === 'header' ? 'text-white font-bold mt-2' : log.type === 'step' ? 'text-blue-400' : 'text-gray-400'}`}>
                <span className="text-gray-600 mr-2">{log.time}</span>{log.msg}
              </p>
            ))}
            <div ref={logsEndRef} />
          </div>
        </div>
      </div>

      {/* Result */}
      {result && (
        <div className={`glass rounded-2xl p-6 border ${result.status === 'SUCCESS' ? 'border-emerald-500/30' : 'border-red-500/30'}`}>
          <div className="flex items-center gap-3">
            <span className="text-3xl">{result.status === 'SUCCESS' ? '✅' : '❌'}</span>
            <div>
              <h3 className="text-lg font-bold text-white">{result.status}</h3>
              <p className="text-gray-400 text-sm">{result.message}</p>
              {result.referenceNumber && <p className="text-gray-500 text-xs font-mono mt-1">Ref: {result.referenceNumber}</p>}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
