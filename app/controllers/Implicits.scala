package controllers

import play.api.mvc.QueryStringBindable
import scalikejdbc._
import scala.util.Try

/**
 * Created by tomohiro_urakawa on 15/04/23.
 */
object Implicits {

  implicit def queryStringAccountListCriteriaBinder(implicit intBinder: QueryStringBindable[Int]) =
    new QueryStringBindable[AccountListCriteria] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, AccountListCriteria]] = {

      import AccountListCriteria._

      val limit = Try(params("per_page").headOption.map(_.toInt).filter(_ <= MaxPageSize).get).getOrElse(DefaultPageSize)
      val offset = Try(params("page").headOption.map(_.toInt - 1).map(_ * limit).get).getOrElse(DefaultOffset)

      val sort = Try(params("sort").headOption.map(c => sortColumns(c)).get).getOrElse(DefaultSortColumn)
      val direction = Try(params("direction").headOption.filter(_ == "desc").map(_ => SQLSyntax.desc).get).getOrElse(DefaultDirection)
      val pagingClause = SQLSyntax.orderBy(sort).append(direction).limit(limit).offset(offset)

      val whereClause: SQLSyntax = params.map(m => (m._1, m._2.headOption))
        .filterKeys(k => whereColumns.keySet.contains(k)).map(k => (whereColumns(k._1), k._2))
        .toList.headOption.map(m => SQLSyntax.like(m._1, m._2.getOrElse("good"))).get

      Some(Right(AccountListCriteria(whereClause, pagingClause)))
    }

    override def unbind(key: String, value: AccountListCriteria): String = ???
  }
}

case class AccountListCriteria(whereClause: SQLSyntax, pagingClause: SQLSyntax)
object AccountListCriteria {
  val DefaultPageSize = 100
  val MaxPageSize = 1000

  val DefaultOffset = 0

  val DefaultSortColumn = entity.MailToken.column.createdAt
  val sortColumns = Map(
    "name" -> entity.MailToken.column.id
  )

  val DefaultDirection = SQLSyntax.desc

  val whereColumns = Map(
    "name" -> entity.MailToken.column.id,
    "agent" -> entity.MailToken.column.email,
    "status" -> entity.MailToken.column.status
  )
}
