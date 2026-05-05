const API_BASE = '/api';

export async function fetchHealth() {
  const res = await fetch(`${API_BASE}/health`);
  return res.json();
}

export async function fetchStats() {
  const res = await fetch(`${API_BASE}/admin/stats`);
  return res.json();
}

export async function fetchAccounts() {
  const res = await fetch(`${API_BASE}/admin/accounts`);
  return res.json();
}

export async function fetchPublicKey() {
  const res = await fetch(`${API_BASE}/keys/public`);
  return res.json();
}

export async function submitTransaction(packet) {
  const res = await fetch(`${API_BASE}/transaction/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(packet),
  });
  return res.json();
}

export async function fetchTransactionStatus(id) {
  const res = await fetch(`${API_BASE}/transaction/${id}`);
  return res.json();
}

export async function fetchTransactions() {
  const res = await fetch(`${API_BASE}/admin/transactions`);
  return res.json();
}
