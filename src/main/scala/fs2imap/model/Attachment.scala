package fs2imap.model

import fs2.Stream

final case class Attachment[F[_]](
    filename: Option[String],
    contentType: ContentType,
    disposition: ContentDisposition,
    content: Stream[F, Byte]
)
