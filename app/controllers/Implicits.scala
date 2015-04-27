package controllers

import entity.MailToken
import play.api.mvc.QueryStringBindable
import scalikejdbc._
import scala.util.Try

/**
 * Created by tomohiro_urakawa on 15/04/23.
 */
object Implicits {

  case class AccountListApiCriteria(whereClause: Option[SQLSyntax], pagingClause: SQLSyntax)

  implicit def accountListApiCriteriaQueryStringBindable = new ListApiCriteriaQueryStringBindable[AccountListApiCriteria] {
    override val defaultPageSize = 100
    override val maxPageSize = 1000

    override val defaultSortColumn = MailToken.column.createdAt
    override val sortColumnTransformer: PartialFunction[String, SQLSyntax] = {
      case "name" => MailToken.column.id
    }

    override val whereColumnTransformer: PartialFunction[(String, Option[String]), SQLSyntax] = {
      case ("name", Some(v)) => sqls"${MailToken.column.id} like ${s"%$v%"}"
      case ("agent", Some(v)) => sqls"${MailToken.column.email} like ${s"%$v%"}"
      case ("status", Some(v)) => sqls"${MailToken.column.status} = $v"
    }

    override val apply = AccountListApiCriteria
  }

  trait ListApiCriteriaQueryStringBindable[T] extends QueryStringBindable[T] {

    /** デフォルトで使用される１ページの件数 */
    def defaultPageSize: Int
    /** １ページ内の最大件数*/
    def maxPageSize: Int

    /** ページ指定が省略された時は先頭からとする */
    final val defaultOffset: Int = 0

    /** デフォルトで使用されるソートカラム */
    def defaultSortColumn: SQLSyntax
    /** パラメータのソートカラム名とSQLSyntaxの変換定義 */
    def sortColumnTransformer: PartialFunction[String, SQLSyntax]

    /** デフォルトで使用されるソート方向 */
    def defaultDirection = SQLSyntax.desc
    /** パラメータのソート方向とSQLSyntaxの変換定義 */
    final val directionTransformer: PartialFunction[String, SQLSyntax] = {
      case "desc" => SQLSyntax.desc
      case "asc" => SQLSyntax.asc
    }

    /** パラメータの抽出条件とSQLSyntaxの変換定義 */
    def whereColumnTransformer: PartialFunction[(String, Option[String]), SQLSyntax]

    /** リスト取得条件クラスの生成 */
    def apply: (Option[SQLSyntax], SQLSyntax) => T

    final override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, T]] = {

      // 同一のKeyに複数のValueがあっても最初のもの以外は無視する
      val param = params.map { case (k, v) => (k, v.headOption) }

      val limit = Try(param("per_page").map(_.toInt).filter(p => 0 <= p && p <= maxPageSize).get).getOrElse(defaultPageSize)
      val offset = Try(param("page").map(_.toInt).map(p => (p - 1) * limit).get).getOrElse(defaultOffset)

      val sort = Try(param("sort").collect(sortColumnTransformer).get).getOrElse(defaultSortColumn)
      val direction = Try(param("direction").collect(directionTransformer).get).getOrElse(defaultDirection)

      val pagingClause = sqls"order by $sort $direction limit $limit offset $offset"
      val whereClause = param.collect(whereColumnTransformer).reduceOption((x, y) => sqls"$x and $y")

      Option(Right(apply(whereClause, pagingClause)))
    }

    final override def unbind(key: String, value: T) = ???
  }
}
