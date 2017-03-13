package zzz.akka.investigation

import akka.actor.{ActorRef, Actor}
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Pilot extends Actor {
  import Pilots._
  import Plane._

  var controls: ActorRef = context.system.deadLetters
  var copilot: ActorRef = context.system.deadLetters
  var autopilot: ActorRef = context.system.deadLetters
  val copilotName = context.system.settings.config.getString("zzz.akka.avionics.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      context.parent ! Plane.GiveMeControl
      implicit val timeout = Timeout(5.second)

      for {
        copilotResolved <- context.actorSelection("../" + copilotName).resolveOne()
        autopilotResolved <- context.actorSelection("../AutoPilot").resolveOne()
        copilot = copilotResolved
        autopilot = autopilotResolved
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
  def pilot: Actor = new Pilot
  def copilot: Actor = new CoPilot
  def autopilot: Actor = new AutoPilot
}
