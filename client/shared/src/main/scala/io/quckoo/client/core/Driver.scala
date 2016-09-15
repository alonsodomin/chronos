package io.quckoo.client.core

import io.quckoo.JobSpec
import io.quckoo.auth.{Credentials, Passport}
import io.quckoo.fault.Fault
import io.quckoo.id.JobId
import io.quckoo.net.QuckooState
import io.quckoo.protocol.registry.{JobDisabled, JobEnabled, RegisterJob}

import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import Scalaz._

/**
  * Created by alonsodomin on 08/09/2016.
  */
trait Driver[P <: Protocol] {
  type TransportRepr <: Transport[P]

  protected val transport: TransportRepr

  trait Op[Cmd[_] <: Command[_], In, Rslt] {
    val marshall: Marshall[Cmd, In, transport.Request]
    val unmarshall: Unmarshall[transport.Response, Rslt]
    val recover: Recover[Rslt] = PartialFunction.empty[Throwable, Rslt]
  }

  trait Ops {
    implicit val authenticateOp: Op[AnonCmd, Credentials, Passport]
    implicit val clusterStateOp: Op[AuthCmd, Unit, QuckooState]
    implicit val registerJobOp: Op[AuthCmd, RegisterJob, ValidationNel[Fault, JobId]]
    implicit val enableJobOp: Op[AuthCmd, JobId, JobEnabled]
    implicit val disableJobOp: Op[AuthCmd, JobId, JobDisabled]
    implicit val fetchJobOp: Op[AuthCmd, JobId, Option[JobSpec]]
  }

  val ops: Ops

  final def invoke[Cmd[_] <: Command[_], In, Rslt](implicit
    ec: ExecutionContext,
    op: Op[Cmd, In, Rslt]
  ): Kleisli[Future, Cmd[In], Rslt] = {
    val encodeRequest  = Kleisli(op.marshall).transform(try2Future)
    val decodeResponse = Kleisli(op.unmarshall).transform(try2Future)

    val execute = encodeRequest >=> transport.send >=> decodeResponse
    execute.mapT(_.recover(op.recover))
  }

}
