/*
 * Copyright 2017-2018 Iaroslav Zeigerman
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
package akkeeper.deploy

import akkeeper.api.InstanceId
import akkeeper.api.ContainerDefinition
import akkeeper.deploy.yarn._
import akkeeper.yarn.YarnMasterClient

import scala.concurrent.Future

/** A client that is responsible for deploying new container instances. */
private[akkeeper] trait DeployClient {

  /** Starts the client. */
  def start(): Unit

  /** Stops the client. */
  def stop(): Unit

  /** Indicates that the client must be stopped because of the error.
    *
    * @param error the error.
    */
  def stopWithError(error: Throwable): Unit

  /** Deploys new instances to a cluster.
    *
    * @param container the container definition that will be used to launch new instances.
    *                  See [[ContainerDefinition]].
    * @param instances the list of instance IDs that will be deployed. The size of this list
    *                  determines the total number of instances that will be launched.
    * @return a collection of container objects that store the result of the deploy operation.
    *         Each item in this list represents a result for a one particular instance.
    *         See [[DeployResult]].
    */
  def deploy(container: ContainerDefinition, instances: Seq[InstanceId]): Seq[Future[DeployResult]]
}

/** A result of the deployment operation. Contains the ID of the instance to which this
  * result is related.
  */
private[akkeeper] sealed trait DeployResult {
  def instanceId: InstanceId
}

/** Indicates that the instance has been deployed successfully. */
private[akkeeper] case class DeploySuccessful(instanceId: InstanceId) extends DeployResult

/** Indicates that the deployment process failed. */
private[akkeeper] case class DeployFailed(instanceId: InstanceId,
                                          e: Throwable) extends DeployResult

private[akkeeper] trait DeployClientFactory[T] extends (T => DeployClient)

private[akkeeper] object DeployClientFactory {

  implicit object YarnDeployClientFactory
    extends DeployClientFactory[YarnApplicationMasterConfig] {

    override def apply(config: YarnApplicationMasterConfig): DeployClient = {
      new YarnApplicationMaster(config, new YarnMasterClient)
    }
  }

  def apply[T: DeployClientFactory](config: T): DeployClient = {
    implicitly[T](config)
  }
}
