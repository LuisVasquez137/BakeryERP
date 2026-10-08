/**
 * Diagnóstico de solo lectura: cuentas de `users` de la empresa y colaboradores, y cómo
 * se enlazan (employee.userId). No escribe nada. No imprime datos personales sensibles
 * (DPI, salario, cuenta bancaria).
 *
 * Uso: node inspect-users-employees.js [--company=<id>]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (k, d) => (process.argv.find((a) => a.startsWith(`--${k}=`)) || '').split('=')[1] || d;
const COMPANY = arg('company', 'tiO54slG1n9sk6k2ePCD');
const base = db.collection('companies').doc(COMPANY);

(async () => {
  const branches = Object.fromEntries((await base.collection('branches').get()).docs.map((d) => [d.id, d.data().name]));
  const users = (await db.collection('users').where('orgId', '==', COMPANY).get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  const employees = (await base.collection('employees').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  const byUser = {};
  employees.filter((e) => e.userId).forEach((e) => (byUser[e.userId] = (byUser[e.userId] || []).concat(e.name)));

  console.log(`USUARIOS (${users.length})`);
  for (const u of users) {
    const br = (u.branchIds || []).map((b) => branches[b] || `?${b}`).join(', ') || '(todas)';
    console.log(`- ${u.email} | ${u.displayName} | ${u.role} | activo=${u.isActive} aprobado=${u.isApproved} | ${(u.permissions || []).length} permisos | sucursales: ${br} | colaborador: ${(byUser[u.id] || []).join(' / ') || '—'}`);
  }
  console.log(`\nCOLABORADORES (${employees.length})`);
  const userIds = new Set(users.map((u) => u.id));
  for (const e of employees) {
    const link = e.userId ? (userIds.has(e.userId) ? 'enlazado' : `userId ROTO ${e.userId}`) : 'sin usuario';
    console.log(`- ${e.name} | ${e.status || ''} active=${e.active} | sucursal: ${branches[e.branchId] || e.branchName || '—'} | puesto: ${e.positionTitle || '—'} | ${link}`);
  }
  const dup = Object.entries(byUser).filter(([, v]) => v.length > 1);
  if (dup.length) console.log('\nUN USUARIO ENLAZADO A VARIOS COLABORADORES:', dup);
  console.log('\nPermisos por usuario:');
  for (const u of users) console.log(`  ${u.email}: ${(u.permissions || []).join(', ')}`);
})().catch((e) => { console.error(e); process.exit(1); });
