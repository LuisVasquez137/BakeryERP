/**
 * Arranque del POS en serio: borra lo que dejaron las pruebas de caja y las órdenes de ComandaYa
 * que nadie recibió, para que la sucursal empiece de cero.
 *
 * Qué borra (de la sucursal elegida, o de todas), creado antes de --before:
 *   sales (+lines) · salesDaily · cashSessions (+movements, denominaciones) · cashAuditEvents ·
 *   spotCashChecks (+denominations) · dailyCashCloses (+subcolecciones) · heldCarts ·
 *   bankDeposits · cashTransfers · counterRemovals · customerDeposits · creditMovements
 *
 * Qué NO toca:
 *   - Ventas con factura FEL CERTIFICADA (siguen vigentes ante SAT) ni `dtes`. Su salesDaily sí se
 *     borra: esas ventas siguen en el detalle y en FEL, pero ya no suman en los reportes por día.
 *   - Cajas (`cashRegisters`), cuentas de crédito (solo se recalcula su saldo), conteos de pan,
 *     compras y gastos que se pagaron de caja (al no existir el turno, la app ya no mueve gaveta).
 *   - Kardex de pan: las ventas de prueba ya descontaron; no se reescribe historial.
 *
 * Con --zero-stock=<branchId> además deja en 0 el inventario de tienda de esa sucursal con un
 * AJUSTE por la diferencia en finishedMovements (corregir = escribir la diferencia).
 *
 * Por qué borrar los turnos arregla el fondo: al abrir, la caja espera lo que entregó su último
 * cierre (`expectedOpeningCash`). Sin turnos previos no espera nada y se cuenta el fondo real.
 *
 * Uso (desde functions/):
 *   node tools/reset-pos-start.js [--branch=<id>|all] [--before=2026-10-26T06:00:00Z]
 *                                 [--zero-stock=<branchId>] [--apply]
 * En seco por defecto. Con --apply deja respaldo en tools/backup_arranque_pos_*.json.
 */
const fs = require('fs');
const path = require('path');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (name) => {
  const a = process.argv.find((x) => x.startsWith(`--${name}=`));
  return a ? a.slice(name.length + 3) : null;
};
const COMPANY = arg('company') || 'tiO54slG1n9sk6k2ePCD';
const BRANCH = arg('branch') || 'all';
const BEFORE = arg('before') ? Date.parse(arg('before')) : Date.now();
const ZERO_STOCK = arg('zero-stock');
const APPLY = process.argv.includes('--apply');
const base = db.collection('companies').doc(COMPANY);

if (Number.isNaN(BEFORE)) { console.error('--before no es una fecha válida'); process.exit(1); }

const ms = (v) => {
  if (v == null) return 0;
  if (typeof v === 'number') return v;
  if (v.toMillis) return v.toMillis();
  return Number(v) || 0;
};
const inBranch = (d) => BRANCH === 'all' || d.branchId === BRANCH;

/** Colecciones planas: campo de fecha con el que se aplica --before. */
const FLAT = [
  ['cashSessions', 'openedAt'],
  ['cashAuditEvents', 'at'],
  ['spotCashChecks', 'checkedAt'],
  ['dailyCashCloses', 'closedAt'],
  ['heldCarts', 'createdAt'],
  ['bankDeposits', 'createdAt'],
  ['cashTransfers', 'createdAt'],
  ['counterRemovals', 'createdAt'],
  ['customerDeposits', 'createdAt'],
];

const backup = {};
const toDelete = []; // DocumentReference

/** Recorre en paralelo, de a 50: una por una son miles de idas y vueltas. */
async function collectAll(docs, label) {
  for (let i = 0; i < docs.length; i += 50) {
    await Promise.all(docs.slice(i, i + 50).map((d) => collectWithSubs(d.ref, label, d)));
  }
}

async function collectWithSubs(ref, label, snapshot) {
  const snap = snapshot || await ref.get();
  backup[label] = backup[label] || [];
  const entry = { path: ref.path, data: snap.data(), subs: {} };
  for (const sub of await ref.listCollections()) {
    const docs = (await sub.get()).docs;
    entry.subs[sub.id] = docs.map((d) => ({ id: d.id, data: d.data() }));
    docs.forEach((d) => toDelete.push(d.ref));
  }
  backup[label].push(entry);
  toDelete.push(ref);
}

