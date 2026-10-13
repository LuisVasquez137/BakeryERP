// functions/fel/felFunctions.js
//
// Facturación electrónica (FEL Guatemala) de BakeOS, certificada con Digifact.
//
// Callables que usa la app:
//   felCertificarVenta   — factura una o varias ventas de UN cobro (pan + cuenta del restaurante)
//   felConsultarNit      — nombre registrado en SAT de un NIT, para el receptor
//   felAnularDte         — anula un DTE (CANCEL FEL)
//   felObtenerPdf        — PDF oficial, bajado de Digifact cuando se pide
//   felGuardarPerfil     — perfil fiscal del NIT y, opcional, sus credenciales de API
// Y un job:
//   felReintentarPendientes — reintenta lo que no se pudo certificar (Digifact caído)
//
// ── Dónde queda cada cosa ──────────────────────────────────────────────────
//   companies/{c}/dtes/{dteId}   el documento: estado, serie, número, autorización, receptor,
//                                totales, frases, XML enviado y certificado
//   companies/{c}/sales/{id}     dteId + invoiceStatus + serie/número, para mostrar y reimprimir
//   felPendientes/{c}__{dteId}   cola de reintentos (nivel superior: una consulta para todas
//                                las empresas, sin índice de grupo de colecciones)
//
// ── Por qué el id del DTE sale de las ventas ───────────────────────────────
// `dte_<huella de empresa + ventas>`: dos toques del botón, dos cajas o un reintento tras
// un timeout caen en el MISMO documento, y un candado en transacción impide certificar dos
// veces. La referencia interna que va a Digifact sale de la misma huella y va en modo
// VALIDAR: aunque algo se escapara acá, Digifact rechaza el segundo documento y lo que se
// hace es recuperar el primero.

const crypto = require("crypto");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const { resolverEmisor, limpiarNit, FEL_PROFILES } = require("./felEmisor");
const { clienteParaPerfil, guardarCredenciales, URLS } = require("./felCredenciales");
const { DigifactClient, serieYNumero } = require("./digifactClient");
const { reportarDte, reportarPendientes, debeReportarse, INGEST_KEY } = require("./comandayaDteReport");
const {
  buildFacturaNuc, prorateDiscount, absorberRecargo, referenciaInterna, fechaEmisionParaAnular, idReceptorParaXml, nowGuatemalaISO,
} = require("./nucXmlBuilder");

const REGION = "us-central1";
// Digifact puede tardar ~45 s en certificar; la función espera hasta 5 min.
const CALL_OPTS = { region: REGION, timeoutSeconds: 300, memory: "256MiB" };
// Certificar avisa a ComandaYa (contador de DTEs por NIT): necesita la clave del puente.
const CERT_OPTS = { ...CALL_OPTS, secrets: [INGEST_KEY] };

const ESTADO = {
  CERTIFICANDO: "CERTIFICANDO",
  CERTIFICADO: "CERTIFICADO",
  PENDIENTE: "PENDIENTE",   // falla transitoria: se reintenta sola
  RECHAZADO: "RECHAZADO",   // Digifact/SAT rechazó el contenido: reintentar no sirve
  ANULADO: "ANULADO",
};
const LOCK_MS = 3 * 60 * 1000;

/**
 * Descripción de la factura "sin detalle": una sola línea por el total, sin listar lo consumido.
 * El mismo texto que ComandaYa, así las facturas de Sabreopan dicen lo mismo salgan de donde
 * salgan. La SAT no valida el contenido, pero una descripción que no dice nada le complica la
 * deducción al comprador.
 */
const DESCRIPCION_SIN_DETALLE = "Servicio de alimentos y/o bebidas";
const MAX_INTENTOS = 12;

const db = () => getFirestore();

// ── Autorización ───────────────────────────────────────────────────────────

async function usuarioDeLaEmpresa(request, companyId) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Iniciá sesión.");
  if (!companyId) throw new HttpsError("invalid-argument", "Falta la empresa.");
  const snap = await db().doc(`users/${request.auth.uid}`).get();
  const user = snap.exists ? snap.data() : null;
  if (!user || user.orgId !== companyId) throw new HttpsError("permission-denied", "No pertenecés a esta empresa.");
  return { id: request.auth.uid, ...user };
}

