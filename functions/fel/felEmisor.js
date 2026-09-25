// functions/fel/felEmisor.js
//
// Qué NIT emite la factura de una venta. No todas las sucursales facturan al mismo NIT
// (Bombero, Democracia y Pacaja van al 593221-6; Calvario y Salida tienen otro), así que la
// identidad fiscal vive en "perfiles fiscales" y cada sucursal apunta al suyo:
//
//   companies/{c}/felProfiles/{pid}   → un NIT emisor con sus datos SAT (sin secretos)
//   companies/{c}/branches/{bid}      → felProfileId + felCodigoEstablecimiento
//
// Mismo modelo que ComandaYa: el código de establecimiento es de la SUCURSAL, porque es lo
// que distingue a Bombero (1) de Pacaja (2) dentro del mismo NIT ante la SAT.

const { HttpsError } = require("firebase-functions/v2/https");

const FEL_PROFILES = "felProfiles";

function limpiarNit(nit) {
  return String(nit || "").replace(/[^0-9Kk]/g, "").toUpperCase();
}

/**
 * Emisor para facturar en una sucursal. Lanza `failed-precondition` con un mensaje para la
 * cajera cuando la sucursal no factura (sin perfil, perfil inactivo).
 *
 * @returns {Promise<{profileId: string, emisor: object}>}
 */
async function resolverEmisor(db, companyId, branchId) {
  const branchSnap = await db.doc(`companies/${companyId}/branches/${branchId}`).get();
  const branch = branchSnap.exists ? branchSnap.data() : null;
  if (!branch) throw new HttpsError("not-found", "La sucursal de la venta ya no existe.");

  const profileId = String(branch.felProfileId || "").trim();
  if (!profileId) {
    throw new HttpsError("failed-precondition",
      `La sucursal ${branch.name || ""} no tiene un NIT asignado para facturar.`);
  }
  const perfilSnap = await db.doc(`companies/${companyId}/${FEL_PROFILES}/${profileId}`).get();
  if (!perfilSnap.exists) {
    throw new HttpsError("failed-precondition",
      `La sucursal ${branch.name || ""} apunta a un NIT que ya no existe.`);
  }
  const perfil = perfilSnap.data();
  if (perfil.active === false) {
    throw new HttpsError("failed-precondition", `La facturación del NIT ${perfil.nit || ""} está desactivada.`);
  }
  if (!perfil.nit) throw new HttpsError("failed-precondition", "El perfil fiscal no tiene NIT.");

  return {
    profileId,
    branchName: branch.name || "",
    emisor: {
      nit: limpiarNit(perfil.nit),
      nombreEmisor: perfil.nombreEmisor || "",
      nombreComercial: perfil.nombreComercial || perfil.nombreEmisor || branch.name || "",
      correo: perfil.correo || "",
      // La dirección del DTE es la del establecimiento (la sucursal), no la fiscal del NIT:
      // es la que el cliente ve en la factura.
      direccion: branch.felDireccion || branch.location || perfil.direccion || "CIUDAD",
      municipio: branch.felMunicipio || perfil.municipio || "Guatemala",
      departamento: branch.felDepartamento || perfil.departamento || "Guatemala",
      codigoPostal: perfil.codigoPostal || "01001",
      codigoEstablecimiento: String(branch.felCodigoEstablecimiento || perfil.codigoEstablecimiento || "1"),
      regimenISR: perfil.regimenISR || "OPCIONAL",
      afiliacionIVA: perfil.afiliacionIVA || "GEN",
    },
  };
}

module.exports = { resolverEmisor, limpiarNit, FEL_PROFILES };
