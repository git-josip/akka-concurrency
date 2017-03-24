package zzz.akka.avionics

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{MustMatchers, WordSpecLike}
import akka.pattern.ask
import zzz.akka.avionics.Altimeter.AltitudeUpdate
import zzz.akka.avionics.DrinkingBehaviour.{FeelingLikeZaphod, FeelingTipsy}
import zzz.akka.avionics.HeadingIndicator.HeadingUpdate

import scala.concurrent.ExecutionContext.Implicits.global

class FakePilot extends Actor {
  override def receive = {
    case _ =>
      throw new Exception("This exception is expected.")
  }
}

class PilotsSpec extends TestKit(ActorSystem("PilotsSpec",
  ConfigFactory.parseString(PilotsSpec.configStr)))
with ImplicitSender
with WordSpecLike
with MustMatchers {
  import PilotsSpec._
  import Plane._
  import CommonTestData._

  def nilActor = system.actorOf(Props[NilActor])
  // These paths are going to prove useful
  val pilotPath = s"/user/TestPilots/$pilotName"
  val copilotPath = s"/user/TestPilots/$copilotName"
  implicit val askTimeout = Timeout(5.seconds)

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
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 5.seconds)
    // Tell the CoPilot that it's ready to go
    system.actorSelection(copilotPath) ! Pilots.ReadyToGo

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
        // Since the test class is the "Plane" we can
        // expect to see this request
        _ = {
          within(6.seconds)(expectMsg(GiveMeControl))

          // The girl who sent it had better be Mary
          for {
            copilotActorRef <- system.actorSelection(copilotPath).resolveOne
            _ <- {
              lastSender must be (copilotActorRef)

              Future.successful({})
            }
          } {}

          Future.successful({})
        }
      } {}
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

      for {
        flyingBehaviourActorRef <- system.actorSelection(p.path + "/FlyingBehaviour").resolveOne()
        _ <- {
          val target = CourseTarget(1, 1.0f, 1000)
          flyingBehaviourActorRef ! Fly(target)
          flyingBehaviourActorRef ! HeadingUpdate(20)
          flyingBehaviourActorRef ! AltitudeUpdate(20)
          flyingBehaviourActorRef ! Controls(nilActor)

          p ! FeelingLikeZaphod
          expectMsgAllOf(
            NewElevatorCalculator(zaphodCalcElevator),
            NewBankCalculator(zaphodCalcAilerons)
          )

          Future.successful({})
        }
      } {}
    }
  }

  "Pilot.becomeTipsy" should {
    "send new tipsyCalcElevator and tipsyCalcAilerons to " +
      "FlyingBehaviour" in {
      val p = makePilot()

      for {
        flyingBehaviourActorRef <- system.actorSelection(p.path + "/FlyingBehaviour").resolveOne()
        _ <- {
          val target = CourseTarget(1, 1.0f, 1000)
          flyingBehaviourActorRef ! Fly(target)
          flyingBehaviourActorRef ! HeadingUpdate(20)
          flyingBehaviourActorRef ! AltitudeUpdate(20)
          flyingBehaviourActorRef ! Controls(nilActor)

          p ! FeelingTipsy
          expectMsgAllClassOf(classOf[NewElevatorCalculator],
            classOf[NewBankCalculator]) foreach {
            case NewElevatorCalculator(f) =>
              f must be(tipsyCalcElevator)
            case NewBankCalculator(f) =>
              f must be(tipsyCalcAilerons)
          }

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