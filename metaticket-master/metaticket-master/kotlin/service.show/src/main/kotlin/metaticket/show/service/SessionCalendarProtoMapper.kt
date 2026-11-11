package metaticket.show.service

import metaticket.show.grpc.ShowMessage
import metaticket.show.pojo.db.SessionCalendarConfig
import metaticket.show.pojo.db.ShowSessionDocument

/** Field vé theo lịch của proto session, dùng chung cho getSessionById và getShowById. */
fun ShowMessage.GetSessionResponse.Builder.applyCalendarFields(session: ShowSessionDocument): ShowMessage.GetSessionResponse.Builder {
    setEndTime(session.endTime.time)
    session.sessionType?.let { setSessionType(it.name) }
    session.calendar?.let { setCalendar(it.toProto()) }
    return this
}

fun SessionCalendarConfig.toProto(): ShowMessage.SessionCalendarProto =
    ShowMessage.SessionCalendarProto.newBuilder()
        .setOpenTime(openTime)
        .setCloseTime(closeTime)
        .setBookingWindowDays(bookingWindowDays)
        .also { builder -> sameDayCutoffMinutes?.let { builder.setSameDayCutoffMinutes(it) } }
        .build()

fun ShowMessage.SessionCalendarProto.toDocument(): SessionCalendarConfig =
    SessionCalendarConfig(
        openTime = openTime,
        closeTime = closeTime,
        bookingWindowDays = bookingWindowDays,
        sameDayCutoffMinutes = if (hasSameDayCutoffMinutes()) sameDayCutoffMinutes else null,
    )
