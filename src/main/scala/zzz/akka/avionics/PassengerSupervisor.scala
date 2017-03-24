package zzz.akka.avionics

import akka.actor.SupervisorStrategy.{Escalate, Resume, Stop}
import akka.actor.{Actor, ActorInitializationException, ActorKilledException, ActorRef, OneForOneStrategy, Props}
import akka.routing.BroadcastGroup
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigList}

import scala.collection.immutable.Iterable
import scala.concurrent.duration._

object PassengerSupervisor {
  // Allows someone to request the BroadcastRouter
  case object GetPassengerBroadcaster
  // Returns the BroadcastRouter to the requestor
  case class PassengerBroadcaster(broadcaster: ActorRef)
  // Factory method for easy construction
  def apply(callButton: ActorRef) = new PassengerSupervisor(callButton)
    with PassengerProvider
}

class PassengerSupervisor(callButton: ActorRef) extends Actor {
  this: PassengerProvider =>
  import PassengerSupervisor._
  // We'll resume our immediate children instead of restarting them
  // on an Exception
  override val supervisorStrategy = OneForOneStrategy() {
    case _: ActorKilledException => Escalate
    case _: ActorInitializationException => Escalate
    case _ => Resume
  }
  // Internal messages we use to communicate between this Actor
  // and its subordinate IsolatedStopSupervisor
  case class GetChildren(forSomeone: ActorRef)
  case class Children(children: Iterable[ActorRef], childrenFor: ActorRef)
  // We use preStart() to create our IsolatedStopSupervisor

  override def preStart() {
    context.actorOf(Props(new Actor {
      val config: Config = context.system.settings.config
      override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
        case _: ActorKilledException => Escalate
        case _: ActorInitializationException => Escalate
        case _ => Stop
      }

      override def preStart() {
        // Get our passenger names from the configuration
        val passengers = passengersActorNames(config)
        // Iterate through them to create the passenger children
        passengers.foreach (
          context.actorOf(Props(newPassenger(callButton)), _)
        )
      }

      // Override the IsolatedStopSupervisor's receive
      // method so that our parent can ask us for our
      // created children
      override def receive = {
        case GetChildren(forSomeone: ActorRef) =>
          val c = forSomeone
          sender ! Children(context.children, forSomeone)
      }
    }), "PassengersSupervisor")
  }

  def passengersActorNames(config: Config): List[String]  = {
    import scala.collection.JavaConverters._

    val passengers = config.getList("zzz.akka.avionics.passengers")

    passengers.asScala.map(
      _.asInstanceOf[ConfigList].unwrapped().asScala
        .mkString("-")
        .replaceAllLiterally(" ", "_")
    ).toList
  }

  // TODO: This noRouter method could be made simpler by using a Future.
  // We'll have to refactor this later.
  def noRouter: Receive = {
    case GetPassengerBroadcaster =>
      context.actorSelection("PassengersSupervisor") ! GetChildren(sender)

    case Children(passengers, destinedFor) =>
      val paths = passengers.map(_.path.toSerializationFormat)
      val router = context.actorOf(
        BroadcastGroup(paths).props(),
        "Passengers"
      )

      destinedFor ! PassengerBroadcaster(router)
      context.become(withRouter(router))
  }

  def withRouter(router: ActorRef): Receive = {
    case GetPassengerBroadcaster =>
      sender ! PassengerBroadcaster(router)
  }

  def receive = noRouter
}

