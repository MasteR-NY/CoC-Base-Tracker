package com.villageclock.app

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 29, 35])
class HiredWorkersTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun fixture(): Village {
        val raw = """{"tag":"#HIREDTEST","timestamp":${System.currentTimeMillis() / 1000},"buildings":[{"data":1000001,"lvl":12},{"data":1000015,"cnt":1},{"data":1000008,"lvl":10,"timer":3600},{"data":1000013,"lvl":5,"timer":5400,"extra":true}],"units":[{"data":4000000,"lvl":1,"timer":1800},{"data":4000001,"lvl":1,"timer":7200,"extra":true}]}"""
        return ExportParser.parse(raw, Store.catalog(context), System.currentTimeMillis())
    }
    @Test fun hiredRowsHaveAnIconAndNoTextBadgeOnEverySupportedFramework() {
        val village = fixture()
        val now = System.currentTimeMillis()
        val hired = village.upgrades.first { it.extraBuilder }
        val view = VillageWidget.row(context, hired, now, village.exportedAt, true).apply(context, AppWidgetHostView(context))
        val icon = view.findViewById<ImageView>(R.id.goblin_tag)
        assertEquals(View.VISIBLE, icon.visibility)
        assertNotNull(icon.drawable)
        assertTrue(icon.contentDescription.toString().contains("Hired Goblin"))
        assertFalse(view.findViewById<TextView>(R.id.row_name).text.toString().contains("GOBLIN"))
        val regular = village.upgrades.first { !it.extraBuilder }
        val plain = VillageWidget.row(context, regular, now, village.exportedAt, true).apply(context, AppWidgetHostView(context))
        assertEquals(View.GONE, plain.findViewById<View>(R.id.goblin_tag).visibility)
    }
    @Test fun builderAndResearchHeadingsShowHiredWorkWithoutChangingPermanentCapacity() {
        val village = fixture()
        val now = System.currentTimeMillis()
        assertTrue(HiredWorkers.active(village, Village.Queue.BUILDERS, now))
        assertTrue(HiredWorkers.active(village, Village.Queue.LAB, now))
        assertEquals(0, village.freeBuilders(now))
        assertEquals(village.upgrades.first { it.queue == Village.Queue.BUILDERS && !it.extraBuilder }.finishAt, village.nextFree(Village.Queue.BUILDERS, now))
        val jobs = VillageWidget.displayJobs(village, now)
        val view = VillageWidget.styledRow(context, village, jobs, 0, now, true).apply(context, AppWidgetHostView(context))
        val title = view.findViewById<TextView>(R.id.section_heading)
        assertEquals("BUILDERS 1/1 +", title.text.toString())
        assertNotNull(title.compoundDrawables[2])
        val heading = TextView(context).apply { text = VillageWidget.sectionTitle(village, Village.Queue.LAB, now) }
        HiredWorkers.mark(heading)
        assertEquals("LAB +", heading.text.toString())
        assertNotNull(heading.compoundDrawables[2])
        assertFalse(HiredWorkers.active(village, Village.Queue.BUILDERS, village.upgrades.maxOf { it.finishAt } + 1))
    }
    @Test fun plannerMarkerMatchesTheExactHiredTimer() {
        val village = fixture()
        val planner = Store.planner(context)
        val hired = village.upgrades.first { it.queue == Village.Queue.BUILDERS && it.extraBuilder }
        val regular = village.upgrades.first { it.queue == Village.Queue.BUILDERS && !it.extraBuilder }
        fun row(upgrade: Village.Upgrade) = UpgradePlanner.Row(requireNotNull(planner.item(upgrade.dataId)), upgrade.level - 1, 1, UpgradePlanner.State.RUNNING, upgrade.finishAt, "", "", false)
        assertTrue(HiredWorkers.plannerRow(village, row(hired)))
        assertFalse(HiredWorkers.plannerRow(village, row(regular)))
    }
}
