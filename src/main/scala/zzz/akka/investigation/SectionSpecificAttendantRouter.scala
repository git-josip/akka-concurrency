package zzz.akka.investigation

import akka.actor.{ActorSystem, Props, SupervisorStrategy}
import akka.dispatch.Dispatchers
import akka.routing._


class SectionSpecificAttendantRouter(val numberOfInstances: Int) extends Pool with FlightAttendantProvider {

  override def routerDispatcher: String = Dispatchers.DefaultDispatcherId

  override def createRouter(system: ActorSystem): Router = {
    val attendants = (1 to numberOfInstances) map { n =>
      val attendant = system.actorOf(Props(newFlightAttendant()), "Attendant-" + n)
      ActorRefRoutee(attendant)
    }

    Router(new RandomRoutingLogic(), attendants.toVector)
  }

  override def nrOfInstances(sys: ActorSystem) = numberOfInstances

  override def resizer = Some(DefaultResizer())

  def supervisorStrategy: SupervisorStrategy =
    SupervisorStrategy.defaultStrategy
}