function exigirPermiso(user, ...permisos) {
  if (user.role === "OWNER") return;
  const tiene = Array.isArray(user.permissions) && permisos.some((p) => user.permissions.includes(p));
  if (!tiene) throw new HttpsError("permission-denied", "No tenés permiso para esta operación.");
}

// ── Certificación ──────────────────────────────────────────────────────────

function dteIdPara(companyId, saleIds, version = 0) {
  return "dte_" + crypto.createHash("sha1")
    .update(`${companyId}|${[...saleIds].sort().join("|")}${version > 0 ? `|v${version}` : ""}`)
    .digest("hex").slice(0, 24);
}

/** Líneas facturables de una venta, con su descuento repartido por línea. */
async function lineasDeVenta(companyId, sale) {
  const snap = await db().collection(`companies/${companyId}/sales/${sale.id}/lines`).get();
  const todas = snap.docs.map((d) => d.data());
  // El recargo por tarjeta no va descrito en la factura: se reparte entre los productos de la
  // MISMA venta. Si la venta solo trae el recargo (el cobro fue de cuentas del restaurante), se
  // devuelve marcado y certificarVentas lo reparte entre todas las líneas del DTE.
  const recargo = todas.filter((l) => l.cardSurcharge === true)
    .reduce((a, l) => a + (Number(l.subtotal) || (Number(l.quantity) || 0) * (Number(l.unitPrice) || 0)), 0);
  const productos = todas.filter((l) => l.cardSurcharge !== true).map((l) => ({
    description: l.productName || l.quickGroupName || "Producto",
    qty: Number(l.quantity) || 0,
    unitPrice: Number(l.unitPrice) || 0,
    discount: Number(l.discount) || 0,
  }));
  const lines = recargo > 0.004
    ? (productos.length ? absorberRecargo(productos, recargo)
      : [{ description: "Recargo", qty: 1, unitPrice: recargo, discount: 0, recargoPendiente: true }])
    : productos;
  // El descuento que la venta guarda en el encabezado (colaborador, promoción de
  // ComandaYa) se reparte entre sus líneas: la SAT lo pide por item, no global.
  const yaPorLinea = lines.reduce((a, l) => a + l.discount, 0);
  const pendiente = Math.max(0, (Number(sale.discount) || 0) - yaPorLinea);
  const conDescuento = pendiente > 0.004 ? prorateDiscount(lines, pendiente) : lines;
  // El domicilio de una cuenta del restaurante no viene como línea y no lleva el descuento
  // de la comida: sin esta línea la factura sale por menos de lo cobrado.
  const domicilio = Number(sale.externalDeliveryFee) || 0;
  return domicilio > 0.004
    ? [...conDescuento, { description: "Servicio a domicilio", qty: 1, unitPrice: domicilio, discount: 0 }]
    : conDescuento;
}

/**
 * Certifica las ventas de un cobro. Se usa desde la app y desde el reintento automático.
 * Nunca deja ventas a medias: o el DTE queda CERTIFICADO, o queda PENDIENTE/RECHAZADO
 * con el motivo, y las ventas dicen lo mismo.
 */
