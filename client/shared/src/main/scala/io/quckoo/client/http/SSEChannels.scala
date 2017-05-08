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

package io.quckoo.client.http

import io.quckoo.api.TopicTag
import io.quckoo.client.core.{Channel, Channels, Unmarshall}
import io.quckoo.serialization.Decoder

/**
  * Created by domingueza on 20/09/2016.
  */
trait SSEChannels extends Channels[HttpProtocol] {

  override def createChannel[E: TopicTag](implicit decoder: Decoder[String, E]) =
    new Channel.Aux[HttpProtocol, E] {
      override val topicTag   = implicitly[TopicTag[E]]
      override val unmarshall = Unmarshall[HttpServerSentEvent, E](_.data.as[E])
    }

}
