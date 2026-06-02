package fs2imap

import cats.effect.IO
import com.icegreen.greenmail.util.{GreenMail, GreenMailUtil, ServerSetupTest}
import fs2imap.client.ImapClient
import fs2imap.config.ImapConfig
import fs2imap.syntax.*
import munit.CatsEffectSuite

import jakarta.mail.internet.MimeMessage
import jakarta.mail.util.ByteArrayDataSource

class JavaMailImapClientSpec extends CatsEffectSuite:

  private def withGreenMail[A](f: (GreenMail, ImapConfig) => IO[A]): IO[A] =
    IO {
      val setups = Array(ServerSetupTest.SMTP, ServerSetupTest.IMAP)
      val greenMail = new GreenMail(setups)
      greenMail.start()
      val config = ImapConfig(
        host = "127.0.0.1",
        port = greenMail.getImap().getPort(),
        user = "test@localhost",
        password = "test",
        ssl = false,
        folder = "INBOX"
      )
      (greenMail, config)
    }.flatMap { case (greenMail, config) =>
      f(greenMail, config).guarantee(IO(greenMail.stop()))
    }

  test("stream mail with text body and attachment over IMAP") {
    val attachName = "data.bin"
    withGreenMail { (greenMail, config) =>
      IO {
        val subject     = "fs2-imap test"
        val text        = "hello from greenmail"
        val attachBytes = Array[Byte](1, 2, 3, 4, 5)

        val msg = new MimeMessage(greenMail.getSmtp().createSession())
        msg.setFrom("sender@localhost")
        msg.addRecipient(jakarta.mail.Message.RecipientType.TO, new jakarta.mail.internet.InternetAddress(config.user))
        msg.setSubject(subject)
        msg.setText(text)

        val multipart = new jakarta.mail.internet.MimeMultipart()
        val bodyPart    = new jakarta.mail.internet.MimeBodyPart()
        bodyPart.setText(text)
        multipart.addBodyPart(bodyPart)

        val attachPart = new jakarta.mail.internet.MimeBodyPart()
        attachPart.setDataHandler(
          new jakarta.activation.DataHandler(new ByteArrayDataSource(attachBytes, "application/octet-stream"))
        )
        attachPart.setFileName(attachName)
        attachPart.setDisposition(jakarta.mail.Part.ATTACHMENT)
        multipart.addBodyPart(attachPart)

        msg.setContent(multipart)
        greenMail.getUserManager().createUser(config.user, config.user, config.password)
        GreenMailUtil.sendMimeMessage(msg)
      } >>
        ImapClient
          .javaMail[IO]
          .connect(config)
          .use { session =>
            session.open(config.folder).use { folder =>
              folder.messages
                .evalTap(_.drainAll)
                .take(1)
                .compile
                .toList
            }
          }
          .flatMap { mails =>
            IO {
              assertEquals(mails.size, 1)
              val mail = mails.head
              assertEquals(mail.envelope.subject, Some("fs2-imap test"))
              assertEquals(mail.envelope.to, List(config.user))
              assert(mail.textBody.isDefined || mail.htmlBody.isDefined)
              assertEquals(mail.attachments.size, 1)
              val att = mail.attachments.head
              assertEquals(att.filename, Some(attachName))
            }
          }
    }
  }

  test("attachment stream yields expected bytes") {
    withGreenMail { (greenMail, config) =>
      val attachBytes = Array[Byte](10, 20, 30)
      IO {
        greenMail.getUserManager().createUser(config.user, config.user, config.password)
        val msg = new MimeMessage(greenMail.getSmtp().createSession())
        msg.setFrom("from@localhost")
        msg.addRecipient(
          jakarta.mail.Message.RecipientType.TO,
          new jakarta.mail.internet.InternetAddress(config.user)
        )
        msg.setSubject("bytes test")

        val attachPart = new jakarta.mail.internet.MimeBodyPart()
        attachPart.setDataHandler(
          new jakarta.activation.DataHandler(new ByteArrayDataSource(attachBytes, "application/octet-stream"))
        )
        attachPart.setFileName("payload.bin")
        attachPart.setDisposition(jakarta.mail.Part.ATTACHMENT)

        val multipart = new jakarta.mail.internet.MimeMultipart()
        val textPart  = new jakarta.mail.internet.MimeBodyPart()
        textPart.setText("body")
        multipart.addBodyPart(textPart)
        multipart.addBodyPart(attachPart)
        msg.setContent(multipart)
        GreenMailUtil.sendMimeMessage(msg)
      } >>
        ImapClient
          .javaMail[IO]
          .connect(config)
          .use { session =>
            session.open(config.folder).use { folder =>
              folder.messages.take(1).evalMap { mail =>
                mail.attachments.head.content.compile.toVector
              }.compile.toList
            }
          }
          .map { vectors =>
            assertEquals(vectors.head.toSeq, attachBytes.toSeq)
          }
    }
  }
