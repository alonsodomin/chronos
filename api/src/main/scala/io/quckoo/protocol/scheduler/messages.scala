/*
 * Copyright 2015 A. Alonso Dominguez
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

package io.quckoo.protocol.scheduler

import java.time.ZonedDateTime

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto._

import io.quckoo._
import io.quckoo.Trigger.Immediate
import io.quckoo.protocol.{Command, Event}
import io.quckoo.serialization.json._

import monocle.macros.Lenses

import scala.concurrent.duration.FiniteDuration

sealed trait SchedulerCommand extends Command
sealed trait SchedulerEvent   extends Event
sealed trait ExecutionEvent extends SchedulerEvent {
  val dateTime: ZonedDateTime
}

@Lenses
final case class ScheduleJob(
    jobId: JobId,
    //params: Map[String, String] = Map.empty,
    trigger: Trigger = Immediate,
    timeout: Option[FiniteDuration] = None
) extends SchedulerCommand
object ScheduleJob {
  implicit val scheduleJobEncoder: Encoder[ScheduleJob] =
    deriveEncoder[ScheduleJob]

  implicit val scheduleJobDecoder: Decoder[ScheduleJob] =
    deriveDecoder[ScheduleJob]
}

final case class TaskScheduled(jobId: JobId, planId: PlanId, task: Task, dateTime: ZonedDateTime)
    extends ExecutionEvent
final case class TaskTriggered(
    jobId: JobId,
    planId: PlanId,
    taskId: TaskId,
    dateTime: ZonedDateTime
) extends ExecutionEvent
final case class TaskCompleted(
    jobId: JobId,
    planId: PlanId,
    taskId: TaskId,
    dateTime: ZonedDateTime,
    outcome: TaskExecution.Outcome
) extends ExecutionEvent

final case class JobFailedToSchedule(jobId: JobId, cause: QuckooError) extends SchedulerEvent

final case class ExecutionPlanStarted(jobId: JobId, planId: PlanId, dateTime: ZonedDateTime)
    extends ExecutionEvent
object ExecutionPlanStarted {
  implicit val executionPlanStartedEncoder: Encoder[ExecutionPlanStarted] =
    deriveEncoder[ExecutionPlanStarted]

  implicit val executionPlanStartedDecoder: Decoder[ExecutionPlanStarted] =
    deriveDecoder[ExecutionPlanStarted]
}

final case class ExecutionPlanFinished(jobId: JobId, planId: PlanId, dateTime: ZonedDateTime)
    extends ExecutionEvent
final case class ExecutionPlanCancelled(jobId: JobId, planId: PlanId, dateTime: ZonedDateTime)
    extends ExecutionEvent
object ExecutionPlanCancelled {
  implicit val executionPlanCancelledEncoder: Encoder[ExecutionPlanCancelled] =
    deriveEncoder[ExecutionPlanCancelled]

  implicit val executionPlanCancelledDecoder: Decoder[ExecutionPlanCancelled] =
    deriveDecoder[ExecutionPlanCancelled]
}

case object GetExecutionPlans                        extends SchedulerCommand
final case class GetExecutionPlan(planId: PlanId)    extends SchedulerCommand
final case class CancelExecutionPlan(planId: PlanId) extends SchedulerCommand

case object GetTaskExecutions                     extends SchedulerCommand
final case class GetTaskExecution(taskId: TaskId) extends SchedulerCommand

final case class TaskQueueUpdated(pendingTasks: Int, inProgressTasks: Int) extends SchedulerEvent
