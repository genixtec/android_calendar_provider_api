package com.genix.samplecalendarapi

import android.annotation.SuppressLint
import android.database.Cursor
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.CalendarContract
import android.accounts.AccountManager
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import kotlinx.android.synthetic.main.activity_main.*
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.AccountPicker
import android.content.Intent
import android.util.Log
import android.widget.Toast
import java.util.*

@Suppress("UNUSED_VARIABLE")
@SuppressLint("Recycle", "LogNotTimber")
class MainActivity : AppCompatActivity() {

    companion object {

        private const val TAG = "MainActivity"

        private val EVENT_PROJECTION: Array<String> = arrayOf(
            CalendarContract.Calendars._ID,                     // 0
            CalendarContract.Calendars.ACCOUNT_NAME,            // 1
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,   // 2
            CalendarContract.Calendars.OWNER_ACCOUNT            // 3
        )

        const val REQUEST_CODE = 12345
    }

    private lateinit var accountName: String

    private lateinit var accountType: String

    private var calId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = getSharedPreferences("CalendarAPI", Context.MODE_PRIVATE)

        // Google Account Picker
        accountPicker.setOnClickListener {
            val googlePicker = AccountPicker
                .newChooseAccountIntent(
                    null, null, arrayOf(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE),
                    true, null, null, null, null
                )
            startActivityForResult(googlePicker, REQUEST_CODE)
        }

        // Query Calendar Details
        queryCalendar.setOnClickListener {
            val uri: Uri = CalendarContract.Calendars.CONTENT_URI
            val selection: String = "((${CalendarContract.Calendars.ACCOUNT_NAME} = ?) AND (" +
                    "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?) AND (" +
                    "${CalendarContract.Calendars.OWNER_ACCOUNT} = ?))"
            val selectionArgs: Array<String> =
                arrayOf(accountName, accountType, accountName)
            val cur: Cursor =
                contentResolver.query(
                    uri, EVENT_PROJECTION, selection, selectionArgs, null
                )!!
            cur.use { curs ->
                // Use the cursor to step through the returned records
                while (cur.moveToNext()) {

                    // Get the field values
                    calId = curs.getLong(curs.getColumnIndex(CalendarContract.Calendars._ID))
                    prefs.edit().putLong("CALID", calId!!).apply()

                    // Selected Account Name
                    val accountName: String =
                        curs.getString(curs.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME))

                    // Selected Display Name
                    val displayName: String =
                        curs.getString(
                            curs.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                        )

                    // Selected Account Owner
                    val ownerName: String =
                        curs.getString(
                            curs.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                        )
                    Toast.makeText(
                        this,
                        "Calendar Details Queried.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Modify Calendar Details
        modifyCalendar.setOnClickListener {
            val values = ContentValues().apply {
                // The new display name for the calendar
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "My Calendars")
            }
            val updateUri: Uri =
                ContentUris.withAppendedId(
                    CalendarContract.Calendars.CONTENT_URI,
                    prefs.getLong("CALID", -1)
                )
            val rows: Int = contentResolver.update(updateUri, values, null, null)
            Log.i(TAG, "Rows updated: $rows")
            Toast.makeText(
                this,
                "Calendar Display Name Changed.",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Create Event in Calendar
        createEvent.setOnClickListener {
            val startMillis: Long = Calendar.getInstance().run {
                timeInMillis + (60000 * 2) // Start time two minutes after
            }
            val endMillis: Long = Calendar.getInstance().run {
                timeInMillis + (60000 * 10)// End time ten minutes after
            }
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "Fever with headache")
                put(CalendarContract.Events.DESCRIPTION, "Patient Visit")
                put(CalendarContract.Events.CALENDAR_ID, prefs.getLong("CALID", -1))
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().displayName)
            }
            val uri: Uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)!!
            val eventID: Long = uri.lastPathSegment!!.toLong()
            prefs.edit().putLong("EventId", eventID).apply()
            Toast.makeText(this, "Event Created", Toast.LENGTH_SHORT).show()
        }

        // Update Calendar Event
        updateEvent.setOnClickListener {
            val values = ContentValues().apply {
                // The new title for the event
                put(CalendarContract.Events.TITLE, "Headache")
            }
            val eventId = prefs.getLong("EventId", -1)
            val updateUri: Uri =
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows: Int = contentResolver.update(updateUri, values, null, null)
            Log.i(TAG, "Rows updated: $rows")
            Toast.makeText(this, "Event Updated.", Toast.LENGTH_SHORT).show()
        }

        // Delete Calendar Event
        deleteEvent.setOnClickListener {
            val eventId = prefs.getLong("EventId", -1)
            val deleteUri: Uri =
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows: Int = contentResolver.delete(deleteUri, null, null)
            Log.i(TAG, "Rows deleted: $rows")
            Toast.makeText(this, "Event Deleted.", Toast.LENGTH_SHORT).show()
        }

        // Set an Reminder for Event. It will alert before in given minutes from the event
        // start time. Eg. If we give 1 minute means it will alert before the one minute from
        // the event start time.
        reminderEvent.setOnClickListener {
            val eventId = prefs.getLong("EventId", -1)
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.MINUTES, 1)
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            val uri: Uri = contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)!!
            val eventID: Long = uri.lastPathSegment!!.toLong()
            Log.i(TAG, eventID.toString())
            Toast.makeText(
                this,
                "Remind before 1 minute from the event start time",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(
        requestCode: Int, resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        // Return selected account details like account name, accountType and so on.
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            accountName = data!!.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
            Log.d(TAG, accountName)
            Toast.makeText(this, "Account Selected.", Toast.LENGTH_SHORT).show()
        }
    }
}
