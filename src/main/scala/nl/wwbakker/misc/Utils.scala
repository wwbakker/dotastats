package nl.wwbakker.misc

import java.time.{Instant, LocalDateTime}
import java.util.TimeZone

object Utils {
  def localDateTimeFromUnixTimestamp(unixTimestamp: Long) : LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTimestamp * 1000L),
      TimeZone.getDefault.toZoneId)

  def percentage(part: Int, total: Int): Long =
    Math.round((part.toDouble / total.toDouble) * 100d)
}
