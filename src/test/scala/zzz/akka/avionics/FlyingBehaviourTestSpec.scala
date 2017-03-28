package zzz.akka.avionics

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import CommonTestData._
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import zzz.akka.avionics.Altimeter.AltitudeUpdate
import zzz.akka.avionics.FlyingBehaviour._
import zzz.akka.avionics.HeadingIndicator.HeadingUpdate
import zzz.akka.avionics.Plane.Controls

class FlyingBehaviourTestSpec extends TestKit(ActorSystem("FlyingBehaviourTestSpec"))
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll {
  def nilActor = system.actorOf(Props[NilActor])

  val target: CourseTarget = CourseTarget(30000, 10, 100)

  def fsm(plane: ActorRef = nilActor,
          heading: ActorRef = nilActor,
          altimeter: ActorRef = nilActor) = {
    TestFSMRef(new FlyingBehaviour(plane, heading, altimeter))
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "FlyingBehaviour" should {
    "start in the Idle state and with Uninitialized data" in {
      val a = fsm()
      a.stateName must be (Idle)
      a.stateData must be (Uninitialized)
    }
  }

  "PreparingToFly state" should {
    "stay in PreparingToFly state when only a HeadingUpdate is received" in {
      val a = fsm()
      a ! Fly(target)
      a ! HeadingUpdate(20)
      a.stateName must be (PreparingToFly)
      val sd = a.stateData.asInstanceOf[FlightData]
      sd.status.altitude must be (-1)
      sd.status.heading must be (20)
    }

    "move to Flying state when all parts are received" in {
      val a = fsm()
      a ! Fly(target)
      a ! HeadingUpdate(20)
      a ! AltitudeUpdate(20)
      a ! Controls(testActor)
      a.stateName must be (Flying)
      val sd = a.stateData.asInstanceOf[FlightData]
      sd.controls must be (testActor)
      sd.status.altitude must be (20)
      sd.status.heading must be (20)
    }
  }

  "transitioning to Flying state" should {
    "create the Adjustment timer" in {
      val a = fsm()
      a.setState(PreparingToFly)
      a.setState(Flying)
      a.isTimerActive("Adjustment") must be (true)
      a.cancelTimer("Adjustment")
    }
  }
}