async function certificarVentas({ companyId, saleIds, receptor: receptorPedido, sinDetalle: sinDetallePedido, userId, reemitir = false }) {
  let sinDetalle = sinDetallePedido;
  let receptor = receptorPedido;
  const firestore = db();
  let ids = [...new Set((saleIds || []).map(String).filter(Boolean))];
  if (!ids.length) throw new HttpsError("invalid-argument", "No hay ventas que facturar.");

  // Volver a facturar cubre lo mismo que cubría la factura anterior: se leen sus ventas del
  // documento viejo. La app manda solo la venta que se está viendo, y reemitir por una parte
  // del cobro dejaría el resto sin factura.
  if (reemitir) {
    const primera = (await firestore.doc(`companies/${companyId}/sales/${ids[0]}`).get()).data();
    const anterior = primera?.dteId
      ? (await firestore.doc(`companies/${companyId}/dtes/${primera.dteId}`).get()).data()
      : null;
    if (Array.isArray(anterior?.saleIds) && anterior.saleIds.length) {
      ids = [...new Set([...ids, ...anterior.saleIds.map(String)])];
    }
  }

  let ventas = [];
  for (const id of ids) {
    const s = await firestore.doc(`companies/${companyId}/sales/${id}`).get();
    if (!s.exists) throw new HttpsError("not-found", `La venta ${id} no existe.`);
    ventas.push({ id, ...s.data() });
  }
  const branchId = ventas[0].branchId;
  if (ventas.some((v) => v.branchId !== branchId)) {
    throw new HttpsError("invalid-argument", "Las ventas de una factura tienen que ser de la misma sucursal.");
  }
  if (ventas.some((v) => v.status === "VOIDED" || v.status === "CANCELLED")) {
    throw new HttpsError("failed-precondition", "No se puede facturar una venta anulada.");
  }
  // Una cuenta que ComandaYa ya facturó queda fuera, y se factura el resto del cobro. Pasa si
  // la facturan en la mesa mientras está en el carrito de la caja: rechazar el documento
  // entero dejaría sin factura también el pan, y cada reintento chocaría con la misma cuenta.
  const enComandaYa = ventas.filter((v) => v.invoiceStatus === "EXTERNA");
  if (enComandaYa.length) {
    if (enComandaYa.length === ventas.length) {
      throw new HttpsError("failed-precondition", "Esta cuenta ya se facturó en ComandaYa.");
    }
    const fuera = new Set(enComandaYa.map((v) => v.id));
    ventas = ventas.filter((v) => !fuera.has(v.id));
    ids = ids.filter((id) => !fuera.has(id));
  }
  // Cada vez que se vuelve a facturar un cobro (la anterior se anuló o la rechazaron) sube la
  // versión: cambia el id del DTE y la referencia interna, porque las viejas ya se usaron.
  const versionActual = Math.max(0, ...ventas.map((v) => Number(v.invoiceVersion) || 0));
  let version = versionActual;
  if (reemitir) {
    const bloquea = ventas.find((v) => v.invoiceStatus &&
      v.invoiceStatus !== ESTADO.ANULADO && v.invoiceStatus !== ESTADO.RECHAZADO);
    if (bloquea) {
      throw new HttpsError("failed-precondition", "Solo se vuelve a facturar una venta cuya factura se anuló o fue rechazada.");
    }
    if (!receptorPedido?.id) throw new HttpsError("invalid-argument", "Falta a quién facturar.");
    version = versionActual + 1;
  }

  // Una venta ya facturada por su cuenta (p. ej. la cuenta que ComandaYa facturó) no se
  // vuelve a meter en otro documento.
  const otraFactura = ventas.find((v) => v.dteId && v.dteId !== dteIdPara(companyId, ids, version) && v.invoiceStatus === ESTADO.CERTIFICADO);
  if (otraFactura) throw new HttpsError("failed-precondition", "Una de las ventas ya tiene su propia factura.");

  if (reemitir) {
    // Queda escrito ANTES de llamar a Digifact: si esto se corta a medias, el barrido de
    // SOLICITADA la retoma con la versión y el receptor nuevos, no con los de la anulada.
    await marcarVentas(companyId, ventas, {
      invoiceVersion: version,
      invoiceStatus: "SOLICITADA",
      invoiceReceptorId: idReceptorParaXml(receptorPedido.id),
      invoiceReceptorName: String(receptorPedido.nombre || "CONSUMIDOR FINAL").trim(),
    });
  }

  // Sin detalle: lo que diga la llamada, o si no viene (barrido de SOLICITADA) lo que la caja
  // dejó escrito en la venta.
  if (sinDetalle === undefined || sinDetalle === null) sinDetalle = ventas.some((v) => v.invoiceWithoutDetail === true);

  // Sin receptor en la llamada (reintento, barrido de SOLICITADA) manda el que la caja dejó
  // escrito en la venta al cobrar: el cliente pidió factura a su NIT y así tiene que salir.
  if (!receptor?.id) {
    const v = ventas.find((x) => x.invoiceReceptorId);
    if (v) receptor = { id: v.invoiceReceptorId, nombre: v.invoiceReceptorName };
  }

  const dteId = dteIdPara(companyId, ids, version);
  const dteRef = firestore.doc(`companies/${companyId}/dtes/${dteId}`);
  const ahora = Date.now();

  // Candado: un solo intento de certificación a la vez por documento.
  let previo = null;
  await firestore.runTransaction(async (tx) => {
    const snap = await tx.get(dteRef);
    if (snap.exists) {
      const d = snap.data();
      if (d.estado === ESTADO.CERTIFICADO) { previo = d; return; }
      if (d.estado === ESTADO.ANULADO) {
        throw new HttpsError("failed-precondition", "La factura de este cobro fue anulada; no se vuelve a emitir.");
      }
      if (d.estado === ESTADO.CERTIFICANDO && ahora - (d.lockedAt || 0) < LOCK_MS) {
        throw new HttpsError("aborted", "Esta factura ya se está certificando. Esperá unos segundos.");
      }
    }
    tx.set(dteRef, {
      id: dteId,
      companyId,
      saleIds: ids,
      branchId,
      estado: ESTADO.CERTIFICANDO,
      lockedAt: ahora,
      intentos: FieldValue.increment(1),
      // El receptor de la PRIMERA solicitud manda: un reintento repite exactamente el
      // mismo documento (y la misma referencia interna).
      ...(snap.exists ? {} : {
        receptorId: idReceptorParaXml(receptor?.id),
        receptorNombre: String(receptor?.nombre || "CONSUMIDOR FINAL").trim() || "CONSUMIDOR FINAL",
        sinDetalle: !!sinDetalle,
        createdAt: ahora,
        createdBy: userId || "",
      }),
    }, { merge: true });
  });
  if (previo) return respuestaDe(previo);

  const dte = (await dteRef.get()).data();
  try {
    const { profileId, emisor, branchName } = await resolverEmisor(firestore, companyId, branchId);

    const todas = [];
    for (const v of ventas) todas.push(...await lineasDeVenta(companyId, v));
    // Recargo de una venta que no traía productos: se reparte entre las del resto del cobro.
    const pendiente = todas.filter((l) => l.recargoPendiente).reduce((a, l) => a + l.unitPrice, 0);
    const resto = todas.filter((l) => !l.recargoPendiente);
    const lines = pendiente > 0.004 ? absorberRecargo(resto, pendiente) : resto;

    const ref = referenciaInterna(companyId, ids, version);
    const numeros = ventas.map((v) => v.externalOrderNumber).filter(Boolean);
    // La fecha de emisión se fija acá y se guarda: anular la exige EXACTA, y la de certificación
    // llega segundos después.
    const fechaEmision = nowGuatemalaISO();
    const { xml, docType, granTotal, totalIVA, frases } = buildFacturaNuc({
      fechaHora: fechaEmision,
      emisor,
      receptor: { id: dte.receptorId, nombre: dte.receptorNombre },
      lines,
      referenciaInterna: ref,
      observaciones: numeros.length ? `Orden ${numeros.join(", ")}` : `Venta ${ids[0].slice(-8)}`,
      descripcionUnica: dte.sinDetalle ? DESCRIPCION_SIN_DETALLE : null,
    });

    // La factura tiene que decir lo que se cobró. Si el detalle no cuadra con los totales
    // (una cuenta de ComandaYa con un cargo que no viajó), queda en el registro para revisarlo.
    const cobrado = ventas.reduce((a, v) => a + (Number(v.total) || 0), 0);
    if (Math.abs(cobrado - granTotal) > 0.01) {
      console.warn(`FEL ${companyId}/${dteId}: la factura suma Q${granTotal} y las ventas Q${cobrado.toFixed(2)}`);
    }

    const digifact = await clienteParaPerfil(companyId, profileId);
    let resp = await digifact.certificarDocumento(emisor.nit, xml);

    let uuid = resp.authNumber;
    let xmlCertificado = resp.xmlCertificado;
    let recuperado = false;
    if (!resp.success && resp.referenciaDuplicada && resp.authNumberPrevio) {
      // Ya se certificó (se perdió nuestra respuesta): se recupera en vez de fallar.
      uuid = resp.authNumberPrevio;
      const b64 = await digifact.obtenerDocumento(emisor.nit, uuid, "XML").catch(() => null);
      xmlCertificado = b64 ? Buffer.from(b64, "base64").toString("utf8") : "";
      recuperado = true;
      resp = { ...resp, success: true };
    }

    if (!resp.success) {
      const motivo = [resp.message || resp.description || `Digifact rechazó el documento (código ${resp.code})`,
        ...(resp.errors || []).slice(0, 3)].join(" · ");
      return await marcarFallo({ companyId, dteId, ventas, estado: ESTADO.RECHAZADO, motivo });
    }

    const { serie, numero } = serieYNumero(xmlCertificado, resp);
    const certificado = {
      estado: ESTADO.CERTIFICADO,
      uuid,
      serie,
      numero,
      docType,
      fechaCertificacion: Date.now(),
      fechaEmision,
      version,
      // El ticket imprime lo mismo que certificó la SAT: una sola línea si fue sin detalle.
      descripcionSinDetalle: dte.sinDetalle ? DESCRIPCION_SIN_DETALLE : "",
      granTotal,
      totalIVA,
      frases,
      ambiente: digifact.ambiente,
      profileId,
      emisorNit: emisor.nit,
      emisorNombre: emisor.nombreEmisor,
      emisorNombreComercial: emisor.nombreComercial,
      emisorDireccion: emisor.direccion,
      codigoEstablecimiento: emisor.codigoEstablecimiento,
      branchName,
      referenciaInterna: ref,
      recuperadoDeDuplicado: recuperado,
      xmlOriginal: xml,
      xmlCertificado,
      ultimoError: "",
      lockedAt: 0,
    };
    // Pendiente de avisar a ComandaYa, escrito junto con la factura: si esta función se cae
    // antes de avisar, el reintento programado lo encuentra igual.
    if (await debeReportarse(companyId, certificado).catch(() => false)) certificado.comandayaReportado = false;
    await dteRef.set(certificado, { merge: true });
    await marcarVentas(companyId, ventas, {
      dteId, invoiceStatus: ESTADO.CERTIFICADO, invoiceSerie: serie, invoiceNumero: numero, invoiceUuid: uuid,
      invoiceVersion: version,
    });
    await firestore.doc(`felPendientes/${companyId}__${dteId}`).delete().catch(() => {});
    if (certificado.comandayaReportado === false) await reportarDte(companyId, dteId, certificado);
    return respuestaDe({ ...dte, ...certificado, id: dteId });
  } catch (e) {
    if (e instanceof HttpsError && e.code === "failed-precondition") {
      // Configuración (sucursal sin NIT, perfil inactivo): no es transitorio.
      await marcarFallo({ companyId, dteId, ventas, estado: ESTADO.RECHAZADO, motivo: e.message });
      throw e;
    }
    // Red, timeout, Digifact caído, credenciales: se reintenta solo.
    console.error(`FEL ${companyId}/${dteId}:`, e);
    return await marcarFallo({ companyId, dteId, ventas, estado: ESTADO.PENDIENTE, motivo: e.message || String(e) });
  }
}

