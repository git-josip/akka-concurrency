package zzz.akka.investigation

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{WordSpecLike, MustMatchers}
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

class FakePilot extends Actor {
  override def receive = {
    case _ =>
      throw new Exception("This exception is expected.")
  }
}

class NilActor extends Actor {
  def receive = {
    case _ => }
}

class PilotsSpec extends TestKit(ActorSystem("PilotsSpec",
  ConfigFactory.parseString(PilotsSpec.configStr)))
with ImplicitSender
with WordSpecLike
with MustMatchers {
  import PilotsSpec._
  import Plane._

  def nilActor = system.actorOf(Props[NilActor])
  // These paths are going to prove useful
  val pilotPath = s"/user/TestPilots/$pilotName"
  val copilotPath = s"/user/TestPilots/$copilotName"
  implicit val askTimeout = Timeout(4.seconds)

  // Helper function to construct the hierarchy we need
  // and ensure that the children are good to go by the
  // time we're done
  def pilotsReadyToGo(): ActorRef = {
    // The 'ask' below needs a timeout value
    // Much like the creation we're using in the Plane
    val a = system.actorOf(Props(new IsolatedStopSupervisor
      with OneForOneStrategyFactory {
      def childStarter() {
        context.actorOf(Props[FakePilot], pilotName)
        context.actorOf(Props(new CoPilot(testActor, nilActor, nilActor)), copilotName)
      }
    }), "TestPilots")
    // Wait for the mailboxes to be up and running for the children
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 3.seconds)
    // Tell the CoPilot that it's ready to go
    for {
      copilotActorRef <- system.actorSelection(copilotPath).resolveOne
      _ = copilotActorRef! Pilots.ReadyToGo
    } {}

    a
  }

  // The Test code
  "CoPilot" should {
    "take control when the Pilot dies" in {
      pilotsReadyToGo()
      // Kill the Pilot
      for {
        pilotActorRef <- system.actorSelection(pilotPath).resolveOne
        _ = pilotActorRef ! PoisonPill
      } {}
      // Since the test class is the "Plane" we can
      // expect to see this request
      expectMsg(GiveMeControl)
      // The girl who sent it had better be Mary
      for {
        copilotActorRef <- system.actorSelection(copilotPath).resolveOne
        _ <- {
          lastSender must be (copilotActorRef)

          Future.successful({})
        }
      } {}
    }
  }
}

object PilotsSpec {
  val copilotName = "Mary"
  val pilotName = "Mark"
  val configStr = s"""
      zzz.akka.avionics.flightcrew.copilotName = "$copilotName"
      zzz.akka.avionics.flightcrew.pilotName = "$pilotName""""
}