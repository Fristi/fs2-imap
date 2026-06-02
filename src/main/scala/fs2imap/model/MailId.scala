package fs2imap.model

object MailId:
  opaque type MessageNumber = Int
  object MessageNumber:
    def apply(value: Int): MessageNumber = value
    extension (n: MessageNumber) def value: Int = n

  opaque type Uid = Long
  object Uid:
    def apply(value: Long): Uid = value
    extension (u: Uid) def value: Long = u
