package com.better.alarm.test

import android.support.test.InstrumentationRegistry
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.test.suitebuilder.annotation.LargeTest
import com.better.alarm.logger.Logger
import com.better.alarm.persistance.AlarmDatabaseHelper
import com.better.alarm.presenter.AlarmsListActivity
import com.robotium.solo.Solo
import junit.framework.Assert
import org.junit.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
@LargeTest
class ActionBarTest {
    var listActivity = ActivityTestRule(
            AlarmsListActivity::class.java, false, /* autostart*/ true)

    @JvmField
    @Rule
    public var chain: TestRule = RuleChain.outerRule(ForceLocaleRule(Locale.US)).around(listActivity)

    private var solo: Solo? = null;

    companion object {
        @JvmStatic
        @BeforeClass
        @AfterClass
        public fun dropDatabase() {
            val context = InstrumentationRegistry.getTargetContext()
            val dbHelper = AlarmDatabaseHelper(context, Logger.create())
            val db = dbHelper.writableDatabase
            db.execSQL("DROP TABLE IF EXISTS alarms")
            dbHelper.onCreate(db)
            db.close()
            println("Dropped database")
        }
    }

    @Before
    public fun setup() {
        solo = Solo(InstrumentationRegistry.getInstrumentation(), listActivity.activity)
    }

    @Test
    fun testBugreportButton() {
        val solo = solo!!
        solo.sendKey(Solo.MENU)
        solo.clickOnText("bugreport")
        Assert.assertTrue(solo.searchText("Describe"))
        solo.clickOnButton("Cancel")
    }

    @Test
    fun rateTheApp() {
        val solo = solo!!
        solo.sendKey(Solo.MENU)
        solo.clickOnText("Rate the app")
        Assert.assertTrue(solo.searchText("Would you like to proceed?"))
        solo.clickOnButton("Cancel")
    }

    @Test
    fun mp3Cutter() {
        val solo = solo!!
        solo.sendKey(Solo.MENU)
        solo.clickOnText("Extensions")
        solo.clickOnText("MP3")
        Assert.assertTrue(solo.searchText("transferred"))
        solo.clickOnButton("Cancel")
    }
}