package zzz.akka.investigation

import akka.actor.{ActorRef, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Pilot(plane: ActorRef,
            autopilot: ActorRef,
            var controls: ActorRef,
            altimeter: ActorRef ) extends Actor {

  import Pilots._
  import Plane._

  var copilot: ActorRef = context.system.deadLetters
  val copilotName = context.system.settings.config.getString("zzz.akka.avionics.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      context.parent ! Plane.GiveMeControl
      implicit val timeout = Timeout(5.second)

      for {
        copilotResolved <- context.actorSelection("../" + copilotName).resolveOne()
        copilot = copilotResolved
      } yield {}

    case Controls(controlSurfaces) =>
      controls = controlSurfaces
  }
}

object Pilots {
  case object ReadyToGo
  case object RelinquishControl
}

trait PilotProvider {
  def newPilot(plane: ActorRef, autopilot: ActorRef, controls: ActorRef, altimeter: ActorRef): Actor = new Pilot(plane, autopilot, controls, altimeter)

  def newCopilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef): Actor = new CoPilot(plane, autopilot, altimeter)

  def newAutopilot(plane: ActorRef) : Actor = new AutoPilot(plane)
}
