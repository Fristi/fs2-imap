package fs2imap.model

import java.time.Instant

import MailId.MessageNumber

final case class MailEnvelope(
    messageNumber: MessageNumber,
    from: List[String],
    to: List[String],
    subject: Option[String],
    sentAt: Option[Instant],
    messageId: Option[String]
)
