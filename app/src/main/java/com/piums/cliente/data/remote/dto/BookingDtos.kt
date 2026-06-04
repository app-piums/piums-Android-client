package com.piums.cliente.data.remote.dto

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

// Maneja string/objeto/null — el backend a veces devuelve solo el ID como string
@JsonAdapter(BookingParticipantAdapter::class)
data class BookingParticipantDto(
    val id: String?,
    val name: String?,
    val nombre: String?,
    val email: String?,
    val avatar: String?
) {
    val resolvedName: String? get() = name ?: nombre
}

class BookingParticipantAdapter : TypeAdapter<BookingParticipantDto?>() {
    override fun write(out: JsonWriter, value: BookingParticipantDto?) {
        if (value == null) { out.nullValue(); return }
        out.beginObject()
        out.name("id"); if (value.id == null) out.nullValue() else out.value(value.id)
        out.name("name"); if (value.name == null) out.nullValue() else out.value(value.name)
        out.name("nombre"); if (value.nombre == null) out.nullValue() else out.value(value.nombre)
        out.name("email"); if (value.email == null) out.nullValue() else out.value(value.email)
        out.name("avatar"); if (value.avatar == null) out.nullValue() else out.value(value.avatar)
        out.endObject()
    }

    override fun read(reader: JsonReader): BookingParticipantDto? {
        return when (reader.peek()) {
            JsonToken.NULL -> { reader.nextNull(); null }
            JsonToken.STRING -> {
                // Backend devolvió solo el ID como string — mantenemos ID, sin nombre
                val id = reader.nextString()
                BookingParticipantDto(id = id, name = null, nombre = null, email = null, avatar = null)
            }
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                var id: String? = null; var name: String? = null
                var nombre: String? = null; var email: String? = null; var avatar: String? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id"     -> id     = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                        "name"   -> name   = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                        "nombre" -> nombre = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                        "email"  -> email  = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                        "avatar" -> avatar = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                BookingParticipantDto(id, name, nombre, email, avatar)
            }
            else -> { reader.skipValue(); null }
        }
    }
}

data class BookingDto(
    val id: String,
    val code: String?,
    val clientId: String,
    val artistId: String,
    val serviceId: String,
    val status: String,
    val paymentStatus: String?,
    val totalPrice: Int,
    val scheduledDate: String,
    val scheduledTime: String?,
    @SerializedName("durationMinutes") val duration: Int?,
    val notes: String?,
    val location: String?,
    val locationLat: Double?,
    val locationLng: Double?,
    val eventId: String?,
    val numDays: Int?,
    val createdAt: String,
    val artist: BookingParticipantDto? = null,
    val artistName: String? = null,
    val serviceName: String? = null,
    val anticipoRequired: Boolean? = null,
    val anticipoAmount: Int? = null,
    val currency: String? = null,
    val couponCode: String? = null,
    val discountAmount: Int? = null
) {
    // artistName (campo raíz) tiene prioridad sobre el nombre anidado en user.nombre
    // que puede ser un ID autogenerado del sistema
    val resolvedArtistName: String? get() = artistName ?: artist?.name ?: artist?.nombre
    val statusEnum: BookingStatus get() = BookingStatus.from(status)
    val canCancel: Boolean get() = status == "PENDING" || status == "CONFIRMED"
    val canPay: Boolean get() = status == "PAYMENT_PENDING"
    val canReview: Boolean get() = status == "COMPLETED"
    val canDispute: Boolean get() = status == "COMPLETED" || status == "CANCELLED_ARTIST"
    val canReschedule: Boolean get() = status == "PENDING" || status == "CONFIRMED" || status == "RESCHEDULED"
    val formattedTotal: String get() = "$${String.format("%,.2f", totalPrice / 100.0)}"
    val canReportNoShow: Boolean get() {
        if (status != "CONFIRMED") return false
        return try {
            java.time.LocalDate.parse(scheduledDate.take(10)) <= java.time.LocalDate.now()
        } catch (e: Exception) { false }
    }
}

