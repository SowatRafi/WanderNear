package com.wandernear.core.model

/**
 * Currency and emergency number by country.
 *
 * These are global reference CONSTANTS (not per-city facts), so a small static
 * table is reliable and works fully offline — safer than fetching a safety-
 * critical emergency number live. This does NOT break the "never hand-curate per
 * city" rule: that rule is about a city's places, not a ~30-row country reference.
 *
 * ponytail: keyed by the English country name the pack stores (Melbourne →
 * "Australia"). When M6 downloads non-English-default cities, make the pipeline
 * geocode with English names (or switch this map to ISO country codes) so the
 * lookup keeps matching. Unknown country → returns null → the card simply omits
 * currency/emergency rather than guessing.
 *
 * `emergency` is a single, diallable primary number (112 works from mobiles in
 * much of the world). Pure Kotlin, no Android imports, so it stays portable.
 */
object CountryFacts {

    data class Facts(val currency: String, val emergency: String)

    private val byCountry: Map<String, Facts> = mapOf(
        "Australia" to Facts("AUD — Australian dollar", "000"),
        "New Zealand" to Facts("NZD — New Zealand dollar", "111"),
        "United States" to Facts("USD — US dollar", "911"),
        "Canada" to Facts("CAD — Canadian dollar", "911"),
        "United Kingdom" to Facts("GBP — pound sterling", "999"),
        "Ireland" to Facts("EUR — euro", "112"),
        "Germany" to Facts("EUR — euro", "112"),
        "France" to Facts("EUR — euro", "112"),
        "Italy" to Facts("EUR — euro", "112"),
        "Spain" to Facts("EUR — euro", "112"),
        "Portugal" to Facts("EUR — euro", "112"),
        "Netherlands" to Facts("EUR — euro", "112"),
        "Belgium" to Facts("EUR — euro", "112"),
        "Greece" to Facts("EUR — euro", "112"),
        "Austria" to Facts("EUR — euro", "112"),
        "Switzerland" to Facts("CHF — Swiss franc", "112"),
        "Japan" to Facts("JPY — Japanese yen", "110"),
        "China" to Facts("CNY — Chinese yuan", "110"),
        "South Korea" to Facts("KRW — South Korean won", "112"),
        "India" to Facts("INR — Indian rupee", "112"),
        "Singapore" to Facts("SGD — Singapore dollar", "999"),
        "Malaysia" to Facts("MYR — Malaysian ringgit", "999"),
        "Thailand" to Facts("THB — Thai baht", "191"),
        "Indonesia" to Facts("IDR — Indonesian rupiah", "112"),
        "Vietnam" to Facts("VND — Vietnamese dong", "113"),
        "United Arab Emirates" to Facts("AED — UAE dirham", "999"),
        "Turkey" to Facts("TRY — Turkish lira", "112"),
        "Mexico" to Facts("MXN — Mexican peso", "911"),
        "Brazil" to Facts("BRL — Brazilian real", "190"),
        "South Africa" to Facts("ZAR — South African rand", "112"),
    )

    /** Facts for a country name, or null if we don't have it (never guessed). */
    fun forCountry(country: String?): Facts? = country?.let { byCountry[it] }
}
