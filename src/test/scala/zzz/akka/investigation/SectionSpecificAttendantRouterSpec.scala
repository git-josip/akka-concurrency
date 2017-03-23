package zzz.akka.investigation

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.RouterConfig
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

// This will be the Routee, which will be put in place // instead of the FlightAttendant
class TestRoutee extends Actor {
  def receive = {
    case m => sender ! m
  }
}

class SectionSpecificAttendantRouterSpec extends TestKit(ActorSystem("SectionSpecificAttendantRouterSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with MustMatchers {

  override def afterAll() { system.terminate() }

  // A simple method to create a new
  // SectionSpecificAttendantRouter with the overridden
  // FlightAttendantProvider that instantiates a TestRoutee

  def newRouter(): RouterConfig = new SectionSpecificAttendantRouter(5) {
    override def newFlightAttendant() = new TestRoutee
  }
  
  "SectionSpecificAttendantRouter" should {
    "route consistently" in {
      val router = system.actorOf(Props[TestRoutee].withRouter(newRouter()))

      (1 to 5) foreach { n =>
        router ! "Test message"
      }

      expectMsg("Test message")
      expectMsg("Test message")
      expectMsg("Test message")
      expectMsg("Test message")
      expectMsg("Test message")
    }
  }
}
