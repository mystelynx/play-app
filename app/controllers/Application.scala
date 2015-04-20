package controllers

import java.util.UUID

import entity.ID
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Invalid, Valid, Constraint, ValidationError}
import play.api.i18n.Messages
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc._
import repository.{MailTokenResourceRepository, MailTokenEventRepository}
import scaldi.{Injectable, Injector}
import scalikejdbc._
import securesocial.controllers.BaseRegistration._
import securesocial.controllers.{RegistrationInfo, BaseRegistration}
import securesocial.core.java.SecuredAction
import securesocial.core.providers.{UsernamePasswordProvider, MailToken}
import securesocial.core.providers.utils.{PasswordValidator, PasswordHasher}
import securesocial.core.services._
import securesocial.core.services.SaveMode._
import securesocial.core._
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * SecureSocialを利用したコントローラ
 *
 */
abstract class SecuredController extends SecureSocial[model.User] {

  case class ErrorHandling[A](action: Action[A])
                             (recovering: PartialFunction[Throwable, Result]) extends Action[A] {
    def apply(request: Request[A]): Future[Result] = action(request) recover {
      recovering
    }

    lazy val parser = action.parser
  }

  /**
   * SecuredAction実行時に自動的にトランザクションを生成するアクション
   *
   * @param f
   * @return
   */
  def RecoverableTxSecuredAction[A](bp: BodyParser[A])
                                   (f: SecuredRequest[A] => model.User => DBSession => Result)
                                   (recovering: PartialFunction[Throwable, Result] = Map.empty) = ErrorHandling {
    SecuredAction(bp) { request =>
      Logger.debug(s"calling by ${request.authenticator}")
      DB localTx { session =>
        val user: model.User = model.User("foo")
        f(request)(user)(session)
      }
    }
  } {
    recovering orElse {
      case ex: Exception => Logger.warn(">_<"); InternalServerError("oops")
    }
  }

  def tryJsonParser[J](implicit reads: Reads[J]) = BodyParsers.parse.tolerantText.map { txt =>
    Try(Json.parse(txt).validate[J]) match {
      case Success(JsSuccess(value, path)) => RequestParseResult[J](Some(value))
      case Success(JsError(errors)) => RequestParseResult[J](None, errors.map { case (jp, errs) => (jp.toString -> errs) })
      case Failure(y) => RequestParseResult[J](None, Seq("" -> Seq(ValidationError("not.json"))))
    }
  }

}

case class RequestParseResult[+R](obj: Option[R] = None, errors: Seq[(String, Seq[ValidationError])] = Nil) {
  def isError = errors != Nil
}
case class AccountUpdateRequest(name: Option[String], age: Int)

class ApplicationRuntimeEnvironment extends RuntimeEnvironment.Default[model.User] {
  protected override def include(p: IdentityProvider) = p.id ->   p

  override lazy val userService: UserService[model.User] = new MyUserService
  override lazy val providers = ListMap(
    include(new UsernamePasswordProvider[model.User](
      userService, None, viewTemplates, passwordHashers))
  )

  //  override lazy val viewTemplates
  //  = new plugins.CustomTemplates(this) /// <====追加
}

/**
 * サンプルApplication
 * SecuredControllerのサブクラスとすることで`TxSecuredAction`が利用可能となる
 *
 */
class ApplicationImpl(implicit inj: Injector) extends Application with Injectable {

  implicit val env = inject[RuntimeEnvironment[model.User]]

  val mailTokenResourceRepository = inject[MailTokenResourceRepository]

}
trait Application extends SecuredController {

  def mailTokenResourceRepository: MailTokenResourceRepository

  implicit val ar: Reads[AccountUpdateRequest] = (
    (__ \ "name").readNullable[String] and
      (__ \ "age").read[Int]
    )(AccountUpdateRequest)

  def sample = RecoverableTxSecuredAction(parse.empty) {
    request => user => implicit session =>

    println(request.user)
    println(request.authenticator)

      mailTokenResourceRepository.findBy(ID(UUID.randomUUID()))

    sql"select * from users".map(_.toMap).list.apply
    sql"delete from users".update.apply

    Ok(views.html.index("Your new application is ready!!!!"))
  } {
    case ex: IllegalStateException => Logger.warn(s"$ex"); BadRequest("(-__-)")
  }


  def elpmas = RecoverableTxSecuredAction(tryJsonParser[AccountUpdateRequest]) {
    request => user => implicit session =>

      println(request.user)
      println(request.authenticator)
      println(request.body)

      if (request.body.isError) throw new IllegalArgumentException(s"${request.body.errors}")

      sql"select * from users".map(_.toMap).list.apply
      sql"delete from users".update.apply

      Ok(views.html.index("Your new application is ready!!!!"))
  } {
    // bodyParser内で発生した例外については相変わらず取れない、、、
    // あくまでAction内で発生した例外のみだが、処理すべき例外を列挙できる
    //TODO errorオブジェクトが決まった形で渡ってくるなら共通化できる
    case ex: IllegalArgumentException => Logger.warn(s"$ex"); Conflict("hoge")
  }
}

class RegistrationImpl(implicit inj: Injector) extends BaseRegistration[model.User] with Injectable {
  implicit val env = inject[RuntimeEnvironment[model.User]]

  // 諸事情によりオーバーライド
  override val form = Form[RegistrationInfo](
    mapping(
      FirstName -> nonEmptyText,
      LastName -> nonEmptyText,
      Password ->
        tuple(
          Password1 -> nonEmptyText.verifying(Constraint[String] {
                // PasswordValidator.constraint(implicit env: RuntimeEnvironment[_])
                // がstaticメソッドであるためどうにもならない。
                // そのため、injectされたenvを使用するためにオーバーライドしています。
            s: String =>
              env.passwordValidator.validate(s) match {
                case Right(_) => Valid
                case Left(error) => Invalid(error._1, error._2: _*)
              }
          }),
          Password2 -> nonEmptyText
        ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
    ) // binding
      ((firstName, lastName, password) => RegistrationInfo(None, firstName, lastName, password._1)) // unbinding
      (info => Some((info.firstName, info.lastName, ("", ""))))
  )
}

@deprecated
class MyUserService extends UserService[model.User] {

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

  override def link(current: model.User, to: BasicProfile): Future[model.User] = ???

  override def passwordInfoFor(user: model.User): Future[Option[PasswordInfo]] = ???

  override def save(profile: BasicProfile, mode: SaveMode): Future[model.User] = Future {

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

          ???
        }
        case (profile, LoggedIn) => {
          // たぶんログイン日時等を記録するためにログイン時にsaveが呼ばれる
          // いまは未実装

          val Some(accountId) =
            sql"""
                  select id from accounts where email = ${profile.userId}
            """.map(rs => UUID.fromString(rs.string("id"))).single.apply

          ???
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

  override def updatePasswordInfo(user: model.User, info: PasswordInfo): Future[Option[BasicProfile]] = ???

  override def saveToken(token: MailToken): Future[MailToken] = Future {

    DB localTx { implicit session =>
      sql"""
            insert into account_updates()
            values(${token.uuid}, ${token.email}, ${token.creationTime},
            ${token.expirationTime}, ${token.isSignUp})
         """.update.apply
    }

    ???
  }
}

