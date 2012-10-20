package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models._

object WishController extends Controller with Secured {


    val editWishlistForm = Form(
        tuple(
          "title" -> nonEmptyText(maxLength = 180,minLength = 2 ),
          "description" -> optional(text(maxLength = 2000))
        )
    )

    val searchForm = Form {
        "term" -> optional(text(maxLength = 99))
    }   

    val simpleAddWishForm = Form {
        "title" -> text(maxLength = 180,minLength = 2 )
    }

    val editWishForm = Form(
      tuple(
        "title" -> nonEmptyText(maxLength = 180,minLength = 2 ),
        "description" -> optional(text(maxLength = 2000))
      )
    )

    val updateWishlistOrderForm = Form(
        "order" -> text(maxLength=500)
    ) 

    def create(username:String) = withCurrentRecipient { currentRecipient => implicit request =>
        editWishlistForm.bindFromRequest.fold(
            errors => {
              Logger.warn("Create failed: " + errors)
              BadRequest(views.html.wishlist.createwishlist(errors))
            },
           titleForm => {
              if(username == currentRecipient.username)  {
                Logger.info("New wishlist: " + titleForm._1)

                val wishlist = new Wishlist(None, titleForm._1.trim, None, currentRecipient).save

                Redirect(routes.WishController.showWishlist(username,wishlist.wishlistId.get)).flashing("messageSuccess" -> "Wishlist created")
              } else {
                  Logger.warn("Recipient %s tried to create wishlist for %d".format(currentRecipient.username,username))
                 Unauthorized(views.html.error.permissiondenied())
              }
          }
        )
    }


  def showEditWishlist(username:String,wishlistId:Long) =  isRecipientOfWishlist(username,wishlistId) { (wishlist,currentRecipient) => implicit request =>
    val editForm = editWishlistForm.fill((wishlist.title,wishlist.description))
    val wishes = Wishlist.findWishesForWishlist(wishlist)
    Ok(views.html.wishlist.editwishlist(wishlist, wishes, editForm))
  }


    def updateWishlist(username:String,wishlistId:Long) = isRecipientOfWishlist(username,wishlistId) { (wishlist,currentRecipient) => implicit request =>
      val wishes = Wishlist.findWishesForWishlist(wishlist)
      editWishlistForm.bindFromRequest.fold(
        errors => {
          Logger.warn("Update failed: " + errors)
          BadRequest(views.html.wishlist.editwishlist(wishlist,wishes,errors))
        }, 
        editForm => {
          Logger.info("Updating wishlist: " + editForm)

          wishlist.copy(title=editForm._1,description=editForm._2).update

          Redirect(routes.WishController.showWishlist(username,wishlist.wishlistId.get)).flashing("message" -> "Wishlist updated")
        }
      )
    }



    def deleteWishlist(username:String,wishlistId:Long) = isRecipientOfWishlist(username,wishlistId) { (wishlist,currentRecipient) => implicit request =>
        Logger.info("Deleting wishlist: " + wishlistId)

        wishlist.delete

        Redirect(routes.Application.index()).flashing("messageWarning" -> "Wishlist deleted")
    }


    def showWishlist(username:String,wishlistId:Long) =  withWishlist(username,wishlistId) { (wishlist,currentRecipient) => implicit request =>
      val wishes = Wishlist.findWishesForWishlist(wishlist)
      Ok(views.html.wishlist.showwishlist(wishlist,wishes,simpleAddWishForm))
    }


    def showConfirmDeleteWishlist(username:String,wishlistId:Long) = isRecipientOfWishlist(username,wishlistId) { (wishlist,currentRecipient) => implicit request =>
        Ok(views.html.wishlist.deletewishlist(wishlist))
    }


    def search = Action { implicit request =>
        searchForm.bindFromRequest.fold(
            errors => {
              Logger.warn("Update failed: " + errors)
              BadRequest
            }, 
            term => {
                val wishlists = term match {
                    case None => Wishlist.findAll
                    case Some(searchTerm) => Wishlist.searchForWishlistsContaining(searchTerm)
                }
                Ok(views.html.wishlist.listwishlists(wishlists,searchForm.fill(term),editWishlistForm))
            }
        )   
   }


    def addWishToWishlist(username:String,wishlistId:Long) = isRecipientOfWishlist(username,wishlistId) { (wishlist,currentRecipient) => implicit request => 
        simpleAddWishForm.bindFromRequest.fold(
          errors => {
              Logger.warn("Add failed: " + errors)
              val wishes = Wishlist.findWishesForWishlist(wishlist)
              BadRequest(views.html.wishlist.showwishlist(wishlist,wishes,errors))
          }, 
          title => {
              Wish(None,title,None,None,Some(wishlist)).save
              Redirect(routes.WishController.showWishlist(username,wishlist.wishlistId.get)).flashing("messageSuccess" -> "Wish added") 
          }            
        )   
   }


  def deleteWishFromWishlist(username:String,wishlistId:Long,wishId:Long) = isRecipientOfWish(username,wishlistId,wishId) { (wish,wishlist,currentRecipient) => implicit request => 

      wish.delete

      Redirect(routes.WishController.showWishlist(username,wishlist.wishlistId.get)).flashing("messageWarning" -> "Wish deleted")
   }



  def updateWish(username:String,wishlistId:Long,wishId:Long) = isRecipientOfWish(username,wishlistId,wishId) { (wish,wishlist,currentRecipient) => implicit request => 
      editWishForm.bindFromRequest.fold(
        errors => {
          Logger.warn("Update failed: " + errors)
          val wishes = Wishlist.findWishesForWishlist(wishlist)
           Redirect(routes.WishController.showWishlist(username,wishlistId)).flashing("messageError" -> "Wish update failed")
        },
        editForm => {
          Logger.debug("Update title: " + editForm._1)
          wish.copy(title=editForm._1,description=editForm._2).update
          Redirect(routes.WishController.showWishlist(username,wishlistId)).flashing("messageSuccess" -> "Wish updated")
        }
      )
  }





    def updateWishlistOrder(username:String,wishlistId:Long) = isRecipientOfWishlist(username,wishlistId) { (wishlist,currentRecipient) => implicit request => 
      
      val wishes = Wishlist.findWishesForWishlist(wishlist)
      updateWishlistOrderForm.bindFromRequest.fold(
        errors => {
          Logger.warn("Update order failed: " + errors)
          Redirect(routes.WishController.showWishlist(username,wishlistId)).flashing("messageError" -> "Order update failed")
        }, 
        listOrder => {
          Logger.info("Updating wishlist's order: " + wishlist.wishlistId.get)

          var ordinalCount = 1;
          listOrder.split(",") map { idOrder => 
            Wish.findById(idOrder.toInt) map { wish =>  
              wish.copy(ordinal=Some(ordinalCount)).updateOrdinal
              ordinalCount += 1
            }
          }

          Redirect(routes.WishController.showWishlist(username,wishlist.wishlistId.get)).flashing("message" -> "Wishlist updated")
         
        }
      )
    }


}