async function marcarVentas(companyId, ventas, campos) {
  const batch = db().batch();
  for (const v of ventas) batch.set(db().doc(`companies/${companyId}/sales/${v.id}`), campos, { merge: true });
  await batch.commit();
}

async function marcarFallo({ companyId, dteId, ventas, estado, motivo }) {
  const firestore = db();
  const ref = firestore.doc(`companies/${companyId}/dtes/${dteId}`);
  await ref.set({ estado, ultimoError: String(motivo).slice(0, 1000), lockedAt: 0 }, { merge: true });
  await marcarVentas(companyId, ventas, { dteId, invoiceStatus: estado });

  const colaRef = firestore.doc(`felPendientes/${companyId}__${dteId}`);
  if (estado === ESTADO.PENDIENTE) {
    const intentos = Number((await ref.get()).data()?.intentos) || 1;
    if (intentos >= MAX_INTENTOS) {
      await ref.set({ estado: ESTADO.RECHAZADO, ultimoError: `Se agotaron los reintentos: ${motivo}` }, { merge: true });
      await marcarVentas(companyId, ventas, { invoiceStatus: ESTADO.RECHAZADO });
      await colaRef.delete().catch(() => {});
    } else {
      // Espera creciente: 2, 4, 8… minutos, tope 60. Digifact caído suele volver en minutos.
      const esperaMin = Math.min(60, 2 ** intentos);
      await colaRef.set({ companyId, dteId, intentos, proximoIntento: Date.now() + esperaMin * 60000 });
    }
  } else {
    await colaRef.delete().catch(() => {});
  }
  const d = (await ref.get()).data();
  return respuestaDe({ ...d, id: dteId });
}

