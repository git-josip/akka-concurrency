package zzz.akka.avionics

import akka.actor.{Props, _}
import akka.testkit.{ImplicitSender, TestKit}

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import akka.pattern.ask


class CoPilotSpec extends TestKit(ActorSystem("CoPilotSpec", ConfigFactory.parseString(CoPilotSpec.configStr)))
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll
  with PilotProvider {
  import PilotsSpec._
  import Plane._
  import CommonTestData._

  def nilActor = system.actorOf(Props[NilActor])
  // These paths are going to prove useful
  val pilotPath = s"/user/TestPilots/$pilotName"
  val copilotPath = s"/user/TestPilots/$copilotName"
  val autoPilotPath = s"/user/Controls/AutoPilot"

  implicit val askTimeout = Timeout(2.seconds)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  // Helper function to construct the hierarchy we need
  // and ensure that the children are good to go by the
  // time we're done
  def pilotsReadyToGo() {
    // The 'ask' below needs a timeout value
    // Much like the creation we're using in the Plane
    val a = system.actorOf(Props(new IsolatedStopSupervisor
      with OneForOneStrategyFactory {
      def childStarter() {
        val coPilot = context.actorOf(Props(new CoPilot(testActor, nilActor, nilActor)), copilotName)
        context.actorOf(Props(newPilot(testActor, coPilot, nilActor, nilActor)), pilotName)
      }
    }), "TestPilots")

    val b = system.actorOf(Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
      def childStarter() {
        context.actorOf(Props(new AutoPilot(testActor)), "AutoPilot")
      }
    }), "Controls")

    // Tell the CoPilot and AutoPilot that it's ready to go
    system.actorSelection(copilotPath) ! Pilots.ReadyToGo
    system.actorSelection(autoPilotPath) ! Pilots.ReadyToGo

    // Wait for the mailboxes to be up and running for the children
    Await.result(b ? IsolatedLifeCycleSupervisor.WaitForStart, 2.seconds)
  }

  // The Test code
  "AutoPilot" should {
    "take control when the CoPilot dies" in {
     pilotsReadyToGo()

      // Kill the Pilot
      system.actorSelection(copilotPath) ! PoisonPill

      expectMsgAnyOf(RequestCoPilot)

      system.actorSelection(lastSender.path) must be (system.actorSelection(autoPilotPath))
    }
  }
}

object CoPilotSpec {
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