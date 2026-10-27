/**
 * Diagnóstico de solo lectura: todo lo que dejó el POS (y ComandaYa) antes de arrancar en serio.
 * Cuenta por colección, sucursal y rango de fechas. No escribe nada.
 *
 * Uso (desde functions/): node tools/inspect-pos-footprint.js [--company=<id>]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY = companyArg ? companyArg.split('=')[1] : 'tiO54slG1n9sk6k2ePCD';
const base = db.collection('companies').doc(COMPANY);

const ms = (v) => {
  if (v == null) return 0;
  if (typeof v === 'number') return v;
  if (v.toMillis) return v.toMillis();
  return Number(v) || 0;
};
const day = (v) => (ms(v) ? new Date(ms(v)).toISOString().slice(0, 10) : '—');

async function summarize(name, dateField, extra) {
  const docs = (await base.collection(name).get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  const byBranch = {};
  for (const d of docs) {
    const k = d.branchName || d.branchId || '(sin sucursal)';
    byBranch[k] = byBranch[k] || { n: 0, min: Infinity, max: 0 };
    const t = ms(d[dateField]);
    byBranch[k].n++;
    if (t) { byBranch[k].min = Math.min(byBranch[k].min, t); byBranch[k].max = Math.max(byBranch[k].max, t); }
  }
  console.log(`\n${name}: ${docs.length}`);
  for (const [k, v] of Object.entries(byBranch)) {
    console.log(`   ${k}: ${v.n}  (${v.min === Infinity ? '—' : day(v.min)} → ${day(v.max)})`);
  }
  if (extra) extra(docs);
  return docs;
}

(async () => {
  const branches = (await base.collection('branches').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  console.log('Sucursales:');
  branches.forEach((b) => console.log(`   ${b.name} (${b.id}) active=${b.active}`));

  const registers = (await base.collection('cashRegisters').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  console.log('\nCajas:');
  registers.forEach((r) => console.log(`   ${r.name} (${r.id}) suc=${r.branchName} active=${r.active} deleted=${r.deleted}`));

  await summarize('sales', 'createdAt', (docs) => {
    const bySource = {};
    for (const s of docs) {
      const k = `${s.source || s.channel || 'POS'} / ${s.status}`;
      bySource[k] = (bySource[k] || 0) + 1;
    }
    console.log('   por origen/estado:', JSON.stringify(bySource));
    const fel = docs.filter((s) => s.felUuid || s.dteId || s.felStatus);
    console.log(`   con FEL: ${fel.length}`, JSON.stringify(fel.slice(0, 5).map((s) => ({ id: s.id, felStatus: s.felStatus, uuid: s.felUuid }))));
    const total = docs.reduce((a, s) => a + (Number(s.total) || 0), 0);
    console.log(`   total vendido: Q ${total.toFixed(2)}`);
  });
  await summarize('salesDaily', 'date');
  await summarize('cashSessions', 'openedAt', (docs) => {
    docs.sort((a, b) => ms(b.openedAt) - ms(a.openedAt)).slice(0, 15).forEach((s) =>
      console.log(`   ${s.registerName} ${day(s.openedAt)} ${s.status} abre=${s.openingBalance} esperado=${s.openingExpected} entrega=${s.carriedOutCash} dailyClose=${s.dailyCloseId || ''}`));
  });
  await summarize('cashTransfers', 'createdAt');
  await summarize('dailyCashCloses', 'closedAt', (docs) =>
    docs.forEach((c) => console.log(`   ${c.branchName} ${c.closeDate} status=${c.status} dejado=${c.leftForNextDay}`)));
  await summarize('cashAuditEvents', 'at');
  await summarize('spotCashChecks', 'checkedAt');
  await summarize('bankDeposits', 'createdAt');
  await summarize('heldCarts', 'createdAt');
  await summarize('breadCounts', 'createdAt');
  await summarize('counterRemovals', 'createdAt');
  await summarize('customerDeposits', 'createdAt');
  await summarize('customerOrders', 'createdAt');
  await summarize('creditAccounts', 'createdAt');
  await summarize('creditMovements', 'createdAt', (docs) => {
    const k = {};
    docs.forEach((m) => { const t = `${m.type}${m.saleId ? ' (venta)' : ''}`; k[t] = (k[t] || 0) + 1; });
    console.log('   tipos:', JSON.stringify(k));
  });
  await summarize('dtes', 'createdAt', (docs) => {
    const k = {};
    docs.forEach((d) => { const t = `${d.estado || d.status} ${d.ambiente || ''}`; k[t] = (k[t] || 0) + 1; });
    console.log('   estados:', JSON.stringify(k));
  });
  await summarize('stockTransfers', 'createdAt');

  const fm = (await base.collection('finishedMovements').get()).docs.map((d) => d.data());
  const k = {};
  fm.forEach((m) => { const t = `${m.type}/${m.referenceType || ''}`; k[t] = (k[t] || 0) + 1; });
  console.log(`\nfinishedMovements: ${fm.length}`, JSON.stringify(k));

  const counters = (await base.collection('counters').get()).docs;
  console.log('\ncounters:');
  counters.forEach((d) => console.log(`   ${d.id}: ${JSON.stringify(d.data())}`));
})().catch((e) => { console.error(e); process.exit(1); });
