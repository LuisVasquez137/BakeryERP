package com.bakery.erp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.BirthdayGreetingRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.model.BirthdayGreeting
import com.bakery.erp.model.BirthdayGreetingStatus
import com.bakery.erp.model.User
import com.bakery.erp.model.birthdayGreetingId
import com.bakery.erp.model.greetingPromptsFor
import com.bakery.erp.model.unseenGreetings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Quien está usando la app: la persona del PIN, o la ficha ligada a la cuenta. */
data class GreetingPerson(val employeeId: String, val name: String, val photoUrl: String)

data class BirthdayGreetingUiState(
    val me: GreetingPerson? = null,
    /** Cumpleañeros de hoy a quienes todavía se ofrece felicitar al entrar (en orden). */
    val prompts: List<BirthdayPerson> = emptyList(),
    /** Cumpleañeros de hoy a quienes ya felicitó (para el botón de la tarjeta del Inicio). */
    val greetedIds: Set<String> = emptySet(),
    /** Felicitaciones nuevas para mí (soy el cumpleañero): el diálogo de celebración. */
    val received: List<BirthdayGreeting> = emptyList(),
    /** Cuántas llevo hoy en total, contando las ya vistas. */
    val receivedTotal: Int = 0,
    /** Acaba de felicitar a esta persona: la animación de "¡Enviado!". */
    val justSentTo: BirthdayPerson? = null
)

/**
 * Felicitaciones de cumpleaños (ver `BirthdayGreeting.kt`). [onEnter] corre cada vez que alguien
 * entra (PIN o cuenta propia): decide si hay que celebrar al cumpleañero y a quién ofrecer
 * felicitar. Lo que se omitió o envió queda guardado, así que no se vuelve a preguntar.
 */
class BirthdayGreetingViewModel(
    private val repository: BirthdayGreetingRepository,
    private val employeeRepository: EmployeeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BirthdayGreetingUiState())
    val uiState: StateFlow<BirthdayGreetingUiState> = _uiState.asStateFlow()
    private var companyId = ""

    fun onEnter(user: User) {
        companyId = user.orgId
        if (companyId.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val employees = employeeRepository.getEmployees(companyId)
                val me = (employees.firstOrNull { it.id == user.operatorEmployeeId && user.operatorEmployeeId.isNotEmpty() }
                    ?: employees.firstOrNull { user.operatorEmployeeId.isEmpty() && it.userId == user.id })
                    ?.let { GreetingPerson(it.id, it.name, it.photoUrl) }
                val todays = buildBirthdays(employees, today).thisWeek.filter { it.daysFromToday == 0 }
                if (me == null) {
                    _uiState.value = BirthdayGreetingUiState()
                    return@launch
                }
                val mine = if (todays.isEmpty()) emptyList()
                    else repository.getSentBy(companyId, me.employeeId, today.year)
                val promptIds = greetingPromptsFor(todays.map { it.employeeId }, me.employeeId, mine)
                val received = if (todays.any { it.employeeId == me.employeeId })
                    repository.getReceivedBy(companyId, me.employeeId, today.year) else emptyList()
                _uiState.value = BirthdayGreetingUiState(
                    me = me,
                    prompts = todays.filter { it.employeeId in promptIds },
                    greetedIds = mine.filter { it.status == BirthdayGreetingStatus.SENT }.map { it.birthdayEmployeeId }.toSet(),
                    received = unseenGreetings(received),
                    receivedTotal = received.count { it.status == BirthdayGreetingStatus.SENT }
                )
            }
        }
    }

    /** Felicitar desde el aviso al entrar o desde el botón de la tarjeta. */
    fun send(person: BirthdayPerson) = save(person, BirthdayGreetingStatus.SENT)

    /** "Omitir": no se vuelve a preguntar hoy; el botón de la tarjeta sigue ahí. */
    fun skip(person: BirthdayPerson) = save(person, BirthdayGreetingStatus.SKIPPED)

    private fun save(person: BirthdayPerson, status: String) {
        val me = _uiState.value.me ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val year = person.date.year
        _uiState.update {
            it.copy(
                prompts = it.prompts.filterNot { p -> p.employeeId == person.employeeId },
                greetedIds = if (status == BirthdayGreetingStatus.SENT) it.greetedIds + person.employeeId else it.greetedIds,
                justSentTo = if (status == BirthdayGreetingStatus.SENT) person else it.justSentTo
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.save(companyId, BirthdayGreeting(
                    id = birthdayGreetingId(year, person.employeeId, me.employeeId),
                    year = year,
                    birthdayEmployeeId = person.employeeId,
                    birthdayName = person.name,
                    senderEmployeeId = me.employeeId,
                    senderName = me.name,
                    senderPhotoUrl = me.photoUrl,
                    status = status,
                    createdAt = now
                ))
            }
        }
    }

    fun clearJustSent() { _uiState.update { it.copy(justSentTo = null) } }

    /** El cumpleañero cerró la celebración: las vistas no vuelven a salir. */
    fun dismissReceived() {
        val ids = _uiState.value.received.map { it.id }
        _uiState.update { it.copy(received = emptyList()) }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.markSeen(companyId, ids, Clock.System.now().toEpochMilliseconds()) }
        }
    }
}
