package fs2imap.config

final case class ImapConfig(
    host: String,
    port: Int,
    user: String,
    password: String,
    ssl: Boolean = true,
    folder: String = "INBOX"
)
