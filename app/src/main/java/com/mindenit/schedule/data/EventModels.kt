package com.mindenit.schedule.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class Event(
    val id: Long,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val type: String,
    val auditorium: Auditorium,
    val numberPair: Int,
    val subject: Subject,
    val groups: List<Group>,
    val teachers: List<Teacher>
) {
    data class Auditorium(val id: Long, val name: String)
    data class Subject(val id: Long, val title: String, val brief: String)
    data class Group(val id: Long, val name: String)
    data class Teacher(val id: Long, val fullName: String, val shortName: String)
}

fun Double.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(this.toLong()), ZoneId.systemDefault())
fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(this), ZoneId.systemDefault())
fun LocalDate.toEpochSecondStart(): Long = this.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
fun LocalDate.endOfDayEpochSecond(): Long = this.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1).toEpochSecond()
