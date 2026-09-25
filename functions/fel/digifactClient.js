// functions/fel/digifactClient.js
//
// Cliente del certificador Digifact (API NUC de Guatemala, REST + token JWT).
//
// Portado de ComandaYa (OrderApp/functions/fel/digifactClient.js), que es donde se
// validó en vivo contra Digifact. Cambios al portarlo:
//   - `fetch` nativo de Node 22 en vez de axios: BakeOS no suma dependencias.
//   - firebase-admin modular (`getFirestore`), que es lo que usa el resto de BakeOS.
//   - Sin respaldo en `.env`: en BakeOS cada NIT trae sus credenciales o no factura.
//
// Operaciones (GT):
//   - Login:      POST {base}/login/get_token       body JSON { Username, Password }
//   - Certificar: POST {base}/v2/transform/nuc      params TAXID, FORMAT, USERNAME
//   - Anular:     POST {base}/CancelFelGT           body JSON
//   - Documento:  GET  {base}/GetDocument           params AUTHNUMBER, TAXID, FORMAT, USERNAME
//   - Consultas:  GET  {base}/shared                params TAXID, DATA1, DATA2, USERNAME
//
// El Username del login es COUNTRY + "." + NIT(12 dígitos) + "." + USER.

const { FieldValue } = require("firebase-admin/firestore");

// Tokens en memoria del runtime (sobreviven entre invocaciones calientes). Un Map porque
// una instancia atiende a varios NIT y con una sola entrada se pisarían.
const memTokens = new Map(); // identidad -> { token, expiresAt }

const TOKEN_SAFETY_MS = 60 * 1000;
// Una factura tardó 44 s en certificar en TEST (medido en ComandaYa, 2026-08-07). Con 40 s
// el documento SE CERTIFICABA pero se daba por fallido.
const CERT_TIMEOUT_MS = 120 * 1000;
const TOKEN_DEFAULT_TTL_MS = 50 * 60 * 1000;

