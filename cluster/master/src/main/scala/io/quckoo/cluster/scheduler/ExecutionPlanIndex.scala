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

package io.quckoo.cluster.scheduler

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ddata._
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}

import io.quckoo.ExecutionPlan
import io.quckoo.id.PlanId
import io.quckoo.cluster.topics
import io.quckoo.protocol.scheduler._

import scala.concurrent.duration._

object ExecutionPlanIndex {

  final val DefaultTimeout = 5 seconds

  final val ExecutionPlanKey = GSetKey[PlanId]("executionPlanIndex")

  final case class Query(cmd: SchedulerCommand, replyTo: ActorRef)

  def props(shardRegion: ActorRef, timeout: FiniteDuration = DefaultTimeout): Props =
    Props(classOf[ExecutionPlanIndex], shardRegion, timeout)

}

class ExecutionPlanIndex(shardRegion: ActorRef, timeout: FiniteDuration) extends Actor with ActorLogging with Stash {
  import ExecutionPlanIndex._
  import Replicator._

  implicit val cluster = Cluster(context.system)
  private[this] val replicator = DistributedData(context.system).replicator
  private[this] val mediator = DistributedPubSub(context.system).mediator

  override def preStart(): Unit = {
    log.debug("Starting execution plan index...")
    context.system.eventStream.subscribe(self, classOf[SchedulerEvent])
  }

  def receive = ready

  def ready: Receive = {
    case event: SchedulerEvent =>
      mediator ! DistributedPubSubMediator.Publish(topics.Scheduler, event)
      event match {
        case ExecutionPlanStarted(_, planId) =>
          log.debug("Indexing execution plan {}", planId)
          replicator ! Update(ExecutionPlanKey, GSet.empty[PlanId], WriteMajority(timeout))(_ + planId)
          context become updatingIndex(planId)

        case _ => // ignore other types of events
      }

    case cmd: GetExecutionPlan =>
      val externalReq = Query(cmd, sender())
      val replicatorReq = Get(`ExecutionPlanKey`, ReadMajority(timeout), Some(externalReq))
      val handler = context.actorOf(Props(classOf[ExecutionPlanSingleQuery], shardRegion))
      replicator.tell(replicatorReq, handler)

    case GetExecutionPlans =>
      val externalReq = Query(GetExecutionPlans, sender())
      val replicatorReq = Get(`ExecutionPlanKey`, ReadMajority(timeout), Some(externalReq))
      val handler = context.actorOf(Props(classOf[ExecutionPlanMultiQuery], shardRegion))
      replicator.tell(replicatorReq, handler)
  }

  private[this] def updatingIndex(planId: PlanId, attempts: Int = 1): Receive = {
    case UpdateSuccess(`ExecutionPlanKey` , _) =>
      unstashAll()
      context become ready

    case UpdateTimeout(`ExecutionPlanKey`, _) =>
      if (attempts < 3) {
        log.warning("Timed out while updating index for execution plan {}, in operation", planId)
        replicator ! Update(ExecutionPlanKey, GSet.empty[PlanId], WriteMajority(timeout))(_ + planId)
        context become updatingIndex(planId, attempts + 1)
      } else {
        log.error("Could not index execution plan {}", planId)
        unstashAll()
        context become ready
      }

    case _ => stash()
  }

}

private class ExecutionPlanSingleQuery(shardRegion: ActorRef) extends Actor with ActorLogging {
  import ExecutionPlanIndex._
  import Replicator._

  def receive: Receive = {
    case res @ GetSuccess(`ExecutionPlanKey`, Some(Query(cmd @ GetExecutionPlan(planId), replyTo))) =>
      val elems = res.get(ExecutionPlanKey).elements
      if (elems.contains(planId)) {
        log.debug("Found execution plan {} in the index, retrieving its state", planId)
        shardRegion.tell(cmd, replyTo)
      } else {
        log.debug("Execution plan {} not found in the index", planId)
        replyTo ! ExecutionPlanNotFound(planId)
      }
      context stop self

    case NotFound(`ExecutionPlanKey`, Some(Query(GetExecutionPlan(planId), replyTo))) =>
      replyTo ! ExecutionPlanNotFound(planId)
      context stop self

    case GetFailure(`ExecutionPlanKey`, Some(Query(_, replyTo))) =>
      replyTo ! Status.Failure(new Exception("Could not retrieve elements from the index"))
      context stop self
  }

}

private class ExecutionPlanMultiQuery(shardRegion: ActorRef) extends Actor with ActorLogging {
  import ExecutionPlanIndex._
  import Replicator._

  private[this] var expectedResultCount = 0

  def receive = {
    case res @ GetSuccess(`ExecutionPlanKey`, Some(Query(GetExecutionPlans, replyTo))) =>
      val elems = res.get(ExecutionPlanKey).elements
      if (elems.nonEmpty) {
        expectedResultCount = elems.size
        log.debug("Found {} execution plans", elems.size)
        elems.foreach { planId =>
          shardRegion ! GetExecutionPlan(planId)
        }
        context become collateResults(replyTo)
      } else {
        log.debug("ExecutionPlan index is empty")
        completeQuery(replyTo)
      }

    case NotFound(`ExecutionPlanKey`, Some(Query(_, replyTo))) =>
      // complete query normally
      completeQuery(replyTo)

    case GetFailure(`ExecutionPlanKey`, Some(Query(_, replyTo))) =>
      replyTo ! Status.Failure(new Exception("Could not retrieve elements from the index"))
      context stop self
  }

  private[this] def collateResults(replyTo: ActorRef): Receive = {
    case plan: ExecutionPlan if expectedResultCount > 0 =>
      replyTo ! plan
      expectedResultCount -= 1
      if (expectedResultCount == 0) {
        completeQuery(replyTo)
      }
  }

  private[this] def completeQuery(replyTo: ActorRef): Unit = {
    replyTo ! Status.Success(())
    context stop self
  }

}