/** Lo que la app necesita para mostrar e imprimir. Nunca incluye el XML. */
function respuestaDe(d) {
  return {
    dteId: d.id,
    estado: d.estado,
    mensaje: d.ultimoError || "",
    serie: d.serie || "",
    numero: d.numero || "",
    uuid: d.uuid || "",
    docType: d.docType || "",
    fechaCertificacion: d.fechaCertificacion || 0,
    granTotal: d.granTotal || 0,
    totalIVA: d.totalIVA || 0,
    receptorId: d.receptorId || "CF",
    receptorNombre: d.receptorNombre || "CONSUMIDOR FINAL",
    emisorNit: d.emisorNit || "",
    emisorNombre: d.emisorNombre || "",
    emisorNombreComercial: d.emisorNombreComercial || "",
    emisorDireccion: d.emisorDireccion || "",
    frases: d.frases || [],
    ambiente: d.ambiente || "",
    sinDetalle: !!d.sinDetalle,
    descripcionSinDetalle: d.descripcionSinDetalle || (d.sinDetalle ? DESCRIPCION_SIN_DETALLE : ""),
  };
}

// ── Callables ──────────────────────────────────────────────────────────────

exports.felCertificarVenta = onCall(CERT_OPTS, async (request) => {
  const { companyId, saleIds, receptor, sinDetalle, reemitir } = request.data || {};
  const user = await usuarioDeLaEmpresa(request, companyId);
  // Volver a facturar es corregir una factura ya emitida: el mismo permiso que anularla.
  if (reemitir) exigirPermiso(user, "VOID_SALE");
  else exigirPermiso(user, "OPERATE_POS");
  return certificarVentas({ companyId, saleIds, receptor, sinDetalle, userId: user.id, reemitir: !!reemitir });
});

