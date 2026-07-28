package com.wandernear.data

import android.content.Context
import android.telephony.TelephonyManager
import android.telephony.emergency.EmergencyNumber
import com.wandernear.core.model.CountryFacts

/**
 * Which number to dial in an emergency, wherever the user happens to be.
 *
 * This is the one fact in the app that must never be blank or wrong, so it is resolved
 * from the most authoritative source available, in order:
 *
 *  1. **The phone itself.** `TelephonyManager.getEmergencyNumberList()` returns the
 *     numbers the CARRIER provisioned for the network you're actually attached to. That
 *     beats any table we could ship, because it's the same data the dialler uses.
 *  2. **Our country table** ([CountryFacts]) — the local number for the ~30 countries
 *     it covers.
 *  3. **112** — the GSM standard, which networks must route to local emergency services.
 *
 * Nothing here is guessed: every step returns a real number from a real source, and the
 * result records WHICH source it came from so the UI can be honest about it.
 *
 * We deliberately do NOT request `READ_PHONE_STATE` just for step 1. A travel app asking
 * for phone-state permission is a poor trade for a number steps 2 and 3 already provide,
 * so step 1 is attempted and allowed to fail quietly.
 */
object EmergencyNumbers {

    /** Where a number came from, so the UI can label it truthfully. */
    enum class Source { CARRIER, COUNTRY_TABLE, INTERNATIONAL }

    data class Result(val number: String, val source: Source) {
        /** True when this is the actual local number rather than the international fallback. */
        val isLocal: Boolean get() = source != Source.INTERNATIONAL
    }

    /**
     * The best emergency number we can establish for [country] (an English country name
     * as stored in a pack, or null when unknown). Never returns null.
     */
    fun resolve(context: Context, country: String?): Result {
        fromCarrier(context)?.let { return Result(it, Source.CARRIER) }
        val fallback = CountryFacts.emergencyFor(country)
        return Result(
            fallback.number,
            if (fallback.local) Source.COUNTRY_TABLE else Source.INTERNATIONAL,
        )
    }

    /**
     * The carrier's own general emergency number, or null if the platform won't tell us
     * (no permission, no SIM, or an older/odd device).
     *
     * Prefers a number flagged for ALL services — that's the single "call for help"
     * number (000 / 911 / 112) rather than a service-specific one like a fire-only line.
     */
    private fun fromCarrier(context: Context): String? = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val numbers = tm?.emergencyNumberList?.values?.flatten().orEmpty()
        numbers.firstOrNull {
            EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED in it.emergencyServiceCategories
        }?.number ?: numbers.firstOrNull()?.number
    } catch (e: SecurityException) {
        null   // needs READ_PHONE_STATE, which we choose not to ask for
    } catch (e: Exception) {
        null   // no telephony, no SIM, or a vendor quirk — the table covers us
    }?.takeIf { it.isNotBlank() }
}
