package entity

import java.sql.ResultSet
import java.util.UUID

import org.joda.time.DateTime
import scalikejdbc._

/**
 * ユーザEntity
 * @param id ID値
 * @param name 名前
 * @param email メールアドレス
 * @param password パスワード
 * @param role ロール
 * @param registeredAt 登録日時
 * @param status 状態
 */
case class User(
               id: Identifier[UUID],
               name: String,
               email: String,
               password: String,
               role: Role,
               registeredAt: DateTime,
               status: UserStatus) extends Entity[Identifier[UUID], UserStatus]

/**
 * ユーザEntityのコンパニオンオブジェクト
 */
object User extends SQLSyntaxSupport[User] with IdentifierTypeBinderSupport
  with RoleTypeBinderSupport with UserStatusTypeBinderSupport {
  override val tableName = "users"

  def apply(s: SyntaxProvider[User])(rs: WrappedResultSet): User = apply(s.resultName)(rs)
  def apply(r: ResultName[User])(rs: WrappedResultSet): User = User(
    id = rs.get(r.id),
    name = rs.get(r.name),
    email = rs.get(r.email),
    password = rs.get(r.password),
    role = rs.get(r.role),
    registeredAt = rs.get(r.registeredAt),
    status = rs.get(r.status)
  )

  def opt(s: SyntaxProvider[User])(rs: WrappedResultSet): Option[User] =
    rs.anyOpt(s.resultName.id).map(_ => User(s)(rs))
}

/**
 * ユーザの状態
 *
 * @param label 状態を表す文字列
 * @param isDiscarded 退会済み
 */
abstract sealed class UserStatus(val label: String, val isDiscarded: Boolean = false) extends Status {
  override def toString = label
}

/**
 * ユーザの状態
 */
object UserStatus {
  case object SigningUp extends UserStatus("signing_up")
  case object Running extends UserStatus("running")
  case object Cancelled extends UserStatus("cancelled", true)

  def apply(label: String) = label match {
    case SigningUp.label => SigningUp
    case Running.label => Running
    case Cancelled.label => Cancelled
    case _ => throw new NoSuchElementException(s"Illegal label: $label")
  }

  def * = Set(SigningUp, Running, Cancelled)
  def unapply(o: UserStatus) = Option((o.label, o.isDiscarded))
}

/**
 * テーブル上のユーザステータスとの変換
 */
trait UserStatusTypeBinderSupport {
  implicit val statusTypeBinder: TypeBinder[UserStatus] = new TypeBinder[UserStatus] {
    override def apply(rs: ResultSet, columnIndex: Int): UserStatus = UserStatus(rs.getString(columnIndex))
    override def apply(rs: ResultSet, columnLabel: String): UserStatus = UserStatus(rs.getString(columnLabel))
  }
}

/**
 * ユーザのロール
 * @param label ロールを示す文字列
 */
abstract sealed class Role(val label: String) {
  override def toString = label
}

/**
 * ユーザのロール
 */
object Role {
  case object Administrator extends Role("administrator")
  case object Operator extends Role("operator")
  case object Handyman extends Role("handyman")

  def apply(label: String) = label match {
    case Administrator.label => Administrator
    case Operator.label => Operator
    case Handyman.label => Handyman
    case _ => throw new NoSuchElementException(s"Illegal label: $label")
  }
}

/**
 * テーブル上のロールとの変換
 */
trait RoleTypeBinderSupport {
  implicit val roleTypeBinder: TypeBinder[Role] = new TypeBinder[Role] {
    override def apply(rs: ResultSet, columnIndex: Int): Role = Role(rs.getString(columnIndex))
    override def apply(rs: ResultSet, columnLabel: String): Role = Role(rs.getString(columnLabel))
  }
}
