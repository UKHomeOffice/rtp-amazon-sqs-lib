package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

trait ResultPromise {
  implicit class ResultPromise[R](p: Promise[Try[R]]) {
    def success(r: R) = {
      val resultSuccess = Success(r)
      p success resultSuccess
      resultSuccess
    }

    def failed(t: Throwable) = {
      val resultFailure = Failure(t)
      p success resultFailure
      resultFailure
    }
  }
}