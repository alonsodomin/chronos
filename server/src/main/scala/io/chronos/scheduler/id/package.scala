package io.chronos.scheduler

/**
 * Created by domingueza on 06/07/15.
 */
package object id {

  type JobId = String
  type ExecutionId = String

  type WorkerId = String
  type WorkId = (JobId, ExecutionId)

}
