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

package io.quckoo.client

import io.quckoo.api.{Cluster, Registry, Scheduler}
import io.quckoo.auth.User
import io.quckoo.protocol.cluster.MasterEvent
import io.quckoo.protocol.registry.RegistryEvent
import io.quckoo.protocol.scheduler.TaskQueueUpdated
import io.quckoo.protocol.worker.WorkerEvent
import monix.reactive.Observable

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by alonsodomin on 26/03/2016.
  */
trait QuckooClient extends Cluster with Registry with Scheduler {

  def registryEvents: Observable[RegistryEvent]

  def masterEvents: Observable[MasterEvent]

  def workerEvents: Observable[WorkerEvent]

  def queueMetrics: Observable[TaskQueueUpdated]

  def principal: Option[User]

  def close()(implicit ec: ExecutionContext): Future[Unit]

}
