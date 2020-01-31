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

package io.quckoo.api

import cats.data.ValidatedNel

import io.quckoo.{QuckooError, JobId, JobNotFound, JobSpec}
import io.quckoo.auth.Passport
import io.quckoo.protocol.registry._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by alonsodomin on 13/12/2015.
  */
trait Registry[F[_]] {

  def registerJob(jobSpec: JobSpec): F[ValidatedNel[QuckooError, JobId]]

  def fetchJobs(): F[List[(JobId, JobSpec)]]
  def fetchJob(jobId: JobId): F[Option[JobSpec]]

  def enableJob(jobId: JobId): F[Unit]
  def disableJob(jobId: JobId): F[Unit]

}
