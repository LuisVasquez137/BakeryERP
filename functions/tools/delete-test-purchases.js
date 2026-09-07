/**
 * Borra las compras de prueba SIN tocar el inventario.
 *
 * ## Por qué no se usa el botón de la app
 *
 * `deletePurchase` del ViewModel revierte el ingreso: descuenta del stock lo que la compra
 * metió. Eso es lo correcto para una factura mal registrada, pero acá el stock que hay es el
 * que se va a producir esta noche — hay que borrar el papeleo y dejar la existencia intacta.
 *
 * ## Qué borra y qué NO
 *
 * BORRA: encabezados de `purchases`, sus subcolecciones `lines` y `payments`.
 *
 * NO TOCA `branchStocks` — es el punto de todo el ejercicio.
 *
 * NO TOCA el Kardex de insumos (`ingredients/{id}/movements`), y esto es deliberado: el
 * historial tiene cientos de movimientos de PRODUCCION contra unas pocas entradas de COMPRA.
 * Borrar las entradas dejaría cientos de salidas sin nada que explique de dónde salió el
 * material — un Kardex peor que el actual. El campo `reference` queda apuntando a un
 * documento que ya no existe, lo cual es inofensivo: ninguna pantalla lo resuelve, solo lo
 * guarda como texto.
 *
 * NO BORRA los lotes FEFO, porque un lote ES existencia física y la existencia se queda. Solo
 * les limpia `purchaseId` para que no apunten a un documento borrado.
 *
 * Antes de borrar deja un respaldo JSON con todo lo eliminado.
 *
 * Uso:
 *   node delete-test-purchases.js            -> reporta y escribe el respaldo (dry run)
 *   node delete-test-purchases.js --apply    -> borra
 *   node delete-test-purchases.js --company=<id>
 */

const fs = require('fs');
const path = require('path');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();

const APPLY = process.argv.includes('--apply');
const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY = companyArg ? companyArg.split('=')[1] : 'tiO54slG1n9sk6k2ePCD';

(async () => {
  const c = db.collection('companies').doc(COMPANY);
  const purchases = await c.collection('purchases').get();

  if (purchases.empty) {
    console.log('No hay compras que borrar.');
    return;
  }

  const backup = [];
  for (const doc of purchases.docs) {
    const lines = await doc.ref.collection('lines').get();
    const payments = await doc.ref.collection('payments').get();
    backup.push({
      id: doc.id,
      data: doc.data(),
      lines: lines.docs.map((d) => ({ id: d.id, data: d.data() })),
      payments: payments.docs.map((d) => ({ id: d.id, data: d.data() }))
    });
  }

  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const file = path.join(__dirname, `backup_compras_${stamp}.json`);
  fs.writeFileSync(file, JSON.stringify({ company: COMPANY, purchases: backup }, null, 2));
  console.log(`Respaldo escrito en ${path.basename(file)}`);

  const purchaseIds = new Set(purchases.docs.map((d) => d.id));
  const lots = await c.collection('stockLots').get();
  const lotsToClear = lots.docs.filter((d) => purchaseIds.has(d.data().purchaseId));

  console.log('');
  backup.forEach((p) => {
    const d = p.data;
    console.log(
      `  ${APPLY ? 'BORRA  ' : 'borraría'} ${(d.supplierName || '?').padEnd(24)} ` +
        `${d.invoiceNumber || 's/f'}  Q${d.totalAmount ?? 0}  ` +
        `(${p.lines.length} líneas, ${p.payments.length} abonos)`
    );
  });
  console.log('');
  console.log(`Lotes FEFO a los que se les limpia purchaseId (NO se borran): ${lotsToClear.length}`);
  lotsToClear.forEach((d) => {
    const l = d.data();
    console.log(`   ${l.ingredientName} · saldo ${l.remainingQuantity}`);
  });
  console.log('branchStocks: intacto. Kardex de insumos: intacto.');

  if (!APPLY) {
    console.log('\nNada se borró. Volvé a correrlo con --apply.');
    return;
  }

  for (const doc of purchases.docs) {
    for (const sub of ['lines', 'payments']) {
      const snap = await doc.ref.collection(sub).get();
      for (const d of snap.docs) await d.ref.delete();
    }
    await doc.ref.delete();
  }
  for (const d of lotsToClear) {
    await d.ref.update({ purchaseId: '' });
  }

  console.log(`\nBorradas: ${backup.length} compras. Stock y Kardex sin cambios.`);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
