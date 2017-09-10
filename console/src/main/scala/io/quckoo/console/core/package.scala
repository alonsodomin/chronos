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

package io.quckoo.console

import cats.data.Kleisli

import diode.{ActionType, Effect}

import io.quckoo.client.{ClientIO, QuckooClient2}

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

/**
  * Created by alonsodomin on 05/07/2016.
  */
package object core {

  type ConsoleIO[A] = Kleisli[ClientIO, QuckooClient2, A]

  implicit def action2Effect[A: ActionType](action: => A)(implicit ec: ExecutionContext): Effect =
    Effect.action[A](action)

}
