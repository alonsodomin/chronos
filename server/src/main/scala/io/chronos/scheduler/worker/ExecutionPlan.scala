package io.chronos.scheduler.worker

import io.chronos.scheduler.id.WorkId

import scala.collection.immutable.Queue

/**
 * Created by aalonsodominguez on 05/07/15.
 */
object ExecutionPlan {

  def empty: ExecutionPlan = ExecutionPlan(
    pendingWork = Queue.empty,
    workInProgress = Map.empty,
    acceptedWorks = Set.empty,
    finishedWorkIds = Set.empty
  )

  trait WorkDomainEvent

  case class WorkAccepted(work: Work) extends WorkDomainEvent
  case class WorkStarted(workId: WorkId) extends WorkDomainEvent
  case class WorkCompleted(workId: WorkId, result: Any) extends WorkDomainEvent

  case class WorkerFailed(workId: WorkId) extends WorkDomainEvent
  case class WorkerTimedOut(workId: WorkId) extends WorkDomainEvent

}

case class ExecutionPlan private (
  private val pendingWork: Queue[Work],
  private val workInProgress: Map[WorkId, Work],
  private val acceptedWorks: Set[WorkId],
  private val finishedWorkIds: Set[WorkId]) {

  import ExecutionPlan._

  def hasWork: Boolean = pendingWork.nonEmpty
  def nextWork: Work = pendingWork.head
  def isAccepted(workId: WorkId): Boolean = acceptedWorks.contains(workId)
  def isInProgress(workId: WorkId): Boolean = workInProgress.contains(workId)
  def isDone(workId: WorkId): Boolean = finishedWorkIds.contains(workId)

  def updated(event: WorkDomainEvent): ExecutionPlan = event match {
    case WorkAccepted(work) =>
      copy(
        pendingWork = pendingWork enqueue work,
        acceptedWorks = acceptedWorks + work.id
      )

    case WorkStarted(workId) =>
      val (work, rest) = pendingWork.dequeue
      require(work.id == workId, s"WorkStarted expected workId $workId == ${work.id}")
      copy(
        pendingWork = rest,
        workInProgress = workInProgress + (workId -> work)
      )

    case WorkCompleted(workId, _) =>
      copy(
        workInProgress = workInProgress - workId,
        finishedWorkIds = finishedWorkIds + workId
      )

    case WorkerFailed(workId) =>
      copy(
        pendingWork = pendingWork enqueue workInProgress(workId),
        workInProgress = workInProgress - workId
      )

    case WorkerTimedOut(workId) =>
      copy(
        pendingWork = pendingWork enqueue workInProgress(workId),
        workInProgress = workInProgress - workId
      )
  }

}
