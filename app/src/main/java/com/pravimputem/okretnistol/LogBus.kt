package com.pravimputem.okretnistol

/** Jednostavna kružna memorija za log linije (do 500). */
object LogBus {
    private const val MAX = 500
    private val lines = ArrayDeque<String>(MAX)

    @Synchronized
    fun add(s: String) {
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast(s)
    }

    @Synchronized
    fun all(): List<String> = lines.toList()
}