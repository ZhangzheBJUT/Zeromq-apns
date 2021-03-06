package com.icestar

import org.slf4j.LoggerFactory

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import com.icestar.utils.CommonUtils
import com.icestar.utils.RedisPool
import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.zeromq.Bind
import akka.zeromq.Connecting
import akka.zeromq.Frame
import akka.zeromq.Listener
import akka.zeromq.SocketType
import akka.zeromq.ZMQMessage
import akka.zeromq.zeromqSystem
import scalaj.collection.Imports.RichSMap
import scalaj.collection.Imports.RichSSeq

/**
 * Server boot class
 * @author IceStar
 */
object Server {
  //***************************CONSTANTS****************************//
  private val logger = LoggerFactory.getLogger(getClass)
  val conf = ConfigFactory.load()
  val cert_path = conf.getString("HttpServer.rootPath") + conf.getString("HttpServer.uploadPath")

  val POLICY_FILE = """<?xml version="1.0"?>
      |<cross-domain-policy>
    	|<site-control permitted-cross-domain-policies="all"/>
    	|<allow-access-from domain="*" to-ports="*" secure="false"/>
    	|<allow-http-request-headers-from domain="*" headers="*" secure="false"/>
	  |</cross-domain-policy>""".stripMargin
  val APN_APPS_MAP = "APN_APPS_MAP::"
  val BACKUP = "APN_BACKUP::"
  val PAYLOADS = "APN_PAYLOADS::"
  val TOKENS = "APN_TOKENS::"
  val URLS = "AD_URLS::"
  val URLS_ACTIVE_DATE = "AD_URLS_ACTDATE::"

  val debugMode: Boolean = conf.getString("apnserver.debugMode").toLowerCase() == "on"

  def apply(system: ActorSystem, address: String) = {
    system.actorOf(Props(new Server(address)), "Server")
  }

  def main(args: Array[String]) = {
    logger.info("Starting ZMQ-APNs server...")
    val system = ActorSystem("apnserver")
    //    Conf read args.head
    logger.info("Reading configure...")
    RedisPool.init(conf.getString("redis.host"), conf.getInt("redis.port"))
    val address = conf.getString("apnserver.address")
    println("ApnServer starting..., " + address)
    Server(system, address)
    HttpServer() start;
    println("[debugMode] = " + debugMode)
  }
}
class Server(val address: String) extends Actor with ActorLogging {
  private val repSocket = context.system.newSocket(SocketType.Rep, Bind(address), Listener(self))
  //**************************MSG COMMANDS*****************************// 
  private val CMD_SET_APP = """app (.+)::(.+)""".r
  private val CMD_DEL_APP = """delapp (.+)""".r
  private val CMD_EXIST_APP = """existapp (.+)""".r
  private val CMD_GET_APPS = "apps"
  private val CMD_SET_PAYLOAD = """payload (.+)::(.+)::(.+)""".r
  private val CMD_DEL_PAYLOAD = """delpayload (.+)::(.+)""".r
  private val CMD_GET_PAYLOADS = """payloads (.+)""".r
  private val CMD_START_PAYLOADS = """start (.+)""".r
  private val CMD_STOP_PAYLOADS = """stop (.+)""".r
  private val CMD_START_PAYLOAD = """start (.+)::(.+)""".r
  private val CMD_STOP_PAYLOAD = """stop (.+)::(.+)""".r
  private val CMD_GET_TOKENS = """tokens (.+)""".r
  private val CMD_GET_TOKENS_COUNT = """tokens_count (.+)""".r
  private val CMD_AUTOCLEAN_TOKENS = """autoclean_tokens (.+)""".r
  private val CMD_PUSH_MSG = """push (.+)::(.+)""".r
  private val CMD_SET_URLS = """urls (.+)::(.+)::(.+)""".r
  private val CMD_GET_URLS = """urls (.+)::(.+)""".r
  private val CMD_SET_URLS_ACTDATE = """url_act (.+)::(.+)""".r
  private val CMD_GET_URLS_ACTDATE = """url_act (.+)""".r

