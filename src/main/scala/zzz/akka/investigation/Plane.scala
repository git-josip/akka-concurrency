package zzz.akka.investigation

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

class Plane extends Actor
  with ActorLogging
  with AltimeterProvider
  with PilotProvider
  with LeadFlightAttendantProvider {

  import Altimeter._
  import Plane._
  import EventSource._
  import IsolatedLifeCycleSupervisor._

  implicit val timeout = Timeout(5.seconds)

  val config = context.system.settings.config
  val pilotName = config.getString("zzz.akka.avionics.flightcrew.pilotName")
  val copilotName = config.getString("zzz.akka.avionics.flightcrew.copilotName")

  def startControls() {
    val controls = context.actorOf(Props(new IsolatedResumeSupervisor with OneForOneStrategyFactory {
      def childStarter() {
        val alt = context.actorOf(Props(newAltimeter), "Altimeter")
        context.actorOf(Props(newAutopilot), "AutoPilot")
        context.actorOf(Props(new ControlSurfaces(alt)), "ControlSurfaces")
      }
    }), "Controls")

    Await.result(controls ? WaitForStart, 5.second)
  }

  // Helps us look up Actors within the "Controls" Supervisor
  def actorForControls(name: String): Future[ActorRef] = context.actorSelection("Controls/" + name).resolveOne
  // Helps us look up Actors within the "Pilots" Supervisor
  def actorForPilots(name: String): Future[ActorRef] = context.actorSelection("Pilots/" + name).resolveOne

  def startPeople() {
    val plane = self
    val controls = actorForControls("ControlSurfaces")
    val autopilot = actorForControls("AutoPilot")
    val altimeter = actorForControls("Altimeter")

    val people = context.actorOf(Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
      def childStarter() {
        for {
          controlsResolved <- controls
          autopilotResolved <- autopilot
          altimeterResolved <- altimeter
          _ <- {
            context.actorOf(Props(newPilot(plane, autopilotResolved, controlsResolved, altimeterResolved)), pilotName)
            context.actorOf(Props(newCopilot(plane, autopilotResolved, altimeterResolved)), copilotName)

            Future.successful({})
          }
        } yield {}
      }
    }), "Pilots")

    // Use the default strategy here, which restarts indefinitely
    context.actorOf(Props(newFlightAttendant), config.getString("zzz.akka.avionics.flightcrew.leadAttendantName"))
    Await.result(people ? WaitForStart, 1.second)
  }

  override def preStart() {
    // Get our children going.  Order is important here.
    startControls()
    startPeople()
    // Bootstrap the system
    for {
      altimeterActorRef <- actorForControls("Altimeter")
      pilotActorRef <- actorForPilots(pilotName)
      copilotActorRef <-  actorForPilots(copilotName)
      _ <- {
        altimeterActorRef ! RegisterListener(self)
        pilotActorRef ! Pilots.ReadyToGo
        copilotActorRef ! Pilots.ReadyToGo

        Future.successful({})
      }
    } yield {}
  }

  def receive = {
    case GiveMeControl =>
      log.info("Plane giving control.")
      for {
        controlsResolved <- actorForControls("ControlSurfaces")
        x = sender ! controlsResolved
      } yield {}
      log.info("GiveMeControl received")

    case AltitudeUpdate(altitude) =>
      log.info(s"Altitude is now: $altitude")
  }
}

object Plane {
  // Returns the control surface to the Actor that asks for them
  case object GiveMeControl
  case class Controls(actorRef: ActorRef)
}