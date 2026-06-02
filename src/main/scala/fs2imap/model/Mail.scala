package fs2imap.model

final case class Mail[F[_]](
    envelope: MailEnvelope,
    textBody: Option[MailPart[F]],
    htmlBody: Option[MailPart[F]],
    attachments: List[Attachment[F]]
)
