package app.petitrythme

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.petitrythme.data.Event
import app.petitrythme.data.EventType
import app.petitrythme.data.Side
import app.petitrythme.ui.Fmt
import android.os.Looper
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Lance la vraie appli (base Room comprise) avec une semaine de données
 * réalistes et enregistre des captures des écrans principaux.
 * Les images sont écrites dans app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    companion object {
        private const val MIN = 60_000L
        private const val H = 60 * MIN

        /** Peuple la base avec 8 jours de vie de bébé, avant le lancement de l'activité. */
        fun seed(withTimer: Boolean) {
            val app = ApplicationProvider.getApplicationContext<BabyApp>()
            app.profile.update { it.copy(name = "Léa", birthDate = Fmt.today().minusDays(38)) }
            val now = System.currentTimeMillis()
            val today0 = Fmt.startOfDay(Fmt.today())
            val events = mutableListOf<Event>()
            for (d in 7 downTo 0) {
                val day0 = today0 - d * 24 * H
                var t = day0 + (40 + d * 7) * MIN
                var i = 0
                while (t < day0 + 24 * H && t < now - 20 * MIN) {
                    val side = if (i % 2 == 0) Side.L else Side.R
                    if (i % 3 == 2) {
                        events += Event(type = EventType.BOTTLE, time = t, amountMl = 90.0 + (i % 2) * 30, sub = "BREAST")
                    } else {
                        events += Event(
                            type = EventType.NURSING, time = t, leftSec = 9 * 60 + i * 20, rightSec = 6 * 60 + d * 30,
                            sub = side.code, endTime = t + 15 * MIN,
                        )
                    }
                    if (t + 25 * MIN < now) events += Event(type = EventType.DIAPER, time = t + 25 * MIN, sub = if (i % 3 == 0) "BOTH" else "PEE", detail = if (i % 3 == 0) "YELLOW" else null)
                    val sleepStart = t + 40 * MIN
                    val sleepLen = if (i == 0 || t > day0 + 20 * H) 3 * H + 10 * MIN else (70 + (i * 13) % 50) * MIN
                    if (sleepStart + sleepLen < now) {
                        events += Event(type = EventType.SLEEP, time = sleepStart, endTime = sleepStart + sleepLen)
                    }
                    t += if (t > day0 + 20 * H || t < day0 + 6 * H) 3 * H + 30 * MIN else 2 * H + 50 * MIN
                    i++
                }
                if (d % 3 == 0) events += Event(type = EventType.BATH, time = day0 + 19 * H)
                events += Event(type = EventType.MEDICINE, time = day0 + 9 * H, detail = "Vitamine D", detail2 = "1 goutte")
            }
            events += Event(type = EventType.GROWTH, time = today0 - 30 * 24 * H, value = 3.45, heightCm = 50.0, headCm = 35.0)
            events += Event(type = EventType.GROWTH, time = today0 - 16 * 24 * H, value = 3.98, heightCm = 52.5, headCm = 36.5)
            events += Event(type = EventType.GROWTH, time = today0 - 2 * 24 * H, value = 4.62, heightCm = 55.0, headCm = 37.8)
            runBlocking { app.db.events().replaceAll(events.filter { it.time < now }) }
            app.timers.cancelNursing()
            app.timers.cancelSleep()
            if (withTimer) {
                app.timers.tapSide(Side.L, now - 7 * MIN)
                app.timers.tapSide(Side.R, now - 3 * MIN)
            }
        }
    }

    /** Attend qu'un texte apparaisse, sans faire avancer l'horloge virtuelle de Compose. */
    private fun waitFor(text: String) {
        repeat(40) {
            if (rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()) return
            settle()
        }
        throw AssertionError("Texte introuvable : $text")
    }

    /** Lance l'activité (après avoir peuplé la base) et laisse Robolectric traiter ce qui est en attente. */
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun restart() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun settle() {
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(150)
            rule.waitForIdle()
        }
    }

    private fun shot(name: String) {
        settle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun screens() {
        seed(withTimer = true)
        restart()
        waitFor("Léa")
        waitFor("Commencée à")
        shot("01_accueil")

        rule.onNodeWithText("Journal").performClick()
        waitFor("repas")
        shot("02_journal")

        rule.onNodeWithText("Rythme").performClick()
        waitFor("Sur 24 heures")
        shot("03_rythme")

        rule.onNodeWithText("Accueil").performClick()
        waitFor("Biberon")
        rule.onNodeWithText("Biberon").performScrollTo().performClick()
        waitFor("Quantité bue")
        shot("04_saisie_biberon")
    }

    @Test
    fun diaperDetails() {
        seed(withTimer = false)
        restart()
        waitFor("Léa")
        shot("05_accueil_sans_chrono")
        rule.onNodeWithText("Journal").performClick()
        waitFor("Pipi + caca")
        rule.onAllNodes(hasText("Pipi + caca"))[0].performClick()
        waitFor("Couleur des selles")
        rule.onNodeWithText("Pâle / blanche").performClick()
        shot("06_couche_details")
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkMode() {
        seed(withTimer = true)
        restart()
        waitFor("Léa")
        shot("07_accueil_nuit")
        rule.onNodeWithText("Rythme").performClick()
        waitFor("Sur 24 heures")
        shot("08_rythme_nuit")
    }
}
