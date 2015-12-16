package io.kairos.console.client

import io.kairos.console.client.core.{ClusterEventListener, ClusterEvent, ClientApi}
import io.kairos.console.info.ClusterInfo
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.Defaults._
import scalacss.ScalaCssReact._

/**
  * Created by alonsodomin on 13/12/2015.
  */
object ClusterView {

  object Style extends StyleSheet.Inline {
    import dsl._

    val container = style(addClassName("well"))

    object section {
      val title = style(
        fontSize(16 px),
        fontWeight.bold
      )
    }

    initInnerObjects(section.title)
  }

  case class State(info: ClusterInfo = ClusterInfo())

  class Backend($: BackendScope[Unit, State]) {
    ClusterEventListener.onMessage(onEvent)

    def onEvent(clusterEvent: ClusterEvent): Unit = {
      println("Received event: " + clusterEvent)
    }

    def render(state: State) =
      <.div(Style.container,
        <.section(
          <.div(Style.section.title,
            "Nodes"
          ),
          <.table(
            <.tr(
              <.td("Active"),
              <.td(state.info.nodeInfo.active)
            ),
            <.tr(
              <.td("Inactive"),
              <.td(state.info.nodeInfo.inactive)
            )
          )
        ),
        <.section(
          <.div(Style.section.title,
            "Workers"
          ),
          <.table(
            <.tr(
              <.td("Active"),
              <.td(state.info.workers)
            )
          )
        )
      )

  }

  private[this] val component = ReactComponentB[Unit]("ClusterView").
    initialState(State()).
    renderBackend[Backend].
    componentDidMount($ => Callback {
      ClientApi.clusterDetails map { details =>
        $.modState(_.copy(info = details))
      }
    }).buildU

  def apply() = component()

}
