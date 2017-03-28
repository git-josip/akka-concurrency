package zzz.akka.avionics

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.io.Tcp.Close
import akka.util.Timeout

import scala.concurrent.duration._
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global


object Avionics {
  // needed for '?' below
  implicit val timeout = Timeout(20.seconds)
  val system = ActorSystem("PlaneSimulation")
  val plane = system.actorOf(Props(Plane()), "Plane")
  val server = system.actorOf(Props(new TelnetServer(plane)), "Telnet")

  def main(args: Array[String]) {
    // Grab the controls
    val control = Await.result((plane ? Plane.GiveMeControl).mapTo[ActorRef], 19.seconds)
    // Takeoff!
    system.scheduler.scheduleOnce(200.millis) {
      control ! ControlSurfaces.StickBack(1f)
    }
    // Level out
    system.scheduler.scheduleOnce(1.seconds) {
      control ! ControlSurfaces.StickBack(0f)
    }
    // Climb
    system.scheduler.scheduleOnce(3.seconds) {
      control ! ControlSurfaces.StickBack(0.5f)
    }
    // Level out
    system.scheduler.scheduleOnce(4.seconds) {
      control ! ControlSurfaces.StickBack(0f)
    }

    server ! Close

    // Shut down
    system.scheduler.scheduleOnce(5.seconds) {
      system.terminate()
    }
  }
}
