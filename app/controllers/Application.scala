package controllers

import java.util.UUID

import org.joda.time.DateTime
import play.api.mvc._
import play.api.mvc.Security._

object Application extends Controller {

  case class Account(id: String, name: String)
  case class Credentials(token: String, account: Account, startAt: DateTime)

  case class Permission(f: Account => Boolean)

  def credentials(request: RequestHeader) = {
    request.headers.get("Authorization").map(_.split(' ')(1))
      .map(Credentials(_, Account("foo", "bar"), DateTime.now))
  }

  def permission(account: Account) = {
    Permission(_ => true)
  }

  def onUnauthorized(request: RequestHeader) = Results.Unauthorized

  def AuthenticatedAction(f: => Credentials => Permission => Request[AnyContent] => Result) = {
    Authenticated(credentials, onUnauthorized) { credentials =>
      Action(request => f(credentials)(permission(credentials.account))(request))
    }
  }

//  def index = AuthenticatedAction { credentials => permission => implicit request =>

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}
