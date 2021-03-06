package com.getjenny.starchat.services

/**
  * Created by Angelo Leto <angelo@getjenny.com> on 01/07/16.
  */

import com.getjenny.starchat.entities._
import org.elasticsearch.common.xcontent.XContentBuilder

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.collection.immutable.{List, Map}
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.common.transport.{InetSocketTransportAddress, TransportAddress}
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.get.{GetResponse, MultiGetItemResponse, MultiGetRequestBuilder, MultiGetResponse}
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchResponse, SearchType}
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import java.net.InetAddress

import scala.collection.JavaConverters._
import com.typesafe.config.ConfigFactory
import org.elasticsearch.action.DocWriteResponse.Result
import org.elasticsearch.search.SearchHit
import org.elasticsearch.rest.RestStatus
import akka.event.{Logging, LoggingAdapter}
import akka.event.Logging._
import com.getjenny.starchat.SCActorSystem

class KnowledgeBaseService(implicit val executionContext: ExecutionContext) {
  val elastic_client = KnowledgeBaseElasticClient
  val log: LoggingAdapter = Logging(SCActorSystem.system, this.getClass.getCanonicalName)

  def search(documentSearch: KBDocumentSearch): Future[Option[SearchKBDocumentsResults]] = {
    val client: TransportClient = elastic_client.get_client()
    val search_builder : SearchRequestBuilder = client.prepareSearch(elastic_client.index_name)
      .setTypes(elastic_client.type_name)
      .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)

    search_builder.setMinScore(documentSearch.min_score.getOrElse(
      Option{elastic_client.query_min_threshold}.getOrElse(0.0f))
    )

    val bool_query_builder : BoolQueryBuilder = QueryBuilders.boolQuery()
    if (documentSearch.doctype.isDefined)
      bool_query_builder.must(QueryBuilders.termQuery("doctype", documentSearch.doctype.get))

    if (documentSearch.verified.isDefined)
      bool_query_builder.must(QueryBuilders.termQuery("verified", documentSearch.verified.get))

    if(documentSearch.question.isDefined)
      bool_query_builder.must(QueryBuilders.matchQuery("question.stem_bm25", documentSearch.question.get))

    if(documentSearch.answer.isDefined)
      bool_query_builder.must(QueryBuilders.matchQuery("answer.stem_bm25", documentSearch.answer.get))

    if(documentSearch.conversation.isDefined)
      bool_query_builder.must(QueryBuilders.matchQuery("conversation", documentSearch.conversation.get))

    search_builder.setQuery(bool_query_builder)

    val search_response : SearchResponse = search_builder
      .setFrom(documentSearch.from.getOrElse(0)).setSize(documentSearch.size.getOrElse(10))
      .execute()
      .actionGet()

    val documents : Option[List[SearchKBDocument]] = Option { search_response.getHits.getHits.toList.map( { case(e) =>

      val item: SearchHit = e

      //        val fields : Map[String, GetField] = item.getFields.toMap
      val id : String = item.getId

      //        val score : Float = fields.get("_score").asInstanceOf[Float]
      val source : Map[String, Any] = item.getSource.asScala.toMap

      val conversation : String = source.get("conversation") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val index_in_conversation : Option[Int] = source.get("index_in_conversation") match {
        case Some(t) => Option { t.asInstanceOf[Int] }
        case None => None : Option[Int]
      }

      val question : String = source.get("question") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val answer : String = source.get("answer") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val verified : Boolean = source.get("verified") match {
        case Some(t) => t.asInstanceOf[Boolean]
        case None => false
      }

      val topics : Option[String] = source.get("topics") match {
        case Some(t) => Option { t.asInstanceOf[String] }
        case None => None : Option[String]
      }

      val doctype : String = source.get("doctype") match {
        case Some(t) => t.asInstanceOf[String]
        case None => doctypes.normal
      }

      val state : Option[String] = source.get("state") match {
        case Some(t) => Option { t.asInstanceOf[String] }
        case None => None : Option[String]
      }

      val status : Int = source.get("status") match {
        case Some(t) => t.asInstanceOf[Int]
        case None => 0
      }

      val document : KBDocument = KBDocument(id = id, conversation = conversation,
        index_in_conversation = index_in_conversation, question = question,
        answer = answer, verified = verified, topics = topics, doctype = doctype,
        state = state, status = status)

      val search_document : SearchKBDocument = SearchKBDocument(score = item.score, document = document)
      search_document
    }) }

    val filtered_doc : List[SearchKBDocument] = documents.getOrElse(List[SearchKBDocument]())

    val max_score : Float = search_response.getHits.getMaxScore
    val total : Int = filtered_doc.length
    val search_results : SearchKBDocumentsResults = SearchKBDocumentsResults(total = total, max_score = max_score,
      hits = filtered_doc)

