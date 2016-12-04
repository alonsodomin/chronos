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

package io.quckoo.resolver.config

import java.net.URL

import com.typesafe.config.ConfigFactory

import io.quckoo.config._
import io.quckoo.resolver.MavenRepository
import io.quckoo.test.TryAssertions

import org.scalatest.{FlatSpec, Matchers}

import pureconfig._

/**
  * Created by domingueza on 04/11/2016.
  */
object IvyConfigSpec {
  final val TestRepositoryName = "foo-repo"
  final val TestRepositoryUrl  = "http://foo.example.com"

  final val TestConfig: String =
    s"""
      |base-dir = "."
      |work-dir = "/resolver"
      |resolution-cache-dir = "cache"
      |repository-cache-dir = "local"
      |
      |repositories = [
      |  {
      |    name = "$TestRepositoryName"
      |    url  = "$TestRepositoryUrl"
      |  }
      |]
    """.stripMargin
}

class IvyConfigSpec extends FlatSpec with Matchers with TryAssertions {
  import IvyConfigSpec._

  "IvyConfig" should "correctly parse config attributes and repositories" in {
    val config = ConfigFactory.parseString(TestConfig)

    val expectedMavenRepo = MavenRepository(TestRepositoryName, new URL(TestRepositoryUrl))

    ifSuccessful(loadConfig[IvyConfig](config)) { result =>
      result.repositories should contain(expectedMavenRepo)
    }
  }

}
