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

package io.quckoo.cluster.registry

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern._
import akka.persistence.query.EventEnvelope
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import io.quckoo.JobSpec
import io.quckoo.id.JobId
import io.quckoo.cluster.QuckooClusterSettings
import io.quckoo.cluster.core.QuckooJournal
import io.quckoo.cluster.registry.Registry.WarmUp
import io.quckoo.cluster.registry.RegistryIndex.UpdateIndex
import io.quckoo.protocol.registry._
import io.quckoo.resolver.Resolver
import io.quckoo.resolver.ivy.IvyResolve

import scala.concurrent._

/**
 * Created by aalonsodominguez on 24/08/15.
 */
object Registry {

  final val EventTag = "registry"

  object WarmUp {
    case object Start
    case object Ack
    case object Completed
    final case class Failed(exception: Throwable)
  }

  def props(settings: QuckooClusterSettings) = {
    val resolve = IvyResolve(settings.ivyConfiguration)
    val props   = Resolver.props(resolve).withDispatcher("quckoo.resolver.dispatcher")
    Props(classOf[Registry], RegistrySettings(props))
  }

  def props(settings: RegistrySettings) =
    Props(classOf[Registry], settings)

}

class Registry(settings: RegistrySettings)
    extends Actor with ActorLogging with QuckooJournal with Stash {
  import Registry.EventTag

  ClusterClientReceptionist(context.system).registerService(self)

  final implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system), "registry"
  )

  private[this] val cluster = Cluster(context.system)
  private[this] val resolver = context.actorOf(settings.resolverProps, "resolver")
  private[this] val shardRegion = startShardRegion
  private[this] val index = readJournal.eventsByTag(EventTag, 0).
    runWith(Sink.actorSubscriber(RegistryIndex.props(shardRegion)))

  private[this] var jobIds = Set.empty[JobId]

  private[this] var handlerRefCount = 0L

  def actorSystem = context.system

  def receive = ready

  private def ready: Receive = {
    case RegisterJob(spec) =>
      handlerRefCount += 1
      val handler = context.actorOf(handlerProps(spec, sender()), s"handler-$handlerRefCount")
      resolver.tell(Resolver.Validate(spec.artifactId), handler)

    case GetJobs =>
      import context.dispatcher
      val origSender = sender()
      queryJobs pipeTo origSender

    case get @ GetJob(jobId) =>
      if (jobIds.contains(jobId)) {
        shardRegion forward get
      } else {
        sender() ! JobNotFound(jobId)
      }

    case msg: RegistryWriteCommand =>
      shardRegion forward msg
  }

  private def warmingUp: Receive = {
    case EventEnvelope(offset, _, _, event @ JobAccepted(jobId, _)) =>
      log.debug("Indexing job {}", jobId)
      jobIds += jobId
      sender() ! WarmUp.Ack

    case WarmUp.Completed =>
      log.info("Registry index warming up finished.")
      unstashAll()
      context become ready

    case _: RegistryReadCommand => stash()
  }

  private def queryJobs: Future[Map[JobId, JobSpec]] = {
    Source.actorRef[(JobId, JobSpec)](10, OverflowStrategy.fail).
      mapMaterializedValue { idsStream =>
        index.tell(GetJobs, idsStream)
      }.runFold(Map.empty[JobId, JobSpec]) {
        case (map, (jobId, jobSpec)) =>
          map + (jobId -> jobSpec)
      }
  }

  private def startShardRegion: ActorRef = if (cluster.selfRoles.contains("registry")) {
    log.info("Starting registry shards...")
    ClusterSharding(context.system).start(
      typeName        = PersistentJob.ShardName,
      entityProps     = PersistentJob.props,
      settings        = ClusterShardingSettings(context.system).withRole("registry"),
      extractEntityId = PersistentJob.idExtractor,
      extractShardId  = PersistentJob.shardResolver
    )
  } else {
    log.info("Starting registry proxy...")
    ClusterSharding(context.system).startProxy(
      typeName        = PersistentJob.ShardName,
      role            = None,
      extractEntityId = PersistentJob.idExtractor,
      extractShardId  = PersistentJob.shardResolver
    )
  }

  private def handlerProps(jobSpec: JobSpec, replyTo: ActorRef): Props =
    Props(classOf[RegistryResolutionHandler], jobSpec, shardRegion, replyTo)

}

private class RegistryResolutionHandler(jobSpec: JobSpec, shardRegion: ActorRef, indexer: ActorRef, replyTo: ActorRef)
    extends Actor with ActorLogging {
  import Resolver._

  private val jobId = JobId(jobSpec)

  def receive = resolvingArtifact

  def resolvingArtifact: Receive = {
    case ArtifactResolved(artifact) =>
      log.debug("Job artifact has been successfully resolved. artifactId={}",
        artifact.artifactId)
      shardRegion.tell(PersistentJob.CreateJob(jobId, jobSpec), replyTo)
      context stop self

    case ResolutionFailed(_, cause) =>
      log.error("Couldn't validate the job artifact id. " + cause)
      replyTo ! JobRejected(jobId, cause)
      context stop self
  }

  def registeringJob: Receive = {
    case evt @ JobAccepted(`jobId`, _) =>
      indexer ! evt
  }

}
