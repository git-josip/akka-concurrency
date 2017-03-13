package zzz.akka.investigation

import akka.actor.{Props, ActorLogging, Actor}

class Plane extends Actor with ActorLogging {
  import Altimeter._
  import Plane._
  import EventSource._

  val altimeter = context.actorOf(Props[Altimeter])
  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)))

  override def preStart() {
    altimeter ! RegisterListener(self)
  }

  def receive = {
    case GiveMeControl =>
      log.info("Plane giving control.")
      sender ! controls

    case AltitudeUpdate(altitude) =>
      log.info(s"Altitude is now: $altitude")
  }
}

object Plane {
  // Returns the control surface to the Actor that asks for them
  case object GiveMeControl
}