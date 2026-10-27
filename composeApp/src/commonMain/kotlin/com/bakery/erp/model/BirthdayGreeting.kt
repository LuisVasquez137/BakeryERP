package com.bakery.erp.model

/**
 * Felicitación de cumpleaños de un colaborador a otro (oct 2026). Al entrar con PIN el día del
 * cumpleaños de alguien, la app ofrece felicitarlo con un toque; el cumpleañero, al entrar, ve
 * quiénes lo felicitaron.
 *
 * Un documento por (año, cumpleañero, quien felicita): el id lo dice, así que tocar dos veces
 * no felicita dos veces. "Omitir" también se guarda ([BirthdayGreetingStatus.SKIPPED]) para no
 * volver a preguntar ese día; el botón de la tarjeta del Inicio lo puede convertir en SENT.
 */
data class BirthdayGreeting(
    val id: String = "",
    val year: Int = 0,
    val birthdayEmployeeId: String = "",
    val birthdayName: String = "",
    val senderEmployeeId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val status: String = BirthdayGreetingStatus.SENT,
    val createdAt: Long = 0L,
    /** Cuándo la vio el cumpleañero. 0 = todavía no: el diálogo vuelve a salir al entrar. */
    val seenAt: Long = 0L
)

object BirthdayGreetingStatus {
    const val SENT = "SENT"
    const val SKIPPED = "SKIPPED"
}

fun birthdayGreetingId(year: Int, birthdayEmployeeId: String, senderEmployeeId: String): String =
    "${year}_${birthdayEmployeeId}_$senderEmployeeId"

/**
 * A quiénes ofrecer felicitar: los que cumplen hoy, menos uno mismo y menos a quienes ya se
 * felicitó u omitió (cualquier documento de [mine] cuenta).
 */
fun greetingPromptsFor(todayIds: List<String>, me: String, mine: List<BirthdayGreeting>): List<String> {
    val done = mine.map { it.birthdayEmployeeId }.toSet()
    return todayIds.filter { it != me && it !in done }
}

/** Felicitaciones recibidas que el cumpleañero todavía no vio, en el orden en que llegaron. */
fun unseenGreetings(received: List<BirthdayGreeting>): List<BirthdayGreeting> =
    received.filter { it.status == BirthdayGreetingStatus.SENT && it.seenAt == 0L }.sortedBy { it.createdAt }
