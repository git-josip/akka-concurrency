package zzz.akka.avionics

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import akka.pattern.ask
import zzz.akka.avionics.Altimeter.AltitudeUpdate
import zzz.akka.avionics.DrinkingBehaviour.{FeelingLikeZaphod, FeelingTipsy}
import zzz.akka.avionics.HeadingIndicator.HeadingUpdate
import zzz.akka.avionics.Pilots.ReadyToGo

class PilotsSpec extends TestKit(ActorSystem("PilotsSpec",
  ConfigFactory.parseString(PilotsSpec.configStr)))
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
  implicit val askTimeout = Timeout(3.seconds)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  // Helper function to construct the hierarchy we need
  // and ensure that the children are good to go by the
  // time we're done
  def pilotsReadyToGo(): ActorRef = {
    // The 'ask' below needs a timeout value
    // Much like the creation we're using in the Plane
    val a = system.actorOf(Props(new IsolatedStopSupervisor
      with OneForOneStrategyFactory {
      def childStarter() {
        val coPilot = context.actorOf(Props(new CoPilot(testActor, nilActor, nilActor)), copilotName)
        val pilot = context.actorOf(Props(newPilot(testActor, coPilot, nilActor, nilActor)), pilotName)

        // Tell the CoPilot and AutoPilot that it's ready to go
        coPilot ! ReadyToGo
        pilot ! ReadyToGo
      }
    }), "TestPilots")

    // Wait for the mailboxes to be up and running for the children
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 3.second)
    a
  }

  // The Test code
  "CoPilot" should {
    "take control when the Pilot dies" in {
      pilotsReadyToGo()

      // Kill the Pilot
      system.actorSelection(pilotPath) ! PoisonPill

      expectMsgAnyOf(GiveMeControl)

      system.actorSelection(lastSender.path) must be (system.actorSelection(copilotPath))
    }
  }

  import Pilots._
  import zzz.akka.avionics.FlyingBehaviour._

  trait TestFlyingProvider extends FlyingProvider {
    override def newFlyingBehaviour(plane: ActorRef, heading: ActorRef, altimeter: ActorRef) = {
      Props(
        new Actor {
          override def receive = {
            case m: NewBankCalculator => testActor forward m
            case n: NewElevatorCalculator => testActor forward n
          }
        }
      )
    }
  }

  def makePilot(): ActorRef = {
    val p = system.actorOf(Props(new Pilot(nilActor, nilActor, nilActor, nilActor) with DrinkingProvider with TestFlyingProvider))
    p ! ReadyToGo
    p
  }

  "Pilot.becomeZaphod" should {
    "send new zaphodCalcElevator and zaphodCalcAilerons to FlyingBehaviour" in {
      val p = makePilot()

      val flyingBehaviour = system.actorSelection(p.path + "/FlyingBehaviour")
      val target = CourseTarget(1, 1.0f, 1000)

      flyingBehaviour ! Fly(target)
      flyingBehaviour ! HeadingUpdate(20)
      flyingBehaviour ! AltitudeUpdate(20)
      flyingBehaviour ! Controls(nilActor)

      p ! FeelingLikeZaphod

      within(7.seconds) {
        expectMsgAllOf(
          NewElevatorCalculator(zaphodCalcElevator),
          NewBankCalculator(zaphodCalcAilerons)
        )
      }
    }
  }

  "Pilot.becomeTipsy" should {
    "send new tipsyCalcElevator and tipsyCalcAilerons to " +
      "FlyingBehaviour" in {
      val p = makePilot()

      val  flyingBehaviour = system.actorSelection(p.path + "/FlyingBehaviour")
      val target = CourseTarget(1, 1.0f, 1000)

      flyingBehaviour ! Fly(target)
      flyingBehaviour ! HeadingUpdate(20)
      flyingBehaviour ! AltitudeUpdate(20)
      flyingBehaviour ! Controls(nilActor)

      p !  FeelingTipsy

      expectMsgAllClassOf(classOf[NewElevatorCalculator],
        classOf[NewBankCalculator]) foreach {
        case NewElevatorCalculator(f) =>
          f must be(tipsyCalcElevator)
        case NewBankCalculator(f) =>
          f must be(tipsyCalcAilerons)
      }
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