/*
 * Copyright 2016 Antonio Alonso Dominguez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.quckoo

import io.quckoo.time.{DateTime, TimeSource}

import scala.concurrent.duration._

/**
 * Created by aalonsodominguez on 08/07/15.
 */
sealed trait Trigger {
  import Trigger.ReferenceTime

  def nextExecutionTime(referenceTime: ReferenceTime)
                       (implicit timeSource: TimeSource): Option[DateTime]

  def isRecurring: Boolean = false

}

object Trigger {

  sealed trait ReferenceTime {
    val when: DateTime
  }
  case class ScheduledTime(when: DateTime) extends ReferenceTime
  case class LastExecutionTime(when: DateTime) extends ReferenceTime

  case object Immediate extends Trigger {

    override def nextExecutionTime(referenceTime: ReferenceTime)
                                  (implicit timeSource: TimeSource): Option[DateTime] = referenceTime match {
      case ScheduledTime(_)     => Some(timeSource.currentDateTime)
      case LastExecutionTime(_) => None
    }

  }

  final case class After(delay: FiniteDuration) extends Trigger {

    override def nextExecutionTime(referenceTime: ReferenceTime)
                                  (implicit timeSource: TimeSource): Option[DateTime] =
      referenceTime match {
        case ScheduledTime(time) =>
          val millis = delay.toMillis
          Some(time.plusMillis(millis))

        case LastExecutionTime(_) => None
      }

  }

  final case class At(when: DateTime, graceTime: Option[FiniteDuration] = None) extends Trigger {

    override def nextExecutionTime(referenceTime: ReferenceTime)
                                  (implicit timeSource: TimeSource): Option[DateTime] =
      referenceTime match {
        case ScheduledTime(_) =>
          if (graceTime.isDefined) {
            graceTime.flatMap { margin =>
              val now = timeSource.currentDateTime
              val diff = Math.abs((now - when).toMillis)
              if (diff <= margin.toMillis) Some(now)
              else if (now < when) Some(when)
              else None
            }
          } else Some(when)

        case LastExecutionTime(_) => None
      }

  }

  final case class Every(frequency: FiniteDuration, startingIn: Option[FiniteDuration] = None) extends Trigger {

    override def nextExecutionTime(referenceTime: ReferenceTime)
                                  (implicit timeSource: TimeSource): Option[DateTime] =
      referenceTime match {
        case ScheduledTime(time) =>
          val millisDelay = (startingIn getOrElse 0.seconds).toMillis
          Some(time.plusMillis(millisDelay))

        case LastExecutionTime(time) =>
          val millisDelay = frequency.toMillis
          Some(time.plusMillis(millisDelay))
      }

    override val isRecurring: Boolean = true

  }

}
