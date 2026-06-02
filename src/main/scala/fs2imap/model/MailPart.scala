package fs2imap.model

import fs2.Stream

final case class MailPart[F[_]](
    contentType: ContentType,
    charset: Option[String],
    content: Stream[F, Byte]
)
