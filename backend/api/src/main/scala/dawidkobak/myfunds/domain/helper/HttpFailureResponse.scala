package dawidkobak.myfunds.domain.helper

import io.circe.Json
import sttp.client3.HttpError
import zio.*
import zio.http.*
import dawidkobak.myfunds.domain.helper.RequestJsonBody.*

object HttpFailureResponse {
  def respond(request: Request, cause: Cause[Throwable]): UIO[Response] =
    for {
      _ <- request.readJson.orElseSucceed(Json.Null)
      (message, traceText, responseStatus) = cause.failureTraceOrCause match {
        case Left((HttpError(responseBody, upstreamStatus), trace)) =>
          val status = if (upstreamStatus.code == 403) Status.Forbidden else Status.InternalServerError
          (responseBody.toString, trace.prettyPrint, status)
        case Left((failure, trace)) =>
          (failure.getMessage, trace.prettyPrint, Status.InternalServerError)
        case Right(defect) =>
          (defect.prettyPrint, defect.trace.prettyPrint, Status.InternalServerError)
      }
      _ <- ZIO.logErrorCause(cause)
    } yield Response(
      status = responseStatus,
      body = Body.fromString(Json.obj(
        "error" -> Json.fromString(message),
        "cause" -> Json.fromString(cause.prettyPrint),
        "trace" -> Json.fromString(traceText)
      ).noSpaces)
    )
}
