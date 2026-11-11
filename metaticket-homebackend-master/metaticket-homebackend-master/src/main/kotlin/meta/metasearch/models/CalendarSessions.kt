package meta.metasearch.models

/**
 * Session vé theo lịch (sessionType = CALENDAR) bán vé cho từng ngày trong [startTime, endTime]: còn hạn thì coi như
 * đang diễn ra, mốc hiển thị/sắp xếp là "hôm nay" chứ không phải ngày đầu của lịch.
 */
object CalendarSessions {
    const val CALENDAR = "CALENDAR"

    fun isCalendar(session: ShowSession): Boolean = session.sessionType == CALENDAR

    /** Session lịch đang diễn ra tại [now]: đã tới ngày đầu và chưa qua ngày cuối (lịch chưa mở thì không phải "đang diễn ra"). */
    fun isRunning(session: ShowSession, now: Long): Boolean =
        isCalendar(session) && session.startTime <= now && session.endTime >= now

    /** Mốc thời gian đại diện của session tại [now]. */
    fun displayStartTime(session: ShowSession, now: Long): Long =
        if (isCalendar(session) && session.endTime >= now) maxOf(session.startTime, now) else session.startTime

    /** Ngày cuối của các session lịch còn bán ([activeStatuses]); null khi show không có session lịch nào còn bán. */
    fun ongoingUntil(sessions: List<ShowSession>, activeStatuses: Collection<String>): Long? =
        sessions.filter { isCalendar(it) && it.status in activeStatuses }.maxOfOrNull { it.endTime }
}
