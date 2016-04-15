package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.Promise

// TODO Move to io lib
trait PromiseOps {
  implicit class PromiseOps[R](p: Promise[R]) {
    /** Complete a Promise as successful with the given result, and give back said result */
    val <~ = (result: R) => {
      p success result
      result
    }
  }
}