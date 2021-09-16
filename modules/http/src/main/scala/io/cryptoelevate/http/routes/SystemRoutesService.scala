package io.cryptoelevate.http.routes

import io.cryptoelevate.http.Main.HttpLayer
import org.http4s._
import org.http4s.dsl.Http4sDsl
import zio.interop.catz._

object SystemRoutesService {

  def routes[R](): HttpRoutes[HttpLayer] = {
    val dsl = Http4sDsl[HttpLayer]
    import dsl._

    HttpRoutes
      .of[HttpLayer] { case GET -> Root =>
        Ok("Hello, I am up.")
      }
  }
}
