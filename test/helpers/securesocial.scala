package helpers

import java.lang.reflect.Constructor

import com.icegreen.greenmail.util.{ServerSetupTest, GreenMail}
import config.Global
import model.User
import org.joda.time.DateTime
import org.specs2.execute.{Result => SpecResult, AsResult}
import play.api.mvc.{RequestHeader, Result => ApiResult}
import play.api.test.{FakeApplication, WithApplication}
import play.api.{Logger, GlobalSettings}
import repository.{MailTokenResourceRepositoryImpl, MailTokenResourceRepository}
import scaldi.Module
import scaldi.play.ControllerInjector
import securesocial.core.RuntimeEnvironment.Default
import securesocial.core.authenticator.{Authenticator, AuthenticatorBuilder, StoreBackedAuthenticator, AuthenticatorStore}
import securesocial.core.providers.utils.PasswordValidator
import securesocial.core.providers.{MailToken, UsernamePasswordProvider}
import securesocial.core.services.{RoutesService, SaveMode, AuthenticatorService, UserService}
import securesocial.core.{PasswordInfo, BasicProfile, IdentityProvider, RuntimeEnvironment}

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * 認証済みの状態でテスト用アプリを立ち上げる
 *
 * @param by 認証ユーザ
 * @param storedTokens 有効なメールトークン
 */
abstract class WithAuthenticatedApplication(
                                             val by: Option[model.User] = Some(model.User("fake-man")),
                                             val storedTokens: Set[String] = Set())
  extends WithApplication(app = FakeApplication(
    withGlobal = Some(new FakeGlobal(by, storedTokens)))) {

  lazy val greenMail = new GreenMail(ServerSetupTest.SMTP)

  override def around[T: AsResult](t: => T): SpecResult = super.around {
    try {
      greenMail.start
      t
    } finally {
      greenMail.stop
    }
  }
}

/**
 * 未認証の状態でテスト用アプリを立ち上げる
 *
 * @param storedTokens 有効なメールトークン
 */
abstract class WithUnauthenticatedApplication(
                                             override val storedTokens: Set[String] = Set()
                                               )
  extends WithAuthenticatedApplication(None, storedTokens)

class FakeGlobal(byUser: Option[model.User], storedTokens: Set[String]) extends Global {
  override def applicationModule = new ControllerInjector :: new Module {
    bind[RuntimeEnvironment[model.User]] to new Default[User] {
      protected override def include(p: IdentityProvider) = p.id -> p

      override lazy val userService: UserService[model.User] =
        new FakeUserService(storedTokens)
      override lazy val providers = ListMap(
        include(new UsernamePasswordProvider[model.User](
          userService, None, viewTemplates, passwordHashers))
      )

      override lazy val authenticatorService = new AuthenticatorService(
        new PassThroughAuthenticatorBuilder(byUser)
      )

      override lazy val routes = new RoutesService.Default {
        override def startSignUpUrl(implicit req: RequestHeader): String =
          absoluteUrl(controllers.routes.RegistrationImpl.startSignUp)
        override def handleStartSignUpUrl(implicit req: RequestHeader): String =
          absoluteUrl(controllers.routes.RegistrationImpl.handleStartSignUp)
        override def signUpUrl(mailToken: String)(implicit req: RequestHeader): String =
          absoluteUrl(controllers.routes.RegistrationImpl.signUp(mailToken))
        override def handleSignUpUrl(mailToken: String)(implicit req: RequestHeader): String =
          absoluteUrl(controllers.routes.RegistrationImpl.handleSignUp(mailToken))
      }
    }
  } :: new Module {
    bind[MailTokenResourceRepository] to new MailTokenResourceRepositoryImpl
  }
}

/**
 * テスト用ユーザサービス
 *
 * @param storedTokens 有効なメールトークン
 */
class FakeUserService(var storedTokens: Set[String]) extends UserService[model.User] {
  override def find(providerId: String, userId: String): Future[Option[BasicProfile]] = ???

  override def findByEmailAndProvider(email: String, providerId: String): Future[Option[BasicProfile]] =
    Future.successful(None)

