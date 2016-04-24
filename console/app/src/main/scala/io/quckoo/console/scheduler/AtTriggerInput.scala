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

package io.quckoo.console.scheduler

import io.quckoo.Trigger
import io.quckoo.console.components._
import io.quckoo.time.{MomentJSDate, MomentJSDateTime, MomentJSTime}

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.vdom.prefix_<^._

/**
  * Created by alonsodomin on 08/04/2016.
  */
object AtTriggerInput {

  case class Props(value: Option[Trigger.At], onUpdate: Option[Trigger.At] => Callback)
  case class State(date: Option[MomentJSDate], time: Option[MomentJSTime])

  implicit val propsReuse = Reusability.by[Props, Option[Trigger.At]](_.value)
  implicit val stateReuse = Reusability.caseClass[State]

  class Backend($: BackendScope[Props, State]) {

    def propagateUpdate: Callback = {
      val value = $.state.map(st => st.date.flatMap(date => st.time.map(time => (date, time))))
      value.flatMap {
        case Some((date, time)) =>
          val dateTime = new MomentJSDateTime(date, time)
          val trigger = Trigger.At(dateTime)
          $.props.flatMap(_.onUpdate(Some(trigger)))

        case _ =>
          $.props.flatMap(_.onUpdate(None))
      }
    }

    def onDateUpdate(value: Option[MomentJSDate]): Callback =
      $.modState(_.copy(date = value), propagateUpdate)

    def onTimeUpdate(value: Option[MomentJSTime]): Callback =
      $.modState(_.copy(time = value), propagateUpdate)

    val DateInput = new Input[MomentJSDate](onDateUpdate)
    val TimeInput = new Input[MomentJSTime](onTimeUpdate)

    def render(props: Props, state: State) = {
      <.div(
        <.div(^.`class` := "form-group",
          <.label(^.`class` := "col-sm-2 control-label", "Date"),
          <.div(^.`class` := "col-sm-10",
            DateInput(state.date)
          )
        ),
        <.div(^.`class` := "form-group",
          <.label(^.`class` := "col-sm-2 control-label", "Time"),
          <.div(^.`class` := "col-sm-10",
            TimeInput(state.time)
          )
        )
      )
    }

  }

  val component = ReactComponentB[Props]("AtTriggerInput").
    initialState_P(_ => State(None, None)).
    renderBackend[Backend].
    configure(Reusability.shouldComponentUpdate).
    build

  def apply(value: Option[Trigger.At], onUpdate: Option[Trigger.At] => Callback) =
    component(Props(value, onUpdate))

}
