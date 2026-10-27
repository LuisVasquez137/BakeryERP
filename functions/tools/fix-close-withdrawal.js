/**
 * Registra el retiro que no se pudo hacer al cerrar (quien cerró no tenía permiso): ajusta
 * closingWithdrawal y carriedOutCash, que es lo que espera la siguiente apertura de esa caja.
 * Uso (desde functions/): node tools/fix-close-withdrawal.js <sessionId>=<lo que quedó> ... [--apply]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const COMPANY = 'tiO54slG1n9sk6k2ePCD';
const apply = process.argv.includes('--apply');
const r2 = (v) => Math.round(v * 100) / 100;
(async () => {
  const c = db.collection('companies').doc(COMPANY);
  for (const pair of process.argv.slice(2).filter((a) => a.includes('='))) {
    const [id, leftText] = pair.split('=');
    const left = Number(leftText);
    const ref = c.collection('cashSessions').doc(id);
    const s = (await ref.get()).data();
    if (!s || s.status !== 'CLOSED') { console.log(id, 'no está cerrada, se omite'); continue; }
    const counted = s.countedCash || 0;
    if (left < 0 || left > counted + 0.004) { console.log(id, 'lo que quedó no puede pasar de lo contado', counted); continue; }
    const later = (await c.collection('cashSessions').where('registerId', '==', s.registerId).get()).docs
      .filter((d) => d.id !== id && (d.get('openedAt') || 0) > (s.closedAt || 0));
    if (later.length) { console.log(id, 'ya hay un turno abierto después en esa caja, se omite'); continue; }
    const withdrawal = r2(counted - left);
    const note = `Retiro de Q ${withdrawal.toFixed(2)} registrado después del cierre (quien cerró no tenía permiso de retirar).`;
    console.log(id, s.registerName, { counted, antes: { closingWithdrawal: s.closingWithdrawal, carriedOutCash: s.carriedOutCash }, despues: { closingWithdrawal: withdrawal, carriedOutCash: r2(left) } });
    if (apply) await ref.update({ closingWithdrawal: withdrawal, carriedOutCash: r2(left), notes: [s.notes, note].filter(Boolean).join(' · ') });
  }
  console.log(apply ? 'Aplicado.' : 'Simulación: agrega --apply para guardar.');
})();
