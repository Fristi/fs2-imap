package fs2imap.client

import cats.effect.Resource

trait ImapSession[F[_]]:
  def open(folder: String): Resource[F, MailFolder[F]]
