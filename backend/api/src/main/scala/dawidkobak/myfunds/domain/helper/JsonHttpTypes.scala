package dawidkobak.myfunds.domain.helper

import io.circe.{Error, Json}
import sttp.client3.{Empty, RequestT, ResponseException}

object JsonHttpTypes {
  type JsonHttpResult = Either[ResponseException[Json, Error], Json]
  type JsonHttpRequest = RequestT[Empty, JsonHttpResult, Any]
}
