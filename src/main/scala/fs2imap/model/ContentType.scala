package fs2imap.model

final case class ContentType(primary: String, sub: String):
  def mimeType: String = s"$primary/$sub"

  override def toString: String = mimeType

object ContentType:
  def parse(raw: String): ContentType =
    val base = raw.split(';').headOption.getOrElse(raw).trim.toLowerCase
    val slash = base.indexOf('/')
    if slash < 0 then ContentType(base, "*")
    else ContentType(base.take(slash), base.drop(slash + 1))

  val TextPlain: ContentType = ContentType("text", "plain")
  val TextHtml: ContentType  = ContentType("text", "html")

enum ContentDisposition:
  case Inline, Attachment
