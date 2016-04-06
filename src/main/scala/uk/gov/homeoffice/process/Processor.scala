package uk.gov.homeoffice.process

import scala.util.Try

/**
  * Process given input.
  * @tparam I Input to process
  * @tparam O Output if successful in processing (wrapped in a Success) or a processing exception (wrapped in a Failure)
  */
trait Processor[I, O] {
  def process(in: I): Try[O]
}