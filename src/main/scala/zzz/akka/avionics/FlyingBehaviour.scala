package zzz.akka.avionics

import akka.actor.{Props, FSM, Actor, ActorRef}
import scala.concurrent.duration._

trait FlyingProvider {
  def newFlyingBehaviour(plane: ActorRef,
                         heading: ActorRef,
                         altimeter: ActorRef): Props = Props(new FlyingBehaviour(plane, heading, altimeter))
}

class FlyingBehaviour(plane: ActorRef,
                      heading: ActorRef,
                      altimeter: ActorRef) extends Actor
  with FSM[FlyingBehaviour.State, FlyingBehaviour.Data] {

  import FSM._

  import FlyingBehaviour._
  import Pilots._
  import Plane._
  import Altimeter._
  import HeadingIndicator._
  import EventSource._

  case object Adjust

  def adjust(flightData: FlightData): FlightData = {
    val FlightData(c, elevCalc, bankCalc, t, s) = flightData
    c ! elevCalc(t, s)
    c ! bankCalc(t, s)
    flightData
  }

  def prepComplete(data: Data) = {
    data match {
      case FlightData(c, _, _, _, s) =>
        if (c != context.system.deadLetters && s.heading != -1f && s.altitude != -1f)
          true
        else false
      case _ =>
        false
    }
  }

  // Sets up the initial values for state and data in the FSM
  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(Fly(target), _) =>
      goto(PreparingToFly) using FlightData(
        context.system.deadLetters,
        calcElevator,
        calcAilerons,
        target,
        CourseStatus(-1, -1, 0, 0)
      )
  }

  onTransition {
    case Idle -> PreparingToFly =>
      plane ! GiveMeControl
      heading ! RegisterListener(self)
      altimeter ! RegisterListener(self)
  }

  onTransition {
    case PreparingToFly -> Flying =>
      setTimer("Adjustment", Adjust, 200.milliseconds, repeat = true)
  }

  when (PreparingToFly) ( transform {
    case Event(HeadingUpdate(head), d: FlightData) =>
      stay using d.copy(status = d.status.copy(heading = head, headingSinceMS = currentMS))
    case Event(AltitudeUpdate(alt), d: FlightData) =>
      stay using d.copy(status = d.status.copy(altitude = alt, altitudeSinceMS = currentMS))
    case Event(Controls(ctrls), d: FlightData) =>
      stay using d.copy(controls = ctrls)
    case Event(StateTimeout, _) =>
      plane ! LostControl
      goto (Idle)
  } using {
    case s if prepComplete(s.stateData) =>
      s.copy(stateName = Flying)
  })

  when(Flying) {
    case Event(Adjust, flightData: FlightData) =>
      stay using adjust(flightData)
    case Event(AltitudeUpdate(alt), d: FlightData) =>
      stay using d.copy(status = d.status.copy(altitude = alt,
        altitudeSinceMS = currentMS))
    case Event(HeadingUpdate(head), d: FlightData) =>
      stay using d.copy(status = d.status.copy(heading = head,
        headingSinceMS = currentMS))
    case Event(NewBankCalculator(c), d: FlightData) =>
      stay using d.copy(bankCalc = c)
    case Event(NewElevatorCalculator(c), d: FlightData) =>
      stay using d.copy(elevCalc = c)
  }


  onTransition {
    case Flying -> _ =>
      cancelTimer("Adjustment")
  }

  onTransition {
    case _ -> Idle =>
      heading ! UnregisterListener(self)
      altimeter ! UnregisterListener(self)
  }

  whenUnhandled {
    case Event(RelinquishControl, _) =>
      goto(Idle)
  }

  initialize
}

object FlyingBehaviour {

  import ControlSurfaces._

  // The states governing behavioural transitions
  sealed trait State

  case object Idle extends State

  case object Flying extends State

  case object PreparingToFly extends State

  // Helper classes to hold course data
  case class CourseTarget(altitude: Double, heading: Float, byMillis: Long)
  case class CourseStatus(altitude: Double, heading: Float, headingSinceMS: Long, altitudeSinceMS: Long)

  case class NewElevatorCalculator(c: Calculator)
  case class NewBankCalculator(c: Calculator)

  // We're going to allow the FSM to vary the behaviour that calculates the
  // control changes using this function definition
  type Calculator = (CourseTarget, CourseStatus) => Any

  // The Data that our FlyingBehaviour can hold
  sealed trait Data

  case object Uninitialized extends Data

  // This is the 'real' data.  We're going to stay entirely immutable and,
  // in doing so, we're going to encapsulate all of the changing state
  // data inside this class
  case class FlightData(controls: ActorRef,
                        elevCalc: Calculator,
                        bankCalc: Calculator,
                        target: CourseTarget,
                        status: CourseStatus) extends Data

  // Someone can tell the FlyingBehaviour to fly
  case class Fly(target: CourseTarget)

  def currentMS = System.currentTimeMillis
  // Calculates the amount of elevator change we need to make and returns it
  def calcElevator(target: CourseTarget, status: CourseStatus): Any = {
    val alt = (target.altitude - status.altitude).toFloat
    val dur = target.byMillis - status.altitudeSinceMS
    if (alt < 0) StickForward((alt / dur) * -1)
    else StickBack(alt / dur)
  }

  // Calculates the amount of bank change we need to make and returns it
  def calcAilerons(target: CourseTarget, status: CourseStatus): Any = {
    import scala.math.{abs, signum}
    val diff = target.heading - status.heading
    val dur = target.byMillis - status.headingSinceMS
    val amount = if (abs(diff) < 180) diff
    else signum(diff) * (abs(diff) - 360f)
    if (amount > 0) StickRight(amount / dur)
    else StickLeft((amount / dur) * -1)
  }
}
