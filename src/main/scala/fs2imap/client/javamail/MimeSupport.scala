package fs2imap.client.javamail

import cats.effect.Async
import fs2.Stream
import fs2.io.readInputStream
import fs2imap.model.*
import fs2imap.model.MailId.MessageNumber
import jakarta.mail.Part
import jakarta.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart, MimeUtility}
import jakarta.mail.{BodyPart, Message, Multipart}

import java.time.Instant
import scala.jdk.CollectionConverters.*

private[javamail] object MimeSupport:

  private val ChunkSize = 8192

  def toMail[F[_]: Async](message: Message): Mail[F] =
    val envelope = buildEnvelope(message)
    val acc      = Acc.empty[F]
    walk(message, acc)
    acc.toMail(envelope)

  private def buildEnvelope(message: Message): MailEnvelope =
    val from = addresses(message.getFrom)
    val to   = addresses(message.getRecipients(Message.RecipientType.TO))
    MailEnvelope(
      messageNumber = MessageNumber(message.getMessageNumber),
      from = from,
      to = to,
      subject = Option(message.getSubject),
      sentAt = Option(message.getSentDate).map(d => d.toInstant),
      messageId = Option(message.getHeader("Message-ID")).flatMap(_.headOption)
    )

  private def addresses(addrs: Array[?]): List[String] =
    Option(addrs)
      .map(_.toList.flatMap {
        case ia: InternetAddress => Option(ia.getAddress).toList
        case other               => List(other.toString)
      })
      .getOrElse(Nil)

  private def walk[F[_]: Async](part: Part, acc: Acc[F]): Unit =
    if part.isMimeType("multipart/*") then
      val mp = part.getContent.asInstanceOf[Multipart]
      var i  = 0
      while i < mp.getCount do
        walk(mp.getBodyPart(i), acc)
        i += 1
    else if part.isMimeType("text/plain") then
      acc.textBody = Some(mailPart(part, ContentType.TextPlain))
    else if part.isMimeType("text/html") then
      acc.htmlBody = Some(mailPart(part, ContentType.TextHtml))
    else
      acc.attachments = acc.attachments :+ attachment(part)

  private def mailPart[F[_]: Async](part: Part, contentType: ContentType): MailPart[F] =
    MailPart(
      contentType = contentType,
      charset = charset(part),
      content = partStream(part)
    )

  private def attachment[F[_]: Async](part: Part): Attachment[F] =
    val rawType = Option(part.getContentType).map(ContentType.parse).getOrElse(ContentType("application", "octet-stream"))
    Attachment(
      filename = Option(part.getFileName).map(MimeUtility.decodeText),
      contentType = rawType,
      disposition = disposition(part),
      content = partStream(part)
    )

  private def disposition(part: Part): ContentDisposition =
    Option(part.getDisposition) match
      case Some(Part.ATTACHMENT) => ContentDisposition.Attachment
      case Some(Part.INLINE)     => ContentDisposition.Inline
      case _                     =>
        if Option(part.getFileName).exists(_.nonEmpty) then ContentDisposition.Attachment
        else ContentDisposition.Inline

  private def charset(part: Part): Option[String] =
    Option(part.getContentType)
      .flatMap { raw =>
        val params = raw.split(';').drop(1).map(_.trim)
        params.collectFirst {
          case p if p.toLowerCase.startsWith("charset=") =>
            p.dropWhile(_ != '=').drop(1).trim.stripPrefix("\"").stripSuffix("\"")
        }
      }

  private def partStream[F[_]: Async](part: Part): Stream[F, Byte] =
    readInputStream(
      Async[F].delay(part.getInputStream),
      ChunkSize,
      closeAfterUse = true
    )

  private final class Acc[F[_]](
      var textBody: Option[MailPart[F]],
      var htmlBody: Option[MailPart[F]],
      var attachments: List[Attachment[F]]
  ):
    def toMail(envelope: MailEnvelope): Mail[F] =
      Mail(envelope, textBody, htmlBody, attachments)

  private object Acc:
    def empty[F[_]]: Acc[F] = Acc(None, None, Nil)
