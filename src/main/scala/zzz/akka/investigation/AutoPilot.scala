package zzz.akka.investigation

import akka.actor.{Terminated, ActorRef, Actor}

class AutoPilot(plane: ActorRef) extends Actor {
  import Plane._
  import Pilots._

  def receive = {
    case ReadyToGo     =>
      plane ! RequestCoPilot

    case CoPilotReference(copilot) =>
      context.watch(copilot)

    case Terminated(_) =>
      // Pilot died
      plane ! GiveMeControl

    case _ =>
  }
}
