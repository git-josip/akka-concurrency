package zzz.akka.investigation

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.concurrent.duration._

object Passenger {
  // These are notifications that tell the Passenger
  // to fasten or unfasten their seat belts
  case object FastenSeatbelts
  case object UnfastenSeatbelts
  // Regular expression to extract Name-Row-Seat tuple
  val SeatAssignment = """([\w\s_]+)-(\d+)-([A-Z])""".r
}

// The DrinkRequestProbability trait defines some
// thresholds that we can modify in tests to
// speed things up.
trait DrinkRequestProbability {
  // Limits the decision on whether the passenger
  // actually asks for a drink
  lazy val askThreshold = 0.9f
  // The minimum time between drink requests
  lazy val requestMin: FiniteDuration = 20.minutes
  // Some portion of this (0 to 100  // to requestMin
  lazy val requestUpper: FiniteDuration = 30.minutes
  // Gives us a 'random' time within the previous
  // two bounds
  def randomishTime(): FiniteDuration = {
    this.requestMin + scala.util.Random.nextInt(this.requestUpper.toMillis.toInt).millis
  }
}

class Passenger(callButton: ActorRef) extends Actor
  with ActorLogging {
  this: DrinkRequestProbability =>
  import Passenger._
  import context.dispatcher

  import FlightAttendant.{GetDrink, Drink}
  import scala.collection.JavaConverters._
  // We'll be adding some randomness to our Passenger,
  // and this shortcut will make things a little more
  // readable.
  val r = scala.util.Random
  // It's about time that someone actually asked for a
  // drink since our Flight Attendants have been coded
  // to serve them up
  case object CallForDrink
  // The name of the Passenger can't have spaces in it,
  // since that's not a valid character in the URI
  // spec.  We know the name will have underscores in
  // place of spaces, and we'll convert those back
  // here.
  val SeatAssignment(myname, _, _) = self.path.name.replaceAllLiterally("_", " ")
  // We'll be pulling some drink names from the
  // configuration file as well
  val drinks = context.system.settings.config.getStringList(
    "zzz.akka.avionics.drinks").asScala.toIndexedSeq
  // A shortcut for the scheduler to make things look
  // nicer later
  val scheduler = context.system.scheduler
  // We've just sat down, so it's time to get a drink
  override def preStart() {
    self ! CallForDrink
  }

  // This method will decide whether or not we actually
  // want to get a drink using some randomness to
  // decide
  def maybeSendDrinkRequest(): Unit = {
    if (r.nextFloat() > askThreshold) {
      val drinkname = drinks(r.nextInt(drinks.length))
      callButton ! GetDrink(drinkname)
    }
  }

  scheduler.scheduleOnce(randomishTime(), self, CallForDrink)
  // Standard message handler

  def receive = {
    case CallForDrink =>
      maybeSendDrinkRequest()
    case Drink(drinkname) =>
      log.info("{} received a {} - Yum", myname, drinkname)
    case FastenSeatbelts =>
      log.info("{} fastening seatbelt", myname)
    case UnfastenSeatbelts =>
      log.info("{} UNfastening seatbelt", myname)
  }
}