class HttpError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}: ${typeof body === "string" ? body.slice(0, 300) : JSON.stringify(body).slice(0, 300)}`);
    this.status = status;
    this.body = body;
  }
}

/** Petición HTTP con timeout. Devuelve el cuerpo ya parseado (JSON si se puede). */
async function http(method, url, { params, headers, body, timeoutMs = 20000 } = {}) {
  const u = new URL(url);
  if (params) for (const [k, v] of Object.entries(params)) u.searchParams.set(k, v);
  const resp = await fetch(u, {
    method,
    headers,
    body,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await resp.text();
  let data = text;
  try { data = JSON.parse(text); } catch (_) { /* no es JSON */ }
  if (!resp.ok) throw new HttpError(resp.status, data);
  return data;
}

class DigifactClient {
  /**
   * @param {object} c  credenciales del emisor (ver felCredenciales.cargarCredenciales)
   */
  constructor(c) {
    this.baseUrl = String(c.baseUrl || "").replace(/\/+$/, "");
    this.country = c.country || "GT";
    this.accountTaxId = c.taxId || "";
    this.user = c.user || "";
    this.password = c.password || "";
    this.ambiente = c.ambiente || "TEST";
    this.tokenRef = c.tokenRef || null;
    this.tokenCacheado = c.tokenCacheado || null;
  }

  get username() {
    return `${this.country}.${padNit(this.accountTaxId)}.${this.user}`;
  }

  /** Token válido, del cache (memoria o documento del emisor) o pidiendo uno nuevo. */
  async getToken(force = false) {
    const now = Date.now();
    // Identidad = endpoint + cuenta: un cambio TEST→PRODUCCIÓN no puede seguir usando el
    // token viejo (duran ~30 días) contra el endpoint nuevo.
    const identidad = `${this.baseUrl}|${this.username}`;

    const enMemoria = memTokens.get(identidad);
    if (!force && enMemoria && enMemoria.expiresAt - now > TOKEN_SAFETY_MS) return enMemoria.token;

    if (!force && this.tokenCacheado && this.tokenCacheado.identidad === identidad &&
        this.tokenCacheado.expiresAt - now > TOKEN_SAFETY_MS) {
      memTokens.set(identidad, this.tokenCacheado);
      return this.tokenCacheado.token;
    }

    if (!this.baseUrl || !this.accountTaxId || !this.user || !this.password) {
      throw new Error("Este NIT no tiene credenciales de Digifact configuradas.");
    }

    let data;
    try {
      data = await http("POST", `${this.baseUrl}/login/get_token`, {
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ Username: this.username, Password: this.password }),
      });
    } catch (e) {
      console.error("Digifact login falló:", e.status, e.body || e.message);
      throw new Error(`No se pudo autenticar con Digifact: ${e.status || ""} ${e.message}`);
    }

    const token = extractToken(data);
    if (!token) throw new Error(`Login de Digifact sin token: ${JSON.stringify(data).slice(0, 300)}`);

    const expiresAt = jwtExpiryMs(token) || (now + TOKEN_DEFAULT_TTL_MS);
    const entry = { token, expiresAt, identidad };
    memTokens.set(identidad, entry);
    this.tokenCacheado = entry;

    // Best-effort, con merge para no pisar usuario/contraseña del mismo documento.
    if (this.tokenRef) {
      this.tokenRef.set({ token, expiresAt, identidad, tokenAt: FieldValue.serverTimestamp() }, { merge: true })
        .catch((e) => console.warn("No se pudo cachear el token:", e.message));
    }
    return token;
  }

  /** Llama con token y, si expiró (401), renueva una vez y reintenta. */
  async conToken(fn) {
    try {
      return await fn(await this.getToken());
    } catch (e) {
      if (e.status === 401) return fn(await this.getToken(true));
      throw e;
    }
  }

  /**
   * Certifica un documento NUC. El body es el XML `<Root>` de nucXmlBuilder: Digifact lo
   * transforma a FEL-SAT, lo firma y lo certifica.
   */
  async certificarDocumento(taxId, xml) {
    let data;
    try {
      data = await this.conToken((token) => http("POST", `${this.baseUrl}/v2/transform/nuc`, {
        headers: { "Content-type": "application/xml", Authorization: token },
        params: { TAXID: padNit(taxId), FORMAT: "XML", USERNAME: this.user },
        body: xml,
        timeoutMs: CERT_TIMEOUT_MS,
      }));
    } catch (e) {
      // Digifact contesta 4xx con el detalle del rechazo en el cuerpo: se normaliza igual
      // para no perder el motivo (y para detectar la referencia duplicada).
      if (e.body && typeof e.body === "object" && e.body.code !== undefined) return normalizeCertResponse(e.body);
      throw new Error(`Error de Digifact: ${e.message}`);
    }
    return normalizeCertResponse(data);
  }

  /**
   * Anula un DTE (CANCEL FEL). NO es una nota de crédito: la NCRE exige receptor con NIT
   * y la SAT la rechaza para Consumidor Final, que es casi toda la venta.
   */
  async anularDocumento(taxId, { autorizacion, idReceptor, fechaEmision, motivo }) {
    let data;
    try {
      data = await this.conToken((token) => http("POST", `${this.baseUrl}/CancelFelGT`, {
        headers: { "Content-Type": "application/json", Authorization: token },
        body: JSON.stringify({
          Taxid: padNit(taxId),
          Autorizacion: autorizacion,
          IdReceptor: idReceptor || "CF",
          FechaEmisionDocumentoAnular: fechaEmision,
          MotivoAnulacion: motivo,
          Username: this.user,
        }),
        timeoutMs: CERT_TIMEOUT_MS,
      }));
    } catch (e) {
      if (e.body && typeof e.body === "object") return normalizeCancelResponse(e.body);
      throw new Error(`Error de Digifact al anular: ${e.message}`);
    }
    return normalizeCancelResponse(data);
  }

  /** Documento certificado (PDF/XML) en base64, o null. Digifact es el custodio. */
  async obtenerDocumento(taxId, authNumber, formato = "PDF") {
    if (!authNumber) return null;
    const data = await this.conToken((token) => http("GET", `${this.baseUrl}/GetDocument`, {
      headers: { Authorization: token },
      params: { AUTHNUMBER: authNumber, TAXID: padNit(taxId), FORMAT: formato, USERNAME: this.user },
      timeoutMs: 40000,
    }));
    const estado = Array.isArray(data?.REQUEST_DATA) ? data.REQUEST_DATA[0] : null;
    if (estado && Number(estado.Codigo) !== 1) return null;
    const datos = Array.isArray(data?.RESPONSE) ? data.RESPONSE[0] : null;
    return datos?.ResponseData1 || datos?.ResponseData3 || null;
  }

  /**
   * Nombre registrado en SAT de un NIT (para autocompletar el receptor). Nunca lanza:
   * una consulta de conveniencia no puede bloquear el cobro.
   */
  async consultarNIT(nit) {
    const limpio = String(nit || "").replace(/[^0-9Kk]/g, "").toUpperCase();
    if (!limpio || limpio === "CF") return null;
    try {
      const data = await this.conToken((token) => http("GET", `${this.baseUrl}/shared`, {
        headers: { Authorization: token },
        // El TAXID es la cuenta que consulta; el NIT buscado va en DATA2. Los cuatro
        // parámetros son obligatorios (sin USERNAME responde 400).
        params: { TAXID: padNit(this.accountTaxId), DATA1: "SHARED_GETINFONITcom", DATA2: `NIT|${limpio}`, USERNAME: this.user },
      }));
      const estado = Array.isArray(data?.REQUEST_DATA) ? data.REQUEST_DATA[0] : null;
      if (estado && Number(estado.Codigo) !== 1) return null;
      const info = Array.isArray(data?.RESPONSE) ? data.RESPONSE[0] : null;
      if (!info || !info.NOMBRE) return null;
      return {
        nit: info.NIT || limpio,
        nombre: info.NOMBRE,
        direccion: info.Direccion || info.DIRECCION || "",
      };
    } catch (e) {
      console.warn("No se pudo consultar el NIT:", e.status || "", e.message);
      return null;
    }
  }
}

// ── Helpers ────────────────────────────────────────────────────────────────

/**
 * NIT rellenado a 12 caracteres, como exige Digifact para el login y el parámetro TAXID.
 * Conserva la K del dígito verificador ("se coloca tal cual", Digifact 2026-08-07).
 */
function padNit(nit) {
  return String(nit || "").replace(/[^0-9Kk]/g, "").toUpperCase().padStart(12, "0");
}

function extractToken(data) {
  if (!data) return null;
  if (typeof data === "string") return data.trim();
  return data.token || data.Token || data.access_token || data.jwt || data.Authorization || null;
}

function jwtExpiryMs(token) {
  try {
    const part = token.split(".")[1];
    const payload = JSON.parse(Buffer.from(part.replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8"));
    return payload.exp ? payload.exp * 1000 : null;
  } catch (_) { return null; }
}

function b64ToUtf8(b64) {
  if (!b64) return "";
  try { return Buffer.from(b64, "base64").toString("utf8"); } catch (_) { return ""; }
}

/**
 * Respuesta de certificación normalizada. `referenciaDuplicada` = Digifact ya certificó
 * un documento con esta referencia interna (validación 9022): no es un fallo, casi siempre
 * es un reintento de algo que sí se emitió y cuya respuesta se perdió.
 */
function normalizeCertResponse(data) {
  const errors = [];
  if (Array.isArray(data?.infoDetails)) {
    for (const d of data.infoDetails) errors.push(typeof d === "string" ? d : (d?.message || JSON.stringify(d)));
  }
  const texto = `${data?.description || ""} ${data?.message || ""}`;
  const esDuplicado = /FEL_VALIDACION_REFERENCIA_INTERNA|\[9022\]/i.test(texto);
  const uuidPrevio = esDuplicado
    ? (texto.match(/UUID\s+([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})/) || [])[1] || ""
    : "";
  return {
    success: Number(data?.code) === 1,
    code: data?.code,
    referenciaDuplicada: esDuplicado,
    authNumberPrevio: uuidPrevio,
    message: data?.message || "",
    description: data?.description || "",
    authNumber: data?.authNumber || "",
    xmlCertificado: b64ToUtf8(data?.responseData1),
    serial: data?.serial || "",
    batch: data?.batch || "",
    errors,
  };
}

function normalizeCancelResponse(data) {
  const codigo = Number(data?.Codigo ?? data?.codigo);
  return {
    success: codigo === 1,
    code: data?.Codigo ?? data?.codigo,
    message: data?.Mensaje || data?.mensaje || "",
    xmlAnulacion: b64ToUtf8(data?.ResponseDATA1),
  };
}

/**
 * Serie y número del DTE certificado. ⚠️ `serial` y `batch` de Digifact vienen AL REVÉS
 * de lo que dicen sus nombres (comprobado en ComandaYa 2026-08-07), así que se leen del
 * XML certificado, que es la fuente de verdad, y la respuesta queda de respaldo.
 */
function serieYNumero(xmlCertificado, response = {}) {
  const etiqueta = String(xmlCertificado || "").match(/<[^>]*NumeroAutorizacion[^>]*>/);
  if (etiqueta) {
    const leer = (n) => (etiqueta[0].match(new RegExp(`${n}="([^"]*)"`)) || [])[1] || "";
    const serie = leer("Serie");
    const numero = leer("Numero");
    if (serie && numero) return { serie, numero };
  }
  return { serie: String(response.batch || ""), numero: String(response.serial || "") };
}

module.exports = { DigifactClient, padNit, serieYNumero, normalizeCertResponse };
