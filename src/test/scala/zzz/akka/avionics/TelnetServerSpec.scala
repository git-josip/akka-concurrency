package zzz.akka.avionics

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.io.Tcp.Connected
import akka.io.{IO, Tcp}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

class PlaneForTelnet extends Actor {
  import HeadingIndicator._
  import Altimeter._
  import Plane._

  def receive = {
    case GetCurrentAltitude =>
      sender ! CurrentAltitude(52500f)
    case GetCurrentHeading =>
      sender ! CurrentHeading(233.4f)
  }
}

class Client(remote: InetSocketAddress, listener: ActorRef) extends Actor
  with ActorLogging {

  import Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)

  def receive = {
    case CommandFailed(_: Connect) =>
      listener ! "failed"
      context stop self

    case c @ Connected(remote, local) =>
      listener ! c
      val connection = sender
      connection ! Register(self)
      context become {
        case data: ByteString        =>
          log.info(s"Received ByteString data. Data: '${TelnetServer.ascii(data)}'")
          connection ! Write(data)

        case CommandFailed(w: Write) =>
          // O/S buffer was full
          log.info("CommandFailed")

        case Received(data)          =>
          log.info(s"Received msg: Received. Data: '${TelnetServer.ascii(data)}'")
          listener ! data

        case "close"                 =>
          log.info("Received close message.")
          connection ! Close

        case _: ConnectionClosed     =>
          log.info("Received ConnectionClosed message.")
          context stop self

      }
  }
}

class TelnetServerSpec extends TestKit(ActorSystem("TelnetServerSpec"))
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "TelnetServer" should {
    "successfully initialize server and receive messages" in {
      val plane = system.actorOf(Props[PlaneForTelnet])
      system.actorOf(Props(new TelnetServer(plane)))

      val remote = new InetSocketAddress("localhost", 31733)
      val client = system.actorOf(Props( new Client(remote, testActor)), "Client" )

      expectMsgAnyClassOf(classOf[Connected])
      expectMsg(ByteString(TelnetServer.welcome))

      client ! ByteString("heading")
      expectMsgPF() {
        case msg: ByteString =>
          TelnetServer.ascii(msg) must include ("233.40 degrees")
      }

      client ! ByteString("altitude")
      expectMsgPF() {
        case msg: ByteString =>
          TelnetServer.ascii(msg) must include ("52500.00 feet")
      }

      client ! "close"
    }
  }
}
