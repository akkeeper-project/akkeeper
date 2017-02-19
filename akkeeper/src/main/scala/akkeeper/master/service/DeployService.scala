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
package akkeeper.master.service

import akka.actor.{Props, ActorRefFactory, ActorRef}
import akka.pattern.pipe
import akkeeper.api._
import akkeeper.common._
import akkeeper.deploy._
import MonitoringService._

private[akkeeper] class DeployService(deployClient: DeployClient.Async,
                                      containerService: ActorRef,
                                      monitoringService: ActorRef) extends RequestTrackingService {

  private implicit val dispatcher = context.dispatcher
  override protected val trackedMessages: List[Class[_]] = List(classOf[DeployContainer])

  private def deployInstances(requestId: RequestId,
                              container: ContainerDefinition,
                              quantity: Int): DeployedInstances = {
    val ids = (0 until quantity).map(_ => InstanceId(container.name))
    val instanceInfos = ids.map(InstanceInfo.deploying(_))
    monitoringService ! InstancesUpdate(instanceInfos)

    val futures = deployClient.deploy(container, ids)
    val logger = log
    futures.foreach(f => {
      f.map {
        case DeploySuccessful(id) =>
          logger.debug(s"Instance $id deployed successfully")
          InstanceInfo.launching(id)
        case DeployFailed(id, e) =>
          logger.error(e, s"Deployment of instance $id failed")
          InstanceInfo.deployFailed(id)
      }.pipeTo(monitoringService)
    })
    DeployedInstances(requestId, container.name, ids)
  }

  override def preStart(): Unit = {
    deployClient.start()
    log.info("Deploy service successfully initialized")
    super.preStart()
  }

  override def postStop(): Unit = {
    deployClient.stop()
    super.postStop()
  }

  override protected def serviceReceive: Receive = {
    case request: DeployContainer =>
      // Before launching a new instance we should first
      // retrieve an information about the container.
      setOriginalSenderContext(request.requestId, request.quantity)
      containerService ! GetContainer(request.name, id = Some(request.requestId))
    case ContainerGetResult(id, container) =>
      // The information about the container was retrieved.
      // Now we can start the deployment process.
      val quantity = originalSenderContextAs[Int](id)
      val result = deployInstances(id, container, quantity)
      sendAndRemoveOriginalSender(result)
    case other: WithRequestId =>
      // Some unexpected response from the container service (likely error).
      // Just send it as is to the original sender.
      sendAndRemoveOriginalSender(other)
    case StopWithError(e) =>
      log.error("Stopping the Deploy service because of external error")
      deployClient.stopWithError(e)
      context.stop(self)
  }
}

object DeployService extends RemoteServiceFactory {
  override val actorName = "deployService"

  private[akkeeper] def createLocal(factory: ActorRefFactory,
                                    deployClient: DeployClient.Async,
                                    containerService: ActorRef,
                                    monitoringService: ActorRef): ActorRef = {
    factory.actorOf(Props(classOf[DeployService], deployClient,
      containerService, monitoringService), actorName)
  }
}
