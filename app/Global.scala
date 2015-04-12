import java.io.File
import java.lang.reflect.Constructor

import controllers.MyUserService
import play.api._
import securesocial.core.providers.UsernamePasswordProvider
import securesocial.core.services.UserService
import securesocial.core.{IdentityProvider, RuntimeEnvironment}

import scala.collection.immutable.ListMap

/**
 * Created by tomohiro_urakawa on 2015/03/29.
 */
object Global extends GlobalSettings {

  /**
   * An implementation that checks if the controller expects a RuntimeEnvironment and
   * passes the instance to it if required.
   *
   * This can be replaced by any DI framework to inject it differently.
   *
   * @param controllerClass
   * @tparam A
   * @return
   */
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
    protected override def include(p: IdentityProvider) = p.id ->   p

    override lazy val userService: UserService[model.User] = new MyUserService
    override lazy val providers = ListMap(
      include(new UsernamePasswordProvider[model.User](
        userService, None, viewTemplates, passwordHashers))
    )

    //  override lazy val viewTemplates
    //  = new plugins.CustomTemplates(this) /// <====追加
  }
}


