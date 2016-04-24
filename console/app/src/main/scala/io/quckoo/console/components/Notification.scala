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

package io.quckoo.console.components

import io.quckoo.console.components.Notification.Level.Level
import io.quckoo.fault.{Faults, ExceptionThrown, Fault}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import scala.language.implicitConversions

/**
 * Created by alonsodomin on 15/10/2015.
 */
object Notification {

  object Level extends Enumeration {
    type Level = Value
    val Danger, Warning, Info, Success = Value
  }

  private final val AlertStyle = Map(
    Level.Danger  -> ContextStyle.danger,
    Level.Warning -> ContextStyle.warning,
    Level.Info    -> ContextStyle.info,
    Level.Success -> ContextStyle.success
  )

  private final val AlertIcon = Map(
    Level.Danger  -> Icons.exclamationCircle,
    Level.Warning -> Icons.exclamationTriangle,
    Level.Info    -> Icons.questionCircle,
    Level.Success -> Icons.checkCircle
  )

  type Renderer = CallbackTo[ReactNode]
  sealed trait ToRenderer {
    def apply(): Renderer
  }

  object ToRenderer {

    implicit def fromVDom(elem: => ReactTagOf[_]): ToRenderer = () => CallbackTo(elem.render)

    implicit def fromRenderer(renderer: => Renderer): ToRenderer = () => renderer

    implicit def fromString(content: String): ToRenderer = () => CallbackTo {
      <.span(content)
    }

    implicit def fromFaults(faults: Faults): ToRenderer =
      if (faults.size == 1) {
        fromFault(faults.head)
      } else {
        fromVDom(<.ul(
          faults.list.toList.map(_.toString).map(desc => <.li(desc))
        ))
      }

    implicit def fromFault(fault: Fault): ToRenderer =
      fromString(fault.toString)

    implicit def fromThrowable(throwable: Throwable): ToRenderer =
      fromFault(ExceptionThrown(throwable))

  }

  def apply(level: Level, contentMagnet: ToRenderer): Notification =
    Notification(level, contentMagnet())

  def danger(magnet: ToRenderer): Notification =
    apply(Level.Danger, magnet)

  def warn(magnet: ToRenderer): Notification =
    apply(Level.Warning, magnet)

  def info(magnet: ToRenderer): Notification =
    apply(Level.Info, magnet)

  def success(magnet: ToRenderer): Notification =
    apply(Level.Success, magnet)

  implicit def toReactNode(notification: Notification): ReactNode =
    notification.render

}

case class Notification private (level: Level, private val renderer: Notification.Renderer) {
  import Notification._

  def render: ReactNode = {
    Alert(AlertStyle(level), <.div(^.`class` := "row",
      <.div(^.`class` := "col-sm-2", AlertIcon(level)),
      <.div(^.`class` := "col-sm-10", renderer.runNow())
    ))
  }

}
