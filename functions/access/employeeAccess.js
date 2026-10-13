// functions/access/employeeAccess.js
//
// PIN personal de cada colaborador. El equipo de la sucursal entra con la cuenta de la
// sucursal (contraseña que solo sabe el dueño) y la PERSONA se identifica con su PIN.
//
// Callables:
//   empleadoFijarPin      — OWNER/ADMIN asigna o restablece el PIN de un colaborador
//   empleadoCambiarPin    — el colaborador cambia el suyo (pide el actual)
//   empleadoVerificarPin  — el equipo pregunta "¿este PIN es de esta persona?"
//
// ── Por qué el PIN vive aquí y no en Firestore al alcance de la app ─────────
// Un PIN de 4–6 dígitos tiene a lo sumo un millón de combinaciones: con el hash a la vista
// se prueba entero en segundos, con o sin sal. Por eso el hash está en `employeePins/`,
// colección que las reglas cierran por completo (solo el Admin SDK la toca), y cada intento
// pasa por aquí, que cuenta los fallos y bloquea.
//
// ── Por qué solo OWNER/ADMIN asignan PIN ────────────────────────────────────
// Quien pone el PIN de otro puede hacerse pasar por él. Si una encargada pudiera restablecer
// el PIN de una cajera, el registro de "quién cobró" dejaría de valer. Además el PIN que pone
// otro nace TEMPORAL: la primera vez que la persona lo usa, la app le pide cambiarlo, así
// nadie más lo conoce (y si alguien lo restablece a escondidas, el dueño de verdad nota que
// el suyo ya no entra).
//
// ── Dónde queda cada cosa ──────────────────────────────────────────────────
//   employeePins/{c}__{employeeId}       hash, sal, fallos, bloqueo, debeCambiar
//   companies/{c}/employees/{id}          pinStatus ("" | TEMPORARY | SET), pinUpdatedAt
//                                         — solo para mostrar; lo escribe solo esta función

const crypto = require("crypto");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { getFirestore } = require("firebase-admin/firestore");

const REGION = "us-central1";
const OPTS = { region: REGION, timeoutSeconds: 30, memory: "256MiB" };
const MAX_FALLOS = 5;
const BLOQUEO_MS = 10 * 60 * 1000;

const PIN_TEMPORAL = "TEMPORARY";
const PIN_LISTO = "SET";

const db = () => getFirestore();
const pinRef = (companyId, employeeId) => db().doc(`employeePins/${companyId}__${employeeId}`);
const empleadoRef = (companyId, employeeId) => db().doc(`companies/${companyId}/employees/${employeeId}`);

// ── Reglas del PIN (puras, con pruebas en employeeAccess.test.js) ──────────

/** Devuelve el motivo por el que el PIN no sirve, o null si sirve. */
function motivoPinInvalido(pin) {
  if (typeof pin !== "string" || !/^\d{4}$/.test(pin)) return "El PIN debe tener 4 números.";
  if (/^(\d)\1+$/.test(pin)) return "El PIN no puede ser un mismo número repetido.";
  const digitos = [...pin].map(Number);
  const pasos = digitos.slice(1).map((d, i) => d - digitos[i]);
  if (pasos.every((p) => p === 1) || pasos.every((p) => p === -1)) {
    return "El PIN no puede ser una serie seguida (1234, 4321…).";
  }
  return null;
}

function hashPin(pin, sal) {
  return crypto.scryptSync(pin, Buffer.from(sal, "base64"), 32).toString("base64");
}

function pinCoincide(pin, sal, hashGuardado) {
  const a = Buffer.from(hashPin(pin, sal), "base64");
  const b = Buffer.from(String(hashGuardado || ""), "base64");
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function nuevoHash(pin) {
  const sal = crypto.randomBytes(16).toString("base64");
  return { sal, hash: hashPin(pin, sal) };
}

/**
 * Resultado de un intento contra el estado guardado. Puro: decide, no escribe.
 * `estado` = { fallos, bloqueadoHasta, sal, hash }.
 */
function evaluarIntento(estado, pin, ahora) {
  if (Number(estado.bloqueadoHasta || 0) > ahora) {
    return { ok: false, bloqueado: true, bloqueadoHasta: Number(estado.bloqueadoHasta), fallos: Number(estado.fallos || 0) };
  }
  if (pinCoincide(pin, estado.sal, estado.hash)) return { ok: true, fallos: 0, bloqueadoHasta: 0 };
  const fallos = Number(estado.fallos || 0) + 1;
  if (fallos >= MAX_FALLOS) return { ok: false, bloqueado: true, fallos: 0, bloqueadoHasta: ahora + BLOQUEO_MS };
  return { ok: false, bloqueado: false, fallos, bloqueadoHasta: 0, restantes: MAX_FALLOS - fallos };
}

// ── Autorización ───────────────────────────────────────────────────────────

const ROLES_PERSONAL = ["OWNER", "ADMIN", "MANAGER", "EMPLOYEE"];

/** Personal aprobado y activo de la empresa (el mismo criterio que firestore.rules). */
async function personalDeLaEmpresa(request, companyId) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Iniciá sesión.");
  if (!companyId || typeof companyId !== "string") throw new HttpsError("invalid-argument", "Falta la empresa.");
  const snap = await db().doc(`users/${request.auth.uid}`).get();
  const u = snap.exists ? snap.data() : null;
  const habilitado = u && u.isApproved !== false && u.isActive !== false;
  if (!u || u.orgId !== companyId || !ROLES_PERSONAL.includes(u.role) || !habilitado) {
    throw new HttpsError("permission-denied", "No pertenecés a esta empresa.");
  }
  return { id: request.auth.uid, ...u };
}

