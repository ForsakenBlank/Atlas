package com.forsakenblank.atlas.data

import com.forsakenblank.atlas.platform.inTransaction
import com.forsakenblank.atlas.util.Sheet
import com.forsakenblank.atlas.util.next
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.startOfDay
import com.forsakenblank.atlas.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class AtlasRepository(private val db: AtlasDatabase) {

    private val items = db.items()
    private val notes = db.notes()
    private val trackers = db.trackers()
    private val tags = db.tags()
    private val events = db.events()
    private val tasks = db.tasks()
    private val timetable = db.timetable()
    private val sheets = db.sheets()
    private val countdowns = db.countdowns()
    private val money = db.money()
    private val dayNotes = db.dayNotes()

    // explorer

    fun children(parentId: Long?): Flow<List<Item>> = items.children(parentId)

    fun folders(): Flow<List<Item>> = items.folders()

    fun observeItem(id: Long): Flow<Item?> = items.observe(id)

    suspend fun item(id: Long): Item? = items.get(id)

    fun searchItems(query: String): Flow<List<Item>> = items.search(query)

    suspend fun createFolder(name: String, parentId: Long?, color: Int? = null): Long =
        items.insert(Item(type = ItemType.FOLDER, parentId = parentId, name = name, color = color))

    suspend fun rename(item: Item, name: String) {
        items.update(item.copy(name = name, updated = System.currentTimeMillis()))
    }

    suspend fun setColor(item: Item, color: Int?) {
        items.update(item.copy(color = color, updated = System.currentTimeMillis()))
    }

    suspend fun setPinned(item: Item, pinned: Boolean) {
        items.update(item.copy(pinned = pinned))
    }

    suspend fun move(item: Item, newParentId: Long?) {
        // stop a folder being moved inside itself
        if (newParentId != null && (newParentId == item.id || newParentId in descendantsOf(item.id))) return
        items.update(item.copy(parentId = newParentId, updated = System.currentTimeMillis()))
    }

    // walks up from a folder to the root so the explorer can show breadcrumbs
    suspend fun pathTo(folderId: Long?): List<Item> {
        val path = mutableListOf<Item>()
        var current = folderId?.let { items.get(it) }
        while (current != null) {
            path.add(0, current)
            current = current.parentId?.let { items.get(it) }
        }
        return path
    }

    private suspend fun descendantsOf(id: Long): List<Long> {
        val found = mutableListOf<Long>()
        val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            for (child in items.childrenOnce(queue.removeFirst())) {
                found += child.id
                queue += child.id
            }
        }
        return found
    }

    // trash

    fun trash(): Flow<List<Item>> = items.trash()

    suspend fun moveToTrash(item: Item) {
        items.setDeleted(listOf(item.id) + descendantsOf(item.id), System.currentTimeMillis())
    }

    suspend fun restore(item: Item) {
        val deletedAt = item.deletedAt ?: return
        db.inTransaction {
            val ids = (listOf(item.id) + descendantsOf(item.id)).filter { items.get(it)?.deletedAt == deletedAt }
            items.setDeleted(ids, null)
            // if the old parent is still in the trash, bring the item back to the root
            val parent = item.parentId?.let { items.get(it) }
            if (item.parentId != null && (parent == null || parent.deletedAt != null)) {
                items.get(item.id)?.let { items.update(it.copy(parentId = null)) }
            }
        }
    }

    suspend fun moveToTrash(ids: Collection<Long>) {
        items.setDeleted(ids.flatMap { listOf(it) + descendantsOf(it) }.distinct(), System.currentTimeMillis())
    }

    suspend fun restoreAll(ids: Collection<Long>) {
        ids.mapNotNull { items.get(it) }.forEach { restore(it) }
    }

    suspend fun setOnHome(ids: Collection<Long>, show: Boolean) {
        db.inTransaction {
            ids.forEach { id -> trackers.get(id)?.let { trackers.update(it.copy(showOnHome = show)) } }
        }
    }

    suspend fun deleteForever(item: Item) {
        items.deleteForever(listOf(item.id) + descendantsOf(item.id))
        tags.dropUnused()
    }

    suspend fun itemCount(): Int = items.count()

    suspend fun emptyTrash() {
        items.emptyTrash()
        tags.dropUnused()
    }

    suspend fun purgeOldTrash(days: Int = 30) {
        if (days <= 0) return // 0 means keep forever
        items.purgeTrash(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
        tags.dropUnused()
    }

    // notes

    fun notes(): Flow<List<NoteRow>> = notes.notes()

    fun notesWithTag(tagId: Long): Flow<List<NoteRow>> = notes.notesWithTag(tagId)

    fun usedTags(): Flow<List<Tag>> = tags.usedTags()

    suspend fun createNote(parentId: Long?, title: String = ""): Long = db.inTransaction {
        val id = items.insert(Item(type = ItemType.NOTE, parentId = parentId, name = title))
        notes.upsert(NoteBody(id, ""))
        id
    }

    suspend fun noteText(id: Long): String = notes.body(id)?.text.orEmpty()

    suspend fun saveNote(id: Long, title: String, text: String) {
        db.inTransaction {
            val item = items.get(id) ?: return@inTransaction
            items.update(item.copy(name = title, updated = System.currentTimeMillis()))
            notes.upsert(NoteBody(id, text))
            setTags(id, findTags(text))
        }
    }

    private suspend fun setTags(itemId: Long, names: Set<String>) {
        tags.clearFor(itemId)
        val links = names.map { name ->
            val tagId = tags.idFor(name) ?: tags.insert(Tag(name = name))
            ItemTag(itemId, tagId)
        }
        if (links.isNotEmpty()) tags.link(links)
        tags.dropUnused()
    }

    // sheets

    fun sheets(): Flow<List<SheetRow>> = sheets.sheets()

    suspend fun createSheet(parentId: Long?, name: String): Long = db.inTransaction {
        val id = items.insert(Item(type = ItemType.SHEET, parentId = parentId, name = name))
        sheets.upsert(SheetBody(id, Sheet().toJson()))
        id
    }

    fun observeSheet(id: Long): Flow<Sheet?> = sheets.observe(id).map { body -> body?.let { Sheet.fromJson(it.json) } }

    // null when the sheet is missing or could not be read
    suspend fun sheet(id: Long): Sheet? = sheets.get(id)?.let { Sheet.fromJson(it.json) }

    suspend fun saveSheet(id: Long, sheet: Sheet) {
        db.inTransaction {
            val item = items.get(id) ?: return@inTransaction
            items.update(item.copy(updated = System.currentTimeMillis()))
            sheets.upsert(SheetBody(id, sheet.toJson()))
        }
    }

    // trackers

    fun trackers(): Flow<List<TrackerWithItem>> = trackers.trackers()

    fun observeTracker(id: Long): Flow<TrackerWithItem?> = trackers.observeTracker(id)

    fun logs(trackerId: Long): Flow<List<LogEntry>> = trackers.logs(trackerId)

    fun logsSince(from: Long): Flow<List<LogEntry>> = trackers.logsSince(from)

    suspend fun createTracker(
        name: String,
        kind: TrackerKind,
        parentId: Long?,
        color: Int?,
        dailyGoal: Int?,
        unit: String?,
        showOnHome: Boolean,
        aggregate: Aggregate? = null,
    ): Long = db.inTransaction {
        val id = items.insert(Item(type = ItemType.TRACKER, parentId = parentId, name = name, color = color))
        trackers.insert(Tracker(id, kind, unit, dailyGoal, showOnHome, aggregate = aggregate))
        id
    }

    suspend fun updateTracker(tracker: Tracker) = trackers.update(tracker)

    // one tap from home or the track tab, returns the new log id so it can be undone
    suspend fun tap(trackerId: Long): Long? {
        val tracker = trackers.get(trackerId) ?: return null
        val now = System.currentTimeMillis()
        return when (tracker.kind) {
            TrackerKind.COUNTER -> trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = now))
            TrackerKind.YES_NO -> {
                val today = trackers.logsSinceOnce(trackerId, startOfDay(now))
                if (today.isEmpty()) {
                    trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = now))
                } else {
                    today.forEach { trackers.deleteLog(it.id) }
                    null
                }
            }
            // these need a value first, the ui asks for it and calls logValue
            TrackerKind.NUMBER, TrackerKind.RATING -> null
            TrackerKind.TIMER -> {
                val since = tracker.runningSince
                if (since == null) {
                    trackers.update(tracker.copy(runningSince = now))
                    null
                } else {
                    trackers.update(tracker.copy(runningSince = null))
                    val seconds = (now - since) / 1000
                    if (seconds < 1) null
                    else trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = since, value = seconds.toDouble(), durationSeconds = seconds))
                }
            }
        }
    }

    suspend fun logValue(trackerId: Long, value: Double, note: String? = null, at: Long = System.currentTimeMillis()): Long =
        trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = at, value = value, note = note?.takeIf { it.isNotBlank() }))

    // used by the focus timer to save a finished session onto a timer tracker
    suspend fun logDuration(trackerId: Long, startedAt: Long, seconds: Long): Long =
        trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = startedAt, value = seconds.toDouble(), durationSeconds = seconds))

    fun logsBetween(from: Long, to: Long): Flow<List<LogEntry>> = trackers.logsBetween(from, to)

    fun loggedDays(from: Long): Flow<List<TrackerDay>> = trackers.loggedDays(from)

    suspend fun cancelTimer(trackerId: Long) {
        trackers.get(trackerId)?.let { trackers.update(it.copy(runningSince = null)) }
    }

    suspend fun deleteLog(id: Long) = trackers.deleteLog(id)

    // something that happened on an earlier day, put at midday so it sits in the right day whatever the time zone does
    suspend fun logPast(trackerId: Long, day: LocalDate, count: Int, value: Double, seconds: Long?, note: String?) {
        val tracker = trackers.get(trackerId) ?: return
        val at = day.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val cleanNote = note?.takeIf { it.isNotBlank() }
        db.inTransaction {
            when (tracker.kind) {
                TrackerKind.COUNTER -> repeat(count.coerceIn(1, 100)) { i ->
                    trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = at + i * 60_000L, note = cleanNote))
                }
                // a yes or no is only ever once a day
                TrackerKind.YES_NO -> {
                    if (trackers.logsBetweenOnce(trackerId, day.startMillis(), day.plusDays(1).startMillis()).isEmpty()) {
                        trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = at, note = cleanNote))
                    }
                }
                TrackerKind.NUMBER, TrackerKind.RATING ->
                    trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = at, value = value, note = cleanNote))
                TrackerKind.TIMER -> {
                    val secs = seconds ?: 0L
                    if (secs > 0) trackers.insertLog(LogEntry(trackerId = trackerId, timestamp = at, value = secs.toDouble(), durationSeconds = secs, note = cleanNote))
                }
            }
        }
    }

    // habits: a yes or no flips for that day, a counter goes up by one
    suspend fun tickDay(trackerId: Long, day: LocalDate) {
        val tracker = trackers.get(trackerId) ?: return
        val logs = trackers.logsBetweenOnce(trackerId, day.startMillis(), day.plusDays(1).startMillis())
        if (tracker.kind == TrackerKind.YES_NO && logs.isNotEmpty()) {
            db.inTransaction { logs.forEach { trackers.deleteLog(it.id) } }
        } else {
            logPast(trackerId, day, count = 1, value = 1.0, seconds = null, note = null)
        }
    }

    // takes the latest log of that day back off, for a counter tapped once too often
    suspend fun untickDay(trackerId: Long, day: LocalDate) {
        trackers.logsBetweenOnce(trackerId, day.startMillis(), day.plusDays(1).startMillis())
            .maxByOrNull { it.timestamp }
            ?.let { trackers.deleteLog(it.id) }
    }

    // calendar

    fun eventsBetween(from: Long, to: Long): Flow<List<Event>> = events.eventsBetween(from, to)

    suspend fun event(id: Long): Event? = events.get(id)

    suspend fun saveEvent(event: Event) {
        events.upsert(event)
    }

    suspend fun deleteEvent(id: Long) = events.delete(id)

    // tasks

    fun tasks(): Flow<List<Task>> = tasks.all()

    fun tasksDueBetween(from: Long, to: Long): Flow<List<Task>> = tasks.dueBetween(from, to)

    fun overdueTasks(before: Long): Flow<List<Task>> = tasks.openDueBefore(before)

    suspend fun saveTask(task: Task) {
        tasks.upsert(task)
    }

    suspend fun quickAddTask(title: String, due: Long? = null, subjectId: Long? = null) {
        tasks.upsert(Task(title = title, due = due, subjectId = subjectId))
    }

    // ticking a repeating task rolls a fresh copy forward to the next due date
    suspend fun setTaskDone(task: Task, done: Boolean) {
        db.inTransaction {
            val repeating = done && task.repeatRule != Repeat.NONE
            // the finished copy stops repeating so unticking it later does not spawn another
            tasks.upsert(
                task.copy(
                    done = done,
                    doneAt = if (done) System.currentTimeMillis() else null,
                    repeatRule = if (repeating) Repeat.NONE else task.repeatRule,
                )
            )
            if (repeating) {
                val from = task.due?.toLocalDate() ?: LocalDate.now()
                tasks.upsert(
                    task.copy(
                        id = 0,
                        done = false,
                        doneAt = null,
                        due = task.repeatRule.next(from).startMillis(),
                        created = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    suspend fun deleteTask(id: Long) = tasks.delete(id)

    suspend fun clearDoneTasks() = tasks.clearDone()

    // timetable

    fun subjects(): Flow<List<Subject>> = timetable.subjects()

    fun slots(): Flow<List<TimetableSlot>> = timetable.slots()

    fun terms(): Flow<List<Term>> = timetable.terms()

    suspend fun saveSubject(subject: Subject, makeFolder: Boolean) {
        db.inTransaction {
            var toSave = subject
            if (makeFolder && subject.folderId == null) {
                val parent = subjectsFolder()
                toSave = subject.copy(folderId = items.insert(Item(type = ItemType.FOLDER, parentId = parent, name = subject.name, color = subject.color)))
            }
            timetable.upsertSubject(toSave)
        }
    }

    // every subject folder lives inside one "Subjects" folder at the root
    private suspend fun subjectsFolder(): Long {
        val existing = items.all().firstOrNull { it.type == ItemType.FOLDER && it.parentId == null && it.name == "Subjects" && it.deletedAt == null }
        return existing?.id ?: items.insert(Item(type = ItemType.FOLDER, name = "Subjects"))
    }

    suspend fun deleteSubject(id: Long) = timetable.deleteSubject(id)

    suspend fun saveSlot(slot: TimetableSlot) {
        timetable.upsertSlot(slot)
    }

    suspend fun deleteSlot(id: Long) = timetable.deleteSlot(id)

    suspend fun saveTerm(term: Term) {
        timetable.upsertTerm(term)
    }

    suspend fun deleteTerm(id: Long) = timetable.deleteTerm(id)

    // countdowns

    fun countdowns(): Flow<List<Countdown>> = countdowns.all()

    suspend fun countdown(id: Long): Countdown? = countdowns.get(id)

    // gives back the id, upsert itself returns -1 when it updated a row
    suspend fun saveCountdown(countdown: Countdown): Long {
        val id = countdowns.upsert(countdown)
        return if (countdown.id != 0L) countdown.id else id
    }

    suspend fun deleteCountdown(id: Long) = countdowns.delete(id)

    // day notes

    fun dayNotes(): Flow<List<DayNote>> = dayNotes.all()

    // an empty note is the same as no note
    // the journal writes both at once, a day with no words and no mood is not kept
    suspend fun saveJournal(day: Long, text: String, mood: Int?) {
        val clean = text.trim()
        if (clean.isEmpty() && mood == null) dayNotes.delete(day) else dayNotes.upsert(DayNote(day, clean, mood = mood))
    }

    // the calendar only edits the words, a mood set in the journal stays
    suspend fun saveDayNote(day: Long, text: String) {
        val mood = dayNotes.get(day)?.mood
        saveJournal(day, text, mood)
    }

    // money

    fun moneyAccounts(): Flow<List<MoneyAccount>> = money.accounts()

    fun moneyEntries(): Flow<List<MoneyEntry>> = money.entries()

    suspend fun moneyAccount(id: Long): MoneyAccount? = money.account(id)

    suspend fun moneyEntry(id: Long): MoneyEntry? = money.entry(id)

    suspend fun saveMoneyAccount(account: MoneyAccount): Long {
        val id = money.upsertAccount(account)
        return if (account.id != 0L) account.id else id
    }

    // entries of the account go with it
    suspend fun deleteMoneyAccount(id: Long) = money.deleteAccount(id)

    suspend fun saveMoneyEntry(entry: MoneyEntry): Long {
        val id = money.upsertEntry(entry)
        return if (entry.id != 0L) entry.id else id
    }

    suspend fun deleteMoneyEntry(entry: MoneyEntry) {
        val transfer = entry.transferId
        if (transfer != null) money.deleteTransfer(transfer) else money.deleteEntry(entry.id)
    }

    fun moneyQuick(): Flow<List<MoneyQuick>> = money.quick()

    suspend fun saveMoneyQuick(quick: MoneyQuick) {
        money.upsertQuick(quick)
    }

    suspend fun deleteMoneyQuick(id: Long) = money.deleteQuick(id)

    // adds the saved entry dated today, gives back the entry id so it can be undone
    suspend fun logMoneyQuick(id: Long): MoneyEntry? {
        val quick = money.quickOne(id) ?: return null
        val entry = MoneyEntry(
            accountId = quick.accountId,
            day = LocalDate.now().toEpochDay(),
            amountPence = quick.amountPence,
            category = quick.category,
            note = quick.note,
        )
        val newId = money.upsertEntry(entry)
        return entry.copy(id = newId)
    }

    // two entries that cancel out, the shared id is the time so it will not clash with another transfer
    suspend fun moneyTransfer(fromId: Long, toId: Long, day: Long, pence: Long, note: String?, historical: Boolean = false) {
        val link = System.nanoTime()
        db.inTransaction {
            money.upsertEntry(MoneyEntry(accountId = fromId, day = day, amountPence = -pence, category = "Transfer", note = note, transferId = link, historical = historical))
            money.upsertEntry(MoneyEntry(accountId = toId, day = day, amountPence = pence, category = "Transfer", note = note, transferId = link, historical = historical))
        }
    }

    // starter packs

    suspend fun addStarterPack(pack: StarterPack) {
        db.inTransaction {
            val folder = items.insert(Item(type = ItemType.FOLDER, name = pack.title, color = pack.color))
            pack.trackers.forEachIndexed { index, t ->
                val id = items.insert(Item(type = ItemType.TRACKER, parentId = folder, name = t.name, color = t.color, sortOrder = index))
                trackers.insert(Tracker(id, t.kind, t.unit, t.goal, showOnHome = true, aggregate = t.aggregate))
            }
            pack.notes.forEach { (title, text) ->
                val id = items.insert(Item(type = ItemType.NOTE, parentId = folder, name = title))
                notes.upsert(NoteBody(id, text))
                setTags(id, findTags(text))
            }
            pack.tasks.forEach { tasks.upsert(Task(title = it)) }
        }
    }

    // the first version put three example trackers on every new install, these find them if they were never used
    suspend fun unusedOldExamples(): List<Item> {
        val names = mapOf(4L to "Cold shower", 5L to "Gym", 6L to "Study")
        return names.mapNotNull { (id, name) ->
            items.get(id)?.takeIf { it.type == ItemType.TRACKER && it.name == name && it.deletedAt == null && trackers.logsSinceOnce(id, 0).isEmpty() }
        }
    }

    suspend fun removeOldExamples() {
        unusedOldExamples().forEach { moveToTrash(it) }
        // the "Habits" folder they came in goes too when it is left empty
        items.get(2)?.let { folder ->
            if (folder.type == ItemType.FOLDER && folder.name == "Habits" && folder.deletedAt == null && items.childrenOnce(folder.id).none { it.deletedAt == null }) {
                moveToTrash(folder)
            }
        }
    }

    // backup

    suspend fun snapshot(): Backup = Backup(
        version = Backup.CURRENT_VERSION,
        exportedAt = System.currentTimeMillis(),
        items = items.all(),
        notes = notes.all(),
        trackers = trackers.all(),
        logs = trackers.allLogs(),
        tags = tags.all(),
        itemTags = tags.allLinks(),
        events = events.all(),
        tasks = tasks.allOnce(),
        subjects = timetable.allSubjects(),
        slots = timetable.allSlots(),
        terms = timetable.allTerms(),
        sheets = sheets.all(),
        countdowns = countdowns.allOnce(),
        moneyAccounts = money.allAccounts(),
        moneyEntries = money.allEntries(),
        moneyQuick = money.allQuick(),
        dayNotes = dayNotes.allOnce(),
    )

    suspend fun restoreSnapshot(backup: Backup) {
        db.inTransaction {
            // items cascade to notes, trackers, logs and tag links
            items.clear()
            tags.dropUnused()
            items.insertAll(backup.items)
            notes.insertAll(backup.notes)
            sheets.insertAll(backup.sheets)
            trackers.insertAll(backup.trackers)
            trackers.insertAllLogs(backup.logs)
            tags.insertAll(backup.tags)
            tags.link(backup.itemTags)
            events.clear()
            events.insertAll(backup.events)
            tasks.clear()
            tasks.insertAll(backup.tasks)
            timetable.clearSubjects() // slots go with their subjects
            timetable.insertSubjects(backup.subjects)
            timetable.insertSlots(backup.slots)
            timetable.clearTerms()
            timetable.insertTerms(backup.terms)
            countdowns.clear()
            countdowns.insertAll(backup.countdowns)
            money.clear() // entries go with their accounts
            money.insertAccounts(backup.moneyAccounts)
            money.insertEntries(backup.moneyEntries)
            money.insertQuick(backup.moneyQuick)
            dayNotes.clear()
            dayNotes.insertAll(backup.dayNotes)
        }
    }

    companion object {
        private val tagPattern = Regex("""(?<![\w#])#([\p{L}\p{N}_-]+)""")

        fun findTags(text: String): Set<String> =
            tagPattern.findAll(text).map { it.groupValues[1].lowercase() }.toSet()
    }
}
