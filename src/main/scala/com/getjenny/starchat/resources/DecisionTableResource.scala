package com.getjenny.starchat.resources

/**
  * Created by Angelo Leto <angelo@getjenny.com> on 27/06/16.
  */

import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.server.Route
import com.getjenny.starchat.entities._
import com.getjenny.starchat.routing.MyResource
import com.getjenny.starchat.services.DecisionTableService
import akka.http.scaladsl.model.StatusCodes
import com.getjenny.starchat.SCActorSystem

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

trait DecisionTableResource extends MyResource {

  val dtElasticService: DecisionTableService

  def decisionTableRoutes: Route = pathPrefix("decisiontable") {
    pathEnd {
      post {
        entity(as[DTDocument]) { document =>
          val result: Future[Option[IndexDocumentResult]] = dtElasticService.create(document)
          completeResponse(StatusCodes.Created, StatusCodes.BadRequest, result)
        }
      } ~
        get {
          parameters("ids".as[String].*) { ids =>
            val result: Future[Option[SearchDTDocumentsResults]] = dtElasticService.read(ids.toList)
            completeResponse(StatusCodes.OK, StatusCodes.BadRequest, result)
          }
        }
    } ~
      path(Segment) { id =>
        put {
          entity(as[DTDocumentUpdate]) { update =>
            val result: Future[Option[UpdateDocumentResult]] = dtElasticService.update(id, update)
            val result_try: Try[Option[UpdateDocumentResult]] = Await.ready(result,  30.seconds).value.get
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
            val result: Future[Option[DeleteDocumentResult]] = dtElasticService.delete(id)
            onSuccess(result) {
              case Some(t) =>
                if(t.found) {
                  completeResponse(StatusCodes.OK, result)
                } else {
                  completeResponse(StatusCodes.BadRequest, result)
                }
              case None => completeResponse(StatusCodes.BadRequest)
            }
          }
      }
  }

  def decisionTableAnalyzerRoutes: Route = pathPrefix("decisiontable_analyzer") {
    pathEnd {
      get {
        val result: Try[Option[DTAnalyzerMap]] =
          Await.ready(dtElasticService.getDTAnalyzerMap, 30.seconds).value.get
        result match {
          case Success(t) =>
            completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Future{Option{t}})
          case Failure(e) =>
            log.error("route=decisionTableAnalyzerRoutes method=GET: " + e.getMessage)
            completeResponse(StatusCodes.BadRequest,
              Future{Option{IndexManagementResponse(message = e.getMessage)}})
        }
      } ~
        post {
          val result: Try[Option[DTAnalyzerLoad]] =
            Await.ready(dtElasticService.loadAnalyzer, 30.seconds).value.get
          result match {
            case Success(t) =>
              completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Future{Option{t}})
            case Failure(e) =>
              log.error("route=decisionTableAnalyzerRoutes method=POST: " + e.getMessage)
              completeResponse(StatusCodes.BadRequest,
                Future{Option{IndexManagementResponse(message = e.getMessage)}})
          }
        }
    }
  }

  def decisionTableSearchRoutes: Route = pathPrefix("decisiontable_search") {
    pathEnd {
      post {
        entity(as[DTDocumentSearch]) { docsearch =>
          val result: Future[Option[SearchDTDocumentsResults]] = dtElasticService.search(docsearch)
          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, result)
        }
      }
    }
  }

  def decisionTableResponseRequestRoutes: Route = pathPrefix("get_next_response") {
    pathEnd {
      post {
        entity(as[ResponseRequestIn]) { response_request =>
          val response: Option[ResponseRequestOutOperationResult] =
            dtElasticService.getNextResponse(response_request)
          response match {
            case Some(t) =>
              if (t.status.code == 200) {
                completeResponse(StatusCodes.OK, StatusCodes.Gone, Future{t.response_request_out})
              }  else {
                completeResponse(StatusCodes.NoContent) // no response found
              }
            case None => completeResponse(StatusCodes.BadRequest)
          }
        }
      }
    }
  }

}



