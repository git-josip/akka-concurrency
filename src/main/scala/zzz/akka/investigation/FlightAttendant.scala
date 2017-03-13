package zzz.akka.investigation

import akka.actor.Actor
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
// This trait allows us to create different Flight Attendants with different
// levels of responsiveness.

trait AttendantResponsiveness {
  val maxResponseTimeMS: Int
  def responseDuration = scala.util.Random.nextInt(maxResponseTimeMS).millis
}

class FlightAttendant extends Actor { this: AttendantResponsiveness =>
  import FlightAttendant._
  def receive = {
    case GetDrink(drinkname) =>
      // We don't respond right away, but use the scheduler to ensure
      // we do eventually
      context.system.scheduler.scheduleOnce(responseDuration, sender, Drink(drinkname))
  }
}

object FlightAttendant {
  case class GetDrink(drinkname: String)
  case class Drink(drinkname: String)
  // By default we will make attendants that respond within 5 minutes
  def apply() = new FlightAttendant with AttendantResponsiveness {
    val maxResponseTimeMS = 300000
  }
}

