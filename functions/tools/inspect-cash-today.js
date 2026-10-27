/**
 * Solo lectura: turnos de caja de hoy de una sucursal, con sus totales, movimientos y el
 * desglose de las ventas por método, para revisar un cuadre que no cuadra.
 * Uso (desde functions/): node tools/inspect-cash-today.js [--branch=<texto del nombre>] [--since=2026-10-26T06:00:00Z]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (n) => { const a = process.argv.find((x) => x.startsWith(`--${n}=`)); return a ? a.slice(n.length + 3) : null; };
const COMPANY = arg('company') || 'tiO54slG1n9sk6k2ePCD';
const BRANCH = (arg('branch') || 'democracia').toLowerCase();
const SINCE = Date.parse(arg('since') || '2026-10-26T06:00:00Z');
const ms = (v) => v == null ? 0 : (typeof v === 'number' ? v : (v.toMillis ? v.toMillis() : 0));
const r2 = (v) => Math.round((v || 0) * 100) / 100;
(async () => {
  const c = db.collection('companies').doc(COMPANY);
  const branches = (await c.collection('branches').get()).docs.filter((d) => (d.get('name') || '').toLowerCase().includes(BRANCH));
  for (const b of branches) {
    console.log(`\n=== Sucursal ${b.get('name')} (${b.id})`);
    const sessions = (await c.collection('cashSessions').where('branchId', '==', b.id).get()).docs
      .filter((d) => ms(d.get('openedAt')) >= SINCE).sort((a, z) => ms(a.get('openedAt')) - ms(z.get('openedAt')));
    for (const s of sessions) {
      const d = s.data();
      console.log(`\n-- Turno ${s.id} ${d.registerName} ${d.status} abrió ${new Date(ms(d.openedAt)).toISOString()} cerró ${d.closedAt ? new Date(ms(d.closedAt)).toISOString() : '-'}`);
      const pick = ['openingBalance','openingExpected','carriedInCard','carriedInTransfer','totalCashSales','totalCardSales','totalTransferSales','totalPedidosYaSales','totalCreditSales','totalSales','totalMovementsIn','totalMovementsOut','expectedCash','countedCash','difference','saleCount','closingWithdrawal','carriedOutCash','carriedOutCard','carriedOutTransfer','voidedSaleCount','totalVoidedSales','totalVoidedNonCash','previousSessionId','notes'];
      console.log(Object.fromEntries(pick.filter((k) => d[k] !== undefined).map((k) => [k, typeof d[k] === 'number' ? r2(d[k]) : d[k]])));
      const exp = (d.openingBalance||0)+(d.totalCashSales||0)+(d.totalMovementsIn||0)-(d.totalMovementsOut||0);
      console.log('esperado efectivo (fórmula app):', r2(exp));
      const movs = (await s.ref.collection('movements').get()).docs;
      for (const m of movs) console.log('  mov', m.get('type'), r2(m.get('amount')), m.get('reason'));
      const sales = (await c.collection('sales').where('sessionId', '==', s.id).get()).docs;
      const byMethod = {}; const byStatus = {}; let sumBreak = {};
      for (const x of sales) {
        const v = x.data();
        byStatus[v.status] = (byStatus[v.status] || 0) + 1;
        const key = v.paymentMethod; byMethod[key] = r2((byMethod[key] || 0) + (v.total || 0));
        const br = (v.paymentBreakdown && Object.keys(v.paymentBreakdown).length) ? v.paymentBreakdown : { [v.paymentMethod]: v.total };
        if (v.status !== 'VOIDED') for (const [k, a] of Object.entries(br)) sumBreak[k] = r2((sumBreak[k] || 0) + a);
        if (v.paymentMethod !== 'CASH') console.log('  venta', x.id, v.status, v.paymentMethod, r2(v.total), JSON.stringify(v.paymentBreakdown || {}), 'cobro total', v.checkoutTotal, 'canal', v.salesChannel, 'factura', v.invoiceSerie || '-');
      }
      console.log('  ventas:', sales.length, byStatus, 'total por paymentMethod', byMethod, 'desglose (sin anuladas)', sumBreak);
    }
  }
})().catch((e) => { console.error(e); process.exit(1); });
