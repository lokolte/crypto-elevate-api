package io.cryptoelevate.connectors.interfaces.ses

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsync
import com.amazonaws.services.simpleemail.model._
import io.cryptoelevate.Config
import io.cryptoelevate.connectors.models.SendingEmail
import io.cryptoelevate.connectors.models.connectors.SesSdkCaller
import zio.blocking.Blocking
import zio.{ Has, Task, UIO, ZIO, ZLayer }

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.collection.JavaConverters._
private[connectors] object SesSdkCaller {

  trait Service {

    def send(email: SendingEmail): Task[SendEmailResult]
  }
  val live: ZLayer[Has[Blocking.Service] with Has[AmazonSimpleEmailServiceAsync], Throwable, SesSdkCaller] =
    (for {

      zioBlocking: Blocking.Service <- ZIO.service[Blocking.Service]
      client                        <- ZIO.service[AmazonSimpleEmailServiceAsync]

    } yield new SesSdkCaller.Service {

      private def buildRequest(email: SendingEmail): SendEmailRequest = {
        val destination = new Destination()
        if (email.to.nonEmpty) destination.setToAddresses(email.to.map(_.encoded).asJavaCollection)
        if (email.cc.nonEmpty) destination.setCcAddresses(email.cc.map(_.encoded).asJavaCollection)
        if (email.bcc.nonEmpty) destination.setBccAddresses(email.bcc.map(_.encoded).asJavaCollection)

        val subject = new Content(email.subject.data).withCharset(email.subject.charset)

        val body = new Body()
        email.bodyHtml.foreach { bodyHtml =>
          val htmlContent = new Content(bodyHtml.data)
          htmlContent.setCharset(bodyHtml.charset)
          body.setHtml(htmlContent)
        }
        email.bodyText.foreach { bodyText =>
          val textContent = new Content(bodyText.data)
          textContent.setCharset(bodyText.charset)
          body.setText(textContent)
        }

        val message = new Message(subject, body)

        val req = new SendEmailRequest(email.source.encoded, destination, message)
        if (email.replyTo.nonEmpty) req.setReplyToAddresses(email.replyTo.map(_.encoded).asJavaCollection)

        email.configurationSet.foreach { configurationSetName =>
          req.setConfigurationSetName(configurationSetName)
        }

        val messageTags = email.messageTags.map { case (name, value) =>
          new MessageTag().withName(name).withValue(value)
        }
        req.setTags(messageTags.asJavaCollection)

        email.returnPath.map { returnPath =>
          req.setReturnPath(returnPath)
        }

        req
      }

      override def send(email: SendingEmail): Task[SendEmailResult] =
        zioBlocking.effectBlocking(client.sendEmail(buildRequest(email)))

    }).toLayer

}
