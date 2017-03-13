package zzz.akka.investigation

import akka.actor.{ActorRef, Props, ActorLogging, Actor}

class Plane extends Actor with ActorLogging {
  import Altimeter._
  import Plane._
  import EventSource._

  val altimeter = context.actorOf(Props(Altimeter()))
  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)))

  val config = context.system.settings.config
  val pilot = context.actorOf(Props[Pilot], config.getString("zzz.akka.avionics.flightcrew.pilotName"))
  val copilot = context.actorOf(Props[CoPilot], config.getString("zzz.akka.avionics.flightcrew.copilotName"))
  val autopilot = context.actorOf(Props[AutoPilot], "AutoPilot")
  val flightAttendant = context.actorOf(Props(LeadFlightAttendant()), config.getString("zzz.akka.avionics.flightcrew.leadAttendantName"))

  override def preStart() {
    // Register ourself with the Altimeter to receive updates on our altitude
    altimeter ! RegisterListener(self)
    List(pilot, copilot) foreach { _ ! Pilots.ReadyToGo }
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
  case class Controls(actorRef: ActorRef)
}