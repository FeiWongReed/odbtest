package odbtest.settings
import scala.collection.JavaConverters._
import scala.collection.mutable

abstract class DbData {
  val dbname: String
  val remoteUrl: String
  val user: String
  val pass: String
  val poolSize: Int
  val concurrency: Int
  val opsPerThread: Int
  val dbtype: String

  override def toString = s"DbData(dbname=$dbname, remoteUrl=$remoteUrl, user=$user, pass=$pass, poolSize=$poolSize, concurrency=$concurrency, opsPerThread=$opsPerThread, dbtype=$dbtype)"
}

class LoadData extends DbData {
  private val env: mutable.Map[String, String] = System.getProperties.asScala
  val host = env.getOrElse("host", "localhost")
  override val dbname = env.getOrElse("dbname", "synco")
  override val user = env.getOrElse("user", "root")
  override val pass = env.getOrElse("pass", "pass")
  override val poolSize: Int = env.getOrElse("poolsize", "16").toInt
  override val dbtype = env.getOrElse("dbtype", "plocal")
  override val concurrency = env.getOrElse("threads", Runtime.getRuntime.availableProcessors().toString).toInt
  override val opsPerThread = env.getOrElse("ops", "1000").toInt

  override val remoteUrl = env.getOrElse("uri", s"remote:$host/$dbname")

}

class ExperimentData extends LoadData {
  override val dbtype = "memory"
  override val concurrency = Runtime.getRuntime.availableProcessors()
  override val opsPerThread: Int = 1000
}
