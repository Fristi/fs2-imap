package fs2imap.client.javamail

import cats.effect.{Async, Resource}
import fs2.Stream
import fs2imap.client.{ImapClient, ImapSession, MailFolder}
import fs2imap.config.ImapConfig
import fs2imap.model.Mail
import jakarta.mail.{Authenticator, Folder, PasswordAuthentication, Session, Store}

import java.util.Properties

final class JavaMailImapClient[F[_]](using F: Async[F]) extends ImapClient[F]:

  def connect(config: ImapConfig): Resource[F, ImapSession[F]] =
    Resource.make(acquireSession(config))(releaseSession)

  private def acquireSession(config: ImapConfig): F[JavaMailSession[F]] =
    F.blocking:
      val props = new Properties()
      val protocol = if config.ssl then "imaps" else "imap"
      props.put("mail.store.protocol", protocol)
      if config.ssl then props.put("mail.imaps.ssl.enable", "true")

      val auth = new Authenticator:
        override def getPasswordAuthentication: PasswordAuthentication =
          new PasswordAuthentication(config.user, config.password)

      val session = Session.getInstance(props, auth)
      val store   = session.getStore(protocol)
      store.connect(config.host, config.port, config.user, config.password)
      JavaMailSession(store)

  private def releaseSession(session: JavaMailSession[F]): F[Unit] =
    F.blocking:
      if session.store.isConnected then session.store.close()

private final class JavaMailSession[F[_]](val store: Store)(using F: Async[F]) extends ImapSession[F]:

  def open(folderName: String): Resource[F, MailFolder[F]] =
    Resource.make(acquireFolder(folderName))(releaseFolder)

  private def acquireFolder(name: String): F[JavaMailFolder[F]] =
    F.blocking:
      val folder = store.getFolder(name)
      folder.open(Folder.READ_ONLY)
      JavaMailFolder(folder)

  private def releaseFolder(folder: JavaMailFolder[F]): F[Unit] =
    F.blocking:
      if folder.jFolder.isOpen then folder.jFolder.close(false)

private final class JavaMailFolder[F[_]](val jFolder: Folder)(using F: Async[F]) extends MailFolder[F]:

  def messages: Stream[F, Mail[F]] =
    messageStream(1L, jFolder.getMessageCount.toLong)

  def messages(range: (Long, Long)): Stream[F, Mail[F]] =
    val (from, to) = range
    messageStream(from.max(1), to.min(jFolder.getMessageCount.toLong))

  private def messageStream(from: Long, to: Long): Stream[F, Mail[F]] =
    if to < from || jFolder.getMessageCount == 0 then Stream.empty
    else
      Stream
        .range(from, to + 1)
        .evalMap(n => F.blocking(fetchMessage(n.toInt)))

  private def fetchMessage(num: Int): Mail[F] =
    val message = jFolder.getMessage(num)
    MimeSupport.toMail(message)

object JavaMailImapClient:
  def apply[F[_]: Async]: JavaMailImapClient[F] = new JavaMailImapClient[F]