exports.felConsultarNit = onCall({ region: REGION, timeoutSeconds: 60 }, async (request) => {
  const { companyId, branchId, nit } = request.data || {};
  await usuarioDeLaEmpresa(request, companyId);
  const { profileId } = await resolverEmisor(db(), companyId, branchId);
  // Con las credenciales del NIT de ESTA sucursal. ComandaYa consultaba con las de un
  // perfil genérico que en empresas con varios NIT no existe.
  const digifact = await clienteParaPerfil(companyId, profileId);
  const info = await digifact.consultarNIT(nit);
  return info ? { encontrado: true, nit: info.nit, nombre: info.nombre } : { encontrado: false };
});

exports.felAnularDte = onCall(CALL_OPTS, async (request) => {
  const { companyId, dteId, motivo } = request.data || {};
  const user = await usuarioDeLaEmpresa(request, companyId);
  exigirPermiso(user, "VOID_SALE");
  if (!motivo || String(motivo).trim().length < 5) {
    throw new HttpsError("invalid-argument", "Escribí el motivo de la anulación (mínimo 5 letras).");
  }
  const ref = db().doc(`companies/${companyId}/dtes/${dteId}`);
  const d = (await ref.get()).data();
  if (!d) throw new HttpsError("not-found", "La factura no existe.");
  if (d.estado !== ESTADO.CERTIFICADO) throw new HttpsError("failed-precondition", "Solo se anula una factura certificada.");

  const digifact = await clienteParaPerfil(companyId, d.profileId);
  const resp = await digifact.anularDocumento(d.emisorNit, {
    autorizacion: d.uuid,
    idReceptor: d.receptorId || "CF",
    fechaEmision: fechaEmisionDelDte(d),
    motivo: String(motivo).trim(),
  });
  if (!resp.success) {
    throw new HttpsError("failed-precondition", resp.message || `Digifact rechazó la anulación (código ${resp.code}).`);
  }
  await ref.set({
    estado: ESTADO.ANULADO,
    fechaAnulacion: Date.now(),
    motivoAnulacion: String(motivo).trim(),
    anuladoPor: user.id,
    xmlAnulacion: resp.xmlAnulacion || "",
  }, { merge: true });
  const ventas = (d.saleIds || []).map((id) => ({ id }));
  await marcarVentas(companyId, ventas, { invoiceStatus: ESTADO.ANULADO });
  return { estado: ESTADO.ANULADO };
});

