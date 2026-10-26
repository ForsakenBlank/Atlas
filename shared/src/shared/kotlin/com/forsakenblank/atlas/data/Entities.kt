package com.forsakenblank.atlas.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class ItemType { FOLDER, NOTE, TRACKER, SHEET }

enum class TrackerKind { COUNTER, YES_NO, TIMER, NUMBER, RATING }

// how a number tracker turns a day of logs into one value
enum class Aggregate { SUM, AVERAGE, LAST }

enum class Repeat { NONE, DAILY, WEEKDAYS, WEEKLY, FORTNIGHTLY, MONTHLY, YEARLY }

enum class CountdownKind { BIRTHDAY, ANNIVERSARY, HOLIDAY, EVENT, OTHER }

// everything in the explorer is an item, the type decides which extra table holds its data
@Serializable
@Entity(tableName = "items", indices = [Index("parentId"), Index("type")])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val parentId: Long? = null, // null means the item sits at the root
    val name: String,
    val color: Int? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val pinned: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    val deletedAt: Long? = null, // set when the item is in the trash
)

@Serializable
@Entity(
    tableName = "note_bodies",
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
)
data class NoteBody(
    @PrimaryKey val itemId: Long,
    val text: String = "",
)

// a spreadsheet saved as json, see util/Sheet.kt for what is inside
@Serializable
@Entity(
    tableName = "sheets",
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
)
data class SheetBody(
    @PrimaryKey val itemId: Long,
    val json: String = "",
)

@Serializable
@Entity(
    tableName = "trackers",
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
)
data class Tracker(
    @PrimaryKey val itemId: Long,
    val kind: TrackerKind,
    val unit: String? = null,
    val dailyGoal: Int? = null, // taps for counters, minutes for timers
    val showOnHome: Boolean = true,
    val runningSince: Long? = null, // timer start, kept in the db so it survives the app closing
    val aggregate: Aggregate? = null, // only used by number trackers, null means sum
    // off keeps its logs off the calendar cells, the full day popup still lists them
    @ColumnInfo(defaultValue = "1") val showOnCalendar: Boolean = true,
)

@Serializable
@Entity(
    tableName = "log_entries",
    indices = [Index("trackerId"), Index("timestamp")],
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["trackerId"], onDelete = ForeignKey.CASCADE)],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val timestamp: Long,
    val value: Double = 1.0,
    val durationSeconds: Long? = null,
    val note: String? = null,
)

@Serializable
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int? = null,
)

@Serializable
@Entity(
    tableName = "item_tags",
    primaryKeys = ["itemId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ItemTag(
    val itemId: Long,
    val tagId: Long,
)

@Serializable
@Entity(tableName = "events", indices = [Index("startsAt")])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startsAt: Long,
    val endsAt: Long,
    val allDay: Boolean = false,
    val color: Int? = null,
    val location: String? = null,
    val notes: String? = null,
    val repeatRule: Repeat = Repeat.NONE,
    val repeatUntil: Long? = null,
    val created: Long = System.currentTimeMillis(),
    // null follows the default in settings, -1 means no reminder, otherwise minutes before the start
    val reminderMinutes: Int? = null,
)

@Serializable
@Entity(tableName = "tasks", indices = [Index("due"), Index("subjectId")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String? = null,
    val due: Long? = null, // start of the due day
    val priority: Int = 0, // 0 none, 1 low, 2 medium, 3 high
    val done: Boolean = false,
    val doneAt: Long? = null,
    val repeatRule: Repeat = Repeat.NONE,
    val subjectId: Long? = null, // homework belongs to a timetable subject
    val created: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val remind: Boolean = true,
)

@Serializable
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int? = null,
    val teacher: String? = null,
    val room: String? = null,
    val notes: String? = null,
    val folderId: Long? = null, // explorer folder made for this subject
)

@Serializable
@Entity(
    tableName = "timetable_slots",
    indices = [Index("subjectId")],
    foreignKeys = [ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.CASCADE)],
)
data class TimetableSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val dayOfWeek: Int, // 1 monday to 7 sunday
    val startMinute: Int, // minutes after midnight
    val endMinute: Int,
    val week: Int = 0, // 0 every week, 1 week A, 2 week B
    val kind: String? = null, // lecture, lab and so on
    val room: String? = null, // overrides the subject room
)

@Serializable
@Entity(tableName = "terms")
data class Term(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDay: Long, // epoch day
    val endDay: Long,
)

@Serializable
@Entity(tableName = "countdowns")
data class Countdown(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    // epoch day, for birthdays the birth date
    val date: Long,
    val kind: CountdownKind = CountdownKind.EVENT,
    val yearly: Boolean = false,
    val yearKnown: Boolean = true,
    val countUp: Boolean = false,
    val color: Int? = null,
    val emoji: String? = null,
    val note: String? = null,
    val pinned: Boolean = false,
    val showOnCalendar: Boolean = true,
    val remind: Boolean = true,
    val created: Long = System.currentTimeMillis(),
)

// a pot of money like a current account, savings or cash
@Serializable
@Entity(tableName = "money_accounts")
data class MoneyAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val openingPence: Long = 0, // what was in it before the first entry
    val color: Int? = null,
    val sortOrder: Int = 0,
    val created: Long = System.currentTimeMillis(),
)

// one bit of money moving, positive is coming in and negative is going out
@Serializable
@Entity(
    tableName = "money_entries",
    indices = [Index("accountId"), Index("day")],
    foreignKeys = [ForeignKey(entity = MoneyAccount::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
)
data class MoneyEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val day: Long, // epoch day, so past dates are just a smaller number
    val amountPence: Long,
    val category: String? = null,
    val note: String? = null,
    val transferId: Long? = null, // both halves of a transfer share this, they stay out of earning and spending totals
    // a record of something in the past, it shows in activity and charts but leaves the balance alone
    @ColumnInfo(defaultValue = "0") val historical: Boolean = false,
    val created: Long = System.currentTimeMillis(),
)

// a saved one tap entry like a coffee, logging it adds a normal entry dated today
@Serializable
@Entity(
    tableName = "money_quick",
    indices = [Index("accountId")],
    foreignKeys = [ForeignKey(entity = MoneyAccount::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
)
data class MoneyQuick(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val name: String,
    val amountPence: Long, // negative is spending
    val category: String? = null,
    val note: String? = null,
    val sortOrder: Int = 0,
)

// a few words about one day, shown on the calendar and in the full day popup
@Serializable
@Entity(tableName = "day_notes")
data class DayNote(
    @PrimaryKey val day: Long, // epoch day
    val text: String,
    val updated: Long = System.currentTimeMillis(),
)