    val search_results_option : Future[Option[SearchKBDocumentsResults]] = Future { Option { search_results } }
    search_results_option
  }

  def create(document: KBDocument): Future[Option[IndexDocumentResult]] = Future {
    val builder : XContentBuilder = jsonBuilder().startObject()

    builder.field("id", document.id)
    builder.field("conversation", document.conversation)

    document.index_in_conversation match {
      case Some(t) => builder.field("index_in_conversation", t)
      case None => ;
    }

    builder.field("question", document.question)
    builder.field("answer", document.answer)
    builder.field("verified", document.verified)

    document.topics match {
      case Some(t) => builder.field("topics", t)
      case None => ;
    }
    builder.field("doctype", document.doctype)
    document.state match {
      case Some(t) => builder.field("state", t)
      case None => ;
    }
    builder.field("status", document.status)

    builder.endObject()

    val json: String = builder.string()
    val client: TransportClient = elastic_client.get_client()
    val response: IndexResponse =
      client.prepareIndex(elastic_client.index_name, elastic_client.type_name, document.id)
        .setSource(json)
        .get()

    val doc_result: IndexDocumentResult = IndexDocumentResult(index = response.getIndex,
      dtype = response.getType,
      id = response.getId,
      version = response.getVersion,
      created = response.status == RestStatus.CREATED
    )

    Option {doc_result}
  }

  def update(id: String, document: KBDocumentUpdate): Future[Option[UpdateDocumentResult]] = Future {
    val builder : XContentBuilder = jsonBuilder().startObject()
    document.conversation match {
      case Some(t) => builder.field("conversation", t)
      case None => ;
    }
    document.question match {
      case Some(t) => builder.field("question", t)
      case None => ;
    }

    document.index_in_conversation match {
      case Some(t) => builder.field("index_in_conversation", t)
      case None => ;
    }

    document.answer match {
      case Some(t) => builder.field("answer", t)
      case None => ;
    }
    document.verified match {
      case Some(t) => builder.field("verified", t)
      case None => ;
    }
    document.topics match {
      case Some(t) => builder.field("topics", t)
      case None => ;
    }
    document.doctype match {
      case Some(t) => builder.field("doctype", t)
      case None => ;
    }
    document.state match {
      case Some(t) => builder.field("state", t)
      case None => ;
    }
    document.status match {
      case Some(t) => builder.field("status", t)
      case None => ;
    }
    builder.endObject()

    val client: TransportClient = elastic_client.get_client()
    val response: UpdateResponse = client.prepareUpdate(elastic_client.index_name, elastic_client.type_name, id)
      .setDoc(builder)
      .get()

    val doc_result: UpdateDocumentResult = UpdateDocumentResult(index = response.getIndex,
      dtype = response.getType,
      id = response.getId,
      version = response.getVersion,
      created = response.status == RestStatus.CREATED
    )

    Option {doc_result}
  }

  def delete(id: String): Future[Option[DeleteDocumentResult]] = Future {
    val client: TransportClient = elastic_client.get_client()
    val response: DeleteResponse = client.prepareDelete(elastic_client.index_name, elastic_client.type_name, id).get()

    val doc_result: DeleteDocumentResult = DeleteDocumentResult(index = response.getIndex,
      dtype = response.getType,
      id = response.getId,
      version = response.getVersion,
      found = response.status != RestStatus.NOT_FOUND
    )

    Option {doc_result}
  }

  def read(ids: List[String]): Future[Option[SearchKBDocumentsResults]] = {
    val client: TransportClient = elastic_client.get_client()
    val multiget_builder: MultiGetRequestBuilder = client.prepareMultiGet()
    multiget_builder.add(elastic_client.index_name, elastic_client.type_name, ids:_*)
    val response: MultiGetResponse = multiget_builder.get()

    val documents : Option[List[SearchKBDocument]] = Option { response.getResponses
        .toList.filter((p: MultiGetItemResponse) => p.getResponse.isExists).map( { case(e) =>

      val item: GetResponse = e.getResponse

        val id : String = item.getId

        val source : Map[String, Any] = item.getSource.asScala.toMap

        val conversation : String = source.get("conversation") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val index_in_conversation : Option[Int] = source.get("index_in_conversation") match {
          case Some(t) => Option { t.asInstanceOf[Int] }
          case None => None : Option[Int]
        }

        val question : String = source.get("question") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val answer : String = source.get("answer") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val verified : Boolean = source.get("verified") match {
          case Some(t) => t.asInstanceOf[Boolean]
          case None => false
        }

        val topics : Option[String] = source.get("topics") match {
          case Some(t) => Option { t.asInstanceOf[String] }
          case None => None : Option[String]
        }

        val doctype : String = source.get("doctype") match {
          case Some(t) => t.asInstanceOf[String]
          case None => doctypes.normal
        }

        val state : Option[String] = source.get("state") match {
          case Some(t) => Option { t.asInstanceOf[String] }
          case None => None : Option[String]
        }

        val status : Int = source.get("status") match {
          case Some(t) => t.asInstanceOf[Int]
          case None => 0
        }

        val document : KBDocument = KBDocument(id = id, conversation = conversation,
          index_in_conversation = index_in_conversation, question = question,
          answer = answer, verified = verified, topics = topics, doctype = doctype,
          state = state, status = status)

        val search_document : SearchKBDocument = SearchKBDocument(score = .0f, document = document)
        search_document
    }) }

    val filtered_doc : List[SearchKBDocument] = documents.getOrElse(List[SearchKBDocument]())

    val max_score : Float = .0f
    val total : Int = filtered_doc.length
    val search_results : SearchKBDocumentsResults = SearchKBDocumentsResults(total = total, max_score = max_score,
      hits = filtered_doc)

    val search_results_option : Future[Option[SearchKBDocumentsResults]] = Future { Option { search_results } }
    search_results_option
  }

}
