/**
 * Recepción de ventas externas — hoy solo el restaurante de Sabreopan en ComandaYa.
 *
 * ComandaYa manda una orden ya cobrada; acá se convierte en una venta de BakeOS y se suma
 * al resumen del día. **BakeOS es quien escribe**, a propósito: su modelo `Sale` todavía
 * está cambiando, y si esta lógica viviera en ComandaYa cada ajuste de esquema obligaría a
 * redesplegar la app que le factura a 40 restaurantes que pagan.
 *
 * La venta entra como `PENDING_COLLECTION`: el cliente ya pagó (es ingreso real) pero el
 * dinero sigue con el mesero. Se vuelve `COMPLETED` y se atribuye a una gaveta cuando
 * alguien la recibe desde el POS. Cada sucursal tiene dos cajas y cualquiera puede recibir,
 * así que no hay forma de deducirlo: solo lo sabe quien recibe el dinero.
 *
 * Nunca toca `finishedStocks` ni el Kardex de pan: los platos no son productos de BakeOS.
 */

const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const { buildAddendum, accountedItemsFrom } = require("./externalAddendum");

const INGEST_KEY = defineSecret("COMANDAYA_INGEST_KEY");

/** Doc de configuración del puente. Único, porque esto es solo de Sabreopan. */
const CONFIG_PATH = "config/comandayaIntegration";

const PENDING_COLLECTION = "PENDING_COLLECTION";
const CANCELLED_STATUS = "CANCELLED";
const COMPLETED_STATUS = "COMPLETED";

/** La cuenta ya se facturó en ComandaYa: BakeOS no la vuelve a facturar. */
const INVOICE_EXTERNAL = "EXTERNA";
/** Estados de una factura emitida (o en camino) por BakeOS. */
const BAKEOS_INVOICE_STATES = ["SOLICITADA", "CERTIFICANDO", "CERTIFICADO", "PENDIENTE"];

// La config cambia casi nunca y la instancia se reutiliza entre invocaciones: cachearla
// evita una lectura por orden sin arriesgar quedarse pegado más de unos minutos.
let configCache = null;
let configCachedAt = 0;
const CONFIG_TTL_MS = 5 * 60 * 1000;

async function loadConfig(db) {
  const now = Date.now();
  if (configCache && now - configCachedAt < CONFIG_TTL_MS) return configCache;
  const snap = await db.doc(CONFIG_PATH).get();
  if (!snap.exists) throw new Error(`Falta el documento de configuración ${CONFIG_PATH}`);
  configCache = snap.data();
  configCachedAt = now;
  return configCache;
}

/** yyyy-MM-dd y el inicio del día, en la zona del negocio (no la del servidor, que es UTC). */
function businessDayOf(millis, timeZone) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", hour12: false
  }).formatToParts(new Date(millis));
  const get = (t) => parts.find((p) => p.type === t).value;
  const date = `${get("year")}-${get("month")}-${get("day")}`;
  const hour = parseInt(get("hour"), 10) % 24;
  // El inicio del día se deriva restando la hora local, sin librería de zonas horarias.
  //
  // ⚠️ Esto vale porque Guatemala es UTC-6 **exacto y sin horario de verano**: los minutos y
  // segundos son los mismos en UTC que en local, así que basta con descontar la hora. En una
  // zona con offset de media hora (India, Nepal) o con DST, este cálculo daría mal.
  //
  // Tiene que dar EXACTAMENTE lo mismo que `atStartOfDayIn(tz)` del lado Kotlin: los dos
  // escriben `dateMillis` en el mismo documento del día, y es el campo por el que los
  // reportes filtran el rango.
  const d = new Date(millis);
  const startMillis = millis
    - hour * 3600000
    - d.getMinutes() * 60000
    - d.getSeconds() * 1000
    - d.getMilliseconds();
  return { date, hour, startMillis };
}

/**
 * Lo que esta venta le aporta al resumen del día. Se guarda **dentro del documento de la
 * venta** (`appliedRollup`) porque es la única forma de revertir exactamente lo que se sumó
 * cuando la orden se reenvía corregida o se cancela: sin eso habría que releer las líneas y
 * confiar en que no cambiaron.
 */
