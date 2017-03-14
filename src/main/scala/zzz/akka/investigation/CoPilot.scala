package zzz.akka.investigation

import akka.actor.{ActorRef, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class CoPilot(plane: ActorRef,
              autopilot: ActorRef,
              var controls: ActorRef,
              altimeter: ActorRef) extends Actor {
  import Pilots._

  var pilot: ActorRef = context.system.deadLetters
  val pilotName = context.system.settings.config.getString("zzz.akka.avionics.flightcrew.pilotName")

  def receive = {
    case ReadyToGo =>
      implicit val timeout = Timeout(5.second)
      for {
        pilotResolved <- context.actorSelection("../" + pilotName).resolveOne()
        pilot = pilotResolved
      } yield {}
  }
}
