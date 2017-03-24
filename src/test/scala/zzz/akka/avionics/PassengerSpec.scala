package zzz.akka.avionics

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{MustMatchers, WordSpecLike}
import zzz.akka.avionics.Passenger.FastenSeatbelts

import scala.concurrent.duration._

trait TestDrinkRequestProbability extends DrinkRequestProbability {
  override lazy val askThreshold = 0f
  override lazy val requestMin: FiniteDuration = 0.milliseconds
  override lazy val requestUpper: FiniteDuration = 2.milliseconds
}

class PassengerSpec extends TestKit(ActorSystem())
  with ImplicitSender
  with WordSpecLike
  with MustMatchers{
  import akka.event.Logging.Info
  import akka.testkit.TestProbe
  var seatNumber = 9
  def newPassenger(): ActorRef = {
    seatNumber += 1
    system.actorOf(Props(new Passenger(testActor) with TestDrinkRequestProbability), s"Pat_Metheny-$seatNumber-B")
  }

  "Passengers" should {
    "fasten seatbelts when asked" in {
      val a = newPassenger()
      val p = TestProbe()
      system.eventStream.subscribe(p.ref, classOf[Info])
      a ! FastenSeatbelts
      p.expectMsgPF() {
        case Info(_, _, m) =>
          m.toString must include ("fastening seatbelt")
      }
    } }
}
