/**
 * Diagnóstico de solo lectura: quién puede ver las gráficas del inicio (VIEW_OPERATIONS_REPORT).
 * En un equipo de sucursal el permiso efectivo es la intersección cuenta ∩ colaborador.
 * Uso: node inspect-home-charts-access.js [--company=<id>]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (k, d) => (process.argv.find((a) => a.startsWith(`--${k}=`)) || '').split('=')[1] || d;
const COMPANY = arg('company', 'tiO54slG1n9sk6k2ePCD');
const base = db.collection('companies').doc(COMPANY);
const P = 'VIEW_OPERATIONS_REPORT';
(async () => {
  const branches = Object.fromEntries((await base.collection('branches').get()).docs.map((d) => [d.id, d.data().name]));
  const users = (await db.collection('users').where('orgId', '==', COMPANY).get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  const emps = (await base.collection('employees').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  console.log('CUENTAS');
  for (const u of users) {
    const br = (u.branchIds || []).map((b) => branches[b] || b).join(', ') || '(todas)';
    console.log(`- ${u.email} | ${u.role} | equipo=${!!u.isDeviceAccount} | ${P}=${(u.permissions || []).includes(P)} | VIEW_ALL_BRANCHES=${(u.permissions || []).includes('VIEW_ALL_BRANCHES')} | sucursales: ${br}`);
  }
  console.log('\nCOLABORADORES CON EL PERMISO');
  for (const e of emps.filter((e) => (e.permissions || []).includes(P)))
    console.log(`- ${e.name} | sucursal: ${branches[e.branchId] || '—'} | activo=${e.active} | usuario propio: ${e.userId || '—'}`);
})().catch((e) => { console.error(e); process.exit(1); });
