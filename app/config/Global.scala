package config

import controllers.ApplicationRuntimeEnvironment
import play.api.GlobalSettings
import repository.{MailTokenResourceRepositoryImpl, MailTokenResourceRepository}
import scaldi.Module
import scaldi.play.{ControllerInjector, ScaldiSupport}
import securesocial.core.RuntimeEnvironment

/**
 * Created by tomohiro_urakawa on 15/04/21.
 */
object Global extends Global

/**
 * Created by tomohiro_urakawa on 2015/03/29.
 */
trait Global extends GlobalSettings with ScaldiSupport {

  override def applicationModule = new ControllerInjector :: new Module {
    bind[RuntimeEnvironment[model.User]] to new ApplicationRuntimeEnvironment
  } :: new Module {
    bind[MailTokenResourceRepository] to new MailTokenResourceRepositoryImpl
  }

}
