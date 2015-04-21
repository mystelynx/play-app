package repository

import entity._
import scalikejdbc._

import scala.util.{Failure, Try}

/**
 * Created by tomohiro_urakawa on 15/04/11.
 */
trait EventRepository[E <: Entity[Identifier[_], _ <: Status] with EventEntity] {

  final def put(event: E)(implicit dbSession: DBSession): Try[E] =
    Try(doPut(event)(dbSession).ensuring(_.id.isDefined))

  protected def doPut(event: E)(implicit dbSession: DBSession): E
}

abstract class SimpleEventRepository[E <: Entity[Identifier[Long], _ <: Status] with EventEntity]
  extends EventRepository[E] {
}

trait ResourceRepository[I <: Identifier[_], E <: Entity[I, _ <: Status] with ResourceEntity] {

  type Syntax = QuerySQLSyntaxProvider[SQLSyntaxSupport[E], E] => SQLSyntax
  type SyntaxOpt = QuerySQLSyntaxProvider[SQLSyntaxSupport[E], E] => Option[SQLSyntax]

  val defaultPagingClause: Syntax

  def findBy(id: I)(implicit dbSession: DBSession): Option[E] =
    findAll(e => Some(SQLSyntax.eq(e.id, id.get)))()(dbSession).ensuring(_.size <= 1).headOption
  def findAll(whereClause: SyntaxOpt)
             (pagingClause: Syntax = defaultPagingClause)
             (implicit dbSession: DBSession): Seq[E]
}

abstract class SimpleResourceRepository[I <: Identifier[_],
  E <: Entity[I, _ <: Status] with ResourceEntity] extends ResourceRepository[I, E] {

  def findAllMatching(whereClause: Syntax)
                     (pagingClause: Syntax = defaultPagingClause)
                     (implicit dBSession: DBSession = AutoSession) =
    findAll(e => Some(whereClause(e)))(pagingClause)(dBSession)

  def findAllMatchingOpt(whereClause: SyntaxOpt)
                     (pagingClause: Syntax = defaultPagingClause)
                     (implicit dbSession: DBSession = AutoSession) =
    findAll(whereClause)(pagingClause)(dbSession)
}

trait ResourceRepository2[I <: Identifier[_], E <: Entity[I, _ <: Status] with ResourceEntity,
  F1 <: Entity[_ <: Identifier[_], _ <: Status]] {

  type Syntax = (QuerySQLSyntaxProvider[SQLSyntaxSupport[E], E],
    QuerySQLSyntaxProvider[SQLSyntaxSupport[F1], F1]) => SQLSyntax

  val defaultPagingClause: Syntax

  def findBy(id: I)(implicit dbSession: DBSession): Option[E] =
    findAll(Some((e, f1) => SQLSyntax.eq(e.id, id.get)))()(dbSession).ensuring(_.size <= 1).headOption
  def findAll(whereClause: Option[Syntax])
             (pagingClause: Syntax = defaultPagingClause)
             (implicit dbSession: DBSession = AutoSession): Seq[E]
}

abstract class SimpleResourceRepository2[I <: Identifier[_], E<: Entity[I, _ <: Status] with ResourceEntity,
  F1 <: Entity[_ <: Identifier[_], _ <: Status]] extends ResourceRepository2[I, E, F1] {

  def findAllMatching(whereClause: Syntax)
                     (pagingClause: Syntax = defaultPagingClause)
                     (implicit dbSession: DBSession = AutoSession) =
    findAll(Some(whereClause))(pagingClause)(dbSession)
}