enum class BookingStatus(val raw: String, val displayName: String, val color: Long) {
    PENDING("PENDING", "Pendiente", 0xFFF59E0B),
    CONFIRMED("CONFIRMED", "Confirmada", 0xFF3B82F6),
    PAYMENT_PENDING("PAYMENT_PENDING", "Pago pendiente", 0xFFF59E0B),
    PAYMENT_COMPLETED("PAYMENT_COMPLETED", "Pago completado", 0xFF22C55E),
    IN_PROGRESS("IN_PROGRESS", "En progreso", 0xFF3B82F6),
    DELIVERED("DELIVERED", "Entregado", 0xFF22C55E),
    COMPLETED("COMPLETED", "Completada", 0xFF22C55E),
    RESCHEDULED("RESCHEDULED", "Reprogramada", 0xFF8B5CF6),
    RESCHEDULE_PENDING_ARTIST("RESCHEDULE_PENDING_ARTIST", "Cambio pendiente", 0xFF8B5CF6),
    RESCHEDULE_PENDING_CLIENT("RESCHEDULE_PENDING_CLIENT", "Confirmar cambio", 0xFF8B5CF6),
    DISPUTE_OPEN("DISPUTE_OPEN", "Disputa abierta", 0xFFEF4444),
    DISPUTE_RESOLVED("DISPUTE_RESOLVED", "Disputa resuelta", 0xFF22C55E),
    CANCELLED_CLIENT("CANCELLED_CLIENT", "Cancelada por ti", 0xFFEF4444),
    CANCELLED_ARTIST("CANCELLED_ARTIST", "Cancelada por artista", 0xFFEF4444),
    REJECTED("REJECTED", "Rechazada", 0xFFEF4444),
    NO_SHOW("NO_SHOW", "No se presentó", 0xFF6B7280);

    companion object {
        fun from(raw: String) = values().firstOrNull { it.raw == raw } ?: PENDING
    }
}

enum class PaymentStatus {
    PENDING, CARD_AUTHORIZED, ANTICIPO_PAID, CHARGING_REMAINING,
    FULLY_PAID, PARTIALLY_REFUNDED, REFUNDED, FROZEN, FAILED, UNKNOWN;

    companion object {
        fun from(s: String?) = values().firstOrNull { it.name == s } ?: UNKNOWN
    }
}

data class BookingsListResponse(
    val bookings: List<BookingDto>?,
    val data: List<BookingDto>?,
    val items: List<BookingDto>?,
    val pagination: PaginationDto?
) {
    val list: List<BookingDto> get() = bookings ?: data ?: items ?: emptyList()
}

data class CreateBookingRequest(
    val artistId: String,
    val serviceId: String,
    val clientId: String,
    val scheduledDate: String,      // ISO 8601 con hora: "2026-04-28T10:00:00.000Z"
    val durationMinutes: Int,
    val location: String?,
    val locationLat: Double?,
    val locationLng: Double?,
    val clientNotes: String?,
    val numDays: Int = 1,
    val eventId: String? = null,
    val couponCode: String? = null,
    val eventType: String? = null
)

data class RescheduleRequest(
    val scheduledDate: String,
    val reason: String?
)

data class ReplacementSearchDto(
    val status: String,
    val expiresAt: String?,
    val matchedArtistIds: List<String> = emptyList(),
    val category: String?,
    val city: String?,
    val scheduledDate: String?
)

enum class EventType(val apiValue: String, val displayName: String) {
    CUMPLEANOS( "CUMPLEANOS",  "Cumpleaños"),
    BODA(       "BODA",        "Boda"),
    GRADUACION( "GRADUACION",  "Graduación"),
    QUINCEANERA("QUINCEANERA", "Quinceañera"),
    CORPORATIVO("CORPORATIVO", "Corporativo"),
    CONCIERTO(  "CONCIERTO",   "Concierto"),
    FIESTA(     "FIESTA",      "Fiesta"),
    BABY_SHOWER("BABY_SHOWER", "Baby Shower"),
    BAUTIZO(    "BAUTIZO",     "Bautizo"),
    ANIVERSARIO("ANIVERSARIO", "Aniversario"),
    OTRO(       "OTRO",        "Otro"),
}
