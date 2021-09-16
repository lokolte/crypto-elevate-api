package io.cryptoelevate.connectors

import com.example.ITSpec.ITSpec
import zio.test.Assertion.equalTo
import zio.test.TestAspect.before
import zio.test.{ assert, assertM, suite, testM }

object ItemRepositorySpec extends ITSpec(Some("items")) {

  def flippingFailure(value: Any): Exception =
    new Exception(
      "Flipping failed! The referred effect was successful with `" + value + "` result of `" + value.getClass + "` type!"
    )

val
  val spec: ITSpec =
    suite("Item Repository")(
      //  def addItem
      testM("Add correct item ") {
        val name: String = "name"
        val price: BigDecimal = 100.0
        for {
        _ <- loggi
          client =  AwsCognitoSdkCaller.live
        } yield contentsCheck
      }
}
