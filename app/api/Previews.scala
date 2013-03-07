/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.Logger
import models.PreviewDAO
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import play.api.libs.json.Json
import play.api.libs.json.JsObject

/**
 * Files and datasets previews.
 * 
 * @author Luigi Marini
 *
 */
object Previews extends Controller {

  /**
   * Download preview bytes.
   */
  def download(id:String) = Authenticated {
    Action { request =>
	    PreviewDAO.getBlob(id) match {
	   
	      case Some((inputStream, filename, contentType, contentLength)) => {
    	    request.headers.get(RANGE) match {
	          case Some(value) => {
	            val range: (Long,Long) = value.substring("bytes=".length).split("-") match {
	              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
	              case x => (x(0).toLong,x(1).toLong)
	            }
	            range match { case (start,end) =>
	             
	              inputStream.skip(start)
	              import play.api.mvc.{SimpleResult, ResponseHeader}
	              SimpleResult(
	                header = ResponseHeader(PARTIAL_CONTENT,
	                  Map(
	                    CONNECTION -> "keep-alive",
	                    ACCEPT_RANGES -> "bytes",
	                    CONTENT_RANGE -> "bytes %d-%d/%d".format(start,end,contentLength),
	                    CONTENT_LENGTH -> (end - start + 1).toString,
	                    CONTENT_TYPE -> contentType
	                  )
	                ),
	                body = Enumerator.fromStream(inputStream)
	              )
	            }
	          }
	          case None => {
	            Ok.stream(Enumerator.fromStream(inputStream))
	            	.withHeaders(CONTENT_TYPE -> contentType)
	            	.withHeaders(CONTENT_LENGTH -> contentLength.toString)
	            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
      
	          }
	        }
	      }
	    }
    }
  }
  
  /**
   * Upload a preview.
   */  
  def upload() = Authenticated {
    Action(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>        
        Logger.info("Uploading file " + f.filename)
        // store file
        val id = PreviewDAO.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
        Ok(toJson(Map("id"->id)))   
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
    }
  }
  
  /**
   * Upload preview metadata.
   * 
   */
  def uploadMetadata(id: String) = Authenticated {
    Action(parse.json) { request =>
      request.body match {
        case JsObject(fields) => {
	      PreviewDAO.findOneById(new ObjectId(id)) match {
	        case Some(preview) =>
	            val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
	            val result = PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), 
	                $set("metadata" -> metadata, 
	                    "section_id"->new ObjectId(metadata("section_id").asInstanceOf[String])), false, false, WriteConcern.Safe)
	            Logger.debug("Updating previews.files " + id + " with " + metadata)
	            Ok(toJson(Map("status"->"success")))
	        case None => BadRequest(toJson("Preview not found"))
	      }
        }
      }
    }
  }
  
  /**
   * Get preview metadata.
   * 
   */
  def getMetadata(id: String) = Authenticated {
    Action { request =>
      PreviewDAO.findOneByID(new ObjectId(id)) match {
        case Some(preview) => Ok(toJson(Map("id"->preview.id.toString)))
        case None => Logger.error("Preview metadata not found " + id); InternalServerError
      }
    }
  }
  
}