package zzz.akka.investigation

import akka.actor.Actor


object CommonTestData {
  class NilActor extends Actor {
    def receive = {
      case _ => }
  }
}
