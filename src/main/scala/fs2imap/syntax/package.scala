package fs2imap.syntax

import cats.effect.Async
import cats.syntax.all.*
import fs2imap.model.{Attachment, Mail, MailPart}

extension [F[_]: Async](mail: Mail[F])
  def drainBodies: F[Unit] =
    val streams =
      mail.textBody.toList.map(_.content) ++
        mail.htmlBody.toList.map(_.content)
    streams.traverse_(_.compile.drain)

  def drainAttachments: F[Unit] =
    mail.attachments.traverse_(a => a.content.compile.drain)

  def drainAll: F[Unit] =
    drainBodies >> drainAttachments

extension [F[_]: Async](part: MailPart[F])
  def drain: F[Unit] = part.content.compile.drain

extension [F[_]: Async](attachment: Attachment[F])
  def drain: F[Unit] = attachment.content.compile.drain
