// functions/fel/felCredenciales.js
//
// Credenciales de API de Digifact, UNA POR NIT EMISOR (Digifact lo confirmó por escrito a
// ComandaYa el 2026-08-11: cada NIT tiene su usuario y contraseña, en TEST y en PRODUCCIÓN).
//
// ── Dónde viven ────────────────────────────────────────────────────────────
// En la colección de nivel superior `FelCredentials`, NO bajo `companies/{c}/...`:
// firestore.rules deja leer y escribir `companies/{companyId}/{document=**}` a cualquier
// usuario de la empresa, y una regla más específica no puede quitar ese permiso. Guardar
// ahí la contraseña la dejaría legible para todo el personal. `FelCredentials` no tiene
// regla, así que queda cerrada para la app y solo la alcanza el Admin SDK.
//
// El token se cachea en el mismo documento: al cargar las credenciales ya viene, y la
// certificación no paga una lectura extra.

const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { HttpsError } = require("firebase-functions/v2/https");
const { DigifactClient } = require("./digifactClient");

const COLECCION = "FelCredentials";

const URLS = {
  TEST: "https://testnucgt.digifact.com/api",
  // En producción la forma corta .../api responde 404.
  PRODUCCION: "https://nucgt.digifact.com/gt.com.apinuc/api",
};

function docId(companyId, profileId) {
  return `${companyId}__${profileId}`;
}

function referencia(companyId, profileId) {
  return getFirestore().collection(COLECCION).doc(docId(companyId, profileId));
}

/** Credenciales con las que certifica un perfil fiscal, o null si no tiene. */
async function cargarCredenciales(companyId, profileId) {
  const ref = referencia(companyId, profileId);
  const snap = await ref.get();
  if (!snap.exists) return null;
  const d = snap.data();
  if (!d.usuario || !d.password || !d.taxId) return null;
  const ambiente = d.ambiente === "PRODUCCION" ? "PRODUCCION" : "TEST";
  return {
    baseUrl: d.baseUrl || URLS[ambiente],
    country: d.country || "GT",
    taxId: d.taxId,
    user: d.usuario,
    password: d.password,
    ambiente,
    tokenRef: ref,
    tokenCacheado: d.token && d.expiresAt ? { token: d.token, expiresAt: d.expiresAt, identidad: d.identidad } : null,
  };
}

/**
 * Guarda o reemplaza las credenciales de un perfil. Solo la llaman las Cloud Functions; la
 * contraseña nunca vuelve a salir hacia la app.
 */
async function guardarCredenciales({ companyId, profileId, nit, usuario, password, ambiente }) {
  const amb = ambiente === "PRODUCCION" ? "PRODUCCION" : "TEST";
  await referencia(companyId, profileId).set({
    companyId,
    profileId,
    taxId: String(nit || "").replace(/[^0-9Kk]/g, "").toUpperCase(),
    usuario: String(usuario).trim(),
    password: String(password),
    country: "GT",
    ambiente: amb,
    baseUrl: URLS[amb],
    // Credenciales nuevas invalidan cualquier token cacheado.
    token: FieldValue.delete(),
    expiresAt: FieldValue.delete(),
    identidad: FieldValue.delete(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
}

/** Cliente autenticado con las credenciales del perfil, o error claro si no tiene. */
async function clienteParaPerfil(companyId, profileId) {
  const c = await cargarCredenciales(companyId, profileId);
  // Configuración, no falla transitoria: `failed-precondition` hace que no se reintente sola.
  if (!c) throw new HttpsError("failed-precondition", "Este NIT no tiene credenciales de Digifact configuradas.");
  return new DigifactClient(c);
}

module.exports = { cargarCredenciales, guardarCredenciales, clienteParaPerfil, COLECCION, URLS };
