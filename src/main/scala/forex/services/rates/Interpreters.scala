package forex.services.rates

import cats.Applicative
import cats.effect.Async
import forex.config.OneFrameConfig
import interpreters._
import org.http4s.client.Client

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F] = new OneFrameDummy[F]()

  def live[F[_]: Async](httpClient: Client[F], config: OneFrameConfig): Algebra[F] = new OneFrameLive[F](config, httpClient)
}
