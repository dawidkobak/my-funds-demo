package dawidkobak.myfunds.cli.helper

import zio.*

object ErrorUtils {
  type ErrorHandler = Throwable => Task[Unit]

  def cliAppErrorHandler(cliArguments: List[String] = List()): ErrorHandler =
    error => {
      val errorCause = Cause.fail(error)
      ZIO.logErrorCause(errorCause)
    }
}