/**
 * Fecha de emisión EXACTA del documento, como la pide la anulación ('YYYY-MM-DDTHH:mm:ss', hora
 * de Guatemala). Se guarda desde la fase 3c; las facturas anteriores la tienen dentro del XML
 * enviado. La de certificación queda de último recurso: llega segundos después.
 */
function fechaEmisionDelDte(d) {
  if (d.fechaEmision) return String(d.fechaEmision).slice(0, 19);
  const m = /<IssuedDateTime>([^<]+)<\/IssuedDateTime>/.exec(d.xmlOriginal || "");
  if (m) return m[1].slice(0, 19);
  return fechaEmisionParaAnular(d.fechaCertificacion);
}

exports.felObtenerPdf = onCall({ region: REGION, timeoutSeconds: 60 }, async (request) => {
  const { companyId, dteId } = request.data || {};
  await usuarioDeLaEmpresa(request, companyId);
  const d = (await db().doc(`companies/${companyId}/dtes/${dteId}`).get()).data();
  if (!d || !d.uuid) throw new HttpsError("not-found", "La factura no está certificada.");
  const digifact = await clienteParaPerfil(companyId, d.profileId);
  const pdfBase64 = await digifact.obtenerDocumento(d.emisorNit, d.uuid, "PDF");
  if (!pdfBase64) throw new HttpsError("unavailable", "Digifact no devolvió el PDF. Intentá de nuevo.");
  return { pdfBase64 };
});

/**
 * Perfil fiscal de un NIT y, si vienen, sus credenciales de API. Solo el dueño. Con
 * credenciales nuevas se prueba el login antes de guardarlas: una contraseña mal escrita
 * se descubre acá y no en la primera venta con fila.
 */
