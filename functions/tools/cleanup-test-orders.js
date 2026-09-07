/**
 * Borrado de los pedidos de prueba del experimento de despacho por turnos
 * (domingos 20/09, 27/09 y 04/10 de 2026).
 *
 * Por qué borrado y no cancelación: la app bloquea cancelar un pedido cuyo lote
 * ya está IN_PROGRESS, y aunque se pudiera, un pedido CANCELLED se queda
 * visible en las listas para siempre. Estos son datos de prueba: no deben dejar
 * rastro ni alimentar las cantidades sugeridas.
 *
 * Uso:
 *   node _cleanup_test_orders.js          → solo reporta (dry run)
 *   node _cleanup_test_orders.js --apply  → borra
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();

const APPLY = process.argv.includes('--apply');
const COMPANY = 'tiO54slG1n9sk6k2ePCD';

const ORDER_IDS = [
  '7OM8ozIyZ5gSlccT4NQW', // 2026-09-20 Calvario -> Bombero
  'J2altGmWxPSK1GS1EXX9', // 2026-09-20 Democracia -> Bombero
  'INUNHytr5EC6Ce09BEfW', // 2026-09-27 Democracia -> Bombero
  'YfjsyRCadbXn9oTUMqFZ', // 2026-09-27 Salida De Almolonga -> Bombero
  '5e1o2qsY8HTkNgv0rUER', // 2026-10-04 Calvario -> Bombero
  'i5mKPaNaXC3wGPhtLwRb'  // 2026-10-04 Democracia -> Bombero
];

const targets = new Set(ORDER_IDS);
const problems = [];
const plan = { orders: [], lots: [], notes: [], patterns: [], patternEdits: [], notifications: [] };

const c = db.collection('companies').doc(COMPANY);

/** Borra una subcolección completa y devuelve cuántos docs eliminó. */
async function deleteSubcollection(docRef, name) {
  const snap = await docRef.collection(name).get();
  for (const d of snap.docs) await d.ref.delete();
  return snap.size;
}

