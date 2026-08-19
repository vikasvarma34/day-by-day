package com.vikaspokala.daybyday.ui.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

@Serializable
sealed class Recurrence {
    abstract val endDate: @Serializable(with = LocalDateSerializer::class) LocalDate?

    @Serializable
    data object Once : Recurrence() {
        override val endDate: LocalDate? get() = null
    }
    
    @Serializable
    data class IntervalDays(
        val intervalDays: Int,
        override val endDate: @Serializable(with = LocalDateSerializer::class) LocalDate? = null
    ) : Recurrence()
    
    @Serializable
    data class Weekdays(
        val days: Set<Int>, // 1 = Monday, 7 = Sunday
        override val endDate: @Serializable(with = LocalDateSerializer::class) LocalDate? = null
    ) : Recurrence()
}

fun Recurrence?.toDisplayString(anchorDate: LocalDate?): String {
    if (this == null || this is Recurrence.Once) return "Does not repeat"
    val base = when (this) {
        is Recurrence.IntervalDays -> {
            if (intervalDays == 1) "Every day"
            else "Every $intervalDays days"
        }
        is Recurrence.Weekdays -> {
            if (days.isEmpty()) "Does not repeat"
            else if (days.size == 7) "Every day"
            else if (anchorDate != null && days.size == 1 && days.contains(anchorDate.dayOfWeek.value)) {
                "Every ${anchorDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}"
            } else if (days.size == 1) {
                "Every ${DayOfWeek.of(days.first()).name.lowercase().replaceFirstChar { it.uppercase() }}"
            } else {
                val sortedDays = days.sortedBy { it % 7 } // Sun(0) to Sat(6) (Note: Sun is 7%7=0)
                sortedDays.joinToString(", ") { 
                    DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } 
                }
            }
        }
    }

    if (base == "Does not repeat") return base
    return if (endDate != null) {
        val endFormatter = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
        "$base, until ${endDate?.format(endFormatter)}"
    } else {
        base
    }
}
