package repository

import java.util.UUID

import entity._
import scalikejdbc._

import scala.util.Try

/**
 * Created by tomohiro_urakawa on 15/04/12.
 */
class MailTokenResourceRepository extends SimpleResourceRepository[Identifier[UUID], MailToken] {

  override val defaultPagingClause: Syntax = m =>
    SQLSyntax.orderBy(m.createdAt.desc, m.expiresAt.desc).limit(1000).offset(0)

  override def findAll(whereClause: Option[Syntax])(pagingClause: Syntax)(implicit dbSession: DBSession): Seq[MailToken] = {
    val m = MailToken.syntax
    withSQL {
      select.from(MailToken as m)
        .where(whereClause.map(_.apply(m))).append(pagingClause(m))
    }.map(MailToken(m)).list.apply
  }
}

class MailTokenEventRepository extends SimpleEventRepository[MailTokenEvent] {
  override def put(event: MailTokenEvent)(implicit dbSession: DBSession): Try[MailTokenEvent] = Try {
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