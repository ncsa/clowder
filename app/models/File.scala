/**
 *
 */
package models

import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import collection.JavaConverters._
import securesocial.core.Identity
import play.api.Logger
import java.util.Calendar
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileSystems
import services.Services


/**
 * Uploaded files.
 * 
 * @author Luigi Marini
 *
 */
case class File(
    id: ObjectId = new ObjectId,    
    path: Option[String] = None, 
    filename: String,
    author: Identity,
    uploadDate: Date, 
    contentType: String,
    length: Long = 0,
    showPreviews: String = "DatasetLevel",
    sections: List[Section] = List.empty,
    previews: List[Preview] = List.empty,
    tags: List[String] = List.empty,
    metadata: List[Map[String, Any]] = List.empty,
	thumbnail_id: Option[String] = None,
	isIntermediate: Option[Boolean] = None,
	userMetadata: Map[String, Any] = Map.empty
)

object FileDAO extends ModelCompanion[File, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[File, ObjectId](collection = x.collection("uploads.files")) {}
  }
  
  def get(id: String): Option[File] = {
    dao.findOneById(new ObjectId(id)) match {
      case Some(file) => { 
        val previews = PreviewDAO.findByFileId(file.id)
        val sections = SectionDAO.findByFileId(file.id)
        val sectionsWithPreviews = sections.map { s =>
          val p = PreviewDAO.findOne(MongoDBObject("section_id"->s.id))
          s.copy(preview = p)
        }
        Some(file.copy(sections = sectionsWithPreviews, previews = previews))
      }
      case None => None
    }
  }
  
  def listOutsideDataset(dataset_id: String): List[File] = {
    Dataset.findOneById(new ObjectId(dataset_id)) match{
        case Some(dataset) => {
          val list = for (file <- Services.files.listFiles(); if(!isInDataset(file,dataset))) yield file
          return list
        }
        case None =>{
          return Services.files.listFiles()	 	  
        } 
      }
  }
  
  def isInDataset(file: File, dataset: Dataset): Boolean = {
    for(dsFile <- dataset.files){
      if(dsFile.id == file.id)
        return true
    }
    return false
  }
  
  
  //Not used yet
  def getMetadata(id: String): scala.collection.immutable.Map[String,Any] = {
		  dao.collection.findOneByID(new ObjectId(id)) match {
		  case None => new scala.collection.immutable.HashMap[String,Any]
		  case Some(x) => {
			  val returnedMetadata = x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
					  returnedMetadata
		  }
	  }
  }
    
  def getUserMetadata(id: String): scala.collection.mutable.Map[String,Any] = {
    dao.collection.findOneByID(new ObjectId(id)) match {
      case None => new scala.collection.mutable.HashMap[String,Any]
      case Some(x) => {
        x.getAs[DBObject]("userMetadata") match{
          case Some(y)=>{
	    	val returnedMetadata = x.getAs[DBObject]("userMetadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]]
			returnedMetadata
          }
          case None => new scala.collection.mutable.HashMap[String,Any]
		}
      }
    }
  }
  
  
  def getUserMetadataJSON(id: String): String = {
    dao.collection.findOneByID(new ObjectId(id)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("userMetadata") match{
          case Some(y)=>{
	    	val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("userMetadata").get)
			returnedMetadata
          }
          case None => "{}"
		}
      }
    }
  }
  
  def getTechnicalMetadataJSON(id: String): String = {
    dao.collection.findOneByID(new ObjectId(id)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("metadata") match{
          case Some(y)=>{
	    	val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("metadata").get)
			returnedMetadata
          }
          case None => "{}"
		}
      }
    }
  }

  
  def addUserMetadata(id: String, json: String) {
    Logger.debug("Adding/modifying user metadata to file " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("userMetadata" -> md), false, false, WriteConcern.Safe)
  }
  

  def findByTag(tag: String): List[File] = {
    dao.find(MongoDBObject("tags" -> tag)).toList
  }
  
  def findIntermediates(): List[File] = {
    dao.find(MongoDBObject("isIntermediate" -> true)).toList
  }

 
  def tag(id: String, tag: String) { 
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)),  $addToSet("tags" -> tag), false, false, WriteConcern.Safe)
  }

  def comment(id: String, comment: Comment) {
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }
  
  def setIntermediate(id: String){
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("isIntermediate" -> Some(true)), false, false, WriteConcern.Safe)
  }
  
  def removeFile(id: String){
    dao.findOneById(new ObjectId(id)) match{
      case Some(file) => {
        if(file.isIntermediate.isEmpty){
        	val fileDatasets = Dataset.findByFileId(file.id)
        	for(fileDataset <- fileDatasets){
	        	Dataset.removeFile(fileDataset.id.toString(), id)
	        	if(!file.thumbnail_id.isEmpty && !fileDataset.thumbnail_id.isEmpty)
		        	if(file.thumbnail_id.get == fileDataset.thumbnail_id.get)
		        	  Dataset.newThumbnail(fileDataset.id.toString())
		    }   		        	  
	        for(section <- SectionDAO.findByFileId(file.id)){
	          SectionDAO.removeSection(section)
	        }
	        for(preview <- PreviewDAO.findByFileId(file.id)){
	          PreviewDAO.removePreview(preview)
	        }
	        for(comment <- Comment.findCommentsByFileId(id)){
	          Comment.removeComment(comment)
	        }
	        for(texture <- ThreeDTextureDAO.findTexturesByFileId(file.id)){
	          ThreeDTextureDAO.remove(MongoDBObject("_id" -> texture.id))
	        }
	        if(!file.thumbnail_id.isEmpty)
	          Thumbnail.remove(MongoDBObject("_id" -> file.thumbnail_id.get))
        }
        FileDAO.remove(MongoDBObject("_id" -> file.id))
      }
      case None =>
    }    
  }
  
  def removeTemporaries(){
    val cal = Calendar.getInstance()
    val timeDiff = play.Play.application().configuration().getInt("rdfTempCleanup.removeAfter")
    cal.add(Calendar.MINUTE, -timeDiff)
    val oldDate = cal.getTime()    
    
    val folder = new java.io.File(play.api.Play.configuration.getString("rdfdumptemporary.dir").getOrElse(""))
    val listOfFiles = folder.listFiles()
    for(currFileDir <- listOfFiles){
      val currFile = currFileDir.listFiles()(0)
      val attrs = Files.readAttributes(FileSystems.getDefault().getPath(currFile.getAbsolutePath()),  classOf[BasicFileAttributes])
      val timeCreated = new Date(attrs.creationTime().toMillis())
      if(timeCreated.compareTo(oldDate) < 0){
    	  currFile.delete()
    	  currFileDir.delete()
        }
    }
  }
  
  
}
