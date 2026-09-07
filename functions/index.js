/**
 * Cloud Functions de BakeOS — entrega de push.
 *
 * La app escribe los avisos en `companies/{companyId}/notifications/{id}`; esa
 * colección es la fuente de verdad y la campana in-app la lee directo. Esta
 * función solo agrega el canal de entrega: cuando aparece un aviso nuevo,
 * resuelve a qué personas les toca y le manda el push a sus dispositivos.
 *
 * Consecuencia de ese diseño: si esta función falla, se cae o todavía no está
 * desplegada, no se pierde ningún aviso — solo no suena el teléfono.
 */

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const db = getFirestore();

/** OWNER tiene todos los permisos de forma implícita (igual que User.hasPermission). */
function userHasPermission(user, permission) {
  if (!permission) return true;
  if (user.role === "OWNER") return true;
  return Array.isArray(user.permissions) && user.permissions.includes(permission);
}

/** OWNER y usuarios sin sucursales asignadas ven todas. */
function userCanAccessBranch(user, branchId) {
  if (user.role === "OWNER") return true;
  const branches = user.branchIds;
  if (!Array.isArray(branches) || branches.length === 0) return true;
  return branches.includes(branchId);
}

function userIsTargeted(user, notification) {
  const targetBranchIds = notification.targetBranchIds || [];
  if (targetBranchIds.length > 0) {
    const matches = targetBranchIds.some((b) => userCanAccessBranch(user, b));
    if (!matches) return false;
  }
  if (!userHasPermission(user, notification.requiredPermission)) return false;
  return true;
}

exports.sendNotificationPush = onDocumentCreated(
  "companies/{companyId}/notifications/{notificationId}",
  async (event) => {
    const notification = event.data && event.data.data();
    if (!notification) return;

    const { companyId } = event.params;

    // 1. Usuarios de la organización que deben recibirlo. Se excluye al autor:
    //    no tiene sentido avisarle a quien acaba de hacer la acción.
    const usersSnap = await db
      .collection("users")
      .where("orgId", "==", companyId)
      .get();

    const targetUserIds = usersSnap.docs
      .map((doc) => ({ id: doc.id, ...doc.data() }))
      .filter((user) => user.isActive !== false && user.isApproved !== false)
      .filter((user) => user.id !== notification.createdByUserId)
      .filter((user) => userIsTargeted(user, notification))
      .map((user) => user.id);

    if (targetUserIds.length === 0) return;

    // 2. Tokens de esos usuarios. Firestore limita `in` a 30 valores por
    //    consulta, así que se trocea.
    const tokenDocs = [];
    for (let i = 0; i < targetUserIds.length; i += 30) {
      const chunk = targetUserIds.slice(i, i + 30);
      const snap = await db
        .collection(`companies/${companyId}/deviceTokens`)
        .where("userId", "in", chunk)
        .get();
      tokenDocs.push(...snap.docs);
    }

    const tokens = [...new Set(tokenDocs.map((d) => d.get("token")).filter(Boolean))];
    if (tokens.length === 0) return;

    // 3. Enviar. `data` lleva el destino para que al tocar la notificación la
    //    app abra la pantalla correcta.
    const response = await getMessaging().sendEachForMulticast({
      tokens,
      notification: {
        title: notification.title || "BakeOS",
        body: notification.body || "",
      },
      data: {
        type: notification.type || "",
        targetScreen: notification.targetScreen || "NONE",
        targetId: notification.targetId || "",
        notificationId: event.params.notificationId,
      },
      android: {
        priority: "high",
        notification: { channelId: "bakeos_pedidos", sound: "default" },
      },
      apns: {
        payload: { aps: { sound: "default" } },
      },
    });

    // 4. Limpiar tokens muertos (app desinstalada, token rotado). Si no se
    //    borran, cada push arrastra envíos fallidos para siempre.
    const staleDeletes = [];
    response.responses.forEach((res, index) => {
      if (res.success) return;
      const code = res.error && res.error.code;
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token" ||
        code === "messaging/invalid-argument"
      ) {
        staleDeletes.push(
          db.doc(`companies/${companyId}/deviceTokens/${tokens[index]}`).delete()
        );
      }
    });
    await Promise.allSettled(staleDeletes);
  }
);
