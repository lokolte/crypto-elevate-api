package io.cryptoelevate.connectors.interfaces.dynamo

import awscala.dynamodbv2.DynamoDB
import io.cryptoelevate.connectors.models.{UserHistory, UserTz1Keys}
import io.cryptoelevate.model.EmailAddress
import zio.{Has, Task, ZIO, ZLayer}
import io.cryptoelevate.Config
import zio._
import zio.blocking.Blocking
import awscala._
import dynamodbv2._
import io.cryptoelevate.connectors.models.connectors.DynamoSdkCaller

private[connectors] object DynamoSdkCaller {

  trait Service {
    def moveTz1Key(email: EmailAddress, tz1Key: UserTz1Keys): Task[UserHistory]
    def burnTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): Task[UserHistory]

    def createNewUser(
      email: EmailAddress,
      actualTZ1Key: Option[UserTz1Keys],
      inactiveTZ1Keys: List[UserTz1Keys] = List(),
      burnedTZ1Keys: List[UserTz1Keys] = List()
    ): ZIO[Any, Throwable, Unit]

    def updateUserKeys(
      email: EmailAddress,
      actual: Option[UserTz1Keys] = None,
      inactiveTZ1Keys: List[UserTz1Keys] = List(),
      burnedTZ1Keys: List[UserTz1Keys] = List()
    ): Task[UserHistory]

    def getUserTz1Keys(email: EmailAddress): Task[Option[UserHistory]]
  }

  val live: ZLayer[Config with Has[Blocking.Service], Throwable, DynamoSdkCaller] = (for {
    zioBlocking: Blocking.Service <- ZIO.service[Blocking.Service]
    config                        <- ZIO.accessM[Config](_.get.loadConfig())
    awsConfig                     <- Task.effectTotal(config.aws)
    keys = awsConfig.awsData
    id = keys.id
    secret = keys.secret
    region = awsConfig.region
  } yield new DynamoSdkCaller.Service {
    implicit val dynamoDB = DynamoDB.apply(Credentials(id, secret))(Region(region))
    val table = dynamoDB.table(awsConfig.dynamo.tableName).get

    private def putRecordWithUserKeys(
      email: EmailAddress,
      actual: Option[UserTz1Keys] = None,
      inactiveKeys: List[UserTz1Keys],
      burnedKeys: List[UserTz1Keys]
    ): Task[UserHistory] = {
      val inactiveToStore = inactiveKeys.map(inactiveTZ1Key => (inactiveTZ1Key.publicKey, inactiveTZ1Key.publicKeyHash))
      val burnedToStore = burnedKeys.map(burnedTZ1Key => (burnedTZ1Key.publicKey, burnedTZ1Key.publicKeyHash))
      val actualToStore = actual.map(actualTZ1Key => (actualTZ1Key.publicKey, actualTZ1Key.publicKeyHash))

      val attributeValues =
        if (actualToStore.isDefined)
          List(
            "inactiveTZ1Keys" -> inactiveToStore,
            "burnedTZ1Keys" -> burnedToStore,
            "publicKey" -> actualToStore.map(key => key._1).getOrElse(""),
            "publicKeyHash" -> actualToStore.map(key => key._2).getOrElse("")
          )
        else List("inactiveTZ1Keys" -> inactiveToStore, "burnedTZ1Keys" -> burnedToStore)

      for {
        _ <- zioBlocking.effectBlocking(table.put(email.value, attributeValues: _*))
      } yield UserHistory(email, actual, inactiveKeys, burnedKeys)
    }

    override def moveTz1Key(email: EmailAddress, tz1Key: UserTz1Keys): Task[UserHistory] =
      for {
        optionalUserHistoryStored <- getUserTz1Keys(email)
        updatedUserHistory = optionalUserHistoryStored
          .map(userHistoryStored =>
            if((userHistoryStored.actualTZ1Keys.isDefined
              && userHistoryStored.actualTZ1Keys.get.publicKey!=tz1Key.publicKey && userHistoryStored.actualTZ1Keys.get.publicKeyHash!=tz1Key.publicKeyHash)
              && !userHistoryStored.inactiveTZ1Keys.contains(tz1Key) && !userHistoryStored.burnedTZ1Keys.contains(tz1Key)
              || !userHistoryStored.actualTZ1Keys.isDefined)
              userHistoryStored.copy(
                inactiveTZ1Keys = userHistoryStored.inactiveTZ1Keys ++ userHistoryStored.actualTZ1Keys,
                actualTZ1Keys = Some(tz1Key)
              )
            else UserHistory(email, None, List(), List())
          )
        userHistory <- updatedUserHistory match {
          case None => IO.fail(new NoSuchElementException)
          case Some(UserHistory(_, None, List(), List())) => IO.fail(new IllegalArgumentException)
          case Some(userHistory) => IO.succeed(userHistory)
        }
        updatedUserHistory <- putRecordWithUserKeys(
          userHistory.email,
          userHistory.actualTZ1Keys,
          userHistory.inactiveTZ1Keys,
          userHistory.burnedTZ1Keys
        )
      } yield updatedUserHistory

    override def burnTz1Keys(email: EmailAddress, tz1Key: UserTz1Keys): Task[UserHistory] =
      for {
        optionalUserHistoryStored <- getUserTz1Keys(email)
        updatedUserHistory: Option[UserHistory] = optionalUserHistoryStored
          .map(userHistoryStored =>
            if(userHistoryStored.inactiveTZ1Keys.contains(tz1Key) && !userHistoryStored.burnedTZ1Keys.contains(tz1Key))
              userHistoryStored.copy(
                burnedTZ1Keys = userHistoryStored.burnedTZ1Keys ++ Some(tz1Key),
                inactiveTZ1Keys = userHistoryStored.inactiveTZ1Keys.filter(_ != tz1Key)
              )
            else UserHistory(email, None, List(), List())
          )
        updatedUserHistory <- updatedUserHistory match {
          case None => IO.fail(new NoSuchElementException)
          case Some(UserHistory(_, None, List(), List())) => IO.fail(new IllegalArgumentException)
          case Some(userHistory) => IO.succeed(userHistory)
        }
        userHistoryResult <- putRecordWithUserKeys(
          updatedUserHistory.email,
          updatedUserHistory.actualTZ1Keys,
          updatedUserHistory.inactiveTZ1Keys,
          updatedUserHistory.burnedTZ1Keys
        )
      } yield userHistoryResult

    override def createNewUser(
      email: EmailAddress,
      actualTZ1Key: Option[UserTz1Keys],
      inactiveTZ1Keys: List[UserTz1Keys] = List(),
      burnedTZ1Keys: List[UserTz1Keys] = List()
    ): ZIO[Any, Throwable, Unit] =
      for {
        _ <- putRecordWithUserKeys(email, actualTZ1Key, inactiveTZ1Keys, burnedTZ1Keys)
      } yield ()

    override def updateUserKeys(
      email: EmailAddress,
      actual: Option[UserTz1Keys] = None,
      inactiveTZ1Keys: List[UserTz1Keys] = List(),
      burnedTZ1Keys: List[UserTz1Keys] = List()
    ): Task[UserHistory] =
      putRecordWithUserKeys(email, actual, inactiveTZ1Keys, burnedTZ1Keys)

    private def getUserHistoryFromDynamoItem(item: Item): Option[UserHistory] =
      item.attributes match {
        case seq if seq.isEmpty => None
        case seq =>
          Some(
            seq
              .map(attribute =>
                attribute.name match {
                  case "inactiveTZ1Keys" =>
                    UserHistory(
                      EmailAddress(""),
                      None,
                      attribute.value.ss
                        .map(inactiveKey => inactiveKey.filterNot("()".toSet).split(",").toList)
                        .map(keys => UserTz1Keys(keys(0), keys(1)))
                        .toList,
                      List()
                    )
                  case "burnedTZ1Keys" =>
                    UserHistory(
                      EmailAddress(""),
                      None,
                      List(),
                      attribute.value.ss
                        .map(burnedKey => burnedKey.filterNot("()".toSet).split(",").toList)
                        .map(keys => UserTz1Keys(keys(0), keys(1)))
                        .toList
                    )
                  case "publicKey" =>
                    UserHistory(EmailAddress(""), Some(UserTz1Keys(attribute.value.s.getOrElse(""), "")), List(), List())
                  case "publicKeyHash" =>
                    UserHistory(EmailAddress(""), Some(UserTz1Keys("", attribute.value.s.getOrElse(""))), List(), List())
                  case "email" => UserHistory(EmailAddress(attribute.value.s.getOrElse("")), None, List(), List())
                }
              )
              .foldLeft(UserHistory(EmailAddress(""), None, List(), List())) {
                case (userHistoryResult, UserHistory(EmailAddress(""), actualTZ1KeysValue, List(), List()))
                    if actualTZ1KeysValue.isDefined && actualTZ1KeysValue.get.publicKey.nonEmpty =>
                  userHistoryResult.copy(actualTZ1Keys =
                    Some(
                      UserTz1Keys(
                        actualTZ1KeysValue.get.publicKey,
                        userHistoryResult.actualTZ1Keys.map(actualTZ1Key => actualTZ1Key.publicKeyHash).getOrElse("")
                      )
                    )
                  )
                case (userHistoryResult, UserHistory(EmailAddress(""), actualTZ1KeysValue, List(), List()))
                    if actualTZ1KeysValue.isDefined && actualTZ1KeysValue.get.publicKeyHash.nonEmpty =>
                  userHistoryResult.copy(actualTZ1Keys =
                    Some(
                      UserTz1Keys(
                        userHistoryResult.actualTZ1Keys.map(actualTZ1Key => actualTZ1Key.publicKey).getOrElse(""),
                        actualTZ1KeysValue.get.publicKeyHash
                      )
                    )
                  )
                case (userHistoryResult, UserHistory(EmailAddress(""), None, inactiveTZ1KeysValue, List()))
                  if inactiveTZ1KeysValue.nonEmpty =>
                  userHistoryResult.copy(inactiveTZ1Keys = inactiveTZ1KeysValue)
                case (userHistoryResult, UserHistory(EmailAddress(""), None, List(), burnedTZ1KeysValue))
                    if burnedTZ1KeysValue.nonEmpty =>
                  userHistoryResult.copy(burnedTZ1Keys = burnedTZ1KeysValue)
                case (userHistoryResult, UserHistory(email, None, List(), List())) => userHistoryResult.copy(email = email)
              }
          )
      }

    override def getUserTz1Keys(email: EmailAddress): Task[Option[UserHistory]] =
      zioBlocking
        .effectBlocking(getUserHistoryFromDynamoItem((table.get(email.value)(dynamoDB)).getOrElse(Item(table, Seq()))))

  }).toLayer

}
