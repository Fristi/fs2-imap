package fs2imap

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import fs2imap.client.ImapClient
import fs2imap.config.ImapConfig
import fs2imap.syntax.*

/** Example: connect to IMAP and print subjects + attachment byte counts.
  *
  * Env vars: IMAP_HOST, IMAP_PORT, IMAP_USER, IMAP_PASSWORD, IMAP_SSL (default true)
  */
object Main extends IOApp.Simple:

  def run: IO[Unit] =
    val config = ImapConfig(
      host = sys.env.getOrElse("IMAP_HOST", "localhost"),
      port = sys.env.get("IMAP_PORT").flatMap(_.toIntOption).getOrElse(993),
      user = sys.env.getOrElse("IMAP_USER", "user@example.com"),
      password = sys.env.getOrElse("IMAP_PASSWORD", ""),
      ssl = sys.env.get("IMAP_SSL").forall(_ != "false")
    )

    ImapClient
      .javaMail[IO]
      .connect(config)
      .use { session =>
        session.open(config.folder).use { folder =>
          folder.messages.evalMap { mail =>
            for
              _ <- IO.println(s"Subject: ${mail.envelope.subject.getOrElse("(none)")}")
              counts <- mail.attachments.traverse(a => a.content.compile.count)
              _ <- mail.drainBodies
              _ <- IO.println(s"Attachment byte counts: ${counts.mkString(", ")}")
            yield ()
          }.compile.drain
        }
      }
