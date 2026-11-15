package dawidkobak.myfunds.domain.helper

import io.circe.Json
import io.circe.parser.parse
import zio.{Task, ZIO}
import zio.http.Request

object RequestJsonBody {
  extension (request: Request)
    def readJson: Task[Json] =
      for {
        bodyText <- request.body.asString
        decodedBody <- ZIO.fromEither(parse(bodyText))
      } yield decodedBody
}
