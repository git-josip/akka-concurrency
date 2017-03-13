package zzz.akka.investigation

import akka.actor.{Props, ActorLogging, Actor}

class Plane extends Actor with ActorLogging {
  import Altimeter._
  import Plane._
  val altimeter = context.actorOf(Props[Altimeter])
  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)))
  def receive = {
    case GiveMeControl =>
      log.info("Plane giving control.")
      sender ! controls
  }
}

object Plane {
  // Returns the control surface to the Actor that asks for them
  case object GiveMeControl
}