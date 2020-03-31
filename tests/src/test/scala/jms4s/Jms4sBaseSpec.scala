package jms4s

import cats.data.NonEmptyList
import cats.effect.concurrent.Ref
import cats.effect.{ Blocker, IO, Resource }
import cats.implicits._
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import jms4s.config._
import jms4s.ibmmq.ibmMQ.makeConnection
import jms4s.jms.{ JmsConnection, JmsMessageConsumer }

import scala.concurrent.duration.{ FiniteDuration, _ }

trait Jms4sBaseSpec {
  implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  val blocker: Resource[IO, Blocker] = Blocker.apply

  val connectionRes: Resource[IO, JmsConnection[IO]] = blocker.flatMap(
    blocker =>
      makeConnection[IO](
        Config(
          qm = QueueManager("QM1"),
          endpoints = NonEmptyList.one(Endpoint("localhost", 1414)),
          // the current docker image seems to be misconfigured, so I need to use admin channel/auth in order to test topic
          // but maybe it's just me not understanding something properly.. as usual
          //          channel = Channel("DEV.APP.SVRCONN"),
          //          username = Some(Username("app")),
          //          password = None,
          channel = Channel("DEV.ADMIN.SVRCONN"),
          username = Some(Username("admin")),
          password = Some(Password("passw0rd")),
          clientId = "jms-specs"
        ),
        blocker
      )
  )

  val nMessages: Int              = 50
  val poolSize: Int               = 4
  val timeout: FiniteDuration     = 2.seconds
  val delay: FiniteDuration       = 500.millis
  val topicName: TopicName        = TopicName("DEV.BASE.TOPIC")
  val inputQueueName: QueueName   = QueueName("DEV.QUEUE.1")
  val outputQueueName1: QueueName = QueueName("DEV.QUEUE.2")
  val outputQueueName2: QueueName = QueueName("DEV.QUEUE.3")

  def receiveBodyAsTextOrFail(consumer: JmsMessageConsumer[IO]): IO[String] =
    consumer.receiveJmsMessage
      .flatMap(_.asJmsTextMessage)
      .flatMap(_.getText)

  def receiveUntil(
    consumer: JmsMessageConsumer[IO],
    received: Ref[IO, Set[String]],
    nMessages: Int
  ): IO[Set[String]] =
    receiveBodyAsTextOrFail(consumer)
      .flatMap(body => received.update(_ + body) *> received.get)
      .iterateUntil(_.size == nMessages)
}