exports.felGuardarPerfil = onCall({ region: REGION, timeoutSeconds: 60 }, async (request) => {
  const { companyId, profileId, perfil, credenciales } = request.data || {};
  const user = await usuarioDeLaEmpresa(request, companyId);
  if (user.role !== "OWNER") throw new HttpsError("permission-denied", "Solo el dueño configura la facturación.");
  const nit = limpiarNit(perfil?.nit);
  if (!nit) throw new HttpsError("invalid-argument", "Falta el NIT.");

  const col = db().collection(`companies/${companyId}/${FEL_PROFILES}`);
  const ref = profileId ? col.doc(profileId) : col.doc();
  const datos = {
    id: ref.id,
    nit,
    nombreEmisor: String(perfil.nombreEmisor || "").trim(),
    nombreComercial: String(perfil.nombreComercial || "").trim(),
    correo: String(perfil.correo || "").trim(),
    direccion: String(perfil.direccion || "").trim(),
    municipio: String(perfil.municipio || "").trim(),
    departamento: String(perfil.departamento || "").trim(),
    codigoPostal: String(perfil.codigoPostal || "01001").trim(),
    regimenISR: perfil.regimenISR || "OPCIONAL",
    afiliacionIVA: perfil.afiliacionIVA || "GEN",
    active: perfil.active !== false,
    updatedAt: Date.now(),
  };

  if (credenciales?.usuario && credenciales?.password) {
    const ambiente = credenciales.ambiente === "PRODUCCION" ? "PRODUCCION" : "TEST";
    // Se prueban ANTES de guardarlas: una contraseña mal tecleada no puede reemplazar a la que
    // ya factura, o la sucursal se queda sin poder emitir hasta que alguien lo note en caja.
    try {
      await new DigifactClient({
        baseUrl: URLS[ambiente], taxId: nit, user: String(credenciales.usuario).trim(),
        password: String(credenciales.password), ambiente,
      }).getToken(true);
    } catch (e) {
      throw new HttpsError("failed-precondition", `Digifact no aceptó las credenciales: ${e.message}`);
    }
    await guardarCredenciales({
      companyId, profileId: ref.id, nit, usuario: credenciales.usuario, password: credenciales.password, ambiente,
    });
    datos.tieneCredenciales = true;
    datos.ambienteApi = ambiente;
  }
  await ref.set(datos, { merge: true });
  return { profileId: ref.id };
});

// ── Reintentos ─────────────────────────────────────────────────────────────

exports.felReintentarPendientes = onSchedule(
  { schedule: "every 5 minutes", region: REGION, timeoutSeconds: 540, secrets: [INGEST_KEY] },
  async () => {
    const vencidos = await db().collection("felPendientes").where("proximoIntento", "<=", Date.now()).limit(20).get();
    for (const doc of vencidos.docs) {
      const { companyId, dteId } = doc.data();
      const d = (await db().doc(`companies/${companyId}/dtes/${dteId}`).get()).data();
      if (!d || d.estado !== ESTADO.PENDIENTE) { await doc.ref.delete(); continue; }
      try {
        await certificarVentas({ companyId, saleIds: d.saleIds, receptor: null, sinDetalle: d.sinDetalle, userId: "" });
      } catch (e) {
        console.warn(`Reintento FEL ${companyId}/${dteId}:`, e.message);
      }
    }
    await certificarSolicitadasHuerfanas();
    await reportarPendientes().catch((e) => console.warn("Avisos a ComandaYa:", e.message));
  }
);

/**
 * Ventas que la caja marcó SOLICITADA y cuya llamada nunca llegó (la tablet se quedó sin
 * internet justo al cobrar). Sin este barrido quedarían sin factura para siempre: la cola de
 * `felPendientes` solo conoce lo que el servidor alcanzó a registrar.
 *
 * Por empresa y no con collectionGroup: una igualdad sobre la colección de ventas usa el
 * índice automático, y un grupo de colecciones pediría declarar uno. Espera 2 minutos para
 * no pisar la llamada de la caja que todavía está en camino (el candado del DTE lo atajaría
 * igual, pero así ni se intenta).
 */
async function certificarSolicitadasHuerfanas() {
  const limite = Date.now() - 2 * 60 * 1000;
  const empresas = await db().collection("companies").listDocuments();
  for (const empresa of empresas) {
    const snap = await empresa.collection("sales").where("invoiceStatus", "==", "SOLICITADA").limit(20).get();
    // Un cobro = una factura: las ventas del mismo checkout van juntas.
    const grupos = new Map();
    for (const d of snap.docs) {
      const v = d.data();
      if ((Number(v.createdAt) || 0) > limite) continue;
      const clave = v.checkoutId || d.id;
      grupos.set(clave, [...(grupos.get(clave) || []), d.id]);
    }
    for (const saleIds of grupos.values()) {
      try {
        await certificarVentas({ companyId: empresa.id, saleIds, receptor: null, sinDetalle: undefined, userId: "" });
      } catch (e) {
        console.warn(`FEL solicitada ${empresa.id}/${saleIds.join(",")}:`, e.message);
      }
    }
  }
}

// Para pruebas de la lógica sin desplegar.
exports._internals = { dteIdPara, respuestaDe, fechaEmisionDelDte };
