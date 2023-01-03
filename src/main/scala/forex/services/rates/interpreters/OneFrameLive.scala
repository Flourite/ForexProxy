package forex.services.rates.interpreters

import cats.effect.Concurrent
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId}
import cats.syntax.functor._
import forex.config.OneFrameConfig
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.http.rates.Protocol.GetApiResponse
import forex.services.rates.Algebra
import forex.services.rates.errors.Error.OneFrameLookupFailed
import forex.services.rates.errors._
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.client.dsl._
import org.http4s.dsl.io.GET

import java.time.Duration
import java.util.Optional

class OneFrameLive[F[_]: Concurrent](config : OneFrameConfig, httpClient: Client[F]) extends Algebra[F] with Http4sClientDsl[F] {
  private var cache: Map[Currency, (Price, Timestamp)] = Map.empty
  override def get(pair: Rate.Pair): F[Error Either Rate] = {
    val priceFromCache = getFromCache(pair)
    if (!priceFromCache.isEmpty) {
        priceFromCache.get().asRight[Error].pure[F]
    } else {
      val uri = Uri
        .fromString(config.uri + "?" + "pair=USDAUD&pair=USDCAD&pair=USDCHF&pair=USDEUR&pair=USDGBP&pair=USDNZD&pair=USDJPY&pair=USDSGD")
        .getOrElse(Uri())

      val responses = httpClient
        .expect[List[GetApiResponse]](
          GET.apply(
            uri,
            "token" -> config.token,
          )
        )
      responses.map(responseList => {
        if (responseList.isEmpty) {
          Left(OneFrameLookupFailed("Look up failed"))
        }
        responseList.foreach(response => {
          putToCache(response)
        })
        getFromCache(pair).get().asRight[Error]
      })
    }
  }

  private def getFromCache(pair: Rate.Pair): Optional[Rate] = {
    val fromRate = getFromCache(pair.from)
    val toRate = getFromCache(pair.to)
    if (fromRate.isEmpty || toRate.isEmpty) {
      Optional.empty();
    } else {
      val price = toRate.get().value * 1.0 / fromRate.get().value
      Optional.of(new Rate(pair, new Price(price), new Timestamp(Timestamp.now.value)))
    }
  }

  private def getFromCache(currency: Currency): Optional[Price] = {
    if (currency.equals(Currency.USD)) {
      return Optional.of(new Price(1.0))
    } else {
      val value = cache.get(currency)
      value match {
        case None => Optional.empty()
        case Some((price, timestamp)) => {
          val time = Duration.between(timestamp.value, Timestamp.now.value).toSeconds
          if (time < 300)
            Optional.of(price)
          else
            Optional.empty()
        }
      }
    }
  }


  private def putToCache(response: GetApiResponse): Unit = {
    cache += (response.to -> (response.price, response.timestamp))
  }
}
