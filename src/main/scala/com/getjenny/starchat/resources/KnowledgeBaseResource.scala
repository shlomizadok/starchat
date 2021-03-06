package com.getjenny.starchat.resources

/**
  * Created by Angelo Leto <angelo@getjenny.com> on 27/06/16.
  */

import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.server.Route
import com.getjenny.starchat.entities._
import com.getjenny.starchat.routing.MyResource
import com.getjenny.starchat.services.KnowledgeBaseService
import akka.http.scaladsl.model.StatusCodes
import com.getjenny.starchat.SCActorSystem

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

trait KnowledgeBaseResource extends MyResource {

  val kbElasticService: KnowledgeBaseService

  def knowledgeBaseRoutes: Route = pathPrefix("knowledgebase") {
    pathEnd {
      post {
        entity(as[KBDocument]) { document =>
          val result: Future[Option[IndexDocumentResult]] = kbElasticService.create(document)
          onSuccess(result) {
            case Some(v) =>
              completeResponse(StatusCodes.Created, StatusCodes.BadRequest, Future{Option{v}})
            case None =>
              completeResponse( StatusCodes.BadRequest,
                Future{Option{ReturnMessageData(code = 300, message = "Error indexing new document")}})
          }
        }
      } ~
      get {
        parameters("ids".as[String].*) { ids =>
          val result: Future[Option[SearchKBDocumentsResults]] = kbElasticService.read(ids.toList)
          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, result)
        }
      }
    } ~
      path(Segment) { id =>
        put {
          entity(as[KBDocumentUpdate]) { update =>
            val result: Future[Option[UpdateDocumentResult]] = kbElasticService.update(id, update)
            val result_try: Try[Option[UpdateDocumentResult]] = Await.ready(result, 30.seconds).value.get
            result_try match {
              case Success(t) =>
                completeResponse(StatusCodes.Created, StatusCodes.BadRequest, Future{Option{t}})
              case Failure(e) =>
                completeResponse(StatusCodes.BadRequest,
                  Future{Option{ReturnMessageData(code = 101, message = e.getMessage)}})
            }
          }
        } ~
          delete {
            val result: Future[Option[DeleteDocumentResult]] = kbElasticService.delete(id)
            completeResponse(StatusCodes.Created, StatusCodes.BadRequest, result)
          }
      }
  }

  def knowledgeBaseSearchRoutes: Route = pathPrefix("knowledgebase_search") {
    pathEnd {
      post {
        entity(as[KBDocumentSearch]) { docsearch =>
          val result: Future[Option[SearchKBDocumentsResults]] = kbElasticService.search(docsearch)
          completeResponse(StatusCodes.Created, StatusCodes.BadRequest, result)
        }
      }
    }
  }

}



