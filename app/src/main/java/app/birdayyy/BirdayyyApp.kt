package app.birdayyy

import android.app.Application
import app.birdayyy.reminders.Channels
import app.birdayyy.reminders.ReminderScheduler

class BirdayyyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Channels.create(this)
        ReminderScheduler.scheduleDaily(this)
    }
}
