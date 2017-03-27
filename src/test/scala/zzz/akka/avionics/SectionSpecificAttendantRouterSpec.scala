package zzz.akka.avionics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import zzz.akka.avionics.SectionSpecificAttendantRoutingLogic.CustomRouting

import scala.collection.immutable.IndexedSeq

class RouterRelay extends Actor {
  import RouterRelay._

    def receive = {
    case RelayTo(masterRouter, msg) =>
      val Passenger.SeatAssignment(_, row, _) = self.path.name.replaceAllLiterally("_", " ")
      masterRouter ! msg + s" I am passenger on row: $row"
  }
}

object RouterRelay {
  case class RelayTo(masterRouter: ActorRef, msg: Any)
}

class SectionSpecificAttendantRouterSpec extends TestKit(ActorSystem("SectionSpecificAttendantRouterSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with MustMatchers {

  import RouterRelay._

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  def relayWithRow(row: Int) = system.actorOf(Props[RouterRelay], s"Someone-$row-C")

  "SectionSpecificAttendantRouter" should {
    "route consistently" in {
      val attentand1 = TestProbe()
      val attentand2 = TestProbe()
      val attentand3 = TestProbe()

      val passengers: IndexedSeq[ActorRef] = (1 to 25).map(i => relayWithRow(i))
      val masterRouter = system.actorOf(Props(new SectionSpecificAttendantMasterRouter(List(attentand1.ref, attentand2.ref, attentand3.ref))))

      passengers(6) ! RelayTo(masterRouter, "Test message.")
      passengers(13) ! RelayTo(masterRouter, "Test message.")
      passengers(21) ! RelayTo(masterRouter, "Test message.")

      attentand1.expectMsg(CustomRouting("Test message." + s" I am passenger on row: 7", passengers(6)))
      attentand2.expectMsg(CustomRouting("Test message." + s" I am passenger on row: 14", passengers(13)))
      attentand3.expectMsg(CustomRouting("Test message." + s" I am passenger on row: 22", passengers(21)))

    }
  }
}
