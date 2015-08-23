package io.chronos

import io.chronos.id._

/**
 * Created by aalonsodominguez on 10/07/15.
 */
case class JobSpec(displayName: String,
                   description: String = "",
                   moduleId: ModuleId,
                   jobClass: String)
