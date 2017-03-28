package zzz.akka.avionics

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString
import akka.pattern.ask
import akka.util._

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.io.{IO, Tcp}

class TelnetServer(plane: ActorRef) extends Actor with ActorLogging {
  import TelnetServer._
  import context.system
  import Tcp._

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 31733))

  def receive = {
    case b @ Bound(localAddress) =>
      log.info("Telnet Server listeninig on port {}", localAddress)

    case c @ Connected(remote, local) =>
      log.info(s"Connected. Remote:  '$remote', local: '$local'")
      val handler = context.actorOf(Props(new SubServer(plane)))
      val connection = sender()
      connection ! Register(handler)
      connection.tell(Write(ByteString(welcome)), connection)

    case CommandFailed(_: Bind) => context stop self
  }

}

object TelnetServer {
  import Plane.{GetCurrentHeading, GetCurrentAltitude}

  implicit val askTimeout = Timeout(1.second)
  val welcome =
    """|Welcome to the Airplane!
       |----------------
       |
       |Valid commands are: 'heading' and 'altitude'
       |
       |> """.stripMargin

  def ascii(bytes: ByteString): String = {
    bytes.decodeString("UTF-8").trim
  }

  class SubServer(plane: ActorRef) extends Actor with ActorLogging {
    import HeadingIndicator._
    import Altimeter._
    import Tcp._

    import context.dispatcher

    def headStr(head: Float): ByteString =
      ByteString(f"current heading is: $head%3.2f degrees\n\n> ")
    def altStr(alt: Double): ByteString =
      ByteString(f"current altitude is: $alt%5.2f feet\n\n> ")
    def unknown(str: String): ByteString =
      ByteString(f"current $str is: unknown\n\n> ")
    def handleHeading(currentSender: ActorRef) = {
      (plane ? GetCurrentHeading).mapTo[CurrentHeading].onComplete {
        case Success(CurrentHeading(heading)) =>
          currentSender ! Write(headStr(heading))
        case Failure(_) =>
          currentSender ! Write(unknown("heading"))
      }
    }
    def handleAltitude(currentSender: ActorRef) = {
      (plane ? GetCurrentAltitude).mapTo[CurrentAltitude].onComplete {
        case Success(CurrentAltitude(altitude)) =>
          currentSender ! Write(altStr(altitude))
        case Failure(m) =>
          currentSender ! Write(unknown(s"altitude error: ${m}"))
      }
    }

    def receive = {
      case Received(data) => {
        val msg: String = ascii(data)
        val currentSender = sender()

        msg match {
          case "heading" =>
            log.info("Received 'heading'")
            handleHeading(currentSender)
          case "altitude" =>
            log.info("Received 'altitude'")
            handleAltitude(currentSender)
          case m =>
            log.info(s"Received 'unknown'. Unknown: $m")
            currentSender ! Write(ByteString("What?\n\n"))
        }
      }
      case PeerClosed     => context stop self
    }
  }
}
