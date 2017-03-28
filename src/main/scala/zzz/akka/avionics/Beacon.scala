package zzz.akka.avionics

import akka.actor._
import akka.event.{EventBus, LookupClassification}

import scala.concurrent.duration._

trait BeaconResolution {
  lazy val beaconInterval = 1.second
}

trait BeaconProvider {
  def newBeacon(heading: Float) = Beacon(heading)
}

object GenericPublisher {
  case class RegisterListener(actor: ActorRef)
  case class UnregisterListener(actor: ActorRef)
}

object Beacon {
  case class BeaconHeading(heading: Float)
  def apply(heading: Float) = new Beacon(heading) with BeaconResolution
}

class Beacon(heading: Float) extends Actor { this: BeaconResolution =>
  import Beacon._
  import GenericPublisher._
  import context.dispatcher

  case object Tick
  val bus = new EventBusForActors()

  val ticker = context.system.scheduler.schedule(beaconInterval, beaconInterval, self, Tick)
  def receive = {
    case RegisterListener(actor) => bus.subscribe(actor, true)
    case UnregisterListener(actor) => bus.unsubscribe(actor)
    case Tick => bus.publish(BeaconHeading(heading))
  }
}

class EventBusForActors extends EventBus with LookupClassification {
  import Beacon._

  type Event = BeaconHeading
  type Classifier = Boolean
  type Subscriber = ActorRef

  // is used for extracting the classifier from the incoming events
  override protected def classify(event: Event): Classifier =
    event match {
      case _: BeaconHeading => true
    }

  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event
  }

  // must define a full order over the subscribers, expressed as expected from
  // `java.lang.Comparable.compare`
  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
    a.compareTo(b)

  // determines the initial size of the index data structure
  // used internally (i.e. the expected number of different classifiers)
  override protected def mapSize: Int = 128

}