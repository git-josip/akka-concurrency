package zzz.akka.avionics

import akka.actor.{Actor, ActorRef, ActorSelection}

class Pilot(plane: ActorRef,
            autopilot: ActorRef,
            heading: ActorRef,
            altimeter: ActorRef) extends Actor { this: DrinkingProvider with FlyingProvider =>

  import Pilots._
  import Plane._
  import context._
  import Altimeter._
  import ControlSurfaces._
  import DrinkingBehaviour._
  import FlyingBehaviour._
  import akka.actor.FSM._

  val copilotName = context.system.settings.config.getString("zzz.akka.avionics.flightcrew.copilotName")

  // We've pulled the bootstrapping code out into a separate receive
  // method.  We'll only ever be in this state once, so there's no point
  // in having it around for long
  def bootstrap: Receive = {
    case ReadyToGo =>
      val coPilot = context.actorSelection("../" + copilotName)
      val flyer = context.actorSelection("FlyingBehaviour")

      flyer ! SubscribeTransitionCallBack(self)
      flyer ! Fly(CourseTarget(20000, 250, System.currentTimeMillis + 30000))
      become(sober(coPilot, flyer))
  }

  // The 'sober' behaviour
  def sober(copilot: ActorSelection, flyer: ActorSelection): Receive = {
    case FeelingSober =>
    // We're already sober
    case FeelingTipsy =>
      becomeTipsy(copilot, flyer)
    case FeelingLikeZaphod =>
      becomeZaphod(copilot, flyer)
  }

  // The 'tipsy' behaviour
  def tipsy(copilot: ActorSelection, flyer: ActorSelection): Receive = {
    case FeelingSober =>
      becomeSober(copilot, flyer)
    case FeelingTipsy =>
    // We're already tipsy
    case FeelingLikeZaphod =>
      becomeZaphod(copilot, flyer)
  }

  // The 'zaphod' behaviour
  def zaphod(copilot: ActorSelection, flyer: ActorSelection): Receive = {
    case FeelingSober =>
      becomeSober(copilot, flyer)
    case FeelingTipsy =>
      becomeTipsy(copilot, flyer)
    case FeelingLikeZaphod =>
    // We're already Zaphod
  }

  // The 'idle' state is merely the state where the Pilot does nothing at all
  def idle: Receive = {
    case _ =>
  }

  // Updates the FlyingBehaviour with sober calculations and then
  // becomes the sober behaviour
  def becomeSober(copilot: ActorSelection, flyer: ActorSelection) = {
    flyer ! NewElevatorCalculator(calcElevator)
    flyer ! NewBankCalculator(calcAilerons)
    become(sober(copilot, flyer))
  }
  // Updates the FlyingBehaviour with tipsy calculations and then
  // becomes the tipsy behaviour
  def becomeTipsy(copilot: ActorSelection, flyer: ActorSelection) = {
    flyer ! NewElevatorCalculator(tipsyCalcElevator)
    flyer ! NewBankCalculator(tipsyCalcAilerons)
    become(tipsy(copilot, flyer))
  }
  // Updates the FlyingBehaviour with zaphod calculations and then
  // becomes the zaphod behaviour
  def becomeZaphod(copilot: ActorSelection, flyer: ActorSelection) = {
    flyer ! NewElevatorCalculator(zaphodCalcElevator)
    flyer ! NewBankCalculator(zaphodCalcAilerons)
    become(zaphod(copilot, flyer))
  }

  override def preStart() {
    actorOf(newDrinkingBehaviour(self), "DrinkingBehaviour")
    actorOf(newFlyingBehaviour(plane, heading, altimeter), "FlyingBehaviour")
  }

  // At any time, the FlyingBehaviour could go back to an Idle state,
  // which means that our behavioural changes don't matter any more
  override def unhandled(msg: Any): Unit = {
    msg match {
      case Transition(_, _, Idle) =>
        become(idle)
      // Ignore these two messages from the FSM rather than have them
      // go to the log
      case Transition(_, _, _) =>
      case CurrentState(_, _) =>
      case m => super.unhandled(m)
    }
  }

  // Initially we start in the bootstrap state
  def receive = bootstrap
}

object Pilots {
  case object ReadyToGo
  case object RelinquishControl

  import FlyingBehaviour._
  import ControlSurfaces._
  // Calculates the elevator changes when we're a bit tipsy
  val tipsyCalcElevator: Calculator  = { (target, status) =>
    val msg = calcElevator(target, status)
    msg match {
      case StickForward(amt) => StickForward(amt * 1.03f)
      case StickBack(amt) => StickBack(amt * 1.03f)
      case m => m
    }
  }

  val tipsyCalcAilerons: Calculator = { (target, status) =>
    val msg = calcAilerons(target, status)
    msg match {
      case StickLeft(amt) => StickLeft(amt * 1.03f)
      case StickRight(amt) => StickRight(amt * 1.03f)
      case m => m
    }
  }

  // Calculates the elevator changes when we're totally out of it
  val zaphodCalcElevator: Calculator = { (target, status) =>
    val msg = calcElevator(target, status)
    msg match {
      case StickForward(amt) => StickBack(1f)
      case StickBack(amt) => StickForward(1f)
      case m => m
    }
  }

  // Calculates the aileron changes when we're totally out of it
  val zaphodCalcAilerons: Calculator = { (target, status) =>
    val msg = calcAilerons(target, status)
    msg match {
      case StickLeft(amt) => StickRight(1f)
      case StickRight(amt) => StickLeft(1f)
      case m => m
    }
  }


}

trait PilotProvider {
  def newPilot(plane: ActorRef, autopilot: ActorRef, heading: ActorRef, altimeter: ActorRef): Actor = new Pilot(plane, autopilot, heading, altimeter) with DrinkingProvider with FlyingProvider

  def newCopilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef): Actor = new CoPilot(plane, autopilot, altimeter)

  def newAutopilot(plane: ActorRef) : Actor = new AutoPilot(plane)
}
