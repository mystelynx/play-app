package entity

import java.sql.ResultSet
import java.util.UUID

import scalikejdbc.TypeBinder

/**
 * Entityの定義
 *
 * @tparam I ID
 * @tparam S ステータス
 */
abstract class Entity[+I <: Identifier[_], +S <: Status] {
  val id: I
  val status: S

  override def equals(any: Any) = any match {
    case that: Entity[I, S] => this.id == that.id
    case _ => false
  }
  override def hashCode = 31 * id.##
}

/**
 * ステータスの定義
 */
trait Status {
  /** ステータスを表現するラベル */
  val label: String
  /** 当該エンティティがリソースとして不要であることを示すフラグ */
  val isDiscarded: Boolean
}

/**
 * 最も簡単なステータス
 *
 * @param label 有効であれば enabled
 *              無効であれば disabled
 * @param isDiscarded 不要であれば true, それ以外では false
 */
sealed abstract class SimpleStatus(val label: String, val isDiscarded: Boolean = false) extends Status {
  override def toString = label
}

/**
 * 最も簡単なステータス
 */
object SimpleStatus {
  case object Enabled extends SimpleStatus("enabled")
  case object Disabled extends SimpleStatus("disabled", true)
  def apply(label: String) = label match {
    case Enabled.label => Enabled
    case Disabled.label => Disabled
    case _ => throw new NoSuchElementException(s"Illegal label: $label")
  }

  def * = Set(Enabled, Disabled) //TODO macros
  def unapply(o: SimpleStatus) = Option((o.label, o.isDiscarded))
}

/**
 * テーブル上のステータスとの変換
 */
trait SimpleStatusTypeBinderSupport {
  implicit val statusTypeBinder: TypeBinder[SimpleStatus] = new TypeBinder[SimpleStatus] {
    override def apply(rs: ResultSet, columnIndex: Int): SimpleStatus = SimpleStatus(rs.getString(columnIndex))
    override def apply(rs: ResultSet, columnLabel: String): SimpleStatus = SimpleStatus(rs.getString(columnLabel))
  }
}

/**
 * 識別子の定義
 * @tparam I 内部表現
 */
sealed abstract class Identifier[+I] {
  def isEmpty: Boolean
  def isDefined: Boolean = !isEmpty
  def get: I
}

/**
 * 識別子
 *
 * @param x 具体的な値
 * @tparam I 内部表現
 */
final case class ID[+I](x: I) extends Identifier[I] {
  override def isEmpty: Boolean = false
  override def get: I = x
}

/**
 * 識別子のコンパニオンオブジェクト
 */
object ID {
  def apply(uuid: UUID): Identifier[UUID] = new ID(uuid)
  def apply(number: Long): Identifier[Long] = new ID(number)
}

/**
 * 無効な識別子
 */
case object EmptyID extends Identifier[Nothing] {
  override def isEmpty = true
  override def get = throw new NoSuchElementException
}

/**
 * テーブル上のIDとの変換
 */
trait IdentifierTypeBinderSupport {

  /**
   * IDがUUIDの場合における変換
   */
  implicit val uuidIdentifierTypeBinder: TypeBinder[Identifier[UUID]] =
    new TypeBinder[Identifier[UUID]] {
      override def apply(rs: ResultSet, columnIndex: Int): Identifier[UUID] =
        if (rs.getObject(columnIndex) == null)
          EmptyID
        else
          ID(UUID.fromString(rs.getString(columnIndex)))
      override def apply(rs: ResultSet, columnLabel: String): Identifier[UUID] =
        if (rs.getObject(columnLabel) == null)
          EmptyID
        else
          ID(UUID.fromString(rs.getString(columnLabel)))
  }

  /**
   * IDがNUMBERの場合における変換
   */
  implicit val numberIdentifierTypeBinder: TypeBinder[Identifier[Long]] =
    new TypeBinder[Identifier[Long]] {
      override def apply(rs: ResultSet, columnIndex: Int): Identifier[Long] =
        if (rs.getObject(columnIndex) == null)
          EmptyID
        else
          ID(rs.getLong(columnIndex))
      override def apply(rs: ResultSet, columnLabel: String): Identifier[Long] =
        if (rs.getObject(columnLabel) == null)
          EmptyID
        else
          ID(rs.getLong(columnLabel))
    }
}
