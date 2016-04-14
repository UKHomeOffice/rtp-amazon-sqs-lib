package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.Promise

// TODO Move to io lib
trait PromiseOps {
  implicit class PromiseOps[R](p: Promise[R]) {
    def done(r: R) = {
      p success r
      r
    }
  }
}