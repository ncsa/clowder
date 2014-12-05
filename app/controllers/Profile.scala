package controllers
import javax.inject.Inject

import api.{Permission, WithPermission}
import play.api.Routes
import play.api.mvc.Action
import play.api.mvc.Controller
import api.Sections
import models.AppAppearance
import javax.inject.{Singleton, Inject}
import play.api.mvc.Action
import services.FileService
import play.api.Logger
import services.UserService
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.Form
import play.api.data.Forms._
import models.{UUID, Collection, Info, User}
import java.util.Date
import play.api.Logger
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import api.WithPermission
import api.Permission
import play.api.Play.current
import javax.inject.{Singleton, Inject}
import services._


class Profile @Inject()(users: UserService) extends  SecuredController {

  val bioForm = Form(
    mapping(
      "avatarUrl" -> optional(text),
      "biography" -> optional(text),
      "currentprojects" -> optional(text),
      "institution" -> optional(text),
      "pastprojects" -> optional(text),
      "position" -> optional(text)
    )(Info.apply)(Info.unapply)
  )

  def editProfile() = SecuredAction() {
    implicit request =>
      implicit val user = request.user
    var avatarUrl: Option[String] = None
    var biography: Option[String] = None
    var currentprojects: Option[String] = None
    var institution: Option[String] = None
    var pastprojects: Option[String] = None
    var position: Option[String] = None
    user match {
      case Some(x) => {
        print(x.email.toString())
        implicit val email = x.email
        email match {
          case Some(addr) => {
            implicit val modeluser = users.findByEmail(addr.toString())
            modeluser match {
              case Some(muser) => {
                muser.avatarUrl match {
                  case Some(filledOut) => avatarUrl = Option(filledOut)
                  case None => avatarUrl = None
                }
                muser.biography match {
                  case Some(filledOut) => biography = Option(filledOut)
                  case None => biography = None
                }
                muser.currentprojects match {
                  case Some(filledOut) => currentprojects = Option(filledOut)
                  case None => currentprojects = None
                }
                muser.institution match {
                  case Some(filledOut) => institution = Option(filledOut)
                  case None => institution = None
                }
                muser.pastprojects match {
                  case Some(filledOut) => pastprojects = Option(filledOut)
                  case None => pastprojects = None
                }
                muser.position match {
                  case Some(filledOut) => position = Option(filledOut)
                  case None => position = None
                }

                val newbioForm = bioForm.fill(Info(
                  avatarUrl,
                  biography,
                  currentprojects,
                  institution,
                  pastprojects,
                  position
                ))
                Ok(views.html.editProfile(newbioForm))
              }
              case None => {
                Ok("NOT WORKS")
              }
            }
          }
        }
      }
      case None => {
        Ok("NOT WORKING")
      }
    } 
  }


  def addFriend(email: String) = SecuredAction() { request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        implicit val myemail = x.email
        myemail match {
          case Some(addr) => {
            implicit val modeluser = users.findByEmail(addr.toString())
            implicit val otherUser = users.findByEmail(email)
            modeluser match {
              case Some(muser) => {
                muser.friends match {
                  case Some(viewList) =>{
                    users.editList(addr.toString(), "friends", email)
                    otherUser match {
                      case Some(other) => {
                        Ok(views.html.profilepage(other))
                      }
                    }
                  }
                  case None => {
                    val newList: List[String] = List(email)
                    users.createList(addr.toString(), "friends", newList)
                    otherUser match {
                      case Some(other) => {
                        Ok(views.html.profilepage(other))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  
  def view = SecuredAction() { request =>
    implicit val user = request.user
    user match {
      case Some(x) => {
        implicit val email = x.email
        email match {
          case Some(addr) => {
            implicit val modeluser = users.findByEmail(addr.toString())
            modeluser match {
              case Some(muser) => {
                Ok(views.html.profilepage(muser))
              }
              case None => {
                Ok("NOT WORKS")
              }
            }
          }
        }
      }
      case None => {
        Ok("NOT WORKING")
      }
    } 
  }  

  def viewProfile(email: Option[String]) = SecuredAction() { request =>
    implicit val user = request.user
    email match {
      case Some(addr) => {
        implicit val modeluser = users.findByEmail(addr.toString())
        modeluser match {
          case Some(muser) => {
            Ok(views.html.profilepage(muser))
          }
          case None => {
            Ok("NOT WORKS")
          }
        }
      }
    }
  }

  def submitChanges = SecuredAction() {  implicit request =>
    implicit val user  = request.user
    bioForm.bindFromRequest.fold(
      errors => BadRequest(views.html.editProfile(errors)),
      form => {
        user match {
          case Some(x) => {
            print(x.email.toString())
            implicit val email = x.email
            email match {
              case Some(addr) => {
                implicit val modeluser = users.findByEmail(addr.toString())
                modeluser match {
                  case Some(muser) => {
                    users.editField(addr.toString(), "avatarUrl", form.avatarUrl)
                    users.editField(addr.toString(), "biography", form.biography)
                    users.editField(addr.toString(), "currentprojects", form.currentprojects)
                    users.editField(addr.toString(), "institution", form.institution)
                    users.editField(addr.toString(), "pastprojects", form.pastprojects)
                    users.editField(addr.toString(), "position", form.position)
                    Redirect(routes.Profile.view)
                  }
                  case None => {
                    Ok("NOT WORKS")
                  }
                }
              }
            }
          }
          case None => {
            Ok("NOT WORKING")
          }
        }
      }
    )
  }
  
}
