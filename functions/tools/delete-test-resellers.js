/**
 * Borra los revendedores de prueba: su ficha (`externalCustomers` tipo RESELLER) y, si se
 * registraron por la app, su cuenta (`users` con rol RESELLER + Firebase Auth). No toca ventas
 * ni pedidos: guardan el nombre del revendedor como copia.
 *
 * Uso: node delete-test-resellers.js [--company=<id>]          → solo muestra
 *      node delete-test-resellers.js --apply [--company=<id>]  → borra (respalda antes)
 */
const fs = require('fs');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (k, d) => (process.argv.find((a) => a.startsWith(`--${k}=`)) || '').split('=')[1] || d;
const COMPANY = arg('company', 'tiO54slG1n9sk6k2ePCD');
const APPLY = process.argv.includes('--apply');
const base = db.collection('companies').doc(COMPANY);

(async () => {
  const customers = (await base.collection('externalCustomers').get()).docs
    .map((d) => ({ id: d.id, ...d.data() }))
    .filter((c) => (c.type || 'RESELLER') === 'RESELLER');
  const users = (await db.collection('users').where('orgId', '==', COMPANY).where('role', '==', 'RESELLER').get()).docs
    .map((d) => ({ id: d.id, ...d.data() }));
  const sales = (await base.collection('sales').where('resellerId', '!=', '').get()).docs;
  const orders = (await base.collection('customerOrders').get()).docs
    .filter((d) => customers.some((c) => c.id === d.data().customerId));

  console.log(`FICHAS DE REVENDEDOR (${customers.length})`);
  customers.forEach((c) => console.log(`- ${c.name} | ${c.businessName || ''} | ${c.registrationStatus} | lista: ${c.priceListName || '—'} | cuenta: ${c.linkedUserId || '—'}`));
  console.log(`\nCUENTAS RESELLER (${users.length})`);
  users.forEach((u) => console.log(`- ${u.email} | ${u.displayName} | aprobado=${u.isApproved}`));
  console.log(`\nVentas a revendedor: ${sales.length} · pedidos de esos clientes: ${orders.length} (no se borran)`);

  if (!APPLY) { console.log('\nSolo vista. Corré con --apply para borrar.'); return; }
  const file = `backup_revendedores_${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
  fs.writeFileSync(file, JSON.stringify({ customers, users }, null, 2));
  console.log(`\nRespaldo: ${file}`);
  for (const c of customers) await base.collection('externalCustomers').doc(c.id).delete();
  for (const u of users) {
    await db.collection('users').doc(u.id).delete();
    await getAuth().deleteUser(u.id).catch((e) => console.log(`  (Auth ${u.email}: ${e.code})`));
  }
  console.log(`Borrados: ${customers.length} fichas, ${users.length} cuentas.`);
})().catch((e) => { console.error(e); process.exit(1); });
