package zzz.akka.avionics

import akka.actor.{Terminated, ActorRef, Actor}
import akka.util.Timeout
import zzz.akka.avionics.Plane.GiveMeControl
import scala.concurrent.Future
import scala.concurrent.duration._

class CoPilot(plane: ActorRef,
              autopilot: ActorRef,
              altimeter: ActorRef) extends Actor {
  import Pilots._
  import context.dispatcher

  var pilot: ActorRef = context.system.deadLetters
  val pilotName = context.system.settings.config.getString("zzz.akka.avionics.flightcrew.pilotName")

  def receive = {
    case ReadyToGo =>
      implicit val timeout = Timeout(5.second)
      for {
        pilotResolved <- context.actorSelection("../" + pilotName).resolveOne()
        _ <- {
          context.watch(pilotResolved)

          Future.successful({})
        }
      } yield {}

    case Terminated(_) =>
      // Pilot died
      plane ! GiveMeControl
  }
}