  override def preStart() = {
    log.debug("ZMQActor Starting")
  }
  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error(reason, "Restarting due to [{}] when processing [{}]",
      reason.getMessage, message.getOrElse(""))
  }

  def println(str: String) {
    if (Server.debugMode) {
      log info str;
      Console println str
    }
  }

  def receive = {
    case y => println(y + "")
  }
//  def receive = {
//    case Connecting => println("ZMQ-APNs Server connected")
//    case "<policy-file-request/>" =>
//      println("get policy file!")
//      sender ! Server.POLICY_FILE
//      context.stop(self)
//    case m: ZMQMessage =>
//      val msg = m.firstFrameAsString
//      println("[Receive]:: " + msg)
//      var cmd: String = ""
//      if (m.frames.length > 1) {
//        val tmp = m.frames(1)
//        cmd = tmp.payload.map(_ toChar).mkString
//        println("[CMD]:: " + cmd)
//      }
//      //      try {
//      msg match {
//        case CMD_GET_TOKENS(appId) =>
//          responseOK(cmd, getTokens(appId))
//        case CMD_GET_TOKENS_COUNT(appId) =>
//          // get appid stored tokens count
//          responseOK(cmd, RedisPool.hlen(Server.TOKENS + appId) toString)
//        case CMD_PUSH_MSG(appId, key) =>
//          // send msg to all the stored device tokens of the appId
//          val content = RedisPool.hget(Server.PAYLOADS + appId, key)
//          if (content != null) {
//            val data = JSON.parseObject(content)
//            val tokens = RedisPool.hkeys(Server.TOKENS + appId)
//            if (data != null && tokens != null) {
//              val payload = data getString "payload"
//              val cert = Server.cert_path + data.getString("cert")
//              val passwd = data getString "passwd"
//              if (payload != null && cert != null && passwd != null) {
//                val apn = Apn(appId, cert, passwd)
//                if (apn != null)
//                  tokens map (apn send (_, payload))
//              }
//            }
//          }
//        case x => x match {
//          case CMD_SET_APP(appId, data) =>
//            // set appid base data
//            RedisPool.hset(Server.APN_APPS_MAP, appId, data)
//            responseOK(cmd)
//          case CMD_GET_APPS =>
//            val data = RedisPool.hgetall(Server.APN_APPS_MAP)
//            //          val content: JSONObject = new JSONObject
//            //          data.foreach(e => {
//            //            content.put(e._1, e._2)
//            //          })
//            if (data != null) {
//              val content = JSON.toJSONString(data.asJava, SerializerFeature.PrettyFormat)
//              responseOK(cmd, content)
//            }
//          case CMD_EXIST_APP(appId) =>
//            responseOK(cmd, RedisPool.hexists(Server.APN_APPS_MAP, appId) + "")
//          case CMD_DEL_APP(appId) =>
//            // backup app details
//            RedisPool.hset(Server.BACKUP, appId, RedisPool.hget(Server.APN_APPS_MAP, appId))
//            RedisPool.hdel(Server.APN_APPS_MAP, appId)
//            // backup app payloads
//            RedisPool.hset(Server.BACKUP, Server.PAYLOADS + appId, getPayloads(appId))
//            RedisPool.del(Server.PAYLOADS + appId)
//            // backup app tokens
//            RedisPool.hset(Server.BACKUP, Server.TOKENS + appId, getTokens(appId))
//            RedisPool.del(Server.TOKENS + appId)
//            responseOK(cmd)
//          case x => x match {
//            case CMD_SET_PAYLOAD(key, appId, value) =>
//              // set appid payload data
//              RedisPool.hset(Server.PAYLOADS + appId, key, value)
//              println("Set payload [" + key + "] success!")
//              responseOK(cmd)
//            case CMD_DEL_PAYLOAD(appId, key) =>
//              MyScheduler(appId, key) delete;
//              println("Del payload [" + key + "] success!")
//              responseOK(cmd)
//            case CMD_GET_PAYLOADS(appId) =>
//              // get all payloads
//              responseOK(cmd, getPayloads(appId))
//            case CMD_START_PAYLOAD(appId, key) =>
//              MyScheduler(appId, key) start;
//              responseOK(cmd)
//            case CMD_STOP_PAYLOAD(appId, key) =>
//              MyScheduler(appId, key) stop;
//              responseOK(cmd)
//            case CMD_START_PAYLOADS(appId) =>
//              // start app's all the pushes
//              val keys = RedisPool.hkeys(Server.PAYLOADS + appId)
//              keys.map(MyScheduler(appId, _) stop)
//              responseOK(cmd)
//            case CMD_STOP_PAYLOADS(appId) =>
//              val keys = RedisPool.hkeys(Server.PAYLOADS + appId)
//              keys.map(MyScheduler(appId, _) stop)
//              responseOK(cmd)
//            case x => x match {
//              case CMD_AUTOCLEAN_TOKENS(appId) =>
//                if (RedisPool.hlen(Server.TOKENS + appId) <= 0) {
//                  responseOK(cmd)
//                } else {
//                  val data = RedisPool.hget(Server.APN_APPS_MAP, appId)
//                  if (data != null) {
//                    val content = JSON.parseObject(data)
//                    val conf = ConfigFactory.load()
//                    val apn = Apn(appId, Server.cert_path, content.getString("passwd"))
//                    if (apn != null) {
//                      apn.cleanInactiveDevies()
//                      responseOK(cmd)
//                    } else {
//                      responseFail(cmd)
//                    }
//                  }
//                }
//              case CMD_SET_URLS(appId, lang, urls) =>
//                RedisPool.hset(Server.URLS + appId, lang, urls)
//                responseOK(cmd, urls)
//              case CMD_GET_URLS(appId, lang) =>
//                responseOK(cmd, CommonUtils.getOrElse(RedisPool.hget(Server.URLS + appId, lang), "{\"lang\":\"" + lang + "\"}"))
//              case CMD_SET_URLS_ACTDATE(appId, date) =>
//                RedisPool.hset(Server.URLS_ACTIVE_DATE, appId, date)
//                responseOK(cmd)
//              case CMD_GET_URLS_ACTDATE(appId) =>
//                responseOK(cmd, RedisPool.hget(Server.URLS_ACTIVE_DATE, appId))
//              case x => log.warning("Received unknown message: {}", x)
//            }
//          }
//        }
//      }
//    //      } catch {
//    //        case e:Exception =>
//    //          log.error(e getStackTraceString)
//    //          e printStackTrace
//    //      }
//    case x => println("Received unknown message: {}" + x)
//  }

  /**
   * get app all payloads of the app
   * @param appId
   */
  private[this] def getPayloads(appId: String): String = {
    val data = RedisPool.hgetall(Server.PAYLOADS + appId)
    if (data != null && data != "") {
      return JSON.toJSONString(data asJava, SerializerFeature.PrettyFormat)
    }
    return null
  }

  /**
   * get all tokens of the app
   * @param appId
   * @return
   */
  private[this] def getTokens(appId: String): String = {
    val data = RedisPool.hkeys(Server.TOKENS + appId)
    if (data != null && data != "") {
      return JSON.toJSONString(data asJava, SerializerFeature.PrettyFormat)
    }
    return null
  }

  private[this] def responseOK(cmd: String, data: String = "") = {
    repSocket ! ZMQMessage(Seq(Frame("OK"), Frame(CommonUtils.getOrElse(cmd)), Frame(CommonUtils.getOrElse(data))))
  }

  private[this] def responseFail(cmd: String, data: String = "") = {
    repSocket ! ZMQMessage(Seq(Frame("Fail"), Frame(CommonUtils.getOrElse(cmd)), Frame(CommonUtils.getOrElse(data))))
  }
}
