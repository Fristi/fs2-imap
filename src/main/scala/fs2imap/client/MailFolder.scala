package fs2imap.client

import fs2.Stream
import fs2imap.model.Mail

/** Folder access for streaming mail.
  *
  * '''Lifecycle:''' Each [[Mail]]'s body and attachment streams must be fully
  * consumed before pulling the next element from [[messages]]. The underlying
  * JavaMail folder stays open for the lifetime of the stream.
  */
trait MailFolder[F[_]]:
  def messages: Stream[F, Mail[F]]
  def messages(range: (Long, Long)): Stream[F, Mail[F]]
