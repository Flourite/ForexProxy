package forex.http.rates

import forex.domain.Currency
import forex.domain.Currency.currencyList
import org.http4s.{ParseFailure, QueryParamDecoder}
import org.http4s.dsl.impl.ValidatingQueryParamDecoderMatcher

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Currency] =
    QueryParamDecoder[String].emap(currencyString => {
      if (currencyList.contains(currencyString))
        Right(Currency.fromString(currencyString))
      else
        Left(ParseFailure("invalid currency code", s"invalid currency code, $currencyString"))
    })

  object FromQueryParam extends ValidatingQueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends ValidatingQueryParamDecoderMatcher[Currency]("to")

}
