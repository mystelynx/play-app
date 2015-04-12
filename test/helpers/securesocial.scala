package helpers

import java.lang.reflect.Constructor

import model.User
import org.joda.time.DateTime
import play.api.mvc.{RequestHeader, Result}
import play.api.{Logger, Application, GlobalSettings}
import securesocial.core.authenticator.{Authenticator, AuthenticatorBuilder, StoreBackedAuthenticator, AuthenticatorStore}
import securesocial.core.providers.{MailToken, UsernamePasswordProvider}
import securesocial.core.services.{SaveMode, AuthenticatorService, UserService}
import securesocial.core.{PasswordInfo, BasicProfile, IdentityProvider, RuntimeEnvironment}

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by tomohiro_urakawa on 15/04/12.
 */

case class FakeGlobal(user: model.User) extends GlobalSettings {

  override def onStart(app: Application) = Logger.debug("running with FakeGlobal.")

  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    val instance  = controllerClass.getConstructors.find { c =>
      val params = c.getParameterTypes
      params.length == 1 && params(0) == classOf[RuntimeEnvironment[model.User]]
    }.map {
      _.asInstanceOf[Constructor[A]].newInstance(ApplicationRuntimeEnvironment)
    }
    instance.getOrElse(super.getControllerInstance(controllerClass))
  }

  object ApplicationRuntimeEnvironment extends RuntimeEnvironment.Default[model.User] {
    protected override def include(p: IdentityProvider) = p.id -> p

    override lazy val userService: UserService[model.User] = new FakeUserService
    override lazy val providers = ListMap(
      include(new UsernamePasswordProvider[model.User](
        userService, None, viewTemplates, passwordHashers))
    )

    override lazy val authenticatorService = new AuthenticatorService(
      new PassThroughAuthenticatorBuilder(user)
    )
  }
}

class FakeUserService extends UserService[model.User] {
  override def find(providerId: String, userId: String): Future[Option[BasicProfile]] = ???

  override def findByEmailAndProvider(email: String, providerId: String): Future[Option[BasicProfile]] = ???

  override def deleteToken(uuid: String): Future[Option[MailToken]] = ???

  override def link(current: User, to: BasicProfile): Future[User] = ???

  override def passwordInfoFor(user: User): Future[Option[PasswordInfo]] = ???

  override def save(profile: BasicProfile, mode: SaveMode): Future[User] = ???

  override def findToken(token: String): Future[Option[MailToken]] = ???

  override def deleteExpiredTokens(): Unit = ???

  override def updatePasswordInfo(user: User, info: PasswordInfo): Future[Option[BasicProfile]] = ???

  override def saveToken(token: MailToken): Future[MailToken] = ???
}

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

  override def starting(result: Result): Future[Result] = Future.successful { result }
}

class PassThroughAuthenticatorBuilder(val user: model.User)
  extends AuthenticatorBuilder[model.User] {

  override val id: String = "pass-through"

  import scala.concurrent.ExecutionContext.Implicits.global

  override def fromRequest(request: RequestHeader): Future[Option[Authenticator[model.User]]] =
    Future {
      Some(PassThroughAuthenticator(
        id = "pass-through",
        user = user,
        expirationDate = DateTime.now.plusYears(1),
        lastUsed = DateTime.now,
        creationDate = DateTime.now.minusSeconds(10),
        store = new FakeAuthenticatorStore
      ))
    }

  override def fromUser(user: User): Future[Authenticator[User]] = ???
}


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