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

class NilActor extends Actor {
  def receive = {
    case _ => }
}

class CoPilotsSpec extends TestKit(ActorSystem("CoPilotsSpec", ConfigFactory.parseString(CoPilotsSpec.configStr)))
  with ImplicitSender
  with WordSpecLike
  with MustMatchers {
  import PilotsSpec._
  import Plane._

  def nilActor = system.actorOf(Props[NilActor])
  // These paths are going to prove useful
  val pilotPath = s"/user/TestPilots/$pilotName"
  val copilotPath = s"/user/TestPilots/$copilotName"
  val autoPilotPath = s"/user/Controls/AutoPilot"

  implicit val askTimeout = Timeout(4.seconds)

  // Helper function to construct the hierarchy we need
  // and ensure that the children are good to go by the
  // time we're done
  def pilotsReadyToGo() {
    // The 'ask' below needs a timeout value
    // Much like the creation we're using in the Plane
    val a = system.actorOf(Props(new IsolatedStopSupervisor
      with OneForOneStrategyFactory {
      def childStarter() {
        context.actorOf(Props[NilActor], pilotName)
        context.actorOf(Props[NilActor], copilotName)
      }
    }), "TestPilots")
    // Wait for the mailboxes to be up and running for the children
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 3.seconds)

    val b = system.actorOf(Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
      def childStarter() {
        context.actorOf(Props(new AutoPilot(testActor)), "AutoPilot")
      }
    }), "Controls")
    // Wait for the mailboxes to be up and running for the children
    Await.result(b ? IsolatedLifeCycleSupervisor.WaitForStart, 3.seconds)

    // Tell the CoPilot that it's ready to go
    for {
      copilotActorRef <- system.actorSelection(copilotPath).resolveOne
      autoPilotActorRef <- system.actorSelection(autoPilotPath).resolveOne
      _ = copilotActorRef ! Pilots.ReadyToGo
      _ = autoPilotActorRef ! Pilots.ReadyToGo
    } {}
  }

  // The Test code
  "AutoPilot" should {
    "take control when the CoPilot dies" in {
     pilotsReadyToGo()

      // Kill the Pilot
      for {
        copPilotActorRef <- system.actorSelection(copilotPath).resolveOne
        _ <- {
          copPilotActorRef ! PoisonPill

          Future.successful({})
        }
      } {}
      // Since the test class is the "Plane" we can
      // expect to see this request
      expectMsg(RequestCoPilot)
      // The girl who sent it had better be Mary
      for {
        autoPilotActorRef <- system.actorSelection(autoPilotPath).resolveOne
        _ <- {
          lastSender must be (autoPilotActorRef)

          Future.successful({})
        }
      } {}
    }
  }
}

object CoPilotsSpec {
  val copilotName = "Mary"
  val pilotName = "Mark"
  val configStr = s"""
                     |zzz { akka
                     |  {
                     |    avionics {
                     |      flightcrew {
                     |        pilotName = "Harry"
                     |        copilotName = "Joan"
                     |        leadAttendantName = "Gizelle"
                     |        attendantNames = [
                     |          "Sally",
                     |          "Jimmy",
                     |          "Mary",
                     |          "Wilhelm",
                     |          "Joseph",
                     |          "Danielle",
                     |          "Marcia",
                     |          "Stewart",
                     |          "Martin",
                     |          "Michelle",
                     |          "Jaime"
                     |        ]
                     |      }
                     |    }
                     |  }
                     |}
      """.stripMargin
}