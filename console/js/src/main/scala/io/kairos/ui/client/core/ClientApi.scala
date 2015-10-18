package io.kairos.ui.client.core

import io.kairos.ui.client.security.ClientAuth
import io.kairos.ui.protocol.{JobSpecDetails, LoginRequest}
import io.kairos.ui.{Api, RegistryApi}
import org.scalajs.dom.ext.Ajax

import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by alonsodomin on 13/10/2015.
 */
object ClientApi extends Api with RegistryApi with ClientAuth {

  private[this] val BaseURI = "/api"
  private[this] val LoginURI = BaseURI + "/login"
  private[this] val LogoutURI = BaseURI + "/logout"

  private[this] val RegistryBaseURI = BaseURI + "/registry"
  private[this] val JobsURI = RegistryBaseURI + "/jobs"

  private[this] val JsonRequestHeaders = Map("Content-Type" -> "application/json")

  override def login(username: String, password: String)(implicit ec: ExecutionContext): Future[Unit] = {
    import upickle.default._

    Ajax.post(LoginURI, write(LoginRequest(username, password)), headers = JsonRequestHeaders).
      map { xhr => () }
  }

  override def logout()(implicit ec: ExecutionContext): Future[Unit] = {
    Ajax.post(LogoutURI, headers = authHeaders) map { xhr => () }
  }

  override def getJobs()(implicit ec: ExecutionContext): Future[Seq[JobSpecDetails]] = {
    import upickle.default._

    Ajax.get(JobsURI, headers = authHeaders).map { xhr =>
      read[Seq[JobSpecDetails]](xhr.responseText)
    }
  }

}
