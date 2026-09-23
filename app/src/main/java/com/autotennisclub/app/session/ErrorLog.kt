package com.autotennisclub.app.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ErrorEntry(val atMillis: Long, val code: String, val detail: String)

interface ErrorLogStore {
    fun load(): List<ErrorEntry>
    fun save(entries: List<ErrorEntry>)
}

/** Operator error log (Figma MAINTENANCE · ERROR LOG), newest first. */
class ErrorLog(
    private val store: ErrorLogStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 50
) {
    private val _entries = MutableStateFlow(store?.load().orEmpty())
    val entries: StateFlow<List<ErrorEntry>> = _entries.asStateFlow()

    fun record(code: String, detail: String = "") {
        _entries.update { (listOf(ErrorEntry(clock(), code, detail)) + it).take(maxEntries) }
        store?.save(_entries.value)
    }
}
