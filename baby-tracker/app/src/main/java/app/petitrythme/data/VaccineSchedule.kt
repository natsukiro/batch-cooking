package app.petitrythme.data

import java.time.LocalDate

/**
 * Calendrier vaccinal du nourrisson en France, proposé en SUGGESTIONS (jamais imposé).
 *
 * Source : « Calendrier des vaccinations et recommandations vaccinales 2026 »
 * (ministère de la Santé, avril 2026), recoupé avec plusieurs synthèses publiques.
 * Dernière vérification : novembre 2026. Le calendrier change régulièrement
 * (ex. méningocoques B et ACWY devenus obligatoires) : à revérifier chaque année
 * (voir review/DECISIONS.md) et toujours confirmer avec le médecin / le carnet de santé.
 */
object VaccineSchedule {
    const val SOURCE = "Calendrier des vaccinations 2026 (ministère de la Santé)"
    const val CHECKED = "novembre 2026"

    data class Vaccine(val name: String, val dose: String, val mandatory: Boolean = true, val detail: String? = null) {
        val label get() = "$name ($dose)" + if (mandatory) "" else " · recommandé"
    }

    data class Step(val key: String, val age: String, val months: Int, val vaccines: List<Vaccine>) {
        fun due(birth: LocalDate): LocalDate = birth.plusMonths(months.toLong())
        val summary get() = vaccines.joinToString(", ") { it.name + if (it.mandatory) "" else " (recommandé)" }
    }

    private const val HEXA = "Hexavalent"
    private const val HEXA_DETAIL = "diphtérie, tétanos, polio, coqueluche, Haemophilus influenzae b, hépatite B"
    private const val PNEUMO = "Pneumocoque"
    private const val ROTA = "Rotavirus"
    private const val MEN_B = "Méningocoque B"
    private const val MEN_ACWY = "Méningocoque ACWY"
    private const val ROR = "ROR"
    private const val ROR_DETAIL = "rougeole, oreillons, rubéole"

    val steps = listOf(
        Step(
            "m2", "2 mois", 2, listOf(
                Vaccine(HEXA, "1re dose", detail = HEXA_DETAIL),
                Vaccine(PNEUMO, "1re dose"),
                Vaccine(ROTA, "1re dose", mandatory = false),
            )
        ),
        Step(
            "m3", "3 mois", 3, listOf(
                Vaccine(MEN_B, "1re dose"),
                Vaccine(ROTA, "2e dose", mandatory = false),
            )
        ),
        Step(
            "m4", "4 mois", 4, listOf(
                Vaccine(HEXA, "2e dose", detail = HEXA_DETAIL),
                Vaccine(PNEUMO, "2e dose"),
                Vaccine(ROTA, "3e dose selon le vaccin", mandatory = false),
            )
        ),
        Step("m5", "5 mois", 5, listOf(Vaccine(MEN_B, "2e dose"))),
        Step("m6", "6 mois", 6, listOf(Vaccine(MEN_ACWY, "1re dose"))),
        Step(
            "m11", "11 mois", 11, listOf(
                Vaccine(HEXA, "rappel", detail = HEXA_DETAIL),
                Vaccine(PNEUMO, "rappel"),
            )
        ),
        Step(
            "m12", "12 mois", 12, listOf(
                Vaccine(ROR, "1re dose", detail = ROR_DETAIL),
                Vaccine(MEN_ACWY, "rappel"),
                Vaccine(MEN_B, "rappel"),
            )
        ),
        Step("m16", "16 à 18 mois", 16, listOf(Vaccine(ROR, "2e dose", detail = ROR_DETAIL))),
    )

    fun step(key: String?) = steps.firstOrNull { it.key == key }

    /**
     * Prochaines étapes à planifier : pas encore liées à un rendez-vous et pas en retard
     * de plus de 3 semaines (au-delà, on suppose que c'est géré avec le médecin).
     */
    fun suggestions(birth: LocalDate, today: LocalDate, planned: Set<String>, limit: Int = 2): List<Step> =
        steps.filter { it.key !in planned && !it.due(birth).isBefore(today.minusDays(21)) }.take(limit)
}
