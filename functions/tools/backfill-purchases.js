/**
 * Backfill de los campos nuevos del encabezado de compras.
 *
 * Las compras registradas antes del módulo de cuentas por pagar no guardan el monto ni el
 * contenido en el encabezado: solo en la subcolección `lines/`. Sin esto la lista mostraría
 * "Q0.00" en toda factura vieja, los KPIs saldrían en cero y buscar "Aceite" no encontraría
 * nada anterior a este cambio (la búsqueda por contenido lee `itemNames`, no las líneas —
 * abrir la subcolección de cada factura al pintar la lista sería una lectura por factura).
 *
 * Qué escribe, por compra, leyendo sus líneas:
 *   totalAmount   suma de las líneas, con la misma fórmula que `PurchaseLine.lineTotal`
 *   itemIds       ids de insumo / producto de reventa, sin repetir
 *   itemNames     nombres visibles, para la búsqueda por contenido
 *   paidAmount    0, o el total si la compra ya estaba marcada como PAID
 *   receivedAt    la fecha de la compra, salvo que esté PENDING_RECEIPT
 *   dueDate       date + los días de crédito del proveedor, si tiene
 *
 * Es idempotente: correrlo dos veces deja lo mismo. Solo pisa campos que faltan o están en
 * cero, así que NO deshace un vencimiento o un abono puestos a mano desde la app.
 *
 * `--sync-terms` resuelve un caso aparte: las condiciones de crédito del proveedor son un
 * SNAPSHOT tomado al registrar la compra (un proveedor que pasa de 8 a 15 días no debe mover
 * el vencimiento de lo ya facturado). Eso deja sin vencimiento a todo lo registrado ANTES de
 * configurarle los días al proveedor — y la primera vez que se configuran no hay ningún
 * snapshot que preservar. Con la bandera, las facturas que quedaron en `dueDate: 0` adoptan
 * las condiciones actuales de su proveedor. Nunca pisa un vencimiento ya puesto.
 *
 * Uso (requiere ADC: `gcloud auth application-default login`):
 *   node backfill-purchases.js                       -> reporta, no escribe (dry run)
 *   node backfill-purchases.js --apply               -> escribe
 *   node backfill-purchases.js --sync-terms          -> reporta también los vencimientos
 *   node backfill-purchases.js --sync-terms --apply  -> escribe también los vencimientos
 *   node backfill-purchases.js --company=<id>        -> otra empresa
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();

const APPLY = process.argv.includes('--apply');
const SYNC_TERMS = process.argv.includes('--sync-terms');
const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY = companyArg ? companyArg.split('=')[1] : 'tiO54slG1n9sk6k2ePCD';

const DAY_MS = 86400000;

/** Espejo de `PurchaseLine.lineTotal` en Kotlin. Si divergen, el papel miente. */
function lineTotal(l) {
  const packageCost = Number(l.packageCost) || 0;
  const packages = Number(l.packagesReceived) || 0;
  const unitCost = Number(l.unitCost) || 0;
  const qty = Number(l.quantityReceived) || 0;
  if (packageCost > 0 && packages > 0) return packageCost * packages;
  if (packageCost > 0) return packageCost;
  return unitCost * qty;
}

const isResale = (l) => l.lineKind === 'RESALE_PRODUCT';
const itemId = (l) => (isResale(l) ? l.productId : l.ingredientId) || '';
const itemName = (l) => (isResale(l) ? l.productName : l.ingredientName) || '';

/** Redondeo a centavos: sumar dobles deja colas de 0.000000001 en el documento. */
const cents = (n) => Math.round(n * 100) / 100;

