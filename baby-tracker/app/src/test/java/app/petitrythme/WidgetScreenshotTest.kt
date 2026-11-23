package app.petitrythme

import android.app.Activity
import android.os.Looper
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import app.petitrythme.data.EventType
import app.petitrythme.data.Side
import app.petitrythme.widget.BabyWidget
import app.petitrythme.widget.LastAction
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rend le widget tel que le lanceur l'afficherait (RemoteViews générées par Glance)
 * et en enregistre des captures, dans plusieurs états.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
class WidgetScreenshotTest {
    private val app get() = ApplicationProvider.getApplicationContext<BabyApp>()

    @OptIn(ExperimentalGlanceApi::class)
    private fun shot(name: String, w: Int, h: Int) {
        val rv = runBlocking { BabyWidget().compose(app, size = DpSize(w.dp, h.dp)) }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        fun px(dp: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), activity.resources.displayMetrics).toInt()
        // Un fond façon fond d'écran, pour juger le contraste du widget.
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(0xFF5E6B7D.toInt())
            setPadding(px(16), px(16), px(16), px(16))
        }
        val view = rv.apply(activity, frame)
        frame.addView(view, FrameLayout.LayoutParams(px(w), px(h)))
        activity.setContentView(frame, ViewGroup.LayoutParams(px(w + 32), px(h + 32)))
        shadowOf(Looper.getMainLooper()).idle()
        frame.captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun states() {
        ScreenshotTest.seed(withTimer = false)
        app.widgetStore.set(null)
        shot("10_widget", 340, 150)
        shot("11_widget_compact", 340, 100)

        app.widgetStore.set(LastAction(1, EventType.DIAPER, "Pipi", System.currentTimeMillis()))
        shot("12_widget_apres_pipi", 340, 150)

        app.widgetStore.set(null)
        app.timers.tapSide(Side.R)
        shot("13_widget_tetee", 340, 150)
        app.timers.cancelNursing()
    }

    @Test
    @Config(qualifiers = "+night")
    fun night() {
        ScreenshotTest.seed(withTimer = false)
        app.timers.startSleep(System.currentTimeMillis() - 40 * 60_000L)
        shot("14_widget_nuit_dodo", 340, 150)
        app.timers.cancelSleep()
    }
}
