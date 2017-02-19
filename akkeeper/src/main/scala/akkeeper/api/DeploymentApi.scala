/*
 * Copyright 2017 Iaroslav Zeigerman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package akkeeper.api

import akkeeper.common._
import spray.json.DefaultJsonProtocol

/** A request to deploy (launch) new instance(s) of the given container.
  * The possible responses are:
  *
  *  - [[DeployedInstances]] - if the deployment attempt was successful.
  *  - [[OperationFailed]] - if error occurred.
  *
  * @param name the name of the container that will be deployed.
  * @param quantity the number of instances that will be deployed.
  * @param id the optional request ID. If not specified a random
  *           ID will be generated.
  */
case class DeployContainer(name: String, quantity: Int,
                           id: Option[RequestId] = None) extends RequestWithId(id)

/** A response that indicates a successful deployment of new instances.
  *
  * @param requestId the ID of the original request.
  * @param containerName the name of the container that has been deployed.
  * @param instanceIds the list of IDs of newly launched instances.
  */
case class DeployedInstances(requestId: RequestId, containerName: String,
                             instanceIds: Seq[InstanceId]) extends WithRequestId

/** JSON (de)serialization for the Deploy API requests and responses. */
trait DeployApiJsonProtocol extends DefaultJsonProtocol {
  import RequestIdJsonProtocol._
  import InstanceIdJsonProtocol._

  implicit val deployContainerFormat = jsonFormat3(DeployContainer.apply)
  implicit val deployedInstancesFormat = jsonFormat3(DeployedInstances.apply)
}

object DeployApiJsonProtocol extends DeployApiJsonProtocol