(async () => {
  console.log(`Empresa ${COMPANY} · sucursal ${BRANCH} · antes de ${new Date(BEFORE).toISOString()} · ${APPLY ? 'APLICANDO' : 'EN SECO'}`);

  // Ventas con factura vigente: se conservan.
  const keep = new Set();
  for (const d of (await base.collection('dtes').get()).docs) {
    const x = d.data();
    if (x.estado === 'CERTIFICADO') (x.saleIds || []).forEach((id) => keep.add(id));
  }
  console.log(`Ventas con factura certificada que se conservan: ${[...keep].join(', ') || 'ninguna'}`);

  // Ventas.
  const sales = (await base.collection('sales').get()).docs
    .filter((d) => inBranch(d.data()) && ms(d.data().createdAt) < BEFORE && !keep.has(d.id));
  const bySource = {};
  let total = 0;
  for (const d of sales) {
    const s = d.data();
    const k = `${s.branchName} ${s.source || 'POS'} ${s.status}`;
    bySource[k] = (bySource[k] || 0) + 1;
    total += Number(s.total) || 0;
  }
  await collectAll(sales, 'sales');
  console.log(`\nsales: ${sales.length} (Q ${total.toFixed(2)})`, JSON.stringify(bySource, null, 1));

  // Resumen diario: se reconstruye de las ventas, sin ellas no tiene sentido.
  const beforeDay = new Date(BEFORE - 6 * 3600 * 1000).toISOString().slice(0, 10); // Guatemala, UTC-6 fijo
  const daily = (await base.collection('salesDaily').get()).docs
    .filter((d) => inBranch(d.data()) && (d.data().businessDate || d.id.split('_')[1] || '') <= beforeDay);
  await collectAll(daily, 'salesDaily');
  console.log(`salesDaily: ${daily.length} (hasta ${beforeDay})`);

  for (const [name, dateField] of FLAT) {
    const docs = (await base.collection(name).get()).docs
      .filter((d) => inBranch(d.data()) && ms(d.data()[dateField]) < BEFORE);
    const open = docs.filter((d) => d.data().status === 'OPEN');
    if (name === 'cashSessions' && open.length) {
      console.log(`  ⚠️ ${open.length} turno(s) ABIERTO(s): ${open.map((d) => d.data().registerName).join(', ')}`);
    }
    await collectAll(docs, name);
    console.log(`${name}: ${docs.length}`);
  }

  // Créditos: se borran los movimientos, las cuentas quedan y se recalcula su saldo.
  const credit = (await base.collection('creditMovements').get()).docs
    .filter((d) => (BRANCH === 'all' || d.data().branchId === BRANCH) && ms(d.data().createdAt) < BEFORE);
  await collectAll(credit, 'creditMovements');
  const touched = [...new Set(credit.map((d) => d.data().accountId).filter(Boolean))];
  const deletedIds = new Set(credit.map((d) => d.id));
  const accountResets = [];
  for (const accId of touched) {
    const remaining = (await base.collection('creditMovements').where('accountId', '==', accId).get()).docs
      .filter((d) => !deletedIds.has(d.id));
    const acc = (await base.collection('creditAccounts').doc(accId).get()).data() || {};
    if (remaining.length) {
      console.log(`  ⚠️ ${acc.name}: le quedan ${remaining.length} movimientos; su saldo no se toca, revisarlo a mano`);
      continue;
    }
    backup.creditAccounts = backup.creditAccounts || [];
    backup.creditAccounts.push({ path: `creditAccounts/${accId}`, data: acc });
    accountResets.push({ accId, name: acc.name, before: acc.balance });
  }
  console.log(`creditMovements: ${credit.length} · cuentas que quedan en 0: ${accountResets.map((a) => `${a.name} (era ${a.before})`).join(', ') || 'ninguna'}`);

  // Folio de anticipos: solo si ya no queda ninguno.
  const depositsLeft = (await base.collection('customerDeposits').get()).docs
    .filter((d) => !toDelete.some((r) => r.path === d.ref.path)).length;
  const resetFolio = depositsLeft === 0;
  console.log(`Folio de anticipos: ${resetFolio ? 'se reinicia (el próximo es ANT-0001)' : `se conserva (quedan ${depositsLeft})`}`);

  // Inventario de tienda en cero.
  let stocks = [];
  if (ZERO_STOCK) {
    stocks = (await base.collection('finishedStocks').where('branchId', '==', ZERO_STOCK).get()).docs
      .filter((d) => Math.abs(Number(d.data().currentStock) || 0) > 1e-9);
    const units = stocks.reduce((a, d) => a + Number(d.data().currentStock), 0);
    console.log(`\nInventario de tienda a cero (${ZERO_STOCK}): ${stocks.length} productos, ${units} unidades`);
  }

  console.log(`\nTotal de documentos a borrar: ${toDelete.length}`);
  if (!APPLY) { console.log('\nEn seco: no se escribió nada. Repetir con --apply.'); return; }

  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const file = path.join(__dirname, `backup_arranque_pos_${stamp}.json`);
  fs.writeFileSync(file, JSON.stringify({ company: COMPANY, branch: BRANCH, before: BEFORE, backup }, null, 1));
  console.log(`Respaldo: ${file}`);

  for (let i = 0; i < toDelete.length; i += 400) {
    const batch = db.batch();
    toDelete.slice(i, i + 400).forEach((r) => batch.delete(r));
    await batch.commit();
    process.stdout.write(`\rBorrados ${Math.min(i + 400, toDelete.length)}/${toDelete.length}`);
  }
  console.log();

  const now = Date.now();
  for (const a of accountResets) {
    await base.collection('creditAccounts').doc(a.accId).update({
      balance: 0, openChargeAmounts: [], openChargeDates: [], updatedAt: now,
    });
  }
  if (resetFolio) await base.collection('counters').doc('customerDeposits').set({ next: 0, updatedAt: now });

  const refId = `arranque-pos-${stamp}`;
  for (const d of stocks) {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(d.ref);
      const s = snap.data();
      const qty = Number(s.currentStock) || 0;
      if (Math.abs(qty) < 1e-9) return;
      const moveRef = base.collection('finishedMovements').doc();
      tx.update(d.ref, { currentStock: 0, updatedAt: now });
      tx.set(moveRef, {
        id: moveRef.id,
        branchId: s.branchId,
        branchName: s.branchName,
        productId: s.productId,
        productName: s.productName,
        type: 'AJUSTE',
        quantity: -qty,
        unitPriceAtMovement: Number(s.avgUnitCost) || 0,
        referenceType: 'ADJUSTMENT',
        referenceId: refId,
        reason: 'Arranque del POS: inventario de tienda a cero (lo acumulado antes no era real)',
        createdAt: now,
        createdByUserId: '',
        createdByUserName: 'Arranque POS',
      });
    });
  }
  if (stocks.length) console.log(`Inventario en cero: ${stocks.length} productos (referencia ${refId})`);
  console.log('Listo.');
})().catch((e) => { console.error(e); process.exit(1); });
