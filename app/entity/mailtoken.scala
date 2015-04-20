package entity

import java.util.UUID

import org.joda.time.DateTime
import scalikejdbc._

/**
 * Created by tomohiro_urakawa on 15/04/12.
 */
sealed trait MailTokenEntity[I] extends Entity[Identifier[I], SimpleStatus]
case class MailToken(
                    id: Identifier[UUID],
                    email: String,
                    createdAt: DateTime,
                    expiresAt: DateTime,
                    isSignup: Boolean,
                    status: SimpleStatus)
  extends MailTokenEntity[UUID] with ResourceEntity

object MailToken extends SQLSyntaxSupport[MailToken] with IdentifierTypeBinderSupport
  with SimpleStatusTypeBinderSupport {
  override val tableName = "mail_tokens"

  def apply(s: SyntaxProvider[MailToken])(rs: WrappedResultSet): MailToken = apply(s.resultName)(rs)
  def apply(r: ResultName[MailToken])(rs: WrappedResultSet): MailToken = MailToken(
    id = rs.get(r.id),
    email = rs.get(r.email),
    createdAt = rs.get(r.createdAt),
    expiresAt = rs.get(r.expiresAt),
    isSignup = rs.get(r.isSignup),
    status = rs.get(r.status)
  )

  def opt(s: SyntaxProvider[MailToken])(rs: WrappedResultSet): Option[MailToken] =
    rs.anyOpt(s.resultName.id).map(_ => MailToken(s)(rs))
}

case class MailTokenEvent(
                         id: Identifier[Long],
                         mailTokenId: Identifier[UUID],
                         email: String,
                         createdAt: DateTime,
                         expiresAt: DateTime,
                         isSignup: Boolean,
                         status: SimpleStatus)
  extends MailTokenEntity[Long] with EventEntity

object MailTokenEvent extends SQLSyntaxSupport[MailTokenEvent] with IdentifierTypeBinderSupport
  with SimpleStatusTypeBinderSupport {
  override val tableName = "mail_token_events"

  def apply(s: SyntaxProvider[MailTokenEvent])(rs: WrappedResultSet): MailTokenEvent = apply(s.resultName)(rs)
  def apply(r: ResultName[MailTokenEvent])(rs: WrappedResultSet): MailTokenEvent = MailTokenEvent(
    id = rs.get(r.id),
    mailTokenId = rs.get(r.mailTokenId),
    email = rs.get(r.email),
    createdAt = rs.get(r.createdAt),
    expiresAt = rs.get(r.expiresAt),
    isSignup = rs.get(r.isSignup),
    status = rs.get(r.status)
  )

  def opt(s: SyntaxProvider[MailTokenEvent])(rs: WrappedResultSet): Option[MailTokenEvent] =
    rs.anyOpt(s.resultName.id).map(_ => MailTokenEvent(s)(rs))
}
