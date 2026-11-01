package com.example.restrelay

/**
 * In-memory ring buffer of notification events, shown by the Debug Log screen.
 * Lives for the lifetime of the app process (the listener runs in the same process).
 */
object LogStore {
    const val MAX = 300

    data class Entry(
        val time: String,
        val event: String,
        val pkg: String,
        val title: String,
        val text: String,
        val bigText: String,
        val subText: String,
        val ongoing: Boolean,
        val importance: String,
        val channelId: String,
        val note: String,
    ) {
        fun toText(): String = buildString {
            append("[$time] $event  $pkg\n")
            append("  title:   $title\n")
            append("  text:    $text\n")
            append("  bigText: $bigText\n")
            append("  subText: $subText\n")
            append("  ongoing=$ongoing importance=$importance channel=$channelId")
            if (note.isNotBlank()) append("\n  note: $note")
        }
    }

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(e: Entry) {
        entries.addLast(e)
        while (entries.size > MAX) entries.removeFirst()
    }

    @Synchronized
    fun all(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()
}
