package zzz.akka.investigation

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import akka.pattern.ask

class Plane extends Actor
  with ActorLogging { this : AltimeterProvider with PilotProvider with LeadFlightAttendantProvider with HeadingIndicatorProvider =>

  import Altimeter._
  import Plane._
  import EventSource._
  import IsolatedLifeCycleSupervisor._
  import context.dispatcher

  implicit val timeout = Timeout(11.seconds)

  val config = context.system.settings.config
  val pilotName = config.getString("zzz.akka.avionics.flightcrew.pilotName")
  val copilotName = config.getString("zzz.akka.avionics.flightcrew.copilotName")

  def startControls() {
    val controls = context.actorOf(Props(new IsolatedResumeSupervisor with OneForOneStrategyFactory {
      def childStarter() {
        val alt = context.actorOf(Props(newAltimeter), "Altimeter")
        val headingIndicator = context.actorOf(Props(newHeadingIndicator), "HeadingIndicator")

        context.actorOf(Props(newAutopilot(self)), "AutoPilot")
        context.actorOf(Props(new ControlSurfaces(self, alt, headingIndicator)), "ControlSurface")
      }
    }), "Controls")

    Await.result(controls ? WaitForStart, 10.second)
  }

  // Helps us look up Actors within the "Controls" Supervisor
  def actorForControls(name: String) = context.actorSelection("Controls/" + name)
  // Helps us look up Actors within the "Pilots" Supervisor
  def actorForPilots(name: String) = context.actorSelection("Pilots/" + name)

  def startPeople() {
    val plane = self
    val controls = actorForControls("ControlSurface").resolveOne()
    val autopilot = actorForControls("AutoPilot").resolveOne()
    val altimeter = actorForControls("Altimeter").resolveOne()

    val people = context.actorOf(Props(new IsolatedStopSupervisor with OneForOneStrategyFactory {
      def childStarter() {
        for {
          controlsResolved <- controls
          autopilotResolved <- autopilot
          altimeterResolved <- altimeter
          _ <- {
            context.actorOf(Props(newPilot(plane, autopilotResolved, controlsResolved, altimeterResolved)), pilotName)
            context.actorOf(Props(newCopilot(plane, autopilotResolved, altimeterResolved)), copilotName)
            log.info("Pilots are created.")

            Future.successful()
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

    actorForControls("Altimeter") ! RegisterListener(self)
    actorForPilots(pilotName) ! Pilots.ReadyToGo
    actorForPilots(copilotName) ! Pilots.ReadyToGo
  }

  def receive = {
    case GiveMeControl =>
      log.info("Plane giving control. Sender: " + sender.path)
      val currentSender = sender
      for {
        controlsResolved <- actorForControls("ControlSurface").resolveOne()
        _ = currentSender ! controlsResolved
      } yield {}
      log.info("GiveMeControl received")

    case AltitudeUpdate(altitude) =>
      log.info(s"Altitude is now: $altitude")

    case RequestCoPilot =>
      val currentSender = sender
      for {
        copilotActorRef <-  actorForPilots(copilotName).resolveOne()
        _ <- {
          currentSender ! CoPilotReference(copilotActorRef)

          Future.successful({})
        }
      } {}

  }
}

object Plane {
  // Returns the control surface to the Actor that asks for them
  case object GiveMeControl
  case class Controls(actorRef: ActorRef)
  case class CoPilotReference(copilot: ActorRef)
  case object RequestCoPilot
  case object LostControl

  def apply() = new Plane with AltimeterProvider with PilotProvider with LeadFlightAttendantProvider with HeadingIndicatorProvider
}