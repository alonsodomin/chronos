package io.kairos.cluster.registry

import akka.actor.{ActorLogging, Props}
import akka.cluster.pubsub.{DistributedPubSubMediator, DistributedPubSub}
import akka.cluster.sharding.ShardRegion
import akka.pattern._
import akka.persistence.{PersistentActor, SnapshotOffer}
import io.kairos.fault.ExceptionThrown
import io.kairos.id._
import io.kairos.protocol._
import io.kairos.resolver.Resolve
import io.kairos.JobSpec

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scalaz._

/**
 * Created by aalonsodominguez on 10/08/15.
 */
object RegistryShard {
  import RegistryProtocol._

  val DefaultSnapshotFrequency = 15 minutes

  def props(resolve: Resolve,
      snapshotFrequency: FiniteDuration = DefaultSnapshotFrequency): Props =
    Props(classOf[RegistryShard], resolve, snapshotFrequency)

  val shardName      = "Registry"
  val numberOfShards = 100

  val idExtractor: ShardRegion.ExtractEntityId = {
    case r: RegisterJob => (JobId(r.job).toString, r)
    case g: GetJob      => (g.jobId.toString, g)
    case d: DisableJob  => (d.jobId.toString, d)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case RegisterJob(jobSpec) => (JobId(jobSpec).hashCode % numberOfShards).toString
    case GetJob(jobId)        => (jobId.hashCode % numberOfShards).toString
    case DisableJob(jobId)    => (jobId.hashCode % numberOfShards).toString
  }

  private object RegistryStore {

    def empty: RegistryStore = new RegistryStore(Map.empty, Map.empty)

  }

  private case class RegistryStore private (
      private val enabledJobs: Map[JobId, JobSpec],
      private val disabledJobs: Map[JobId, JobSpec]) {

    def get(id: JobId): Option[JobSpec] =
      enabledJobs.get(id)

    def isEnabled(jobId: JobId): Boolean =
      enabledJobs.contains(jobId)

    def listEnabled: Seq[JobSpec] = enabledJobs.values.toSeq

    def updated(event: RegistryEvent): RegistryStore = event match {
      case JobAccepted(jobId, jobSpec) =>
        copy(enabledJobs = enabledJobs + (jobId -> jobSpec))

      case JobDisabled(jobId) =>
        val job = enabledJobs(jobId)
        copy(enabledJobs = enabledJobs - jobId,
            disabledJobs = disabledJobs + (jobId -> job))

      // Any event other than the previous ones have no impact in the state
      case _ => this
    }

  }

  private case object Snap

}

class RegistryShard(resolve: Resolve, snapshotFrequency: FiniteDuration)
    extends PersistentActor with ActorLogging {

  import RegistryShard._
  import RegistryProtocol._
  import context.dispatcher
  private val snapshotTask = context.system.scheduler.schedule(
      snapshotFrequency, snapshotFrequency, self, Snap)

  private val mediator = DistributedPubSub(context.system).mediator
  private var store = RegistryStore.empty

  override val persistenceId: String = "registry"

  override def postStop(): Unit = snapshotTask.cancel()

  override def receiveRecover: Receive = {
    case event: RegistryEvent =>
      store = store.updated(event)
      log.info("Replayed registry event. event={}", event)

    case SnapshotOffer(_, snapshot: RegistryStore) =>
      store = snapshot
  }

  override def receiveCommand: Receive = {
    case RegisterJob(jobSpec) =>
      resolve(jobSpec.artifactId, download = false) map {

        case Success(_) =>
          log.debug("Job artifact has been successfully resolved. artifactId={}",
              jobSpec.artifactId)
          val jobId = JobId(jobSpec)
          JobAccepted(jobId, jobSpec)

        case Failure(errors) =>
          log.error("Couldn't validate the job artifact id. " + errors)
          JobRejected(jobSpec.artifactId, errors.list)

      } recover {
        case NonFatal(ex) =>
          JobRejected(jobSpec.artifactId, List(ExceptionThrown(ex)))

      } map { response =>
        persist(response) { event =>
          store = store.updated(event)
          mediator ! DistributedPubSubMediator.Publish(RegistryTopic, event)
        }
        response
      } pipeTo sender()

    case DisableJob(jobId) =>
      if (!store.isEnabled(jobId)) {
        sender() ! JobNotEnabled(jobId)
      } else {
        persist(JobDisabled(jobId)) { event =>
          store = store.updated(event)
          mediator ! DistributedPubSubMediator.Publish(RegistryTopic, event)
          sender() ! event
        }
      }

    case GetJob(jobId) =>
      sender() ! store.get(jobId)

    case Snap =>
      saveSnapshot(store)
  }

}