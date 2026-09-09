/**
 * Borra las notas de despacho que quedaron como residuo del bug de duplicacion
 * del reparto por turnos (corregido en agosto con sourceOrderAllocations +
 * ProductionLot.isShiftSplit).
 *
 * Que busca: notas NUNCA entregadas (DRAFT o READY) de un pedido cuyo lote fue
 * un reparto por turnos, donde el MISMO pedido genero dos notas -una por turno-
 * con el pedido entero en las dos. Antes del arreglo, cada turno despachaba el
 * pedido completo en vez de su porcion.
 *
 * Por que borrar y no cancelar: `cancelNote` suma `packedQuantity` al stock de
 * terminado de la planta, porque una nota real cancelada es pan que existe y se
 * queda ahi. Estas notas nunca correspondieron a pan que se empacara: cancelarlas
 * ingresaria miles de unidades fantasma al inventario de hoy. No documentan nada
 * que haya viajado -ninguna paso de DRAFT/READY- asi que borrarlas no pierde
 * historia, y sacarlas limpia los KPIs de despacho y `aggregateDispatchInRange`.
 *
 * Guardas (aborta sin tocar nada si alguna falla):
 *   - Solo status DRAFT o READY. Cualquier nota que haya viajado se respeta.
 *   - Solo notas anteriores a CUTOFF (el arreglo ya estaba en produccion despues).
 *   - Backup a JSON de encabezado + lineas ANTES de borrar.
 *
 * Uso:
 *   node cleanup-duplicated-dispatch-notes.js          -> reporta (dry run)
 *   node cleanup-duplicated-dispatch-notes.js --apply  -> borra
 */
const fs = require('fs');
const path = require('path');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();

const COMPANY = 'tiO54slG1n9sk6k2ePCD';
const APPLY = process.argv.includes('--apply');

/** El arreglo del reparto por turnos ya estaba en produccion el 29/08/2026. */
const CUTOFF = new Date('2026-08-29T00:00:00-06:00').getTime();
const KEEPABLE = ['DRAFT', 'READY'];

const c = db.collection('companies').doc(COMPANY);
const ms = (v) => (v && v.toMillis) ? v.toMillis() : (typeof v === 'number' ? v : 0);
const day = (v) => { const m = ms(v); return m ? new Date(m - 6 * 3600e3).toISOString().slice(0, 10) : '-'; };

(async () => {
  const snap = await c.collection('dispatchNotes').get();
  const all = snap.docs.map((d) => Object.assign({ id: d.id }, d.data()));

  const candidates = all.filter((n) =>
    KEEPABLE.includes(n.status) && ms(n.productionDate) < CUTOFF
  );

  // Un pedido cuyas notas sobrevivientes son TODAS sin entregar y anteriores al
  // arreglo. Si alguna nota de ese pedido llego a viajar, el dia se respeta entero.
  const byOrder = {};
  all.forEach((n) => { (byOrder[n.sourceOrderId] || (byOrder[n.sourceOrderId] = [])).push(n); });

  const toDelete = [];
  const skipped = [];
  for (const n of candidates) {
    const siblings = byOrder[n.sourceOrderId] || [];
    const anyTravelled = siblings.some((s) => !KEEPABLE.includes(s.status));
    if (anyTravelled) {
      skipped.push({ note: n, why: 'otra nota del mismo pedido si viajo' });
      continue;
    }
    toDelete.push(n);
  }

  console.log('Notas candidatas (DRAFT/READY anteriores al ' + day(CUTOFF) + '): ' + candidates.length);
  console.log('A borrar: ' + toDelete.length + '   Respetadas: ' + skipped.length);
  if (skipped.length) {
    console.log('\nRespetadas:');
    skipped.forEach((s) => console.log('   ' + day(s.note.productionDate) + '  ' +
      s.note.destinationBranchName + '  ' + s.note.status + '  -> ' + s.why));
  }

  // Backup completo (encabezado + lineas) antes de tocar nada.
  const backup = [];
  let totalLines = 0, totalUnits = 0;
  console.log('\nDetalle:');
  for (const n of toDelete) {
    const lines = (await c.collection('dispatchNotes').doc(n.id).collection('lines').get())
      .docs.map((d) => Object.assign({ id: d.id }, d.data()));
    totalLines += lines.length;
    const units = lines.reduce((s, l) => s + (l.packedQuantity || 0), 0);
    totalUnits += units;
    backup.push({ note: n, lines: lines });
    console.log('   ' + day(n.productionDate) + '  ' + String(n.destinationBranchName).padEnd(20) +
      ' turno=' + String(n.shiftName).padEnd(22) + ' ' + String(n.status).padEnd(6) +
      ' lineas=' + String(lines.length).padStart(3) + ' unidades=' + units);
  }
  console.log('\nTOTAL: ' + toDelete.length + ' notas, ' + totalLines + ' lineas, ' + totalUnits + ' unidades');

  if (!APPLY) {
    console.log('\n[dry run] Nada borrado. Repeti con --apply.');
    return;
  }

  const file = path.join(__dirname,
    'backup_dispatch_notes_' + new Date().toISOString().replace(/[:.]/g, '-') + '.json');
  fs.writeFileSync(file, JSON.stringify(backup, null, 1));
  console.log('\nBackup guardado en ' + path.basename(file));

  let done = 0;
  for (const entry of backup) {
    const ref = c.collection('dispatchNotes').doc(entry.note.id);
    // Las lineas primero: si algo falla a media pasada, queda el encabezado como
    // rastro de que esa nota existia, en vez de un encabezado huerfano invisible.
    for (const l of entry.lines) await ref.collection('lines').doc(l.id).delete();
    await ref.delete();
    done++;
  }
  console.log('OK: ' + done + ' notas borradas.');
})().catch((e) => { console.error('ERROR:', e.message); process.exit(1); });
