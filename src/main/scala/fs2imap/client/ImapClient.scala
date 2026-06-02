package fs2imap.client

import cats.effect.Async
import cats.effect.Resource
import fs2imap.client.javamail.JavaMailImapClient
import fs2imap.config.ImapConfig

trait ImapClient[F[_]]:
  def connect(config: ImapConfig): Resource[F, ImapSession[F]]

object ImapClient:
  def javaMail[F[_]: Async]: ImapClient[F] = JavaMailImapClient[F]
