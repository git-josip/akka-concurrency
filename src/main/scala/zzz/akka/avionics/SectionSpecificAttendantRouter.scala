package zzz.akka.avionics

import akka.actor.{Actor, ActorRef}
import akka.routing._

//Since these examples are done 4 years after book has been written AkkaAPI has been changed and to create custom router which decisions
// are made upon SENDER we need to create router which can receive custom message that holds on senderRef
// Also, in order to be available to test this we will accept attendants actor refs

class SectionSpecificAttendantMasterRouter(attendants: Iterable[ActorRef]) extends Actor with FlightAttendantProvider {
  import SectionSpecificAttendantRoutingLogic._

  private val router = {
    val attendantRoutees = attendants.map(ActorRefRoutee).toIndexedSeq

    Router(new SectionSpecificAttendantRoutingLogic(attendantRoutees.size), attendantRoutees)
  }

  def receive = {
    case msg: Any =>
      val currentSender = sender()
      router.route(CustomRouting(msg, sender), currentSender)
  }
}

class SectionSpecificAttendantRoutingLogic(nbrCopies: Int) extends RoutingLogic {
  import SectionSpecificAttendantRoutingLogic._

  val random = RandomRoutingLogic()
  def select(message: Any, routees: scala.collection.immutable.IndexedSeq[Routee]): Routee = {
    message match {
    case CustomRouting(_, sender) =>
      // in case of our custom route message that is holding passenger sender we will route it depending on name
      val Passenger.SeatAssignment(_, row, _) = sender.path.name
      routees(math.floor(row.toInt / 11).toInt)

    case _ =>
      //in case of any other message we will route it by random logic
      val targets = (1 to nbrCopies).map(_ => random.select(message, routees))
      SeveralRoutees(targets)
    }
  }
}

object SectionSpecificAttendantRoutingLogic {
  case class CustomRouting(msg: Any, sender: ActorRef)
}


