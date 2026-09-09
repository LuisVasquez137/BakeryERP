/**
 * Repara una nota de despacho que `startLot` no alcanzo a crear.
 *
 * Por que existe: `createDispatchNotesForLot` recorre los pedidos fuente en
 * serie y tarda 1-2.5 min (resuelve el precio de cada producto uno por uno).
 * Si el proceso muere a media pasada -la tablet se durmio, se cerro la app, se
 * cayo la red- las sucursales que faltaban del arreglo se quedan sin nota, en
 * silencio: el `runCatching` de `startLot` se traga el error y nada reintenta
 * hasta `completeLot`. Paso el 2026-09-08 con Pacaja (5a de 5).
 *
 * Replica la logica de FirebaseDispatchRepository.createDispatchNotesForLot
 * + FirebasePriceListRepository.resolveWholesalePrice.
 *
 * Uso:
 *   node repair-missing-dispatch-note.js <lotId> <orderId>          -> dry run
 *   node repair-missing-dispatch-note.js <lotId> <orderId> --apply  -> escribe
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();

const COMPANY = 'tiO54slG1n9sk6k2ePCD';
const [, , LOT_ID, ORDER_ID] = process.argv;
const APPLY = process.argv.includes('--apply');
if (!LOT_ID || !ORDER_ID) {
  console.error('Uso: node repair-missing-dispatch-note.js <lotId> <orderId> [--apply]');
  process.exit(1);
}

const c = db.collection('companies').doc(COMPANY);
const ms = (v) => (v && v.toMillis) ? v.toMillis() : (typeof v === 'number' ? v : 0);

/** Cascada de FirebasePriceListRepository.resolveWholesalePrice. */
async function resolveWholesalePrice(branchId, productId, cache) {
  if (cache.list === undefined) {
    const bs = await c.collection('branches').doc(branchId).get();
    const listId = (bs.exists && bs.data().defaultPriceListId) || '';
    let list = null;
    if (listId) {
      const ls = await c.collection('priceLists').doc(listId).get();
      if (ls.exists && ls.data().active) list = Object.assign({ id: ls.id }, ls.data());
    }
    if (!list) {
      const q = await c.collection('priceLists')
        .where('type', '==', 'WHOLESALE').where('isDefault', '==', true).get();
      if (!q.empty) list = Object.assign({ id: q.docs[0].id }, q.docs[0].data());
    }
    cache.list = list;
  }
  const list = cache.list;
  if (!list) return { unitPrice: 0, priceListId: '', priceListName: '', found: false, source: 'NONE' };

  // 1) Override explicito por producto en esta lista.
  const ov = await c.collection('productPrices').doc(productId + '_' + list.id).get();
  if (ov.exists && (ov.data().unitPrice || 0) > 0) {
    return {
      unitPrice: ov.data().unitPrice, priceListId: list.id, priceListName: list.name,
      found: true, source: 'PRODUCT_OVERRIDE'
    };
  }
  // 2) Factor sobre el precio de venta al cliente final.
  const factor = list.priceFactor;
  if (factor > 0 && (list.type === 'WHOLESALE' || list.type === 'CUSTOM')) {
    const ps = await c.collection('products').doc(productId).get();
    const retail = (ps.exists && ps.data().salePrice) || 0;
    if (retail > 0) {
      return {
        unitPrice: retail * factor, priceListId: list.id, priceListName: list.name,
        found: true, source: 'LIST_FACTOR'
      };
    }
  }
  // 3) Sin precio resoluble.
  return { unitPrice: 0, priceListId: list.id, priceListName: list.name, found: false, source: 'NONE' };
}