async function colaborador(companyId, employeeId, { soloActivos }) {
  if (!employeeId || typeof employeeId !== "string") throw new HttpsError("invalid-argument", "Falta el colaborador.");
  const snap = await empleadoRef(companyId, employeeId).get();
  if (!snap.exists) throw new HttpsError("not-found", "No existe ese colaborador.");
  const e = snap.data();
  const status = e.status || (e.active === false ? "FIRED" : "ACTIVE");
  if (["FIRED", "RESIGNED"].includes(status)) throw new HttpsError("failed-precondition", "Ese colaborador ya no trabaja en la empresa.");
  if (soloActivos && status !== "ACTIVE") throw new HttpsError("failed-precondition", "Ese colaborador no está activo (suspendido o de vacaciones).");
  return { id: employeeId, ...e };
}

function exigirPinValido(pin) {
  const motivo = motivoPinInvalido(pin);
  if (motivo) throw new HttpsError("invalid-argument", motivo);
}

/**
 * Comprueba el PIN dentro de una transacción (dos equipos probando a la vez no se pisan
 * el contador de fallos). Las escrituras se hacen en la transacción y el error se lanza
 * DESPUÉS: si se lanzara adentro, la transacción se desharía y el fallo no contaría.
 */
async function comprobarPin(companyId, employeeId, pin) {
  const ref = pinRef(companyId, employeeId);
  const r = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return { sinPin: true };
    const estado = snap.data();
    const ahora = Date.now();
    const res = evaluarIntento(estado, String(pin || ""), ahora);
    // Mientras dura el bloqueo no se toca nada: el intento ni se evalúa.
    const yaBloqueado = Number(estado.bloqueadoHasta || 0) > ahora;
    const cambio = res.fallos !== Number(estado.fallos || 0) || res.bloqueadoHasta !== Number(estado.bloqueadoHasta || 0);
    if (!yaBloqueado && cambio) tx.update(ref, { fallos: res.fallos, bloqueadoHasta: res.bloqueadoHasta });
    return { ...res, debeCambiar: estado.debeCambiar === true };
  });
  if (r.sinPin) throw new HttpsError("failed-precondition", "Este colaborador todavía no tiene PIN. Pedíselo al dueño.");
  if (r.bloqueado) {
    const min = Math.max(1, Math.ceil((r.bloqueadoHasta - Date.now()) / 60000));
    throw new HttpsError("resource-exhausted", `Demasiados intentos. Probá de nuevo en ${min} min.`);
  }
  if (!r.ok) {
    throw new HttpsError("permission-denied", `PIN incorrecto. ${r.restantes === 1 ? "Queda 1 intento" : `Quedan ${r.restantes} intentos`}.`);
  }
  return r;
}

async function guardarPin(companyId, employeeId, pin, debeCambiar, actorId) {
  const { sal, hash } = nuevoHash(pin);
  const ahora = Date.now();
  await pinRef(companyId, employeeId).set({
    companyId, employeeId, sal, hash,
    fallos: 0, bloqueadoHasta: 0, debeCambiar,
    actualizadoEn: ahora, actualizadoPor: actorId,
  });
  await empleadoRef(companyId, employeeId).update({
    pinStatus: debeCambiar ? PIN_TEMPORAL : PIN_LISTO,
    pinUpdatedAt: ahora,
  });
}

// ── Callables ──────────────────────────────────────────────────────────────

exports.empleadoFijarPin = onCall(OPTS, async (request) => {
  const { companyId, employeeId, pin, escritoPorColaborador } = request.data || {};
  const user = await personalDeLaEmpresa(request, companyId);
  if (!["OWNER", "ADMIN"].includes(user.role)) {
    throw new HttpsError("permission-denied", "Solo el dueño o un administrador asigna PINs.");
  }
  await colaborador(companyId, employeeId, { soloActivos: false });
  exigirPinValido(pin);
  // Si el colaborador lo escribió él mismo frente al dueño, nadie más lo conoce: no hace
  // falta obligarlo a cambiarlo.
  const debeCambiar = escritoPorColaborador !== true;
  await guardarPin(companyId, employeeId, pin, debeCambiar, user.id);
  return { ok: true, debeCambiar };
});

exports.empleadoCambiarPin = onCall(OPTS, async (request) => {
  const { companyId, employeeId, pinActual, pinNuevo } = request.data || {};
  const user = await personalDeLaEmpresa(request, companyId);
  await colaborador(companyId, employeeId, { soloActivos: true });
  await comprobarPin(companyId, employeeId, pinActual);
  exigirPinValido(pinNuevo);
  if (pinNuevo === pinActual) throw new HttpsError("invalid-argument", "El PIN nuevo tiene que ser distinto del actual.");
  await guardarPin(companyId, employeeId, pinNuevo, false, user.id);
  return { ok: true };
});

exports.empleadoVerificarPin = onCall(OPTS, async (request) => {
  const { companyId, employeeId, pin } = request.data || {};
  await personalDeLaEmpresa(request, companyId);
  const e = await colaborador(companyId, employeeId, { soloActivos: true });
  const r = await comprobarPin(companyId, employeeId, pin);
  return {
    employeeId: e.id,
    name: e.name || "",
    branchId: e.branchId || "",
    photoUrl: e.photoUrl || "",
    permissions: Array.isArray(e.permissions) ? e.permissions : [],
    debeCambiar: r.debeCambiar,
  };
});

// Para las pruebas.
exports._internos = { motivoPinInvalido, hashPin, pinCoincide, nuevoHash, evaluarIntento, MAX_FALLOS, BLOQUEO_MS };