  override def deleteToken(uuid: String): Future[Option[MailToken]] = Future.successful {
    None
  }

  override def link(current: User, to: BasicProfile): Future[User] = ???

  override def passwordInfoFor(user: User): Future[Option[PasswordInfo]] = ???

  override def save(profile: BasicProfile, mode: SaveMode): Future[model.User] = Future.successful {
    mode match {
      case SaveMode.SignUp => {
        //TODO save to db
        model.User(profile.fullName.get)
      }
      case _ => ???
    }
  }

  override def findToken(token: String): Future[Option[MailToken]] = Future.successful {
    storedTokens.find(_ == token).map { found =>
      MailToken(
        uuid = found,
        email = "test@urau.la",
        creationTime = DateTime.now.minusDays(3),
        expirationTime = DateTime.now.plusDays(3),
        isSignUp = true
      )
    }
  }

  override def deleteExpiredTokens(): Unit = ???

  override def updatePasswordInfo(user: User, info: PasswordInfo): Future[Option[BasicProfile]] = ???

  override def saveToken(token: MailToken): Future[MailToken] = ???
}

/**
 * 認証素通りユーザ
 *
 * @param id
 * @param user
 * @param expirationDate
 * @param lastUsed
 * @param creationDate
 * @param store
 * @tparam U
 */
case class PassThroughAuthenticator[U](
                                        id: String,
                                        user: U,
                                        expirationDate: DateTime,
                                        lastUsed: DateTime,
                                        creationDate: DateTime,
                                        store: AuthenticatorStore[PassThroughAuthenticator[U]]
                                        ) extends StoreBackedAuthenticator[U, PassThroughAuthenticator[U]] {

  // never timed out
  override val absoluteTimeoutInSeconds: Int = Int.MaxValue

  override def withUser(user: U): PassThroughAuthenticator[U] = this.copy(user = user)

  override def withLastUsedTime(time: DateTime): PassThroughAuthenticator[U] = this.copy(lastUsed = time)

  // never timed out
  override val idleTimeoutInMinutes: Int = Int.MaxValue

  override def starting(result: ApiResult): Future[ApiResult] = Future.successful { result }

  override def toString = s"id: $id, user: $user, lastUsedAt: $lastUsed createdAt: $creationDate, expiresAt: $expirationDate"
}

/**
 * 認証素通りユーザのビルダー
 *
 * @param user
 */
class PassThroughAuthenticatorBuilder(val user: Option[model.User])
  extends AuthenticatorBuilder[model.User] {

  override val id: String = "pass-through"

  import scala.concurrent.ExecutionContext.Implicits.global

  override def fromRequest(request: RequestHeader): Future[Option[Authenticator[model.User]]] =
    Future {
      user.map { u =>
        PassThroughAuthenticator(
          id = "pass-through",
          user = u,
          expirationDate = DateTime.now.plusYears(1),   // 期限は１年後
          lastUsed = DateTime.now,                      //
          creationDate = DateTime.now.minusSeconds(10), // １０秒前に作成された
          store = new FakeAuthenticatorStore
        )
      }
    }

  override def fromUser(user: User): Future[Authenticator[User]] = ???
}

/**
 * 認証素通りユーザのためのデータストア
 *
 * @tparam U ユーザ
 */
class FakeAuthenticatorStore[U] extends AuthenticatorStore[PassThroughAuthenticator[U]] {

  var authenticators: Seq[PassThroughAuthenticator[U]] = Nil

  override def find(id: String)
                   (implicit ct: ClassTag[PassThroughAuthenticator[U]]): Future[Option[PassThroughAuthenticator[U]]] = Future {
    authenticators.find(_.id == id)
  }

  override def delete(id: String): Future[Unit] = Future {
    authenticators = authenticators.filterNot(_.id == id)
  }

  override def save(authenticator: PassThroughAuthenticator[U],
                    timeoutInSeconds: Int): Future[PassThroughAuthenticator[U]] = Future {
    authenticators = (authenticators :+ authenticator).distinct
    authenticator
  }
}