(async () => {
  const lotSnap = await c.collection('productionLots').doc(LOT_ID).get();
  if (!lotSnap.exists) throw new Error('El lote no existe');
  const lot = lotSnap.data();
  if (!(lot.sourceOrderIds || []).includes(ORDER_ID)) {
    throw new Error('El pedido no es fuente de ese lote');
  }

  // Guarda de idempotencia: la misma que usa la app.
  const dup = await c.collection('dispatchNotes')
    .where('lotId', '==', LOT_ID).where('sourceOrderId', '==', ORDER_ID).get();
  if (!dup.empty) {
    console.log('Ya existe nota ' + dup.docs[0].id + ' para ese lote+pedido. Nada que hacer.');
    return;
  }

  const orderSnap = await c.collection('productionOrders').doc(ORDER_ID).get();
  const order = orderSnap.data();
  const orderLines = (await orderSnap.ref.collection('lines').get()).docs.map((d) => d.data());

  const lotLines = (await lotSnap.ref.collection('lines').get()).docs.map((d) => d.data());
  const byProduct = {};
  lotLines.forEach((l) => { byProduct[l.productId] = l; });
  const isSplitLot = lot.isShiftSplit === true || lotLines.some((l) => (l.soaOrderIds || []).length > 0);

  // dispatchQuantityFor: la porcion de ESTE pedido en ESTE turno.
  const qtyFor = (productId, requested) => {
    const ll = byProduct[productId];
    const ids = (ll && ll.soaOrderIds) || [];
    if (ids.length > 0) {
      const i = ids.indexOf(ORDER_ID);
      return i >= 0 ? (ll.soaQuantities[i] || 0) : 0;
    }
    return isSplitLot ? 0 : requested;
  };

  const forNote = orderLines
    .map((l) => Object.assign({}, l, { requestedQuantity: qtyFor(l.productId, l.requestedQuantity) }))
    .filter((l) => l.requestedQuantity > 0);
  if (forNote.length === 0) {
    console.log('A este turno no le toca nada de ese pedido. No se crea nota.');
    return;
  }

  const cache = {};
  const resolved = [];
  for (const l of forNote) {
    resolved.push([l, l.isSpecial ? null : await resolveWholesalePrice(order.sellingBranchId, l.productId, cache)]);
  }
  const first = resolved.map((r) => r[1]).find((r) => r);
  const hasPriceMissing = resolved.some((pair) => pair[1] && !pair[1].found);
  const now = Date.now();
  const noteRef = c.collection('dispatchNotes').doc();

  const note = {
    id: noteRef.id, lotId: LOT_ID, sourceOrderId: ORDER_ID,
    originBranchId: lot.producingBranchId, originBranchName: lot.producingBranchName,
    destinationType: 'BRANCH',
    destinationBranchId: order.sellingBranchId, destinationBranchName: order.sellingBranchName,
    destinationCustomerId: '', destinationCustomerName: '',
    productionDate: ms(lot.productionDate), shiftId: lot.shiftId || '', shiftName: lot.shiftName || '',
    status: 'DRAFT', deliveryRunId: '',
    packedByUserId: '', packedByUserName: '', packedAt: null, dispatchedAt: null,
    arrivedAt: null, arrivedByUserId: '', arrivedByUserName: '',
    receivedByUserId: '', receivedByUserName: '', receivedAt: null,
    totalValueWholesale: 0,
    priceListId: (first && first.priceListId) || '',
    priceListName: (first && first.priceListName) || '',
    hasPriceMissing: hasPriceMissing, hasDiscrepancy: false, notes: null,
    statusBeforeCancel: '', cancelledAt: null, cancelledByUserId: '', cancelledByUserName: '',
    cancelReason: null, plantAbsorbedAtCancel: false,
    createdAt: now, updatedAt: now, lineCount: forNote.length
  };

  console.log('');
  console.log('Nota a crear: ' + note.destinationBranchName + '  turno=' + note.shiftName + '  ' + forNote.length + ' productos');
  console.log('Lista de precios: ' + note.priceListName + ' (' + note.priceListId + ')   hasPriceMissing=' + hasPriceMissing);
  let units = 0, value = 0;
  resolved.forEach((pair) => {
    const l = pair[0], r = pair[1];
    const src = r ? r.source : 'SPECIAL_ORDER';
    const price = r ? r.unitPrice : (l.specialUnitPrice || 0);
    units += l.requestedQuantity;
    value += l.requestedQuantity * price;
    console.log('   ' + String(l.productName).padEnd(34) +
      ' x' + String(l.requestedQuantity).padStart(5) +
      '  Q' + price.toFixed(2).padStart(7) + '  ' + src +
      (r && !r.found ? '  <-- SIN PRECIO' : ''));
  });
  console.log('   ' + 'TOTAL'.padEnd(34) + ' x' + String(units).padStart(5) + '  Q' + value.toFixed(2));

  if (!APPLY) {
    console.log('');
    console.log('[dry run] Nada escrito. Repeti con --apply para crearla.');
    return;
  }

  const batch = db.batch();
  batch.set(noteRef, note);
  resolved.forEach((pair) => {
    const l = pair[0], r = pair[1];
    const lineRef = noteRef.collection('lines').doc();
    batch.set(lineRef, {
      id: lineRef.id, productId: l.productId, productName: l.productName, productUnit: l.productUnit || '',
      requestedQuantity: l.requestedQuantity,
      packedQuantity: l.requestedQuantity, // pre-llenado con lo pedido, igual que la app
      packedReason: null, receivedQuantity: 0, receivedReason: null, discrepancyType: null,
      unitWholesalePrice: r ? r.unitPrice : (l.specialUnitPrice || 0),
      priceSource: r ? r.source : 'SPECIAL_ORDER',
      lineCheckedAt: null, lineArrivedConfirmedAt: null,
      isSpecial: l.isSpecial || false,
      specialRawWeightGrams: l.specialRawWeightGrams || null,
      specialRecipeId: l.specialRecipeId || '', specialRecipeName: l.specialRecipeName || ''
    });
  });
  await batch.commit();
  console.log('');
  console.log('OK: nota ' + noteRef.id + ' creada en estado DRAFT con ' + forNote.length + ' lineas.');
})().catch((e) => { console.error('ERROR:', e.message); process.exit(1); });