function contributionOf(sale, lines, payments, day) {
  const products = {};
  for (const l of lines) {
    if (!l.productId) continue;
    const p = products[l.productId] || { name: l.productName, qty: 0, revenue: 0 };
    p.qty += l.quantity;
    p.revenue += l.subtotal;
    products[l.productId] = p;
  }
  // Una cuenta dividida se cobra con varios métodos y cada uno cuadra contra algo distinto
  // (el efectivo contra la gaveta, la tarjeta contra los vouchers). Guardar solo el método
  // dominante descuadraría el arqueo justo en las cuentas separadas, que es cuando pasa.
  const byMethod = {};
  for (const pay of payments) {
    byMethod[pay.method] = (byMethod[pay.method] || 0) + pay.amount;
  }
  return {
    total: sale.total,
    units: lines.reduce((a, l) => a + l.quantity, 0),
    byMethod,
    channel: sale.salesChannel,
    cashierId: sale.externalUserId || "",
    cashierName: sale.externalUserName || "",
    hour: String(day.hour),
    products
  };
}

/**
 * Aplica (o revierte, con sign = -1) una contribución sobre el resumen del día.
 *
 * ⚠️ Los desgloses van como **mapas anidados**, no como rutas con punto
 * (`channelRevenue.RESTAURANT`). Las rutas con punto solo las interpreta `update()`; con
 * `set(..., {merge:true})` Firestore crea un campo cuyo NOMBRE lleva el punto y el mapa real
 * queda vacío. Se descubrió con datos reales: `revenue` salía bien y `channelRevenue` en
 * cero, que es el modo de fallo más caro — parece que funciona.
 *
 * Anidado sí funciona: `merge` fusiona mapas en profundidad y `increment` opera a cualquier
 * nivel.
 */
function rollupUpdate(applied, sign, meta) {
  const inc = (n) => FieldValue.increment(sign * n);
  const paymentTotal = {}, paymentCount = {};
  for (const [method, amount] of Object.entries(applied.byMethod || {})) {
    paymentTotal[method] = inc(amount);
    paymentCount[method] = inc(1);
  }
  const cashierName = {}, cashierTotal = {}, cashierCount = {};
  if (applied.cashierId) {
    cashierName[applied.cashierId] = applied.cashierName;
    cashierTotal[applied.cashierId] = inc(applied.total);
    cashierCount[applied.cashierId] = inc(1);
  }
  const productName = {}, productQty = {}, productRevenue = {};
  for (const [pid, p] of Object.entries(applied.products || {})) {
    productName[pid] = p.name;
    productQty[pid] = inc(p.qty);
    productRevenue[pid] = inc(p.revenue);
  }
  return {
    id: meta.dailyId,
    branchId: meta.branchId,
    branchName: meta.branchName,
    businessDate: meta.date,
    dateMillis: meta.startMillis,
    revenue: inc(applied.total),
    // Un adicional suma dinero a una cuenta que ya contó como transacción: contarla otra vez
    // inflaría las transacciones del día y bajaría el ticket promedio.
    transactionCount: inc(applied.transactions ?? 1),
    unitsSold: inc(applied.units),
    hourTotal: { [applied.hour]: inc(applied.total) },
    channelRevenue: { [applied.channel]: inc(applied.total) },
    channelCount: { [applied.channel]: inc(1) },
    channelUnits: { [applied.channel]: inc(applied.units) },
    paymentTotal, paymentCount,
    cashierName, cashierTotal, cashierCount,
    productName, productQty, productRevenue,
    updatedAt: Date.now()
  };
}

function badRequest(res, message) {
  console.warn("ingestExternalSale rechazado:", message);
  res.status(400).json({ ok: false, error: message });
}

