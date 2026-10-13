/** Diagnóstico solo lectura: recepción de despachos por sucursal (quién llegó/recibió). */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const COMPANY = 'tiO54slG1n9sk6k2ePCD';
const base = db.collection('companies').doc(COMPANY);
const d = (ms) => ms ? new Date(typeof ms === 'number' ? ms : ms.toMillis()).toISOString().slice(0,16) : '—';
(async () => {
  const branches = (await base.collection('branches').get()).docs.map((x) => ({ id: x.id, ...x.data() }));
  console.log('SUCURSALES'); branches.forEach((b) => console.log(`  ${b.id} | ${b.name} | type=${b.type||b.branchType||''} active=${b.isActive ?? b.active}`));
  const users = (await db.collection('users').where('orgId', '==', COMPANY).get()).docs.map((x) => ({ id: x.id, ...x.data() }));
  const bn = Object.fromEntries(branches.map((b) => [b.id, b.name]));
  console.log('\nUSUARIOS');
  users.forEach((u) => console.log(`  ${u.id} | ${u.email} | ${u.displayName} | ${u.role} | device=${u.isDeviceAccount} | branches=${(u.branchIds||[]).map((b)=>bn[b]||'?'+b).join(',')||'(todas)'} | RECEIVE_DISPATCH=${(u.permissions||[]).includes('RECEIVE_DISPATCH')}`));
  const since = Date.now() - 12 * 86400000;
  const notes = (await base.collection('dispatchNotes').get()).docs.map((x) => ({ id: x.id, ...x.data() }))
    .filter((n) => { const t = typeof n.createdAt === 'number' ? n.createdAt : n.createdAt?.toMillis?.() || 0; return t > since; });
  const uname = Object.fromEntries(users.map((u) => [u.id, `${u.displayName}/${u.role}`]));
  const byDest = {};
  notes.forEach((n) => (byDest[n.destinationBranchName] = (byDest[n.destinationBranchName] || []).concat(n)));
  for (const [dest, ns] of Object.entries(byDest)) {
    console.log(`\n== ${dest} (${ns.length})`);
    ns.sort((a,b)=>(a.productionDate||0)-(b.productionDate||0)).forEach((n) => console.log(`  ${n.id} prod=${d(n.productionDate)} ${n.shiftName||''} st=${n.status} destId=${n.destinationBranchId} baskets=${n.basketsSent||0}/${n.basketsReceived??'-'} arrivedBy=${n.arrivedByUserName||'—'}(${uname[n.arrivedByUserId]||n.arrivedByUserId||''}) receivedBy=${n.receivedByUserName||'—'}(${uname[n.receivedByUserId]||n.receivedByUserId||''}) recv=${d(n.receivedAt)}`));
  }
})().catch((e) => { console.error(e); process.exit(1); });
