package forex.services.rates.interpreters

import cats.effect.Concurrent
import cats.syntax.functor._
import forex.config.OneFrameConfig
import forex.domain.Rate
import forex.http.rates.Protocol.GetApiResponse
import forex.services.rates.Algebra
import forex.services.rates.errors.Error._
import forex.services.rates.errors._
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.client.dsl._
import org.http4s.dsl.io.GET

class OneFrameLive[F[_]: Concurrent](config : OneFrameConfig, httpClient: Client[F]) extends Algebra[F] with Http4sClientDsl[F] {
  override def get(pair: Rate.Pair): F[Error Either Rate] = {
    val uri = Uri
      .fromString(config.uri)
      .map(_.withQueryParam("pair", s"${pair.from}${pair.to}"))
      .getOrElse(Uri())

    httpClient
      .expect[List[GetApiResponse]](
        GET(
          uri,
          "token" -> config.token,
        )
      )
      .map { responseList =>
        if (responseList.isEmpty) {
          Left(OneFrameLookupFailed(s"Unavailable to get rate for $pair"))
        } else {
          //Right(responseList.map(response => Rate(pair, response.price, response.timestamp)))
          Right(Rate(pair, responseList.head.price, responseList.head.timestamp))
        }
      }

  }
}
