package com.getjenny.starchat.services

/**
  * Created by Angelo Leto <angelo@getjenny.com> on 01/07/16.
  */

import java.net.InetAddress

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.transport.client.PreBuiltTransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.{InetSocketTransportAddress, TransportAddress}

import scala.collection.immutable.{List, Map}
import scala.collection.JavaConverters._

trait ElasticClient {
  val config: Config = ConfigFactory.load()
  val index_name: String = config.getString("es.index_name")
  val cluster_name: String = config.getString("es.cluster_name")
  val ignore_cluster_name: Boolean = config.getBoolean("es.ignore_cluster_name")
  val index_language: String = config.getString("es.index_language")

  val host_map : Map[String, Int] = config.getAnyRef("es.host_map")
    .asInstanceOf[java.util.Map[String, Int]].asScala.toMap

  val settings: Settings = Settings.builder()
    .put("cluster.name", cluster_name)
    .put("client.transport.ignore_cluster_name", ignore_cluster_name)
    .put("client.transport.sniff", false).build()

  val inet_addresses: List[TransportAddress] =
    host_map.map{ case(k,v) => new InetSocketTransportAddress(InetAddress.getByName(k), v) }.toList

  var client : TransportClient = open_client()

  def open_client(): TransportClient = {
    val client: TransportClient = new PreBuiltTransportClient(settings)
      .addTransportAddresses(inet_addresses:_*)
    client
  }

  def get_client(): TransportClient = {
    this.client
  }

  def close_client(client: TransportClient): Unit = {
    client.close()
  }
}
