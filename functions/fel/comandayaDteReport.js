// functions/fel/comandayaDteReport.js
//
// Avisa a ComandaYa cada factura que BakeOS certifica con un NIT de Sabreopan, para que su
// contador por NIT (`FelProfiles.dtesConsumidos`) cuadre con lo que Digifact descuenta. En
// Sabreopan se cobra y se factura en BakeOS, pero el saldo de DTEs de cada NIT se vigila desde
// ComandaYa, que es donde se cargan los paquetes.
//
// ComandaYa es idempotente por número de autorización, así que reintentar es seguro. El aviso
// NUNCA tumba la factura: si ComandaYa no contesta, la factura ya está emitida y el DTE queda
// con `comandayaReportado = false` para que el reintento programado lo vuelva a mandar.

const { defineSecret } = require("firebase-functions/params");
const { getFirestore } = require("firebase-admin/firestore");

/** La misma clave del puente ComandaYa → BakeOS, usada en la dirección contraria. */
const INGEST_KEY = defineSecret("COMANDAYA_INGEST_KEY");

const CONFIG_PATH = "config/comandayaIntegration";
const DEFAULT_URL = "https://us-central1-orderapp-e0c31.cloudfunctions.net/registrarDteBakeos";

let configCache = null;
let configCachedAt = 0;

async function integracion() {
  if (configCache && Date.now() - configCachedAt < 5 * 60 * 1000) return configCache;
  const snap = await getFirestore().doc(CONFIG_PATH).get();
  configCache = snap.exists ? snap.data() : {};
  configCachedAt = Date.now();
  return configCache;
}

/** Solo la empresa conectada a ComandaYa, y solo facturas reales: las de pruebas no gastan DTEs. */
async function debeReportarse(companyId, dte) {
  const config = await integracion();
  return config.enabled !== false && config.companyId === companyId && dte.ambiente === "PRODUCCION";
}

/**
 * Manda el aviso de un DTE certificado y deja marcado el resultado. Nunca lanza.
 * @returns {Promise<boolean>} true si ComandaYa lo registró (o ya lo tenía).
 */
async function reportarDte(companyId, dteId, dte) {
  const ref = getFirestore().doc(`companies/${companyId}/dtes/${dteId}`);
  try {
    if (!(await debeReportarse(companyId, dte))) return false;
    const config = await integracion();
    const resp = await fetch(config.dteRegisterUrl || DEFAULT_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-bakeos-key": INGEST_KEY.value() },
      body: JSON.stringify({
        uuid: dte.uuid,
        nit: dte.emisorNit,
        serie: dte.serie,
        numero: dte.numero,
        total: dte.granTotal,
        fechaCertificacion: dte.fechaCertificacion,
      }),
      signal: AbortSignal.timeout(15000),
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${(await resp.text()).slice(0, 200)}`);
    await ref.set({ comandayaReportado: true, comandayaError: "" }, { merge: true });
    return true;
  } catch (e) {
    console.warn(`FEL ${companyId}/${dteId}: no se pudo avisar a ComandaYa:`, e.message);
    await ref.set({ comandayaReportado: false, comandayaError: String(e.message).slice(0, 300) }, { merge: true })
      .catch(() => {});
    return false;
  }
}

/** Reintenta los avisos que fallaron. Lo llama el job de reintentos cada 5 minutos. */
async function reportarPendientes() {
  const config = await integracion();
  if (!config.companyId || config.enabled === false) return;
  // Sin filtrar por estado: una factura anulada después también gastó su DTE en Digifact.
  // La marca solo existe en DTEs certificados, así que no hace falta más.
  const snap = await getFirestore().collection(`companies/${config.companyId}/dtes`)
    .where("comandayaReportado", "==", false)
    .limit(20).get();
  for (const doc of snap.docs) await reportarDte(config.companyId, doc.id, doc.data());
}

module.exports = { reportarDte, reportarPendientes, debeReportarse, INGEST_KEY };
