package zzz.akka.investigation

import akka.actor._
import akka.actor.SupervisorStrategy.{Escalate, Resume, Stop, Decider}

import scala.concurrent.duration.Duration

trait IsolatedLifeCycleSupervisor extends Actor {
  import IsolatedLifeCycleSupervisor._
  def receive = {
    // Signify that we've started
    case WaitForStart =>
      sender ! Started
    // We don't handle anything else, but we give a decent
    // error message stating the error
    case m =>
      throw new Exception(s"Don't call ${self.path.name} directly ($m).")
  }

  // To be implemented by subclass
  def childStarter(): Unit
  // Only start the children when we're started
  final override def preStart() { childStarter() }
  // Don't call preStart(), which would be the default behaviour

  final override def postRestart(reason: Throwable) { }
  // Don't stop the children, which would be the default behaviour
  final override def preRestart(reason: Throwable, message: Option[Any]) { }
}

object IsolatedLifeCycleSupervisor {
  // Messages we use in case we want people to be able to wait for
  // us to finish starting
  case object WaitForStart
  case object Started
}


trait SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int, withinTimeRange: Duration)(decider: Decider): SupervisorStrategy
}

trait OneForOneStrategyFactory extends SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int, withinTimeRange: Duration)(decider: Decider): SupervisorStrategy =
    OneForOneStrategy(maxNrRetries, withinTimeRange)(decider)
}

trait AllForOneStrategyFactory extends SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int, withinTimeRange: Duration)(decider: Decider): SupervisorStrategy =
    AllForOneStrategy(maxNrRetries, withinTimeRange)(decider)
}


abstract class IsolatedResumeSupervisor(maxNrRetries: Int = -1, withinTimeRange: Duration = Duration.Inf) extends IsolatedLifeCycleSupervisor {
  this: SupervisionStrategyFactory =>
  override val supervisorStrategy = makeStrategy(maxNrRetries, withinTimeRange) {
    case _: ActorInitializationException => Stop
    case _: ActorKilledException => Stop
    case _: Exception => Resume
    case _ => Escalate
  }
}


abstract class IsolatedStopSupervisor(maxNrRetries: Int = -1, withinTimeRange: Duration = Duration.Inf) extends IsolatedLifeCycleSupervisor {
  this: SupervisionStrategyFactory =>

  override val supervisorStrategy = makeStrategy(maxNrRetries, withinTimeRange) {
    case _: ActorInitializationException => Stop
    case _: ActorKilledException => Stop
    case _: Exception => Stop
    case _ => Escalate
  }
}
