package repository

import java.util.UUID

import entity._
import scalikejdbc._

import scala.util.Try

/**
 * Created by tomohiro_urakawa on 15/04/12.
 */
trait MailTokenResourceRepository extends SimpleResourceRepository[Identifier[UUID], MailToken] {

  override val defaultPagingClause: Syntax = m =>
    SQLSyntax.orderBy(m.createdAt.desc, m.expiresAt.desc).limit(1000).offset(0)

  override def findAll(whereClause: SyntaxOpt)(pagingClause: Syntax)(implicit dbSession: DBSession): Seq[MailToken] = {
    val m = MailToken.syntax
    withSQL {
      select.from(MailToken as m)
        .where(whereClause(m)).append(pagingClause(m))
    }.map(MailToken(m)).list.apply
  }
}
class MailTokenResourceRepositoryImpl extends MailTokenResourceRepository

trait MailTokenEventRepository extends SimpleEventRepository[MailTokenEvent] {
  override def doPut(event: MailTokenEvent)(implicit dbSession: DBSession): MailTokenEvent = {
    val registered = withSQL {
      val c = MailTokenEvent.column
      insert into MailToken namedValues(
        c.mailTokenId -> event.mailTokenId.get,
        c.email -> event.email,
        c.createdAt -> event.createdAt,
        c.expiresAt -> event.expiresAt,
        c.isSignup -> event.isSignup,
        c.status -> event.status.label
        )
    }.updateAndReturnGeneratedKey.apply

    event.copy(id = ID(registered))
  }
}
class MailTokenEventRepositoryImpl extends MailTokenEventRepository
