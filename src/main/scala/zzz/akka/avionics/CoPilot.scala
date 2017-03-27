package zzz.akka.avionics

import akka.actor.{Actor, ActorIdentity, ActorRef, Identify, Terminated}
import zzz.akka.avionics.Plane.GiveMeControl

class CoPilot(plane: ActorRef,
              autopilot: ActorRef,
              altimeter: ActorRef) extends Actor {
  import Pilots._

  val pilotName = context.system.settings.config.getString("zzz.akka.avionics.flightcrew.pilotName")

  def receive = {
    case ReadyToGo =>
      context.actorSelection("../" + pilotName) ! Identify(1)

    // this is way to obtain som e actorRef by sending some actor Identify message it will response with ActorIdentity || we could also resolve future that we can get from pilotSelection.resolveOne()
    case ActorIdentity(_, Some(pilotRef)) =>
      context.watch(pilotRef)

    case Terminated(_) =>
      // Pilot died
      plane ! GiveMeControl
  }
}