exports.ingestExternalSale = onRequest(
  { secrets: [INGEST_KEY], region: "us-central1", cors: false },
  async (req, res) => {
    if (req.method !== "POST") return res.status(405).json({ ok: false, error: "Usa POST" });

    // Clave compartida. Se compara completa y no por prefijo; es un secreto de servidor a
    // servidor, no una sesión de usuario.
    if ((req.get("x-bakeos-key") || "") !== INGEST_KEY.value()) {
      console.warn("ingestExternalSale: clave inválida");
      return res.status(401).json({ ok: false, error: "No autorizado" });
    }

    const payload = req.body || {};
    const externalId = payload.externalId;
    if (!externalId) return badRequest(res, "Falta externalId");
    if (!payload.externalBranchId) return badRequest(res, "Falta externalBranchId");

    const db = getFirestore();

    let config;
    try {
      config = await loadConfig(db);
    } catch (e) {
      console.error("ingestExternalSale: configuración ilegible", e);
      return res.status(500).json({ ok: false, error: e.message });
    }
    if (config.enabled === false) {
      return res.status(200).json({ ok: true, skipped: "integración apagada" });
    }

    const companyId = config.companyId;
    const branchId = (config.branchMap || {})[payload.externalBranchId];
    if (!companyId) return badRequest(res, "La configuración no trae companyId");
    if (!branchId) {
      // Que falle ruidoso: una sucursal sin mapear significa ventas que nadie va a ver.
      return badRequest(res, `Sucursal ${payload.externalBranchId} sin mapear en branchMap`);
    }

    const timeZone = config.timeZone || "America/Guatemala";
    const closedAt = Number(payload.closedAt) || Date.now();
    const day = businessDayOf(closedAt, timeZone);

    const branchSnap = await db.doc(`companies/${companyId}/branches/${branchId}`).get();
    const branchName = branchSnap.exists ? (branchSnap.data().name || "") : "";

    const saleRef = db.doc(`companies/${companyId}/sales/${externalId}`);
    const dailyId = `${branchId}_${day.date}`;
    const dailyRef = db.doc(`companies/${companyId}/salesDaily/${dailyId}`);

    const isCancelled = payload.status === CANCELLED_STATUS;
    const rawLines = Array.isArray(payload.lines) ? payload.lines : [];
    const lines = rawLines.map((l, i) => ({
      id: `l${i}`,
      saleId: externalId,
      productId: l.externalItemId || null,
      productName: l.name || "",
      quantity: Number(l.qty) || 0,
      unitPrice: Number(l.unitPrice) || 0,
      subtotal: Number(l.lineTotal) || 0,
      costAtSale: 0,
      priceSource: "EXTERNAL",
      stockDeducted: false
    }));

    // Métodos de pago ya normalizados por ComandaYa (CASH | CARD | TRANSFER).
    const payments = (Array.isArray(payload.payments) ? payload.payments : [])
      .map((p) => ({ method: p.method || "CASH", amount: Number(p.amount) || 0 }))
      .filter((p) => p.amount > 0);
    if (payments.length === 0) payments.push({ method: "CASH", amount: Number(payload.total) || 0 });
    // El método del encabezado es el de mayor monto: es el que se muestra en una sola línea.
    const dominantMethod = payments.reduce((a, b) => (b.amount > a.amount ? b : a)).method;

    // ComandaYa manda `billed`/`dteId` en cada envío, y reenvía la orden cuando la factura
    // después de cobrarla: así BakeOS se entera y no la factura otra vez.
    const billedExternally = payload.billed === true;

    const sale = {
      id: externalId,
      branchId,
      branchName,
      registerId: "",
      registerName: "",
      sessionId: "",
      cashierId: "",
      cashierName: "",
      subtotal: Number(payload.subtotal) || 0,
      discount: Number(payload.discountAmount) || 0,
      total: Number(payload.total) || 0,
      paymentMethod: dominantMethod,
      // Desglose completo como MAPA, no como lista de objetos: GitLive no deserializa
      // listas de objetos, y del otro lado esto se lee con `get<Map<String, Double>>`.
      paymentBreakdown: payments.reduce((acc, p) => {
        acc[p.method] = (acc[p.method] || 0) + p.amount;
        return acc;
      }, {}),
      collectedAt: 0,
      tenderedAmount: 0,
      changeAmount: 0,
      // Toda venta cobrada entra a la lista de caja, sin importar el método — incluidas las
      // apps de delivery. El ticket se imprime igual y alguien tiene que hacerse cargo de esa
      // venta; si no aparece, el ingreso queda en el aire y no cuadra contra nada.
      status: isCancelled ? CANCELLED_STATUS : PENDING_COLLECTION,
      lineCount: lines.length,
      linkedCustomerOrderId: "",
      source: "COMANDAYA",
      salesChannel: "RESTAURANT",
      externalId,
      externalUserId: payload.externalUserId || "",
      externalUserName: payload.externalUserName || "",
      externalTableLabel: payload.tableLabel || "",
      externalOrderNumber: String(payload.orderNumber || ""),
      externalOrderAt: Number(payload.orderCreatedAt) || 0,
      // El cargo de domicilio no viaja como línea: la factura lo agrega aparte, o saldría
      // por menos de lo que se cobró.
      externalDeliveryFee: Number(payload.deliveryFee) || 0,
      invoiceStatus: billedExternally ? INVOICE_EXTERNAL : "",
      externalDteId: billedExternally ? String(payload.dteId || "") : "",
      createdAt: closedAt
    };

    const contribution = contributionOf(sale, lines, payments, day);
    const meta = {
      dailyId, branchId, branchName,
      date: day.date, startMillis: day.startMillis
    };

    let previousLineCount = 0;
    let skipped = "";
    try {
      await db.runTransaction(async (tx) => {
        const existing = await tx.get(saleRef);
        const prev = existing.exists ? existing.data() : null;
        previousLineCount = prev ? (prev.lineCount || 0) : 0;

        // Ya recibida en caja: su dinero ya se sumó a una gaveta y la venta lleva quién la
        // recibió, el cobro y quizá su factura. Reescribirla la devolvía a "pendiente de
        // recibir" —reaparecía en el POS para cobrarse dos veces— y le borraba todo eso. Lo
        // único que se acepta de un reenvío es enterarse de que ComandaYa la facturó.
        if (prev && prev.status === COMPLETED_STATUS) {
          skipped = "ya recibida en caja";
          const patch = {};
          if (billedExternally && prev.invoiceStatus !== INVOICE_EXTERNAL) {
            const bakeosInvoiced = BAKEOS_INVOICE_STATES.includes(prev.invoiceStatus);
            // Facturada en los dos lados: no se pisa la de BakeOS, se deja la marca para que
            // alguien anule una de las dos.
            if (bakeosInvoiced) patch.invoiceConflict = true;
            else patch.invoiceStatus = INVOICE_EXTERNAL;
            patch.externalDteId = String(payload.dteId || "");
            if (bakeosInvoiced) console.error(`ingestExternalSale: ${externalId} facturada en BakeOS Y en ComandaYa`);
          }

          if (isCancelled) {
            // Reabrir la orden para agregar algo también pasa por acá (deja de estar "Pagado").
            // No se hace nada: si vuelve con más, entra como adicional en el siguiente envío.
            console.warn(`ingestExternalSale: ${externalId} dejó de estar pagada después de recibirse en caja`);
          } else {
            // Lo ya contabilizado: guardado desde el primer adicional; antes de eso, las líneas
            // con que se recibió. Se lee antes de escribir nada (regla de las transacciones).
            let accountedItems;
            if (Array.isArray(prev.accountedItems)) {
              accountedItems = Object.fromEntries(prev.accountedItems.map((i) => [i.key, i]));
            } else {
              const prevLines = await tx.get(saleRef.collection("lines"));
              accountedItems = accountedItemsFrom(prevLines.docs.map((d) => d.data()));
            }
            const accountedTotal = typeof prev.accountedTotal === "number" ? prev.accountedTotal : (Number(prev.total) || 0);
            const orderLabel = sale.externalOrderNumber ? `orden ${sale.externalOrderNumber}` : "";
            const ad = buildAddendum({ accountedTotal, accountedItems, newTotal: sale.total, newLines: lines, orderLabel });

            if (ad && ad.refund) {
              console.error(`ingestExternalSale: ${externalId} bajó Q${ad.refund} después de cobrarse: revisar devolución`);
            } else if (ad) {
              const n = (Number(prev.addendumCount) || 0) + 1;
              const adId = `${externalId}_ad${n}`;
              const adRef = db.doc(`companies/${companyId}/sales/${adId}`);
              const adLines = ad.lines.map((l, i) => ({
                id: `l${i}`, saleId: adId, productId: l.productId, productName: l.productName,
                quantity: l.quantity, unitPrice: l.unitPrice, subtotal: l.subtotal,
                costAtSale: 0, priceSource: "EXTERNAL", stockDeducted: false,
              }));
              const adSale = {
                ...sale,
                id: adId,
                externalId: adId,
                parentSaleId: externalId,
                subtotal: ad.subtotal,
                discount: ad.discount,
                total: ad.deltaTotal,
                paymentMethod: dominantMethod,
                paymentBreakdown: { [dominantMethod]: ad.deltaTotal },
                lineCount: adLines.length,
                externalTableLabel: `${sale.externalTableLabel || "Cuenta"} · adicional`,
                // El domicilio ya se cobró en la cuenta original; un cambio de monto entra
                // como renglón de ajuste.
                externalDeliveryFee: 0,
                status: PENDING_COLLECTION,
                createdAt: closedAt,
              };
              const adContribution = {
                ...contributionOf(adSale, adLines, [{ method: dominantMethod, amount: ad.deltaTotal }], day),
                transactions: 0,
              };
              tx.set(dailyRef, rollupUpdate(adContribution, +1, meta), { merge: true });
              tx.set(adRef, { ...adSale, appliedRollup: adContribution });
              for (const l of adLines) tx.set(adRef.collection("lines").doc(l.id), l);

              // Lo que queda contabilizado pasa a ser lo nuevo: un reenvío idéntico no crea
              // otro adicional. Lista y no mapa: un nombre con punto rompería la clave.
              patch.accountedTotal = sale.total;
              patch.accountedItems = Object.entries(ad.items).map(([key, i]) => ({ key, ...i }));
              patch.addendumCount = n;
              skipped = `ya recibida en caja; adicional ${adId} por Q${ad.deltaTotal}`;
            }
          }
          if (Object.keys(patch).length) tx.set(saleRef, patch, { merge: true });
          return;
        }

        // Idempotencia real: se revierte exactamente lo que esta venta había sumado antes
        // (guardado en `appliedRollup`) y se aplica lo nuevo. Un reintento con los mismos
        // datos se cancela solo; una corrección aplica la diferencia; una cancelación deja
        // el día como si la venta nunca hubiera existido.
        if (prev && prev.appliedRollup) {
          tx.set(dailyRef, rollupUpdate(prev.appliedRollup, -1, meta), { merge: true });
        }
        if (!isCancelled) {
          tx.set(dailyRef, rollupUpdate(contribution, +1, meta), { merge: true });
        }

        tx.set(saleRef, {
          ...sale,
          appliedRollup: isCancelled ? null : contribution
        });
      });
    } catch (e) {
      console.error(`ingestExternalSale: falló la transacción de ${externalId}`, e);
      return res.status(500).json({ ok: false, error: e.message });
    }

    if (skipped) {
      console.log(`ingestExternalSale: ${externalId} sin cambios (${skipped})`);
      return res.status(200).json({ ok: true, saleId: externalId, skipped });
    }

    // Las líneas van fuera de la transacción, con id determinista (`l0`, `l1`, …) para que
    // un reenvío las sobreescriba en vez de duplicarlas. Si la orden encogió, las sobrantes
    // se borran: quedarían sumando en cualquier pantalla que lea el detalle.
    const batch = db.batch();
    const linesCol = saleRef.collection("lines");
    for (const line of lines) batch.set(linesCol.doc(line.id), line);
    for (let i = lines.length; i < previousLineCount; i++) batch.delete(linesCol.doc(`l${i}`));
    await batch.commit();

    console.log(
      `ingestExternalSale: ${externalId} → ${branchName} ${day.date} ` +
      `(${isCancelled ? "CANCELADA" : `Q${sale.total}`})`
    );
    return res.status(200).json({ ok: true, saleId: externalId, branchId, day: day.date });
  }
);