(async () => {
  // ── 1. Pedidos ──────────────────────────────────────────────────────────
  for (const id of ORDER_IDS) {
    const snap = await c.collection('productionOrders').doc(id).get();
    if (!snap.exists) { problems.push('pedido inexistente: ' + id); continue; }
    const lines = await snap.ref.collection('lines').get();
    plan.orders.push({ id, data: snap.data(), lineCount: lines.size });
  }

  // ── 2. Lotes: solo los que NO contienen ningún pedido real ──────────────
  const lots = await c.collection('productionLots').get();
  for (const l of lots.docs) {
    const sources = l.data().sourceOrderIds || [];
    if (!sources.some(id => targets.has(id))) continue;
    const foreign = sources.filter(id => !targets.has(id));
    if (foreign.length) {
      problems.push('lote ' + l.id + ' también contiene pedidos reales: ' + foreign.join(', '));
      continue;
    }
    if (l.data().status === 'COMPLETED') {
      problems.push('lote ' + l.id + ' está COMPLETED (ya descontó insumos) — requiere revisión manual');
      continue;
    }
    const ll = await l.ref.collection('lines').get();
    const sponge = ll.docs.filter(x => Number(x.data().spongeGramsUsed || 0) > 0);
    if (sponge.length) {
      problems.push('lote ' + l.id + ' consumió esponja de ' + sponge.length + ' lote(s) de preparación');
      continue;
    }
    plan.lots.push({ id: l.id, status: l.data().status, lineCount: ll.size });
  }

  // ── 3. Notas de despacho ────────────────────────────────────────────────
  const runs = await c.collection('deliveryRuns').get();
  const notes = await c.collection('dispatchNotes').get();
  for (const n of notes.docs) {
    const nd = n.data();
    if (!targets.has(nd.sourceOrderId)) continue;
    if (!['DRAFT', 'READY', 'CANCELLED'].includes(nd.status)) {
      problems.push('nota ' + n.id + ' está en ' + nd.status + ' (ya salió a reparto o se recibió)');
      continue;
    }
    const inRun = runs.docs.filter(r => (r.data().dispatchNoteIds || []).includes(n.id));
    if (inRun.length) {
      problems.push('nota ' + n.id + ' está asignada al reparto ' + inRun.map(r => r.id).join(', '));
      continue;
    }
    const nl = await n.ref.collection('lines').get();
    plan.notes.push({ id: n.id, status: nd.status, lineCount: nl.size });
  }

  // ── 4. Patrones de sugerencia ───────────────────────────────────────────
  // Si el patrón solo tiene pedidos de prueba se borra entero: la app lo
  // reconstruye del historial real la próxima vez (rebuildOrderPatternFromHistory).
  // Si tuviera pedidos reales mezclados, se le quitan solo las muestras de prueba.
  const pats = await c.collection('orderPatterns').get();
  for (const p of pats.docs) {
    const recent = p.data().recentOrderIds || [];
    const qty = p.data().quantitiesByOrder || {};
    const dirty = [...new Set([...recent, ...Object.keys(qty)])].filter(id => targets.has(id));
    if (!dirty.length) continue;
    const clean = recent.filter(id => !targets.has(id));
    if (clean.length === 0) {
      plan.patterns.push({ id: p.id, recent });
    } else {
      const newQty = {};
      Object.keys(qty).forEach(k => { if (!targets.has(k)) newQty[k] = qty[k]; });
      plan.patternEdits.push({ id: p.id, keep: clean, remove: dirty, newQty });
    }
  }

  // ── 5. Avisos de la campana que apuntan a estos pedidos/lotes ───────────
  const lotIds = new Set(plan.lots.map(l => l.id));
  const notifs = await c.collection('notifications').get();
  for (const n of notifs.docs) {
    const t = n.data().targetId;
    if (t && (targets.has(t) || lotIds.has(t))) {
      plan.notifications.push({ id: n.id, type: n.data().type, targetId: t });
    }
  }

  // ── Reporte ─────────────────────────────────────────────────────────────
  console.log('MODO: ' + (APPLY ? 'BORRADO REAL' : 'simulación (dry run)'));
  console.log('\nPedidos (' + plan.orders.length + '):');
  plan.orders.forEach(o => console.log('  ' + o.id + ' | ' + o.data.sellingBranchName + ' -> ' +
    o.data.producingBranchName + ' | ' + o.data.status + ' | ' + o.lineCount + ' líneas'));
  console.log('Lotes (' + plan.lots.length + '):');
  plan.lots.forEach(l => console.log('  ' + l.id + ' | ' + l.status + ' | ' + l.lineCount + ' líneas'));
  console.log('Notas de despacho (' + plan.notes.length + '):');
  plan.notes.forEach(n => console.log('  ' + n.id + ' | ' + n.status + ' | ' + n.lineCount + ' líneas'));
  console.log('Patrones a borrar enteros (' + plan.patterns.length + '):');
  plan.patterns.forEach(p => console.log('  ' + p.id + ' | ' + JSON.stringify(p.recent)));
  console.log('Patrones a limpiar parcialmente (' + plan.patternEdits.length + '):');
  plan.patternEdits.forEach(p => console.log('  ' + p.id + ' | quita ' + JSON.stringify(p.remove) +
    ' | conserva ' + JSON.stringify(p.keep)));
  console.log('Avisos de campana (' + plan.notifications.length + '):');
  plan.notifications.forEach(n => console.log('  ' + n.id + ' | ' + n.type + ' -> ' + n.targetId));

  if (problems.length) {
    console.log('\n*** PROBLEMAS (nada se borró) ***');
    problems.forEach(p => console.log('  - ' + p));
    process.exit(1);
  }

  if (!APPLY) {
    console.log('\nSin cambios. Volvé a correr con --apply para borrar.');
    process.exit(0);
  }

  // ── Respaldo antes de borrar ────────────────────────────────────────────
  // Un volcado plano de cada documento + sus líneas. No restaura solo, pero
  // permite reconstruir a mano si algo salió mal.
  const backup = { company: COMPANY, exportedAt: new Date().toISOString(), orders: [], lots: [], notes: [], patterns: [], notifications: [] };
  const dump = async (ref) => {
    const d = await ref.get();
    const lines = await ref.collection('lines').get();
    return { id: ref.id, data: d.data(), lines: lines.docs.map(x => ({ id: x.id, data: x.data() })) };
  };
  for (const o of plan.orders) backup.orders.push(await dump(c.collection('productionOrders').doc(o.id)));
  for (const l of plan.lots) backup.lots.push(await dump(c.collection('productionLots').doc(l.id)));
  for (const n of plan.notes) backup.notes.push(await dump(c.collection('dispatchNotes').doc(n.id)));
  for (const p of plan.patterns) {
    const d = await c.collection('orderPatterns').doc(p.id).get();
    backup.patterns.push({ id: p.id, data: d.data() });
  }
  for (const n of plan.notifications) {
    const d = await c.collection('notifications').doc(n.id).get();
    backup.notifications.push({ id: n.id, data: d.data() });
  }
  const backupPath = require('path').join(__dirname, 'backup_pedidos_prueba.json');
  require('fs').writeFileSync(backupPath, JSON.stringify(backup, null, 2), 'utf8');
  console.log('\nRespaldo escrito en ' + backupPath);

  // ── Borrado ─────────────────────────────────────────────────────────────
  console.log('\nBorrando...');
  for (const n of plan.notes) {
    const ref = c.collection('dispatchNotes').doc(n.id);
    const k = await deleteSubcollection(ref, 'lines');
    await ref.delete();
    console.log('  nota ' + n.id + ' (+' + k + ' líneas)');
  }
  for (const l of plan.lots) {
    const ref = c.collection('productionLots').doc(l.id);
    const k = await deleteSubcollection(ref, 'lines');
    await ref.delete();
    console.log('  lote ' + l.id + ' (+' + k + ' líneas)');
  }
  for (const o of plan.orders) {
    const ref = c.collection('productionOrders').doc(o.id);
    const k = await deleteSubcollection(ref, 'lines');
    await ref.delete();
    console.log('  pedido ' + o.id + ' (+' + k + ' líneas)');
  }
  for (const p of plan.patterns) {
    await c.collection('orderPatterns').doc(p.id).delete();
    console.log('  patrón ' + p.id + ' (borrado entero)');
  }
  for (const p of plan.patternEdits) {
    await c.collection('orderPatterns').doc(p.id).update({
      recentOrderIds: p.keep,
      quantitiesByOrder: p.newQty,
      updatedAt: Date.now()
    });
    console.log('  patrón ' + p.id + ' (limpiado)');
  }
  for (const n of plan.notifications) {
    await c.collection('notifications').doc(n.id).delete();
    console.log('  aviso ' + n.id);
  }
  console.log('\nListo.');
  process.exit(0);
})().catch(e => { console.error('ERROR:', e.stack); process.exit(1); });
