package controllers

import java.util.UUID

import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc._
import play.api.mvc.Security._
import scalikejdbc._
import securesocial.core.providers.MailToken
import securesocial.core.providers.utils.PasswordHasher
import securesocial.core.services._
import securesocial.core.services.SaveMode._
import securesocial.core._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class Application(override implicit val env: RuntimeEnvironment[User]) extends SecureSocial[User] {

//  def index = AuthenticatedAction { credentials => permission => implicit request =>

  def index = SecuredAction {
    Ok(views.html.index("Your new application is ready."))
  }

}

case class User(id: UUID, profile: BasicProfile)
class MyUserService extends UserService[User] {

  // ログイン時に呼ばれる
  override def find(providerId: String, userId: String): Future[Option[BasicProfile]] =
  // いまのところUsernamePasswordによる認証しかしてないのでproviderIdは無視
    Future.successful {
      val u = DB readOnly { implicit session =>
        sql"""
              select id, name, email, password, status from accounts
              where email = $userId
          """
          .map(rs => BasicProfile(
          providerId, rs.string("email"), None, None,
          rs.stringOpt("name"), rs.stringOpt("email"), None, AuthenticationMethod.UserPassword,
          None, None, Some(PasswordInfo(PasswordHasher.id, rs.string("password"), None)))
          ).single.apply
      }
      Logger.debug(s"userId=$userId found=$u")

      u
    }

  // サインアップページでメールアドレスを送信したときに呼ばれる
  // まだアカウントがない場合(None)はサインアップメールが送信される
  // すでにアカウントが存在する場合(Some(BasicProfile))はパスワードリセットメールが送信される
  override def findByEmailAndProvider(email: String, providerId: String): Future[Option[BasicProfile]] =
    Future.successful {
      DB readOnly { implicit session => // move to repository
        sql"""select id, name, email, password, status from accounts where email = $email"""
          .map(rs => BasicProfile(
            providerId, email, None, None,
            rs.stringOpt("name"), rs.stringOpt("email"), None, AuthenticationMethod.UserPassword,
          None, None, Some(PasswordInfo("bcrypt", rs.string("password"), None)))
          ).single.apply
      }
    }

  override def deleteToken(uuid: String): Future[Option[MailToken]] =
    Future.successful(None) // save()で保存済みであるためこの処理では何もしない

  override def link(current: User, to: BasicProfile): Future[User] = ???

  override def passwordInfoFor(user: User): Future[Option[PasswordInfo]] = ???

  override def save(profile: BasicProfile, mode: SaveMode): Future[User] = Future.successful {

    DB localTx { implicit session => //TODO move to repository
      (profile, mode) match {
        case (_, SignUp) => {

          val Some(accountId) =
            sql"""
                  select id from accounts where email = ${profile.userId}
            """.map(rs => UUID.fromString(rs.string("id"))).single.apply

          sql"""
              insert into account_updates (account_id, name, email, password, status)
                values(${accountId}, ${profile.fullName}, ${profile.email},
                ${profile.passwordInfo.map(_.password)}, ${"running"})
          """.update.apply

          User(accountId, profile)
        }
        case (profile, LoggedIn) => {
          // たぶんログイン日時等を記録するためにログイン時にsaveが呼ばれる
          // いまは未実装

          val Some(accountId) =
            sql"""
                  select id from accounts where email = ${profile.userId}
            """.map(rs => UUID.fromString(rs.string("id"))).single.apply

          User(accountId, profile)
        }
        case (profile, PasswordChange) => ???
      }
    }
  }

  // トークン指定でサインアップページが表示された場合に呼び出される
  override def findToken(token: String): Future[Option[MailToken]] = Future.successful {
    DB readOnly { implicit session => //move to repository
      // 指定されたトークンに対応するアカウントの情報を取得して返す
      sql"""
            select id, name, email, password, status, registered_at from accounts
            where password = $token
         """.map(rs => MailToken(token, rs.string("email"),
        rs.jodaDateTime("registered_at"), rs.jodaDateTime("registered_at").plusWeeks(1),
        rs.string("status") == "temporary")).single.apply
    }
  }

  override def deleteExpiredTokens(): Unit = ???

  override def updatePasswordInfo(user: User, info: PasswordInfo): Future[Option[BasicProfile]] = ???

  override def saveToken(token: MailToken): Future[MailToken] = {
//    Logger.debug(s"!saveToken=$token")
//
//    DB localTx { implicit session =>
//      sql"""
//            insert into account_updates()
//            values(${token.uuid}, ${token.email}, ${token.creationTime},
//            ${token.expirationTime}, ${token.isSignUp})
//         """.update.apply
//    }
//
//    Future.successful(token)
    ???
  }
}