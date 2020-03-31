package jms4s.ibmmq

import cats.data.NonEmptyList
import cats.effect.{ Blocker, Resource, Sync }
import cats.implicits._
import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.common.CommonConstants
import io.chrisdavenport.log4cats.Logger
import jms4s.config.{ Config, Endpoint }
import jms4s.jms.JmsConnection

object ibmMQ {

  def makeConnection[F[_]: Sync: Logger](config: Config, blocker: Blocker): Resource[F, JmsConnection[F]] =
    for {
      connection <- Resource.fromAutoCloseable(
                     Logger[F].info(s"Opening QueueConnection to MQ at ${hosts(config.endpoints)}...") >>
                       Sync[F].delay {
                         val queueConnectionFactory: MQConnectionFactory = new MQConnectionFactory()
                         queueConnectionFactory.setTransportType(CommonConstants.WMQ_CM_CLIENT)
                         queueConnectionFactory.setQueueManager(config.qm.value)
                         queueConnectionFactory.setConnectionNameList(hosts(config.endpoints))
                         queueConnectionFactory.setChannel(config.channel.value)
                         queueConnectionFactory.setClientID(config.clientId)

                         val connection = config.username.map { (username) =>
                           queueConnectionFactory.createConnection(
                             username.value,
                             config.password.map(_.value).getOrElse("")
                           )
                         }.getOrElse(queueConnectionFactory.createConnection)

                         connection.start()
                         connection
                       }
                   )
      _ <- Resource.liftF(Logger[F].info(s"Opened QueueConnection $connection."))
    } yield new JmsConnection[F](connection, blocker)

  private def hosts(endpoints: NonEmptyList[Endpoint]): String =
    endpoints.map(e => s"${e.host}(${e.port})").toList.mkString(",")

}
