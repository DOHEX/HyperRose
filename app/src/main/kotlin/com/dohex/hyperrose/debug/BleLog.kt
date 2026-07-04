package com.dohex.hyperrose.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** BLE/RFCOMM 指令收发日志缓冲区。App 进程单例。 */
object BleLog {
    private const val MAX_ENTRIES = 300

    data class Entry(
        val source: String,
        val direction: String,
        val data: String,
        val parsed: String,
        val time: String,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun log(source: String, direction: String, data: String, parsed: String, time: String) {
        val entry = Entry(source, direction, data, parsed, time)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