(async () => {
  const companyRef = db.collection('companies').doc(COMPANY);

  const suppliersSnap = await companyRef.collection('suppliers').get();
  const termsBySupplier = new Map(
    suppliersSnap.docs.map((d) => [d.id, Number(d.data().paymentTermsDays) || 0])
  );

  const purchasesSnap = await companyRef.collection('purchases').get();
  console.log(`Empresa ${COMPANY}: ${purchasesSnap.size} compras, ${suppliersSnap.size} proveedores.`);

  let touched = 0;
  let skipped = 0;
  let noLines = 0;
  let syncedDue = 0;

  for (const doc of purchasesSnap.docs) {
    const p = doc.data();
    const linesSnap = await doc.ref.collection('lines').get();
    const lines = linesSnap.docs.map((d) => d.data());

    if (lines.length === 0) noLines++;

    const total = cents(lines.reduce((sum, l) => sum + lineTotal(l), 0));
    const ids = [...new Set(lines.map(itemId).filter(Boolean))];
    const names = [...new Set(lines.map(itemName).filter(Boolean))];

    const patch = {};
    if (!(Number(p.totalAmount) > 0) && total > 0) patch.totalAmount = total;
    if (!Array.isArray(p.itemIds) && ids.length) patch.itemIds = ids;
    if (!Array.isArray(p.itemNames) && names.length) patch.itemNames = names;
    if (p.lineCount !== lines.length && lines.length > 0) patch.lineCount = lines.length;

    // Una compra ya marcada como pagada nace saldada; si no, arranca sin abonos.
    // Ojo con el `??`: no atrapa NaN, y `Number(undefined)` es NaN — una factura sin monto
    // (líneas sin costo registrado) escribía `paidAmount: NaN` en Firestore.
    const headerTotal = Number(p.totalAmount) > 0 ? Number(p.totalAmount) : total;
    if (p.paidAmount === undefined) {
      patch.paidAmount = p.status === 'PAID' ? headerTotal : 0;
    }

    // Todo lo registrado antes de este cambio ingresó stock al guardarse, sin importar el
    // estado — ése era justamente el bug. Así que ya está recibido, salvo lo que quedó
    // explícitamente pendiente, que la app tratará ahora como pendiente de verdad.
    if (p.receivedAt === undefined) {
      patch.receivedAt = p.status === 'PENDING_RECEIPT' ? 0 : Number(p.date) || 0;
    }

    const terms = termsBySupplier.get(p.supplierId) ?? 0;
    if (p.paymentTermsDays === undefined) patch.paymentTermsDays = terms;
    if (p.dueDate === undefined) {
      patch.dueDate = terms > 0 && Number(p.date) > 0 ? Number(p.date) + terms * DAY_MS : 0;
    }

    // Adopción retroactiva: solo donde NO hay vencimiento y el proveedor sí tiene condiciones.
    const dueNow = patch.dueDate ?? Number(p.dueDate) ?? 0;
    if (SYNC_TERMS && !(dueNow > 0) && terms > 0 && Number(p.date) > 0) {
      patch.dueDate = Number(p.date) + terms * DAY_MS;
      patch.paymentTermsDays = terms;
      syncedDue++;
    }

    if (Object.keys(patch).length === 0) {
      skipped++;
      continue;
    }
    touched++;
    const label = `${(p.supplierName || '?').padEnd(24)} ${p.invoiceNumber || 's/f'}`;
    console.log(`  ${APPLY ? 'ESCRIBE' : 'faltaría'} ${label} → ${JSON.stringify(patch)}`);
    if (APPLY) await doc.ref.update(patch);
  }

  console.log(
    `\n${APPLY ? 'Actualizadas' : 'Por actualizar'}: ${touched} · ya completas: ${skipped}` +
      (noLines ? ` · sin líneas: ${noLines}` : '') +
      (syncedDue ? ` · vencimientos adoptados: ${syncedDue}` : '')
  );
  if (!SYNC_TERMS) {
    console.log(
      'Sugerencia: agregá --sync-terms si acabás de configurar los días de crédito de un ' +
        'proveedor y querés que sus facturas ya registradas adopten el vencimiento.'
    );
  }
  if (!APPLY && touched > 0) console.log('Nada se escribió. Volvé a correrlo con --apply.');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
